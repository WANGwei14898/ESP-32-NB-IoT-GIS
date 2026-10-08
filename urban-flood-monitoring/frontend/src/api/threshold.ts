// 阈值与协议配置接口
import { request } from '@/utils/request'
import type { CoapConfig, MqttConfig, Threshold } from '@/types'

/** 查询阈值配置 */
export function getThresholds() {
  return request<Threshold[]>({ url: '/thresholds', method: 'get' })
}

/** 保存阈值配置 */
export function updateThresholds(data: Threshold[]) {
  return request<void>({ url: '/thresholds', method: 'put', data })
}

/** 查询 MQTT 配置 */
export function getMqttConfig() {
  return request<MqttConfig>({ url: '/config/mqtt', method: 'get' })
}

/** 查询 CoAP 配置 */
export function getCoapConfig() {
  return request<CoapConfig>({ url: '/config/coap', method: 'get' })
}
