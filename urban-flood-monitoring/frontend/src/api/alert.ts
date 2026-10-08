// 告警管理接口
import { request } from '@/utils/request'
import type { Alert, AlertLevel, PushRecord, PageResult } from '@/types'

export interface AlertQuery {
  page?: number
  size?: number
  level?: AlertLevel | ''
  status?: string
  stationId?: number
  start?: string
  end?: string
}

export interface AlertStats {
  total: number
  active: number
  handled: number
  blue: number
  yellow: number
  orange: number
  red: number
}

/** 分页查询告警 */
export function getAlerts(params: AlertQuery) {
  return request<PageResult<Alert>>({ url: '/alerts', method: 'get', params })
}

/** 查询未处理告警 */
export function getActiveAlerts() {
  return request<Alert[]>({ url: '/alerts/active', method: 'get' })
}

/** 告警统计 */
export function getAlertStats() {
  return request<AlertStats>({ url: '/alerts/statistics', method: 'get' })
}

/** 处理告警 */
export function handleAlert(id: number, data: { handler?: string; remark?: string }) {
  return request<Alert>({ url: `/alerts/${id}/handle`, method: 'put', data })
}

/** 查询告警推送记录 */
export function getAlertPushes(id: number) {
  return request<PushRecord[]>({ url: `/alerts/${id}/pushes`, method: 'get' })
}

