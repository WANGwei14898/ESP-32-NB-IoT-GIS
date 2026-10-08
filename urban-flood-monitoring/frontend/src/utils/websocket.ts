// WebSocket 实时数据推送客户端（支持自动重连与心跳保活）
// 服务端推送消息结构约定：{ type: 'telemetry' | 'alert' | 'ping' | 'pong', data: any }

export type WsMessageType = 'telemetry' | 'alert' | 'ping' | 'pong' | 'station'

interface WsMessage {
  type: WsMessageType | string
  data?: unknown
  timestamp?: number
}

type MessageHandler = (data: any) => void

class RealtimeSocket {
  private ws: WebSocket | null = null
  private url: string
  private handlers: Map<string, Set<MessageHandler>> = new Map()
  private reconnectTimer: number | null = null
  private heartbeatTimer: number | null = null
  private shouldReconnect = true
  private reconnectDelay = 3000

  constructor(url?: string) {
    this.url = url || this.resolveUrl()
  }

  /** 根据当前页面协议与主机拼接 WebSocket 地址 */
  private resolveUrl(): string {
    const protocol = window.location.protocol === 'https:' ? 'wss://' : 'ws://'
    return `${protocol}${window.location.host}/ws/realtime`
  }

  /** 建立连接 */
  connect(): void {
    this.shouldReconnect = true
    if (this.ws && (this.ws.readyState === WebSocket.OPEN || this.ws.readyState === WebSocket.CONNECTING)) {
      return
    }
    try {
      this.ws = new WebSocket(this.url)
      this.ws.onopen = () => {
        this.startHeartbeat()
        this.dispatch('__open__', null)
      }
      this.ws.onmessage = (ev) => this.handleMessage(ev)
      this.ws.onclose = () => {
        this.stopHeartbeat()
        this.ws = null
        if (this.shouldReconnect) this.scheduleReconnect()
      }
      this.ws.onerror = () => {
        this.ws?.close()
      }
    } catch (e) {
      this.scheduleReconnect()
    }
  }

  /** 解析并分发消息 */
  private handleMessage(ev: MessageEvent): void {
    let msg: WsMessage
    try {
      msg = JSON.parse(ev.data)
    } catch {
      return
    }
    if (msg.type === 'pong') return
    this.dispatch(msg.type as string, msg.data)
  }

  /** 心跳保活 */
  private startHeartbeat(): void {
    this.stopHeartbeat()
    this.heartbeatTimer = window.setInterval(() => {
      if (this.ws?.readyState === WebSocket.OPEN) {
        this.ws.send(JSON.stringify({ type: 'ping', timestamp: Date.now() }))
      }
    }, 30000)
  }

  private stopHeartbeat(): void {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer)
      this.heartbeatTimer = null
    }
  }

  private scheduleReconnect(): void {
    if (this.reconnectTimer) return
    this.reconnectTimer = window.setTimeout(() => {
      this.reconnectTimer = null
      this.connect()
    }, this.reconnectDelay)
  }

  /** 订阅某类消息 */
  on(type: string, handler: MessageHandler): void {
    if (!this.handlers.has(type)) this.handlers.set(type, new Set())
    this.handlers.get(type)!.add(handler)
  }

  /** 取消订阅 */
  off(type: string, handler: MessageHandler): void {
    this.handlers.get(type)?.delete(handler)
  }

  /** 触发某类消息的所有回调 */
  private dispatch(type: string, data: unknown): void {
    this.handlers.get(type)?.forEach((fn) => fn(data))
  }

  /** 关闭连接（不再重连） */
  close(): void {
    this.shouldReconnect = false
    this.stopHeartbeat()
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }
    this.ws?.close()
    this.ws = null
  }
}

// 导出单例
export const realtimeSocket = new RealtimeSocket()
export default realtimeSocket

