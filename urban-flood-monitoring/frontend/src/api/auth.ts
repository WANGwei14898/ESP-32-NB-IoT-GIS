// 认证相关接口
import { request } from '@/utils/request'
import type { UserInfo } from '@/types'

export interface LoginParams {
  username: string
  password: string
}

export interface LoginResult {
  token: string
  userInfo: UserInfo
}

/** 登录 */
export function login(data: LoginParams) {
  return request<LoginResult>({ url: '/auth/login', method: 'post', data })
}

/** 退出登录 */
export function logout() {
  return request<void>({ url: '/auth/logout', method: 'post' })
}

/** 获取当前用户信息 */
export function getUserInfo() {
  return request<UserInfo>({ url: '/auth/me', method: 'get' })
}
