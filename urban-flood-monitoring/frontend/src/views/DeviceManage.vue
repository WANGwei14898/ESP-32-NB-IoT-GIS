<template>
  <div class="device">
    <!-- 统计卡片 -->
    <el-row :gutter="16" class="stats">
      <el-col :span="6"><div class="stat"><div class="stat__num">{{ stats.total }}</div><div class="stat__label">设备总数</div></div></el-col>
      <el-col :span="6"><div class="stat stat--ok"><div class="stat__num">{{ stats.online }}</div><div class="stat__label">在线</div></div></el-col>
      <el-col :span="6"><div class="stat stat--off"><div class="stat__num">{{ stats.offline }}</div><div class="stat__label">离线</div></div></el-col>
      <el-col :span="6"><div class="stat stat--fault"><div class="stat__num">{{ stats.fault }}</div><div class="stat__label">故障</div></div></el-col>
    </el-row>

    <!-- 列表 -->
    <el-card class="panel-card">
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="设备编号 / 名称" clearable style="width: 220px" @keyup.enter="load" />
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 140px">
          <el-option label="在线" value="ONLINE" />
          <el-option label="离线" value="OFFLINE" />
          <el-option label="故障" value="FAULT" />
        </el-select>
        <el-button type="primary" @click="load">查询</el-button>
        <el-button @click="resetQuery">重置</el-button>
        <div class="toolbar__right">
          <el-button type="success" @click="openDialog()">新增设备</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="list" stripe>
        <el-table-column prop="code" label="设备编号" width="140" />
        <el-table-column prop="name" label="设备名称" min-width="140" />
        <el-table-column prop="stationName" label="所属站点" min-width="120" />
        <el-table-column prop="type" label="类型" width="110" />
        <el-table-column prop="protocol" label="协议" width="100" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <span class="dot" :style="{ background: STATUS_COLOR[row.status as DeviceStatus] }"></span>
            {{ STATUS_TEXT[row.status as DeviceStatus] }}
          </template>
        </el-table-column>
        <el-table-column label="电量" width="110">
          <template #default="{ row }">
            <el-progress v-if="row.battery != null" :percentage="row.battery" :stroke-width="8" :color="row.battery > 20 ? '#67C23A' : '#F56C6C'" />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="信号" width="90">
          <template #default="{ row }">{{ row.signalStrength != null ? row.signalStrength + ' dBm' : '-' }}</template>
        </el-table-column>
        <el-table-column label="最后心跳" width="170">
          <template #default="{ row }">{{ formatTime(row.lastHeartbeat) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-button link type="danger" @click="onDelete(row)">删除</el-button>
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

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑设备' : '新增设备'" width="520px">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="90px">
        <el-form-item label="设备编号" prop="code"><el-input v-model="form.code" /></el-form-item>
        <el-form-item label="设备名称" prop="name"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="设备类型" prop="type"><el-input v-model="form.type" placeholder="如：水位传感器" /></el-form-item>
        <el-form-item label="协议" prop="protocol">
          <el-select v-model="form.protocol" style="width: 100%">
            <el-option label="NB-IoT" value="NB-IoT" />
            <el-option label="MQTT" value="MQTT" />
            <el-option label="CoAP" value="CoAP" />
          </el-select>
        </el-form-item>
        <el-form-item label="所属站点"><el-input v-model="form.stationName" placeholder="可留空" /></el-form-item>
        <el-form-item label="固件版本"><el-input v-model="form.firmware" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import type { Device, DeviceStatus } from '@/types'
import { STATUS_COLOR, STATUS_TEXT, formatTime } from '@/utils/format'
import { createDevice, deleteDevice, getDevices, getDeviceStats, updateDevice } from '@/api/device'

const loading = ref(false)
const saving = ref(false)
const list = ref<Device[]>([])
const total = ref(0)
const stats = reactive({ total: 0, online: 0, offline: 0, fault: 0 })

const query = reactive({ page: 1, size: 10, keyword: '', status: '' })

const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<Partial<Device>>({})

const formRules: FormRules = {
  code: [{ required: true, message: '请输入设备编号', trigger: 'blur' }],
  name: [{ required: true, message: '请输入设备名称', trigger: 'blur' }],
}

async function load() {
  loading.value = true
  try {
    const data = await getDevices({ ...query })
    list.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}

async function loadStats() {
  const s = await getDeviceStats()
  Object.assign(stats, s)
}

function resetQuery() {
  query.keyword = ''
  query.status = ''
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

function openDialog(row?: Device) {
  // 先清空表单，避免新增时残留上次编辑的 id 等字段
  Object.keys(form).forEach((k) => delete (form as Record<string, unknown>)[k])
  Object.assign(form, row || { code: '', name: '', type: '', protocol: 'NB-IoT', stationName: '', firmware: '' })
  dialogVisible.value = true
}

async function onSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    if (form.id) await updateDevice(form.id, form)
    else await createDevice(form)
    ElMessage.success('保存成功')
    dialogVisible.value = false
    load()
    loadStats()
  } finally {
    saving.value = false
  }
}

async function onDelete(row: Device) {
  try {
    await ElMessageBox.confirm(`确认删除设备「${row.name}」？`, '提示', { type: 'warning' })
  } catch {
    return // 用户取消删除
  }
  await deleteDevice(row.id)
  ElMessage.success('删除成功')
  load()
  loadStats()
}

onMounted(() => {
  load()
  loadStats()
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
  border-left: 4px solid #409eff;
}
.stat--ok { border-left-color: #67c23a; }
.stat--off { border-left-color: #909399; }
.stat--fault { border-left-color: #f56c6c; }
.stat__num { font-size: 28px; font-weight: 700; }
.stat__label { color: #909399; margin-top: 4px; }
.toolbar { display: flex; gap: 12px; margin-bottom: 16px; align-items: center; }
.toolbar__right { margin-left: auto; }
.dot { display: inline-block; width: 8px; height: 8px; border-radius: 50%; margin-right: 4px; }
.pager { margin-top: 16px; justify-content: flex-end; }
</style>

