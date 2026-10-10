<template>
  <div class="settings">
    <!-- 阈值配置 -->
    <el-card class="panel-card">
      <template #header>
        <div class="card-head">
          <span>告警阈值配置</span>
          <el-button type="primary" :loading="saving" @click="saveThresholds">保存阈值</el-button>
        </div>
      </template>
      <el-table :data="thresholds" stripe>
        <el-table-column prop="code" label="编码" width="180" />
        <el-table-column prop="name" label="名称" min-width="160" />
        <el-table-column label="预警等级" width="130">
          <template #default="{ row }"><AlertBadge :level="row.level" /></template>
        </el-table-column>
        <el-table-column label="阈值" width="220">
          <template #default="{ row }">
            <el-input-number v-model="row.thresholdValue" :precision="2" :step="1" controls-position="right" style="width: 160px" />
            <span class="unit">{{ row.unit }}</span>
          </template>
        </el-table-column>
        <el-table-column label="启用" width="90">
          <template #default="{ row }"><el-switch v-model="row.enabled" /></template>
        </el-table-column>
      </el-table>
      <div class="tip">水位预警阈值：蓝色 ≤ 黄色 ≤ 橙色 ≤ 红色，超过对应阈值即触发对应等级告警。</div>
    </el-card>

    <!-- 协议配置 -->
    <el-row :gutter="16" class="proto">
      <el-col :span="12">
        <el-card class="panel-card">
          <template #header><span>MQTT 配置</span></template>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="Broker 地址">{{ mqtt?.host || '-' }}</el-descriptions-item>
            <el-descriptions-item label="端口">{{ mqtt?.port || '-' }}</el-descriptions-item>
            <el-descriptions-item label="客户端 ID">{{ mqtt?.clientId || '-' }}</el-descriptions-item>
            <el-descriptions-item label="订阅主题">{{ mqtt?.topic || '-' }}</el-descriptions-item>
            <el-descriptions-item label="QoS">{{ mqtt?.qos ?? '-' }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card class="panel-card">
          <template #header><span>CoAP 配置</span></template>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="服务地址">{{ coap?.host || '-' }}</el-descriptions-item>
            <el-descriptions-item label="端口">{{ coap?.port || '-' }}</el-descriptions-item>
            <el-descriptions-item label="资源路径">{{ coap?.path || '-' }}</el-descriptions-item>
            <el-descriptions-item label="Observe 订阅">{{ coap?.observeEnabled ? '开启' : '关闭' }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { CoapConfig, MqttConfig, Threshold } from '@/types'
import { getCoapConfig, getMqttConfig, getThresholds, updateThresholds } from '@/api/threshold'
import AlertBadge from '@/components/AlertBadge.vue'

const thresholds = ref<Threshold[]>([])
const mqtt = ref<MqttConfig | null>(null)
const coap = ref<CoapConfig | null>(null)
const saving = ref(false)

async function load() {
  thresholds.value = await getThresholds()
  try {
    mqtt.value = await getMqttConfig()
  } catch {
    /* 未配置则忽略 */
  }
  try {
    coap.value = await getCoapConfig()
  } catch {
    /* 未配置则忽略 */
  }
}

async function saveThresholds() {
  saving.value = true
  try {
    await updateThresholds(thresholds.value)
    ElMessage.success('阈值保存成功')
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.unit {
  margin-left: 6px;
  color: #909399;
}
.tip {
  margin-top: 12px;
  font-size: 12px;
  color: #909399;
}
.proto {
  margin-top: 16px;
}
</style>

