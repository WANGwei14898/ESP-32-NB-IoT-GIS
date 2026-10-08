// GIS 地图接口
import { request } from '@/utils/request'
import type { HeatPoint, InfluenceCircle, Station, WaterPoint } from '@/types'

/** 查询站点列表（含坐标与最新遥测） */
export function getStations() {
  return request<Station[]>({ url: '/gis/stations', method: 'get' })
}

/** 查询积水点 */
export function getWaterPoints() {
  return request<WaterPoint[]>({ url: '/gis/water-points', method: 'get' })
}

/** 查询影响范围圆 */
export function getInfluenceCircles() {
  return request<InfluenceCircle[]>({ url: '/gis/influence', method: 'get' })
}

/** 查询水位热力数据 */
export function getHeatmap() {
  return request<HeatPoint[]>({ url: '/gis/heatmap', method: 'get' })
}

