// 推送记录（通知中心）接口
import { request } from '@/utils/request'
import type { PageResult, PushRecord } from '@/types'

/** 推送历史查询参数 */
export interface PushLogQuery {
  page?: number
  size?: number
  channel?: string
  status?: string
  alertId?: number
}

/** 推送统计 */
export interface PushStatistics {
  total: number
  success: number
  failed: number
}

/** 分页查询推送历史（通知中心） */
export function getPushLogs(params: PushLogQuery) {
  return request<PageResult<PushRecord>>({ url: '/push-logs', method: 'get', params })
}

/** 推送统计（总数 / 成功 / 失败） */
export function getPushStatistics() {
  return request<PushStatistics>({ url: '/push-logs/statistics', method: 'get' })
}
