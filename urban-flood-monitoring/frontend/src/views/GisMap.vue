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
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import L from 'leaflet'
import type { HeatPoint, InfluenceCircle, Station, WaterPoint, AlertLevel, Prediction } from '@/types'
import { LEVEL_COLOR, LEVEL_TEXT, STATUS_TEXT, formatTime } from '@/utils/format'
import { getHeatmap, getInfluenceCircles, getStations, getWaterPoints } from '@/api/gis'
import { getPrediction } from '@/api/prediction'
import { realtimeSocket } from '@/utils/websocket'
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

// 站点 -> 预测结果 缓存（供弹窗展示）
const predictionMap = new Map<number, Prediction>()

const showStations = ref(true)
const showWaterPoints = ref(true)
const showHeat = ref(true)
const showInfluence = ref(true)

const levels: AlertLevel[] = ['BLUE', 'YELLOW', 'ORANGE', 'RED']

let stationLayer: L.LayerGroup | null = null
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

// 加载全部站点预测（供弹窗展示当前/预测水位）
async function loadPredictions() {
  for (const s of stations.value) {
    try {
      const p = await getPrediction({ stationId: s.id })
      predictionMap.set(s.id, p)
    } catch {
      /* 预测失败忽略 */
    }
  }
}

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
  await loadPredictions()
  renderStations()
  renderWaterPoints()
  renderInfluence()
}

// 渲染站点标注（命令式创建 Leaflet Marker）
function renderStations() {
  stationLayer?.clearLayers()
  if (!showStations.value) return
  stations.value.forEach((s) => {
    const marker = L.marker([s.latitude, s.longitude], { icon: createStationIcon(s) })
      .addTo(stationLayer!)
      .bindPopup(buildStationPopup(s))
    marker.on('click', () => onSelect(s))
  })
}

// 数据新鲜度阈值：最新数据超过该时长视为离线（毫秒）
const OFFLINE_THRESHOLD_MS = 5 * 60 * 1000

// 判断站点是否离线（状态非 ONLINE，或无最新数据，或数据过旧）
function isOffline(s: Station): boolean {
  if (s.status !== 'ONLINE') return true
  const t = s.latest
  if (!t) return true
  const ts = new Date(t.timestamp).getTime()
  if (Number.isNaN(ts)) return true
  return Date.now() - ts > OFFLINE_THRESHOLD_MS
}

// 数据来源标签
function sourceText(s: Station): string {
  return s.latest?.source === 'simulator' ? '模拟器' : '真实设备'
}

// 离线文案：模拟器离线 / 设备离线
function offlineText(s: Station): string {
  return s.latest?.source === 'simulator' ? '模拟器离线' : '设备离线'
}

// 生成带来源区分的站点 divIcon（真实=预警色，模拟=灰色）
function createStationIcon(s: Station): L.DivIcon {
  const isSimulated = s.latest?.source === 'simulator'
  // 真实数据按预警等级着色（蓝/黄/橙/红），模拟数据用灰色
  const color = isSimulated ? '#909399' : (LEVEL_COLOR[s.alertLevel] || '#67C23A')
  const offline = isOffline(s)
  return L.divIcon({
    className: 'station-marker',
    html: `
      <div class="station-marker__pin" style="background:${color};${offline ? 'opacity:0.45;' : ''}"></div>
      <div class="station-marker__label">${s.name}${isSimulated ? ' (模拟)' : ''}</div>
    `,
    iconSize: [14, 14],
    iconAnchor: [7, 7],
    popupAnchor: [0, -12],
  })
}

// 站点弹窗内容（显示站点编号、数据来源、当前水位与预测水位）
function buildStationPopup(s: Station): string {
  const t = s.latest
  const offline = isOffline(s)
  const pred = predictionMap.get(s.id)
  const rows = [
    ['站点编号', s.code],
    ['所属区域', s.region || '-'],
    ['数据来源', sourceText(s)],
    ['设备状态', offline ? offlineText(s) : (STATUS_TEXT[s.status] || '-')],
    ['预警等级', LEVEL_TEXT[s.alertLevel] || '正常'],
  ]
  if (t && !offline) {
    rows.push(['当前水位', `<span style="font-weight:700;color:#409eff;border-bottom:2px solid #409eff">${t.waterLevel} m</span>`])
    rows.push(['雨量', `${t.rainfall} mm`])
    rows.push(['流速', `${t.flowVelocity} m/s`])
    rows.push(['采集时间', formatTime(t.timestamp)])
  }
  if (pred) {
    rows.push(['预测 30 分钟后', `<span style="color:#f97316;border-bottom:2px dashed #f97316">${pred.predicted30m ?? '-'} m</span>`])
    rows.push(['预测 60 分钟后', `<span style="color:#f97316;border-bottom:2px dashed #f97316">${pred.predicted60m ?? '-'} m</span>`])
  }
  const body = rows.map(([k, v]) => `<div class="kv"><span>${k}</span><span>${v}</span></div>`).join('')
  return `<div class="station-popup"><h4>${s.name}</h4>${body}</div>`
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
    renderStations()
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
watch(showStations, () => renderStations())
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
  stationLayer = L.layerGroup().addTo(m)
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
.map-container {
  height: 100%;
  min-height: 600px;
  width: 100%;
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

