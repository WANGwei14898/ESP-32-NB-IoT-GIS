<template>
  <!-- 通知中心：铃铛 + 未读徽标，点击弹出推送历史面板 -->
  <el-popover placement="bottom-end" :width="540" trigger="click" @show="onOpen">
    <template #reference>
      <div class="notify-trigger">
        <el-badge :value="unread" :hidden="unread === 0" :max="99">
          <el-icon :size="20" class="notify-trigger__icon"><Bell /></el-icon>
        </el-badge>
      </div>
    </template>

    <div class="notify-panel">
      <div class="notify-head">
        <span class="notify-title">通知中心</span>
        <el-button link type="primary" size="small" :disabled="unread === 0" @click="markAllRead">
          全部已读
        </el-button>
      </div>

      <div class="notify-filter">
        <el-select
          v-model="query.channel"
          placeholder="全部渠道"
          clearable
          size="small"
          style="width: 130px"
          @change="reload"
        >
          <el-option v-for="c in channels" :key="c.value" :label="c.label" :value="c.value" />
        </el-select>
        <el-select
          v-model="query.status"
          placeholder="全部状态"
          clearable
          size="small"
          style="width: 110px"
          @change="reload"
        >
          <el-option label="成功" value="SUCCESS" />
          <el-option label="失败" value="FAILED" />
        </el-select>
        <el-button size="small" @click="reload">刷新</el-button>
      </div>

      <el-scrollbar max-height="380px">
        <div v-loading="loading" class="notify-list">
          <div v-if="list.length === 0" class="notify-empty">暂无推送记录</div>
          <div v-for="item in list" :key="item.id" class="notify-item">
            <div class="notify-item__top">
              <el-tag :type="channelType(item.channel)" size="small">{{ channelText(item.channel) }}</el-tag>
              <el-tag :type="item.status === 'SUCCESS' ? 'success' : 'danger'" size="small" effect="plain">
                {{ item.status === 'SUCCESS' ? '成功' : '失败' }}
              </el-tag>
              <span class="notify-item__time">{{ formatTime(item.pushTime) }}</span>
            </div>
            <div class="notify-item__content">{{ item.content }}</div>
            <div class="notify-item__target">目标：{{ item.target || '-' }}</div>
          </div>
        </div>
      </el-scrollbar>

      <el-pagination
        class="notify-pager"
        layout="prev, pager, next"
        :total="total"
        :page-size="query.size"
        :current-page="query.page"
        small
        @current-change="onPageChange"
      />
    </div>
  </el-popover>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElNotification } from 'element-plus'
import { Bell } from '@element-plus/icons-vue'
import type { AlertLevel, PushRecord } from '@/types'
import { LEVEL_TEXT, formatTime } from '@/utils/format'
import { getPushLogs } from '@/api/push'
import { realtimeSocket } from '@/utils/websocket'

// 渠道选项
const channels = [
  { label: '短信', value: 'SMS' },
  { label: '微信', value: 'WECHAT' },
  { label: '邮件', value: 'EMAIL' },
  { label: 'App', value: 'APP' },
  { label: '实时推送', value: 'WEBSOCKET' },
]

const loading = ref(false)
const list = ref<PushRecord[]>([])
const total = ref(0)
const unread = ref(0)
const query = reactive({ page: 1, size: 10, channel: '', status: '' })

// 渠道文案映射
function channelText(channel: string): string {
  return channels.find((c) => c.value === channel)?.label || channel || '未知'
}

// 渠道标签颜色映射
function channelType(channel: string): 'success' | 'warning' | 'info' | 'primary' {
  const map: Record<string, 'success' | 'warning' | 'info' | 'primary'> = {
    SMS: 'info',
    WECHAT: 'success',
    EMAIL: 'warning',
    APP: 'primary',
    WEBSOCKET: 'info',
  }
  return map[channel] || 'info'
}

// 等级 → ElementPlus 通知类型
function levelType(level: AlertLevel): 'success' | 'warning' | 'info' | 'error' {
  const map: Record<string, 'success' | 'warning' | 'info' | 'error'> = {
    NORMAL: 'info',
    BLUE: 'info',
    YELLOW: 'warning',
    ORANGE: 'warning',
    RED: 'error',
  }
  return map[level] || 'info'
}

// 加载推送历史
async function load() {
  loading.value = true
  try {
    const data = await getPushLogs({ ...query })
    list.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function reload() {
  query.page = 1
  load()
}

function onPageChange(p: number) {
  query.page = p
  load()
}

// 打开面板时加载最新数据
function onOpen() {
  reload()
}

function markAllRead() {
  unread.value = 0
}

// 收到实时告警：弹出通知 + 未读计数 + 刷新列表
function onAlert(data: any) {
  const level = (data?.level || 'NORMAL') as AlertLevel
  unread.value += 1
  ElNotification({
    title: '城市内涝预警',
    message: `${LEVEL_TEXT[level] || level}：${data?.message || '监测到异常'}`,
    type: levelType(level),
    duration: 5000,
  })
  // 面板若已加载过数据则刷新，保证历史实时更新
  if (total.value > 0) load()
}

onMounted(() => {
  realtimeSocket.on('alert', onAlert)
  realtimeSocket.connect()
})

onBeforeUnmount(() => {
  realtimeSocket.off('alert', onAlert)
})
</script>

<style scoped>
.notify-trigger {
  display: flex;
  align-items: center;
  cursor: pointer;
}
.notify-trigger__icon {
  color: #606266;
}
.notify-trigger__icon:hover {
  color: #409eff;
}
.notify-panel {
  margin: -12px;
}
.notify-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid #ebeef5;
}
.notify-title {
  font-size: 15px;
  font-weight: 600;
}
.notify-filter {
  display: flex;
  gap: 8px;
  padding: 10px 16px;
  border-bottom: 1px solid #f2f6fc;
}
.notify-list {
  min-height: 120px;
}
.notify-empty {
  padding: 40px 0;
  text-align: center;
  color: #909399;
}
.notify-item {
  padding: 12px 16px;
  border-bottom: 1px solid #f2f6fc;
}
.notify-item:last-child {
  border-bottom: none;
}
.notify-item__top {
  display: flex;
  align-items: center;
  gap: 8px;
}
.notify-item__time {
  margin-left: auto;
  font-size: 12px;
  color: #909399;
}
.notify-item__content {
  margin-top: 6px;
  font-size: 13px;
  color: #303133;
  word-break: break-all;
}
.notify-item__target {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
}
.notify-pager {
  padding: 10px 16px;
  justify-content: flex-end;
}
</style>


