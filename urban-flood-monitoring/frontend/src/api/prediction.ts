// 水位预测接口
import { request } from '@/utils/request'
import type { Prediction } from '@/types'

export interface PredictionQuery {
  stationId: number
  minutes?: number
  model?: string
}

/** 查询预测结果（未来 30~60 分钟水位曲线） */
export function getPrediction(params: PredictionQuery) {
  return request<Prediction>({ url: '/prediction', method: 'get', params })
}

/** 查询可用预测模型 */
export function getModels() {
  return request<string[]>({ url: '/prediction/models', method: 'get' })
}

/** 触发一次预测计算 */
export function runPrediction(data: { stationId: number; minutes?: number; model?: string }) {
  return request<Prediction>({ url: '/prediction/run', method: 'post', data })
}

