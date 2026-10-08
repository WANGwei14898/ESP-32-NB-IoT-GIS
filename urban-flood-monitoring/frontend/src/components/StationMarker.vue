<script setup lang="ts">
import { onBeforeUnmount, onMounted, watch } from 'vue'
import L from 'leaflet'
import type { Station } from '@/types'
import { LEVEL_COLOR, LEVEL_TEXT, STATUS_TEXT, formatTime } from '@/utils/format'

/**
 * 站点标注（无模板组件）：将 Station 渲染为 Leaflet 标注，
 * 颜色随预警等级与在线状态变化，点击触发 select 事件。
 */
const props = defineProps<{
  station: Station
  map: L.Map
}>()

const emit = defineEmits<{
  (e: 'select', station: Station): void
}>()

let marker: L.Marker | null = null

// 生成带等级颜色的 divIcon
function createIcon(): L.DivIcon {
  const color = LEVEL_COLOR[props.station.alertLevel] || '#67C23A'
  const offline = props.station.status !== 'ONLINE'
  return L.divIcon({
    className: 'station-marker',
    html: `
      <div class="station-marker__pin" style="background:${color};${offline ? 'opacity:0.45;' : ''}"></div>
      <div class="station-marker__label">${props.station.name}</div>
    `,
    iconSize: [14, 14],
    iconAnchor: [7, 7],
    popupAnchor: [0, -12],
  })
}

// 弹窗内容
function buildPopup(): string {
  const s = props.station
  const t = s.latest
  const rows = [
    ['站点编号', s.code],
    ['所属区域', s.region || '-'],
    ['设备状态', STATUS_TEXT[s.status] || '-'],
    ['预警等级', LEVEL_TEXT[s.alertLevel] || '正常'],
  ]
  if (t) {
    rows.push(
      ['水位', `${t.waterLevel} cm`],
      ['雨量', `${t.rainfall} mm`],
      ['流速', `${t.flowVelocity} m/s`],
      ['采集时间', formatTime(t.timestamp)],
    )
  }
  const body = rows.map(([k, v]) => `<div class="kv"><span>${k}</span><span>${v}</span></div>`).join('')
  return `<div class="station-popup"><h4>${s.name}</h4>${body}</div>`
}

onMounted(() => {
  marker = L.marker([props.station.latitude, props.station.longitude], {
    icon: createIcon(),
  })
    .addTo(props.map)
    .bindPopup(buildPopup())
  marker.on('click', () => emit('select', props.station))
})

// 等级 / 状态变化时刷新图标与弹窗
watch(
  () => [props.station.alertLevel, props.station.status, props.station.latest],
  () => {
    marker?.setIcon(createIcon())
    marker?.setPopupContent(buildPopup())
  },
  { deep: true },
)

onBeforeUnmount(() => {
  marker?.remove()
  marker = null
})
</script>

