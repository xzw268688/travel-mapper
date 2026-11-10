<script setup lang="ts">
import { formatDateTimeSeconds } from '../../utils/datetime'
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getServerMetrics, type ServerMetrics } from '../../api/admin'
import Sparkline from '../Sparkline.vue'
import { useVisiblePolling } from '../../utils/useVisiblePolling'

const loading = ref(false)
const metrics = ref<ServerMetrics | null>(null)
const cpuSamples = ref<number[]>([])
const processSamples = ref<number[]>([])
const memorySamples = ref<number[]>([])
const heapSamples = ref<number[]>([])

useVisiblePolling(load, 15000)

async function load(): Promise<void> {
  loading.value = true
  try {
    const data = await getServerMetrics()
    metrics.value = data
    push(cpuSamples, data.systemCpuUsage * 100)
    push(processSamples, data.processCpuUsage * 100)
    push(memorySamples, usedPercent(data.totalMemoryBytes, data.freeMemoryBytes))
    push(heapSamples, heapPercent(data.jvm.heapUsedBytes, data.jvm.heapMaxBytes))
  } catch (error) {
    ElMessage.error((error as Error).message || '加载服务监控失败')
  } finally {
    loading.value = false
  }
}

function push(samples: { value: number[] }, value: number): void {
  samples.value = [...samples.value, Math.max(0, value)].slice(-20)
}

function usedPercent(total: number, free: number): number {
  if (total <= 0) {
    return 0
  }
  return ((total - free) / total) * 100
}

function heapPercent(used: number, max: number): number {
  if (max <= 0) {
    return 0
  }
  return Math.min(100, (used / max) * 100)
}

function percent(used: number, total: number): number {
  if (total <= 0) {
    return 0
  }
  return Math.min(100, (used / total) * 100)
}

function formatBytes(value: number): string {
  if (!value) {
    return '0 B'
  }
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let size = value
  let index = 0
  while (size >= 1024 && index < units.length - 1) {
    size /= 1024
    index += 1
  }
  return `${size.toFixed(1)} ${units[index]}`
}

function formatSeconds(value: number): string {
  if (!value) {
    return '—'
  }
  const days = Math.floor(value / 86400)
  const hours = Math.floor((value % 86400) / 3600)
  const minutes = Math.floor((value % 3600) / 60)
  if (days > 0) {
    return `${days} 天 ${hours} 小时`
  }
  if (hours > 0) {
    return `${hours} 小时 ${minutes} 分`
  }
  return `${minutes} 分`
}

function formatTime(value: string): string {
  return formatDateTimeSeconds(value) || '—'
}
</script>

