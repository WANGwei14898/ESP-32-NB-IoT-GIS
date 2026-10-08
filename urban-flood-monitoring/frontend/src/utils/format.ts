// 通用格式化与映射工具
import type { AlertLevel, DeviceStatus } from '@/types'

/** 预警等级中文文案 */
export const LEVEL_TEXT: Record<AlertLevel, string> = {
  NORMAL: '正常',
  BLUE: '蓝色预警',
  YELLOW: '黄色预警',
  ORANGE: '橙色预警',
  RED: '红色预警',
}

/** 预警等级颜色 */
export const LEVEL_COLOR: Record<AlertLevel, string> = {
  NORMAL: '#67C23A',
  BLUE: '#409EFF',
  YELLOW: '#E6A23C',
  ORANGE: '#F97316',
  RED: '#F56C6C',
}

/** 设备状态文案 */
export const STATUS_TEXT: Record<DeviceStatus, string> = {
  ONLINE: '在线',
  OFFLINE: '离线',
  FAULT: '故障',
}

/** 设备状态颜色 */
export const STATUS_COLOR: Record<DeviceStatus, string> = {
  ONLINE: '#67C23A',
  OFFLINE: '#909399',
  FAULT: '#F56C6C',
}

/** 格式化时间字符串 */
export function formatTime(ts?: string | number): string {
  if (!ts) return '-'
  const d = new Date(ts)
  if (Number.isNaN(d.getTime())) return '-'
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

/** 格式化短时间（仅时分） */
export function formatShortTime(ts?: string | number): string {
  if (!ts) return '-'
  const d = new Date(ts)
  if (Number.isNaN(d.getTime())) return '-'
  const p = (n: number) => String(n).padStart(2, '0')
  return `${p(d.getHours())}:${p(d.getMinutes())}`
}

/** 保留小数 */
export function toFixed(value: number, digits = 2): number {
  return Number(Number(value || 0).toFixed(digits))
}
