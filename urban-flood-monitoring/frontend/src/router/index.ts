import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/store'

// 路由表
const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { title: '登录', public: true },
  },
  { path: '/', redirect: '/gis' },
  {
    path: '/gis',
    name: 'GisMap',
    component: () => import('@/views/GisMap.vue'),
    meta: { title: 'GIS 地图' },
  },
  {
    path: '/digital-twin',
    name: 'DigitalTwin',
    component: () => import('@/views/DigitalTwin.vue'),
    meta: { title: '数字孪生大屏' },
  },
  {
    path: '/devices',
    name: 'DeviceManage',
    component: () => import('@/views/DeviceManage.vue'),
    meta: { title: '设备管理' },
  },
  {
    path: '/alerts',
    name: 'AlertManage',
    component: () => import('@/views/AlertManage.vue'),
    meta: { title: '告警管理' },
  },
  {
    path: '/history',
    name: 'HistoryQuery',
    component: () => import('@/views/HistoryQuery.vue'),
    meta: { title: '历史查询' },
  },
  {
    path: '/prediction',
    name: 'Prediction',
    component: () => import('@/views/Prediction.vue'),
    meta: { title: '预测展示' },
  },
  {
    path: '/settings',
    name: 'Settings',
    component: () => import('@/views/Settings.vue'),
    meta: { title: '系统设置' },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// 全局前置守卫：校验登录态
router.beforeEach((to, _from, next) => {
  const userStore = useUserStore()
  document.title = to.meta.title
    ? `${to.meta.title as string} - 城市内涝监测预警系统`
    : '城市内涝监测预警系统'

  if (to.meta.public) {
    next()
  } else if (!userStore.token) {
    // 未登录，跳转登录页并携带回跳地址
    next({ path: '/login', query: { redirect: to.fullPath } })
  } else {
    next()
  }
})

export default router