<template>
  <div v-loading="loading">
    <div class="monitor-toolbar">
      <span class="monitor-hint">每 15 秒自动刷新；趋势为当前页面最近 20 个采样点</span>
      <el-button @click="load">刷新</el-button>
    </div>

    <el-alert
      v-if="metrics?.partial"
      type="warning"
      :closable="false"
      show-icon
      title="部分指标在当前平台不可用，已展示可读取的字段"
      style="margin-bottom: 12px"
    />

    <template v-if="metrics">
      <div class="monitor-cards">
        <div class="monitor-card">
          <div class="monitor-card-label">系统 CPU</div>
          <div class="monitor-card-value">{{ (metrics.systemCpuUsage * 100).toFixed(1) }}%</div>
          <Sparkline :values="cpuSamples" :max="100" />
        </div>
        <div class="monitor-card">
          <div class="monitor-card-label">进程 CPU</div>
          <div class="monitor-card-value">{{ (metrics.processCpuUsage * 100).toFixed(1) }}%</div>
          <Sparkline :values="processSamples" :max="100" color="#52c41a" />
        </div>
        <div class="monitor-card">
          <div class="monitor-card-label">物理内存</div>
          <div class="monitor-card-value">
            {{ usedPercent(metrics.totalMemoryBytes, metrics.freeMemoryBytes).toFixed(1) }}%
          </div>
          <div class="monitor-card-sub">
            {{ formatBytes(metrics.totalMemoryBytes - metrics.freeMemoryBytes) }} / {{ formatBytes(metrics.totalMemoryBytes) }}
          </div>
          <Sparkline :values="memorySamples" :max="100" color="#faad14" />
        </div>
        <div class="monitor-card">
          <div class="monitor-card-label">JVM 堆内存</div>
          <div class="monitor-card-value">
            {{ heapPercent(metrics.jvm.heapUsedBytes, metrics.jvm.heapMaxBytes).toFixed(1) }}%
          </div>
          <div class="monitor-card-sub">
            {{ formatBytes(metrics.jvm.heapUsedBytes) }} / {{ formatBytes(metrics.jvm.heapMaxBytes) }}
          </div>
          <Sparkline :values="heapSamples" :max="100" color="#eb2f96" />
        </div>
      </div>

      <div class="monitor-grid">
        <div class="monitor-block">
          <h4>服务器</h4>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="操作系统">{{ metrics.osName }} {{ metrics.osVersion }}</el-descriptions-item>
            <el-descriptions-item label="架构">{{ metrics.osArch }}</el-descriptions-item>
            <el-descriptions-item label="CPU 核数">
              {{ metrics.logicalCpuCount }} 逻辑 / {{ metrics.physicalCpuCount }} 物理
            </el-descriptions-item>
            <el-descriptions-item label="可用处理器">{{ metrics.availableProcessors }}</el-descriptions-item>
            <el-descriptions-item label="运行时长">{{ formatSeconds(metrics.systemUptimeSeconds) }}</el-descriptions-item>
            <el-descriptions-item label="磁盘数量">{{ metrics.disks.length }}</el-descriptions-item>
          </el-descriptions>
        </div>

        <div class="monitor-block">
          <h4>Java 虚拟机</h4>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="Java 版本">{{ metrics.jvm.javaVersion }}</el-descriptions-item>
            <el-descriptions-item label="供应商">{{ metrics.jvm.javaVendor }}</el-descriptions-item>
            <el-descriptions-item label="非堆内存">{{ formatBytes(metrics.jvm.nonHeapUsedBytes) }}</el-descriptions-item>
            <el-descriptions-item label="线程数">
              {{ metrics.jvm.threadCount }}（峰值 {{ metrics.jvm.peakThreadCount }}）
            </el-descriptions-item>
            <el-descriptions-item label="GC 次数">{{ metrics.jvm.gcCount }}</el-descriptions-item>
            <el-descriptions-item label="GC 耗时">{{ metrics.jvm.gcTimeMs }} ms</el-descriptions-item>
            <el-descriptions-item label="启动时间">{{ formatTime(metrics.jvm.startTime) }}</el-descriptions-item>
            <el-descriptions-item label="JVM 运行时长">{{ formatSeconds(metrics.jvm.uptimeMs / 1000) }}</el-descriptions-item>
          </el-descriptions>
        </div>
      </div>

      <div class="monitor-block">
        <h4>磁盘</h4>
        <el-table :data="metrics.disks" size="small" border>
          <el-table-column prop="name" label="名称" min-width="140" />
          <el-table-column prop="mount" label="挂载点" min-width="140" />
          <el-table-column label="已用 / 总量" min-width="220">
            <template #default="{ row }">
              <el-progress
                :percentage="Math.round(percent(row.totalBytes - row.usableBytes, row.totalBytes))"
                :stroke-width="10"
              />
              <div class="monitor-sub">
                {{ formatBytes(row.totalBytes - row.usableBytes) }} / {{ formatBytes(row.totalBytes) }}
              </div>
            </template>
          </el-table-column>
          <el-table-column label="可用" width="130">
            <template #default="{ row }">{{ formatBytes(row.usableBytes) }}</template>
          </el-table-column>
        </el-table>
      </div>
    </template>
  </div>
</template>