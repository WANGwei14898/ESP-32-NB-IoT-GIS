<template>
  <div class="twin">
    <!-- 三维场景 -->
    <div ref="sceneRef" class="twin__scene"></div>

    <!-- 右侧图表联动 -->
    <div class="twin__panel">
      <ChartCard title="实时最高水位" :option="gaugeOption" height="220px" />
      <ChartCard title="预警等级分布" :option="pieOption" height="220px" />
      <ChartCard title="实时水位趋势" :option="trendOption" height="260px" />
    </div>

    <!-- 图例 -->
    <div class="twin__legend panel-card">
      <div class="twin__legend-title">预警等级</div>
      <div v-for="lvl in levels" :key="lvl" class="twin__legend-row">
        <span class="twin__dot" :style="{ background: LEVEL_COLOR[lvl] }"></span>{{ LEVEL_TEXT[lvl] }}
      </div>
      <div class="twin__hint">柱体高度 = 水位，颜色 = 预警等级</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import * as THREE from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
import type { EChartsOption } from 'echarts'
import type { AlertLevel, Station } from '@/types'
import { LEVEL_COLOR, LEVEL_TEXT, formatShortTime } from '@/utils/format'
import { getStations } from '@/api/gis'
import { realtimeSocket } from '@/utils/websocket'
import ChartCard from '@/components/ChartCard.vue'

const sceneRef = ref<HTMLDivElement>()
const stations = ref<Station[]>([])
const levels: AlertLevel[] = ['BLUE', 'YELLOW', 'ORANGE', 'RED']

// 实时趋势缓冲（最近 30 条）
const trendData = ref<{ time: string; value: number }[]>([])

// Three.js 对象
let scene: THREE.Scene | null = null
let camera: THREE.PerspectiveCamera | null = null
let renderer: THREE.WebGLRenderer | null = null
let controls: OrbitControls | null = null
let rafId = 0
const meshes = new Map<number, THREE.Mesh>()
const labels = new Map<number, THREE.Sprite>()

// 初始化三维场景
function initThree(el: HTMLDivElement) {
  scene = new THREE.Scene()
  scene.background = new THREE.Color(0x0b1b33)

  camera = new THREE.PerspectiveCamera(50, el.clientWidth / el.clientHeight, 0.1, 3000)
  camera.position.set(120, 120, 160)

  renderer = new THREE.WebGLRenderer({ antialias: true })
  renderer.setSize(el.clientWidth, el.clientHeight)
  renderer.setPixelRatio(window.devicePixelRatio)
  el.appendChild(renderer.domElement)

  controls = new OrbitControls(camera, renderer.domElement)
  controls.enableDamping = true
  controls.maxPolarAngle = Math.PI / 2.1

  scene.add(new THREE.AmbientLight(0xffffff, 0.7))
  const dir = new THREE.DirectionalLight(0xffffff, 1)
  dir.position.set(120, 240, 120)
  scene.add(dir)

  const grid = new THREE.GridHelper(400, 20, 0x33507a, 0x1d3353)
  scene.add(grid)
  const ground = new THREE.Mesh(
    new THREE.PlaneGeometry(400, 400),
    new THREE.MeshStandardMaterial({ color: 0x0e2240 }),
  )
  ground.rotation.x = -Math.PI / 2
  ground.position.y = -0.6
  scene.add(ground)
}

// 生成站点名称标签（Canvas 精灵）
function makeLabel(text: string): THREE.Sprite {
  const canvas = document.createElement('canvas')
  canvas.width = 256
  canvas.height = 64
  const ctx = canvas.getContext('2d')!
  ctx.fillStyle = '#ffffff'
  ctx.font = 'bold 26px sans-serif'
  ctx.textAlign = 'center'
  ctx.textBaseline = 'middle'
  ctx.fillText(text, 128, 32)
  const tex = new THREE.CanvasTexture(canvas)
  const sprite = new THREE.Sprite(new THREE.SpriteMaterial({ map: tex }))
  sprite.scale.set(26, 6.5, 1)
  return sprite
}

// 计算站点三维坐标（经纬度归一化到平面）
function toPosition(s: Station, center: { lat: number; lng: number }) {
  const scale = 900 // 经纬度缩放系数
  return {
    x: (s.longitude - center.lng) * scale,
    z: -(s.latitude - center.lat) * scale,
  }
}

function heightOf(s: Station): number {
  return 3 + Math.max(0, (s.latest?.waterLevel || 0)) * 0.8
}

// 构建站点三维模型
function buildScene(list: Station[]) {
  if (!scene) return
  // 清除旧模型
  meshes.forEach((m) => scene!.remove(m))
  labels.forEach((l) => scene!.remove(l))
  meshes.clear()
  labels.clear()

  const center = {
    lat: list.reduce((a, s) => a + s.latitude, 0) / (list.length || 1),
    lng: list.reduce((a, s) => a + s.longitude, 0) / (list.length || 1),
  }

  list.forEach((s) => {
    const { x, z } = toPosition(s, center)
    const h = heightOf(s)
    const color = LEVEL_COLOR[s.alertLevel] || '#67C23A'
    const geo = new THREE.CylinderGeometry(4, 4, 1, 24)
    const mat = new THREE.MeshStandardMaterial({ color, emissive: color, emissiveIntensity: 0.25 })
    const mesh = new THREE.Mesh(geo, mat)
    mesh.position.set(x, h / 2, z)
    mesh.scale.y = h
    scene!.add(mesh)
    meshes.set(s.id, mesh)

    const label = makeLabel(s.name)
    label.position.set(x, h + 12, z)
    scene!.add(label)
    labels.set(s.id, label)
  })
}

