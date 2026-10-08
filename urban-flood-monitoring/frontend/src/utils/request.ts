import axios from 'axios'
import type { AxiosInstance, AxiosRequestConfig, AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'

// 后端统一响应结构：{ code, message, data }
interface ApiResponse<T = unknown> {
  code: number
  message: string
  data: T
}

// 创建 Axios 实例
const service: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 15000,
})

// 请求拦截器：附加 JWT Token（直接读 localStorage，避免与 store 循环依赖）
service.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error),
)

// 响应拦截器：统一解包 data，统一错误提示
service.interceptors.response.use(
  (response: AxiosResponse): any => {
    const res = response.data as ApiResponse
    // 约定 code === 0 表示成功
    if (res.code !== 0) {
      ElMessage.error(res.message || '请求失败')
      return Promise.reject(new Error(res.message || 'Error'))
    }
    return res.data
  },
  (error) => {
    const status = error?.response?.status
    if (status === 401) {
      // 登录失效，清理状态并跳转登录页
      localStorage.removeItem('token')
      if (!window.location.pathname.startsWith('/login')) {
        window.location.href = '/login'
      }
      ElMessage.error('登录已过期，请重新登录')
    } else {
      const msg = error?.response?.data?.message || error.message || '网络错误'
      ElMessage.error(msg)
    }
    return Promise.reject(error)
  },
)

/** 通用请求方法，返回 Promise<T>，T 为解包后的 data 类型 */
export function request<T = unknown>(config: AxiosRequestConfig): Promise<T> {
  return service.request(config) as unknown as Promise<T>
}

export default service

