<template>
  <div class="chart-card">
    <div class="chart-card__header">
      <span class="chart-card__title">{{ title }}</span>
      <div class="chart-card__extra">
        <slot name="extra" />
      </div>
    </div>
    <div class="chart-card__body">
      <div ref="chartRef" class="chart-card__chart" :style="{ height }" />
      <slot />
    </div>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch, nextTick } from 'vue'
import * as echarts from 'echarts'
import type { EChartsOption } from 'echarts'

// ECharts 卡片容器：接收 option，自动初始化、更新与自适应缩放
const props = withDefaults(
  defineProps<{
    title?: string
    option: EChartsOption
    height?: string
    loading?: boolean
  }>(),
  {
    title: '',
    height: '300px',
    loading: false,
  },
)

const chartRef = ref<HTMLDivElement>()
let chart: echarts.ECharts | null = null

// 渲染图表
function render() {
  if (!chartRef.value) return
  if (!chart) {
    chart = echarts.init(chartRef.value)
  }
  chart.setOption(props.option, true)
}

// 自适应窗口缩放
function resize() {
  chart?.resize()
}

onMounted(() => {
  render()
  window.addEventListener('resize', resize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  chart?.dispose()
  chart = null
})

// 数据变化时重绘
watch(
  () => props.option,
  () => {
    nextTick(render)
  },
  { deep: true },
)

// loading 态
watch(
  () => props.loading,
  (v) => {
    if (!chart) return
    if (v) chart.showLoading({ text: '加载中...' })
    else chart.hideLoading()
  },
)
</script>

<style scoped>
.chart-card {
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.06);
  overflow: hidden;
}
.chart-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid #ebeef5;
}
.chart-card__title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}
.chart-card__extra {
  display: flex;
  align-items: center;
  gap: 8px;
}
.chart-card__body {
  padding: 12px 16px 16px;
}
.chart-card__chart {
  width: 100%;
}
</style>