// 更新单个站点模型（高度与颜色联动）
function updateMesh(s: Station) {
  const mesh = meshes.get(s.id)
  const label = labels.get(s.id)
  if (!mesh || !label) return
  const h = heightOf(s)
  mesh.scale.y = h
  mesh.position.y = h / 2
  const color = LEVEL_COLOR[s.alertLevel] || '#67C23A'
  ;(mesh.material as THREE.MeshStandardMaterial).color.set(color)
  ;(mesh.material as THREE.MeshStandardMaterial).emissive.set(color)
  label.position.y = h + 12
}

// 动画循环
function animate() {
  rafId = requestAnimationFrame(animate)
  controls?.update()
  if (scene && camera && renderer) renderer.render(scene, camera)
}

function resize() {
  if (!renderer || !camera || !sceneRef.value) return
  const w = sceneRef.value.clientWidth
  const h = sceneRef.value.clientHeight
  camera.aspect = w / h
  camera.updateProjectionMatrix()
  renderer.setSize(w, h)
}

// 实时遥测：更新水位与趋势
function onTelemetry(data: any) {
  if (!data || data.stationId == null) return
  const s = stations.value.find((x) => x.id === data.stationId)
  if (s) {
    s.latest = { ...s.latest, ...data } as Station['latest']
    updateMesh(s)
  }
  trendData.value = [...trendData.value, { time: formatShortTime(Date.now()), value: data.waterLevel ?? 0 }].slice(-30)
}

// 实时告警：更新站点预警等级（颜色）
function onAlert(data: any) {
  if (!data || data.stationId == null) return
  const s = stations.value.find((x) => x.id === data.stationId)
  if (s && data.level) {
    s.alertLevel = data.level
    updateMesh(s)
  }
}

async function loadStations() {
  stations.value = await getStations()
  buildScene(stations.value)
}

// 仪表盘：实时最高水位
const gaugeOption = computed<EChartsOption>(() => {
  const max = Math.max(0, ...stations.value.map((s) => s.latest?.waterLevel || 0))
  return {
    series: [
      {
        type: 'gauge',
        min: 0,
        max: 100,
        progress: { show: true, width: 12 },
        axisLine: { lineStyle: { width: 12 } },
        pointer: { width: 4 },
        detail: { formatter: '{value} cm', fontSize: 20 },
        data: [{ value: Number(max.toFixed(1)), name: '最高水位' }],
      },
    ],
  }
})

// 饼图：预警等级分布
const pieOption = computed<EChartsOption>(() => {
  const counts: Record<string, number> = { BLUE: 0, YELLOW: 0, ORANGE: 0, RED: 0 }
  stations.value.forEach((s) => {
    if (s.alertLevel !== 'NORMAL') counts[s.alertLevel]++
  })
  const data = levels
    .map((l) => ({ name: LEVEL_TEXT[l], value: counts[l] }))
    .filter((d) => d.value > 0)
  return {
    tooltip: { trigger: 'item' },
    legend: { bottom: 0 },
    color: levels.map((l) => LEVEL_COLOR[l]),
    series: [{ type: 'pie', radius: ['40%', '65%'], data, label: { formatter: '{b}: {c}' } }],
  }
})

// 折线：实时水位趋势
const trendOption = computed<EChartsOption>(() => ({
  tooltip: { trigger: 'axis' },
  grid: { left: 40, right: 16, top: 20, bottom: 30 },
  xAxis: { type: 'category', data: trendData.value.map((t) => t.time) },
  yAxis: { type: 'value', name: 'cm' },
  series: [
    {
      type: 'line',
      smooth: true,
      showSymbol: false,
      areaStyle: { opacity: 0.2 },
      lineStyle: { color: '#67c23a', width: 2 },
      itemStyle: { color: '#67c23a' },
      data: trendData.value.map((t) => t.value),
    },
  ],
}))

onMounted(() => {
  initThree(sceneRef.value as HTMLDivElement)
  animate()
  window.addEventListener('resize', resize)
  loadStations()
  realtimeSocket.on('telemetry', onTelemetry)
  realtimeSocket.on('alert', onAlert)
  realtimeSocket.connect()
})

onBeforeUnmount(() => {
  cancelAnimationFrame(rafId)
  window.removeEventListener('resize', resize)
  realtimeSocket.off('telemetry', onTelemetry)
  realtimeSocket.off('alert', onAlert)
  renderer?.dispose()
  renderer = null
  scene = null
})
</script>

<style scoped>
.twin {
  position: relative;
  height: calc(100vh - 92px);
  min-height: 520px;
  display: flex;
  gap: 16px;
}
.twin__scene {
  flex: 1;
  border-radius: 8px;
  overflow: hidden;
  background: #0b1b33;
}
.twin__panel {
  width: 340px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  overflow: auto;
}
.twin__legend {
  position: absolute;
  left: 16px;
  bottom: 16px;
  background: rgba(11, 27, 51, 0.9);
  color: #fff;
  padding: 12px 16px;
}
.twin__legend-title {
  font-weight: 600;
  margin-bottom: 8px;
}
.twin__legend-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  line-height: 1.9;
}
.twin__dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  display: inline-block;
}
.twin__hint {
  margin-top: 8px;
  font-size: 12px;
  color: #a3b1cc;
}
</style>

