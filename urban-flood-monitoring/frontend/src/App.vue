<template>
  <!-- 登录页独立渲染，不带侧边布局 -->
  <router-view v-if="isLoginPage" />
  <el-container v-else class="layout">
    <!-- 左侧导航 -->
    <el-aside width="220px" class="aside">
      <div class="logo">
        <el-icon class="logo__icon"><Cloudy /></el-icon>
        <span class="logo__text">城市内涝监测预警</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        router
        class="menu"
        background-color="#0b1b33"
        text-color="#a3b1cc"
        active-text-color="#ffffff"
      >
        <el-menu-item index="/gis">
          <el-icon><Location /></el-icon>
          <span>GIS 地图</span>
        </el-menu-item>
        <el-menu-item index="/digital-twin">
          <el-icon><DataBoard /></el-icon>
          <span>数字孪生大屏</span>
        </el-menu-item>
        <el-menu-item index="/devices">
          <el-icon><Cpu /></el-icon>
          <span>设备管理</span>
        </el-menu-item>
        <el-menu-item index="/alerts">
          <el-icon><Bell /></el-icon>
          <span>告警管理</span>
        </el-menu-item>
        <el-menu-item index="/history">
          <el-icon><Histogram /></el-icon>
          <span>历史查询</span>
        </el-menu-item>
        <el-menu-item index="/prediction">
          <el-icon><TrendCharts /></el-icon>
          <span>预测展示</span>
        </el-menu-item>
        <el-menu-item index="/settings">
          <el-icon><Setting /></el-icon>
          <span>系统设置</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <!-- 顶部栏 -->
      <el-header class="header">
        <div class="header__title">{{ currentTitle }}</div>
        <div class="header__right">
          <NotificationCenter />
          <span class="user">
            <el-icon><User /></el-icon>
            {{ userStore.username }}
          </span>
          <el-button link type="primary" @click="handleLogout">退出登录</el-button>
        </div>
      </el-header>

      <!-- 内容区 -->
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import {
  Location,
  DataBoard,
  Cpu,
  Bell,
  Histogram,
  TrendCharts,
  Setting,
  User,
  Cloudy,
} from '@element-plus/icons-vue'
import { useUserStore } from '@/store'
import NotificationCenter from '@/components/NotificationCenter.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

// 是否登录页
const isLoginPage = computed(() => route.name === 'Login')
// 当前高亮菜单
const activeMenu = computed(() => route.path)
// 当前标题
const currentTitle = computed(() => (route.meta.title as string) || '')

// 退出登录
async function handleLogout() {
  try {
    await ElMessageBox.confirm('确认退出登录？', '提示', {
      confirmButtonText: '退出',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    // 用户取消退出
    return
  }
  await userStore.logout()
  router.push('/login')
}
</script>

<style scoped>
.layout {
  height: 100%;
}
.aside {
  background: #0b1b33;
  color: #fff;
}
.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #fff;
  font-size: 16px;
  font-weight: 700;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}
.logo__icon {
  font-size: 22px;
}
.menu {
  border-right: none;
}
.header {
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
}
.header__title {
  font-size: 18px;
  font-weight: 600;
}
.header__right {
  display: flex;
  align-items: center;
  gap: 16px;
}
.user {
  display: flex;
  align-items: center;
  gap: 4px;
  color: #606266;
}
.main {
  padding: 16px;
  overflow: auto;
}
</style>

