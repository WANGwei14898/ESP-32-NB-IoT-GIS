// 全局共享类型定义（与后端实体/DTO 对应）

/** 预警等级 */
export type AlertLevel = 'NORMAL' | 'BLUE' | 'YELLOW' | 'ORANGE' | 'RED'
/** 设备状态 */
export type DeviceStatus = 'ONLINE' | 'OFFLINE' | 'FAULT'
/** 告警处理状态 */
export type AlertStatus = 'ACTIVE' | 'HANDLED'

/** 登录用户信息 */
export interface UserInfo {
  id: number
  username: string
  nickname?: string
  role?: string
}

/** 监测站点 */
export interface Station {
  id: number
  code: string
  name: string
  longitude: number
  latitude: number
  address?: string
  region?: string
  deviceId?: number
  status: DeviceStatus
  installTime?: string
  /** 最新遥测数据（GIS/大屏联动使用） */
  latest?: Telemetry
  /** 当前预警等级 */
  alertLevel: AlertLevel
}

/** 监测设备 */
export interface Device {
  id: number
  code: string
  name: string
  stationId?: number
  stationName?: string
  type: string
  protocol: string
  status: DeviceStatus
  lastHeartbeat?: string
  battery?: number
  signalStrength?: number
  firmware?: string
  installTime?: string
}

/** 遥测数据（水位/雨量/流速） */
export interface Telemetry {
  id: number
  stationId: number
  deviceId?: number
  waterLevel: number
  rainfall: number
  flowVelocity: number
  battery?: number
  signalStrength?: number
  timestamp: string
}

/** 告警推送记录 */
export interface PushRecord {
  id: number
  alertId: number
  channel: string
  target: string
  content: string
  status: string
  pushTime: string
}

/** 告警信息 */
export interface Alert {
  id: number
  stationId: number
  stationName?: string
  level: AlertLevel
  type: string
  message: string
  waterLevel?: number
  threshold?: number
  status: AlertStatus
  handler?: string
  handledTime?: string
  createTime: string
  pushRecords?: PushRecord[]
}

/** 预测曲线点 */
export interface PredictionPoint {
  time: string
  value: number
  lower?: number
  upper?: number
}

/** 水位预测结果 */
export interface Prediction {
  id?: number
  stationId: number
  modelType: string
  predictTime: string
  points: PredictionPoint[]
}

/** 告警阈值配置 */
export interface Threshold {
  id: number
  code: string
  name: string
  thresholdValue: number
  unit: string
  level: AlertLevel
  enabled: boolean
}

/** 积水点 */
export interface WaterPoint {
  id: number
  longitude: number
  latitude: number
  name: string
  depth: number
  area?: number
  reportTime: string
}

/** 影响范围圆 */
export interface InfluenceCircle {
  stationId: number
  stationName: string
  longitude: number
  latitude: number
  radius: number
  level: AlertLevel
}

/** 热力点 */
export interface HeatPoint {
  longitude: number
  latitude: number
  value: number
}

/** MQTT 配置 */
export interface MqttConfig {
  host: string
  port: number
  clientId: string
  topic: string
  username?: string
  qos: number
}

/** CoAP 配置 */
export interface CoapConfig {
  host: string
  port: number
  path: string
  observeEnabled: boolean
}

/** 分页结果 */
export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
}
