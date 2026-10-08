<template>
  <div class="gis">
    <div ref="mapRef" class="map-container"></div>

    <!-- 图层控制 -->
    <div class="gis__panel panel-card">
      <div class="gis__panel-title">图层控制</div>
      <el-checkbox v-model="showStations">站点标注</el-checkbox>
      <el-checkbox v-model="showWaterPoints">积水点</el-checkbox>
      <el-checkbox v-model="showHeat">水位热力</el-checkbox>
      <el-checkbox v-model="showInfluence">影响范围</el-checkbox>
    </div>

    <!-- 图例 -->
    <div class="gis__legend panel-card">
      <div class="gis__panel-title">预警等级</div>
      <div v-for="lvl in levels" :key="lvl" class="gis__legend-row">
        <span class="gis__legend-dot" :style="{ background: LEVEL_COLOR[lvl] }"></span>
        <span>{{ LEVEL_TEXT[lvl] }}</span>
      </div>
    </div>

    <!-- 选中站点详情 -->
    <div v-if="selected" class="gis__info panel-card">
      <div class="gis__panel-title">站点信息</div>
      <div class="gis__info-row"><span>名称</span><span>{{ selected.name }}</span></div>
      <div class="gis__info-row"><span>编号</span><span>{{ selected.code }}</span></div>
      <div class="gis__info-row"><span>预警</span><AlertBadge :level="selected.alertLevel" /></div>
      <div v-if="selected.latest" class="gis__info-row"><span>水位</span><span>{{ selected.latest.waterLevel }} cm</span></div>
      <div v-if="selected.latest" class="gis__info-row"><span>雨量</span><span>{{ selected.latest.rainfall }} mm</span></div>
      <div v-if="selected.latest" class="gis__info-row"><span>流速</span><span>{{ selected.latest.flowVelocity }} m/s</span></div>
    </div>

    <!-- 站点标注 -->
    <template v-if="map && showStations">
      <StationMarker v-for="s in stations" :key="s.id" :station="s" :map="map" @select="onSelect" />
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import L from 'leaflet'
import type { HeatPoint, InfluenceCircle, Station, WaterPoint, AlertLevel } from '@/types'
import { LEVEL_COLOR, LEVEL_TEXT } from '@/utils/format'
import { getHeatmap, getInfluenceCircles, getStations, getWaterPoints } from '@/api/gis'
import { realtimeSocket } from '@/utils/websocket'
import StationMarker from '@/components/StationMarker.vue'
import AlertBadge from '@/components/AlertBadge.vue'

// 水位热力图层（Canvas 径向渐变叠加）
class HeatLayer extends L.Layer {
  private canvas: HTMLCanvasElement | null = null
  private points: HeatPoint[] = []
  private map: L.Map | null = null

  onAdd(map: L.Map): this {
    this.map = map
    const canvas = L.DomUtil.create('canvas', 'leaflet-heat-layer') as HTMLCanvasElement
    canvas.style.position = 'absolute'
    canvas.style.pointerEvents = 'none'
    canvas.style.zIndex = '400'
    this.canvas = canvas
    map.getPanes().overlayPane.appendChild(canvas)
    map.on('move zoom resize', this.redraw)
    this.redraw()
    return this
  }

  onRemove(map: L.Map): this {
    map.off('move zoom resize', this.redraw)
    if (this.canvas?.parentNode) this.canvas.parentNode.removeChild(this.canvas)
    this.canvas = null
    this.map = null
    return this
  }

  setPoints(points: HeatPoint[]): void {
    this.points = points
    this.redraw()
  }

  private redraw = (): void => {
    const canvas = this.canvas
    const map = this.map
    if (!canvas || !map) return
    const size = map.getSize()
    canvas.width = size.x
    canvas.height = size.y
    const ctx = canvas.getContext('2d')
    if (!ctx) return
    ctx.clearRect(0, 0, size.x, size.y)
    ctx.globalCompositeOperation = 'lighter'
    for (const p of this.points) {
      const pt = map.latLngToContainerPoint([p.latitude, p.longitude])
      if (pt.x < -80 || pt.y < -80 || pt.x > size.x + 80 || pt.y > size.y + 80) continue
      const r = 30 + Math.min(60, p.value * 0.6)
      const hue = 120 - Math.min(120, (p.value / 100) * 120)
      const grad = ctx.createRadialGradient(pt.x, pt.y, 0, pt.x, pt.y, r)
      grad.addColorStop(0, `hsla(${hue}, 90%, 50%, 0.55)`)
      grad.addColorStop(1, 'hsla(0, 0%, 0%, 0)')
      ctx.fillStyle = grad
      ctx.beginPath()
      ctx.arc(pt.x, pt.y, r, 0, Math.PI * 2)
      ctx.fill()
    }
    ctx.globalCompositeOperation = 'source-over'
  }
}

const mapRef = ref<HTMLDivElement>()
const map = ref<L.Map | null>(null)

const stations = ref<Station[]>([])
const waterPoints = ref<WaterPoint[]>([])
const influences = ref<InfluenceCircle[]>([])
const selected = ref<Station | null>(null)

