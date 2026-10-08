<template>
  <div class="alert">
    <!-- 等级统计 -->
    <el-row :gutter="16" class="stats">
      <el-col :span="6"><div class="stat stat--blue"><div class="stat__num">{{ stats.blue }}</div><div class="stat__label">蓝色预警</div></div></el-col>
      <el-col :span="6"><div class="stat stat--yellow"><div class="stat__num">{{ stats.yellow }}</div><div class="stat__label">黄色预警</div></div></el-col>
      <el-col :span="6"><div class="stat stat--orange"><div class="stat__num">{{ stats.orange }}</div><div class="stat__label">橙色预警</div></div></el-col>
      <el-col :span="6"><div class="stat stat--red"><div class="stat__num">{{ stats.red }}</div><div class="stat__label">红色预警</div></div></el-col>
    </el-row>

    <el-card class="panel-card">
      <!-- 筛选 -->
      <div class="toolbar">
        <el-date-picker
          v-model="dateRange"
          type="datetimerange"
          range-separator="至"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          value-format="YYYY-MM-DD HH:mm:ss"
        />
        <el-select v-model="query.status" placeholder="处理状态" clearable style="width: 140px">
          <el-option label="未处理" value="ACTIVE" />
          <el-option label="已处理" value="HANDLED" />
        </el-select>
        <el-button type="primary" @click="load">查询</el-button>
        <el-button @click="resetQuery">重置</el-button>
      </div>

      <!-- 等级标签页 -->
      <el-tabs v-model="levelTab" @tab-change="onTabChange">
        <el-tab-pane label="全部" name="ALL" />
        <el-tab-pane label="蓝色" name="BLUE" />
        <el-tab-pane label="黄色" name="YELLOW" />
        <el-tab-pane label="橙色" name="ORANGE" />
        <el-tab-pane label="红色" name="RED" />
      </el-tabs>

      <el-table v-loading="loading" :data="list" stripe>
        <el-table-column label="等级" width="110">
          <template #default="{ row }"><AlertBadge :level="row.level" /></template>
        </el-table-column>
        <el-table-column prop="stationName" label="站点" min-width="120" />
        <el-table-column prop="type" label="类型" width="110" />
        <el-table-column prop="message" label="告警内容" min-width="200" show-overflow-tooltip />
        <el-table-column label="水位/阈值" width="120">
          <template #default="{ row }">{{ row.waterLevel }} / {{ row.threshold }} cm</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'danger' : 'success'" size="small">
              {{ row.status === 'ACTIVE' ? '未处理' : '已处理' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="发生时间" width="170">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'ACTIVE'" link type="primary" @click="openHandle(row)">处理</el-button>
            <el-button link type="primary" @click="openPushes(row)">推送记录</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="pager"
        layout="total, sizes, prev, pager, next"
        :total="total"
        :page-size="query.size"
        :current-page="query.page"
        :page-sizes="[10, 20, 50]"
        @current-change="onPageChange"
        @size-change="onSizeChange"
      />
    </el-card>

    <!-- 处理弹窗 -->
    <el-dialog v-model="handleVisible" title="处理告警" width="440px">
      <el-form label-width="80px">
        <el-form-item label="处理人"><el-input v-model="handleForm.handler" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="handleForm.remark" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="handleVisible = false">取消</el-button>
        <el-button type="primary" :loading="handling" @click="submitHandle">确认处理</el-button>
      </template>
    </el-dialog>

    <!-- 推送记录弹窗 -->
    <el-dialog v-model="pushVisible" title="推送记录" width="640px">
      <el-table :data="pushRecords" stripe>
        <el-table-column prop="channel" label="渠道" width="110" />
        <el-table-column prop="target" label="目标" min-width="150" />
        <el-table-column prop="content" label="内容" min-width="180" show-overflow-tooltip />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'SUCCESS' ? 'success' : 'danger'" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="160">
          <template #default="{ row }">{{ formatTime(row.pushTime) }}</template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { Alert, AlertLevel, PushRecord } from '@/types'
import { formatTime } from '@/utils/format'
import { getAlerts, getAlertStats, getAlertPushes, handleAlert } from '@/api/alert'
import { realtimeSocket } from '@/utils/websocket'
import AlertBadge from '@/components/AlertBadge.vue'

const loading = ref(false)
const handling = ref(false)
const list = ref<Alert[]>([])
const total = ref(0)
const stats = reactive({ blue: 0, yellow: 0, orange: 0, red: 0, total: 0, active: 0, handled: 0 })

const levelTab = ref<'ALL' | AlertLevel>('ALL')
const dateRange = ref<any>(null)
const query = reactive({ page: 1, size: 10, level: '' as AlertLevel | '', status: '', start: '', end: '' })

const handleVisible = ref(false)
const handleForm = reactive({ id: 0, handler: '', remark: '' })
const pushVisible = ref(false)
const pushRecords = ref<PushRecord[]>([])

async function load() {
  buildQuery()
  loading.value = true
  try {
    const data = await getAlerts({ ...query })
    list.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}

async function loadStats() {
  const s = await getAlertStats()
  Object.assign(stats, s)
}

function buildQuery() {
  query.start = dateRange.value?.[0] || ''
  query.end = dateRange.value?.[1] || ''
}

function onTabChange() {
  query.level = levelTab.value === 'ALL' ? '' : levelTab.value
  query.page = 1
  load()
}

function resetQuery() {
  levelTab.value = 'ALL'
  dateRange.value = null
  query.status = ''
  query.level = ''
  query.page = 1
  load()
}

function onPageChange(p: number) {
  query.page = p
  load()
}
function onSizeChange(s: number) {
  query.size = s
  query.page = 1
  load()
}

function openHandle(row: Alert) {
  handleForm.id = row.id
  handleForm.handler = ''
  handleForm.remark = ''
  handleVisible.value = true
}

async function submitHandle() {
  handling.value = true
  try {
    await handleAlert(handleForm.id, { handler: handleForm.handler, remark: handleForm.remark })
    ElMessage.success('处理成功')
    handleVisible.value = false
    load()
    loadStats()
  } finally {
    handling.value = false
  }
}

async function openPushes(row: Alert) {
  pushRecords.value = await getAlertPushes(row.id)
  pushVisible.value = true
}

// 实时告警推送：新告警插到列表顶部并刷新统计
function onAlert(data: any) {
  loadStats()
  if (query.level && data?.level !== query.level) return
  load()
}

onMounted(() => {
  buildQuery()
  load()
  loadStats()
  realtimeSocket.on('alert', onAlert)
  realtimeSocket.connect()
})

onBeforeUnmount(() => {
  realtimeSocket.off('alert', onAlert)
})
</script>

<style scoped>
.stats { margin-bottom: 16px; }
.stat {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
  text-align: center;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.06);
  border-left: 4px solid #909399;
}
.stat--blue { border-left-color: #409eff; }
.stat--yellow { border-left-color: #e6a23c; }
.stat--orange { border-left-color: #f97316; }
.stat--red { border-left-color: #f56c6c; }
.stat__num { font-size: 28px; font-weight: 700; }
.stat__label { color: #909399; margin-top: 4px; }
.toolbar { display: flex; gap: 12px; margin-bottom: 16px; align-items: center; }
.pager { margin-top: 16px; justify-content: flex-end; }
</style>

