<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  values: number[]
  max?: number
  color?: string
  height?: number
}>(), {
  max: 100,
  color: '#2f6df6',
  height: 42
})

const points = computed(() => {
  const values = props.values.length ? props.values : [0]
  const max = Math.max(props.max, ...values, 1)
  const step = values.length > 1 ? 100 / (values.length - 1) : 100
  return values
    .map((value, index) => {
      const x = index * step
      const y = 29 - (Math.max(0, value) / max) * 26
      return `${x.toFixed(2)},${y.toFixed(2)}`
    })
    .join(' ')
})
</script>

<template>
  <svg class="sparkline" viewBox="0 0 100 30" preserveAspectRatio="none" :style="{ height: `${height}px` }">
    <polyline
      :points="points"
      fill="none"
      :stroke="color"
      stroke-width="1.5"
      stroke-linejoin="round"
      vector-effect="non-scaling-stroke"
    />
  </svg>
</template>

<style scoped>
.sparkline {
  display: block;
  width: 100%;
  overflow: visible;
}
</style>