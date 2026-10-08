<template>
  <div class="history">
    <el-card class="panel-card">
      <!-- 筛选条件 -->
      <div class="toolbar">
        <el-select v-model="query.stationId" placeholder="选择站点" filterable style="width: 220px">
          <el-option v-for="s in stations" :key="s.id" :label="s.name" :value="s.id" />
        </el-select>
        <el-select v-model="query.metric" style="width: 140px">
          <el-option label="水位 (cm)" value="waterLevel" />
          <el-option label="雨量 (mm)" value="rainfall" />
          <el-option label="流速 (m/s)" value="flowVelocity" />
        </el-select>
        <el-date-picker
          v-model="dateRange"
          type="datetimerange"
          range-separator="至"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          value-format="YYYY-MM-DD HH:mm:ss"
        />
        <el-button type="primary" @click="load">查询</el-button>
      </div>
    </el-card>

    <div class="charts">
      <ChartCard :title="`${metricText}历史曲线 - ${currentStationName}`" :option="lineOption" height="420px" :loading="loading" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import type { EChartsOption } from 'echarts'
import type { Station, Telemetry } from '@/types'
import { formatShortTime } from '@/utils/format'
import { getStations } from '@/api/gis'
import { getHistory } from '@/api/telemetry'
import ChartCard from '@/components/ChartCard.vue'

const stations = ref<Station[]>([])
const records = ref<Telemetry[]>([])
const loading = ref(false)

const metricMap = { waterLevel: '水位 (cm)', rainfall: '雨量 (mm)', flowVelocity: '流速 (m/s)' } as const
const query = reactive({ stationId: 0, metric: 'waterLevel' as keyof typeof metricMap })
const dateRange = ref<any>(null)

const metricText = computed(() => metricMap[query.metric])
const currentStationName = computed(
  () => stations.value.find((s) => s.id === query.stationId)?.name || '未选择',
)

// 曲线图配置
const lineOption = computed<EChartsOption>(() => {
  const times = records.value.map((r) => formatShortTime(r.timestamp))
  const values = records.value.map((r) => r[query.metric] as number)
  return {
    tooltip: { trigger: 'axis' },
    grid: { left: 50, right: 30, top: 40, bottom: 50 },
    xAxis: { type: 'category', data: times, boundaryGap: false },
    yAxis: { type: 'value', name: metricText.value },
    dataZoom: [{ type: 'inside' }, { type: 'slider', height: 20, bottom: 8 }],
    series: [
      {
        name: metricText.value,
        type: 'line',
        smooth: true,
        showSymbol: false,
        areaStyle: { opacity: 0.15 },
        lineStyle: { width: 2, color: '#409eff' },
        itemStyle: { color: '#409eff' },
        data: values,
      },
    ],
  }
})

async function loadStations() {
  stations.value = await getStations()
  if (stations.value.length) query.stationId = stations.value[0].id
}

async function load() {
  if (!query.stationId) return
  loading.value = true
  try {
    records.value = await getHistory({
      stationId: query.stationId,
      metric: query.metric,
      start: dateRange.value?.[0],
      end: dateRange.value?.[1],
      limit: 500,
    })
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await loadStations()
  load()
})
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}
.charts {
  margin-top: 16px;
}
</style>

