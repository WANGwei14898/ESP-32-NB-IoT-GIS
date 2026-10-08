import { defineStore } from 'pinia'
import { getUserInfo, login as loginApi, logout as logoutApi } from '@/api/auth'
import type { UserInfo } from '@/types'

/**
 * 用户状态管理
 */
export const useUserStore = defineStore('user', {
  state: () => ({
    // 从 localStorage 恢复 token，保证刷新后仍保持登录
    token: localStorage.getItem('token') || '',
    userInfo: null as UserInfo | null,
  }),
  getters: {
    isLoggedIn: (state) => !!state.token,
    username: (state) => state.userInfo?.username || 'admin',
  },
  actions: {
    /** 登录 */
    async login(username: string, password: string) {
      const data = await loginApi({ username, password })
      this.token = data.token
      localStorage.setItem('token', data.token)
      if (data.userInfo) this.userInfo = data.userInfo
      return data
    },
    /** 拉取用户信息 */
    async fetchUserInfo() {
      this.userInfo = await getUserInfo()
      return this.userInfo
    },
    /** 退出登录 */
    async logout() {
      try {
        await logoutApi()
      } catch {
        /* 忽略登出接口异常 */
      }
      this.reset()
    },
    /** 清空登录状态 */
    reset() {
      this.token = ''
      this.userInfo = null
      localStorage.removeItem('token')
    },
  },
})

