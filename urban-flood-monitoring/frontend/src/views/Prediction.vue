<template>
  <div class="prediction">
    <el-card class="panel-card">
      <div class="toolbar">
        <el-select v-model="query.stationId" placeholder="选择站点" filterable style="width: 220px">
          <el-option v-for="s in stations" :key="s.id" :label="s.name" :value="s.id" />
        </el-select>
        <el-select v-model="query.minutes" style="width: 150px">
          <el-option label="未来 30 分钟" :value="30" />
          <el-option label="未来 60 分钟" :value="60" />
        </el-select>
        <el-select v-model="query.model" placeholder="预测模型" style="width: 180px">
          <el-option v-for="m in models" :key="m" :label="modelText(m)" :value="m" />
        </el-select>
        <el-button type="primary" :loading="loading" @click="load">预测</el-button>
      </div>
    </el-card>

    <div class="charts">
      <ChartCard title="未来水位预测曲线" :option="predictionOption" height="460px" :loading="loading" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import type { EChartsOption } from 'echarts'
import type { Station, Threshold } from '@/types'
import { LEVEL_COLOR } from '@/utils/format'
import { formatShortTime } from '@/utils/format'
import { getStations } from '@/api/gis'
import { getModels, getPrediction } from '@/api/prediction'
import { getThresholds } from '@/api/threshold'
import ChartCard from '@/components/ChartCard.vue'

const stations = ref<Station[]>([])
const models = ref<string[]>(['ARIMA'])
const thresholds = ref<Threshold[]>([])
const loading = ref(false)

const prediction = ref<{ points: { time: string; value: number; lower?: number; upper?: number }[]; modelType: string; predictTime: string } | null>(null)

const query = reactive({ stationId: 0, minutes: 30, model: '' })

function modelText(m: string): string {
  const map: Record<string, string> = { ARIMA: 'ARIMA 时间序列', LSTM: 'LSTM 神经网络', MOVING_AVERAGE: '移动平均' }
  return map[m] || m
}

const predictionOption = computed<EChartsOption>(() => {
  const pts = prediction.value?.points || []
  const times = pts.map((p) => formatShortTime(p.time))
  const values = pts.map((p) => p.value)
  const upper = pts.map((p) => p.upper ?? p.value)
  const lower = pts.map((p) => p.lower ?? p.value)

  // 水位阈值参考线
  const markLines = thresholds.value
    .filter((t) => t.enabled && t.unit === 'cm')
    .map((t) => ({
      name: t.name,
      yAxis: t.thresholdValue,
      lineStyle: { color: LEVEL_COLOR[t.level], type: 'dashed' as const },
      label: { formatter: t.name },
    }))

  return {
    tooltip: { trigger: 'axis' },
    legend: { data: ['预测水位', '置信上界', '置信下界'] },
    grid: { left: 60, right: 30, top: 50, bottom: 50 },
    xAxis: { type: 'category', data: times, boundaryGap: false },
    yAxis: { type: 'value', name: '水位 (cm)', scale: true },
    series: [
      {
        name: '预测水位',
        type: 'line',
        smooth: true,
        showSymbol: false,
        lineStyle: { width: 3, color: '#409eff' },
        itemStyle: { color: '#409eff' },
        areaStyle: {
          color: {
            type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [{ offset: 0, color: 'rgba(64,158,255,0.35)' }, { offset: 1, color: 'rgba(64,158,255,0.02)' }],
          },
        },
        markLine: markLines.length ? { symbol: 'none', data: markLines } : undefined,
        data: values,
      },
      { name: '置信上界', type: 'line', smooth: true, showSymbol: false, lineStyle: { type: 'dashed', color: '#909399' }, data: upper },
      { name: '置信下界', type: 'line', smooth: true, showSymbol: false, lineStyle: { type: 'dashed', color: '#909399' }, data: lower },
    ],
  }
})

async function loadStations() {
  stations.value = await getStations()
  if (stations.value.length) query.stationId = stations.value[0].id
}

async function loadModels() {
  try {
    const list = await getModels()
    if (list.length) {
      models.value = list
      query.model = list[0]
    }
  } catch {
    query.model = 'ARIMA'
  }
}

async function loadThresholds() {
  thresholds.value = await getThresholds()
}

async function load() {
  if (!query.stationId) return
  loading.value = true
  try {
    prediction.value = await getPrediction(query)
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await Promise.all([loadStations(), loadModels(), loadThresholds()])
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