const showStations = ref(true)
const showWaterPoints = ref(true)
const showHeat = ref(true)
const showInfluence = ref(true)

const levels: AlertLevel[] = ['BLUE', 'YELLOW', 'ORANGE', 'RED']

let waterLayer: L.LayerGroup | null = null
let influenceLayer: L.LayerGroup | null = null
let heatLayer: HeatLayer | null = null

// 由站点最新水位派生的热力数据
const heatPoints = computed<HeatPoint[]>(() =>
  stations.value
    .filter((s) => s.latest)
    .map((s) => ({
      longitude: s.longitude,
      latitude: s.latitude,
      value: s.latest!.waterLevel,
    })),
)

// 加载全部 GIS 数据
async function loadData() {
  try {
    const [stationList, wp, inf, heat] = await Promise.all([
      getStations(),
      getWaterPoints(),
      getInfluenceCircles(),
      getHeatmap(),
    ])
    stations.value = stationList
    waterPoints.value = wp
    influences.value = inf
    if (heat.length) heatLayer?.setPoints(heat)
  } catch {
    /* 错误已统一提示 */
  }
  renderWaterPoints()
  renderInfluence()
}

// 渲染积水点
function renderWaterPoints() {
  waterLayer?.clearLayers()
  if (!showWaterPoints.value) return
  waterPoints.value.forEach((wp) => {
    L.circleMarker([wp.latitude, wp.longitude], {
      radius: 5 + Math.min(10, wp.depth * 0.2),
      color: '#1d6ef5',
      fillColor: '#409eff',
      fillOpacity: 0.7,
    })
      .bindPopup(`<div class="station-popup"><h4>${wp.name}</h4><div class="kv"><span>积水深度</span><span>${wp.depth} cm</span></div></div>`)
      .addTo(waterLayer!)
  })
}

// 渲染影响范围圆
function renderInfluence() {
  influenceLayer?.clearLayers()
  if (!showInfluence.value) return
  influences.value.forEach((c) => {
    const color = LEVEL_COLOR[c.level] || '#409eff'
    L.circle([c.latitude, c.longitude], {
      radius: c.radius,
      color,
      weight: 1.5,
      fillColor: color,
      fillOpacity: 0.15,
    })
      .bindPopup(`<div class="station-popup"><h4>${c.stationName}</h4><div class="kv"><span>影响半径</span><span>${c.radius} m</span></div></div>`)
      .addTo(influenceLayer!)
  })
}

// 实时遥测更新
function onTelemetry(data: any) {
  if (!data || data.stationId == null) return
  const s = stations.value.find((x) => x.id === data.stationId)
  if (s) {
    s.latest = { ...s.latest, ...data } as Station['latest']
  }
}

// 选中站点
function onSelect(s: Station) {
  selected.value = s
  map.value?.flyTo([s.latitude, s.longitude], 15)
}

// 图层开关联动
watch([showWaterPoints, showInfluence], () => {
  renderWaterPoints()
  renderInfluence()
})
watch(showHeat, (v) => {
  if (!heatLayer) return
  if (v) heatLayer.setPoints(heatPoints.value)
  else heatLayer.setPoints([])
})
watch(heatPoints, (pts) => {
  if (showHeat.value) heatLayer?.setPoints(pts)
})

onMounted(() => {
  const m = L.map(mapRef.value as HTMLDivElement, { center: [30.2741, 120.1551], zoom: 13 })
  L.tileLayer('https://webrd0{s}.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}', {
    subdomains: '1234',
    maxZoom: 18,
    attribution: '&copy; 高德地图',
  }).addTo(m)
  map.value = m
  waterLayer = L.layerGroup().addTo(m)
  influenceLayer = L.layerGroup().addTo(m)
  heatLayer = new HeatLayer().addTo(m)

  loadData()
  realtimeSocket.on('telemetry', onTelemetry)
  realtimeSocket.connect()
})

onBeforeUnmount(() => {
  realtimeSocket.off('telemetry', onTelemetry)
  heatLayer?.remove()
  map.value?.remove()
  map.value = null
})
</script>

<style scoped>
.gis {
  position: relative;
  height: calc(100vh - 92px);
  min-height: 480px;
}
.gis__panel,
.gis__legend,
.gis__info {
  position: absolute;
  z-index: 1000;
  background: rgba(255, 255, 255, 0.95);
  padding: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
}
.gis__panel {
  top: 12px;
  right: 12px;
  width: 140px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.gis__legend {
  bottom: 24px;
  left: 12px;
  min-width: 130px;
}
.gis__info {
  top: 12px;
  left: 12px;
  min-width: 200px;
}
.gis__panel-title {
  font-weight: 600;
  margin-bottom: 6px;
}
.gis__legend-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}
.gis__legend-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  display: inline-block;
}
.gis__info-row {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  font-size: 13px;
  line-height: 1.9;
}
.gis__info-row span:last-child {
  font-weight: 600;
}
</style>

