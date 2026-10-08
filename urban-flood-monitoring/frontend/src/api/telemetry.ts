// 遥测数据接口（水位/雨量/流速）
import { request } from '@/utils/request'
import type { Telemetry } from '@/types'

export interface TelemetryQuery {
  stationId?: number
  metric?: 'waterLevel' | 'rainfall' | 'flowVelocity'
  start?: string
  end?: string
  limit?: number
}

/** 查询站点最新遥测 */
export function getLatest(stationId?: number) {
  return request<Telemetry | Telemetry[]>({
    url: '/telemetry/latest',
    method: 'get',
    params: { stationId },
  })
}

/** 查询历史遥测序列 */
export function getHistory(params: TelemetryQuery) {
  return request<Telemetry[]>({ url: '/telemetry/history', method: 'get', params })
}

/** 查询全部站点实时快照 */
export function getRealtime() {
  return request<Telemetry[]>({ url: '/telemetry/realtime', method: 'get' })
}

