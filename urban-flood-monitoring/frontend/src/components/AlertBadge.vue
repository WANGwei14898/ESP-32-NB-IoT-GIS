<template>
  <span class="alert-badge" :style="{ color, borderColor: color, backgroundColor: bgColor }">
    <span class="alert-badge__dot" :style="{ backgroundColor: color }" />
    {{ text }}
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { AlertLevel } from '@/types'
import { LEVEL_COLOR, LEVEL_TEXT } from '@/utils/format'

// 告警等级徽章：按等级展示对应颜色与文案
const props = withDefaults(
  defineProps<{
    level: AlertLevel
    text?: string
  }>(),
  {
    text: '',
  },
)

const color = computed(() => LEVEL_COLOR[props.level] || '#909399')
const text = computed(() => props.text || LEVEL_TEXT[props.level] || props.level)
// 徽章背景色使用同色系浅色
const bgColor = computed(() => `${color.value}1A`)
</script>

<style scoped>
.alert-badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 2px 10px;
  border: 1px solid;
  border-radius: 12px;
  font-size: 12px;
  line-height: 1.6;
  white-space: nowrap;
}
.alert-badge__dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  display: inline-block;
}
</style>

