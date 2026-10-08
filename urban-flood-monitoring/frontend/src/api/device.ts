// 设备管理接口
import { request } from '@/utils/request'
import type { Device, PageResult } from '@/types'

export interface DeviceQuery {
  page?: number
  size?: number
  keyword?: string
  status?: string
}

export interface DeviceStats {
  online: number
  offline: number
  fault: number
  total: number
}

/** 分页查询设备列表 */
export function getDevices(params: DeviceQuery) {
  return request<PageResult<Device>>({ url: '/devices', method: 'get', params })
}

/** 查询设备详情 */
export function getDevice(id: number) {
  return request<Device>({ url: `/devices/${id}`, method: 'get' })
}

/** 设备状态统计 */
export function getDeviceStats() {
  return request<DeviceStats>({ url: '/devices/stats', method: 'get' })
}

/** 新增设备 */
export function createDevice(data: Partial<Device>) {
  return request<Device>({ url: '/devices', method: 'post', data })
}

/** 更新设备 */
export function updateDevice(id: number, data: Partial<Device>) {
  return request<Device>({ url: `/devices/${id}`, method: 'put', data })
}

/** 删除设备 */
export function deleteDevice(id: number) {
  return request<void>({ url: `/devices/${id}`, method: 'delete' })
}

