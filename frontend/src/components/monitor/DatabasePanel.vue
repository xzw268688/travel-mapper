<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getDatabaseMetrics, type DatabaseMetrics } from '../../api/admin'
import Sparkline from '../Sparkline.vue'
import { useVisiblePolling } from '../../utils/useVisiblePolling'

const loading = ref(false)
const metrics = ref<DatabaseMetrics | null>(null)
const activeSamples = ref<number[]>([])
const acquireSamples = ref<number[]>([])

useVisiblePolling(load, 15000)

async function load(): Promise<void> {
  loading.value = true
  try {
    const data = await getDatabaseMetrics()
    metrics.value = data
    activeSamples.value = [...activeSamples.value, data.activeConnections].slice(-20)
    acquireSamples.value = [...acquireSamples.value, data.acquireAvgMs].slice(-20)
  } catch (error) {
    ElMessage.error((error as Error).message || '加载数据库监控失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div v-loading="loading">
    <div class="monitor-toolbar">
      <span class="monitor-hint">HikariCP 连接池指标，每 15 秒自动刷新</span>
      <el-button @click="load">刷新</el-button>
    </div>

    <el-alert
      v-if="metrics?.partial"
      type="warning"
      :closable="false"
      show-icon
      title="连接池指标不完整，可能尚未建立连接或指标未启用"
      style="margin-bottom: 12px"
    />

    <template v-if="metrics">
      <div class="monitor-cards">
        <div class="monitor-card">
          <div class="monitor-card-label">活跃连接</div>
          <div class="monitor-card-value">{{ metrics.activeConnections }}</div>
          <Sparkline :values="activeSamples" :max="Math.max(metrics.maxPoolSize, 1)" />
        </div>
        <div class="monitor-card">
          <div class="monitor-card-label">空闲连接</div>
          <div class="monitor-card-value">{{ metrics.idleConnections }}</div>
          <div class="monitor-card-sub">总连接 {{ metrics.totalConnections }}</div>
        </div>
        <div class="monitor-card">
          <div class="monitor-card-label">等待线程</div>
          <div class="monitor-card-value">{{ metrics.waitingThreads }}</div>
          <div class="monitor-card-sub">最大连接 {{ metrics.maxPoolSize }}</div>
        </div>
        <div class="monitor-card">
          <div class="monitor-card-label">获取延迟（平均）</div>
          <div class="monitor-card-value">{{ metrics.acquireAvgMs.toFixed(2) }} ms</div>
          <div class="monitor-card-sub">
            最大 {{ metrics.acquireMaxMs.toFixed(2) }} ms · 采样 {{ metrics.acquireCount }}
          </div>
          <Sparkline :values="acquireSamples" :max="Math.max(metrics.acquireMaxMs, 1)" color="#722ed1" />
        </div>
      </div>

      <div class="monitor-block">
        <h4>连接池配置</h4>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="最大连接数">{{ metrics.maxPoolSize }}</el-descriptions-item>
          <el-descriptions-item label="最小空闲连接">{{ metrics.minIdle }}</el-descriptions-item>
          <el-descriptions-item label="获取连接超时">{{ metrics.connectionTimeoutMs }} ms</el-descriptions-item>
          <el-descriptions-item label="当前连接状态">
            活跃 {{ metrics.activeConnections }} / 空闲 {{ metrics.idleConnections }} / 总 {{ metrics.totalConnections }}
          </el-descriptions-item>
        </el-descriptions>
      </div>
    </template>
  </div>
</template>