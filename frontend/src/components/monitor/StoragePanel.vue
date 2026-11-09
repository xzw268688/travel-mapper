<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getStorageMetrics,
  getStorageStats,
  type StorageMetrics,
  type StorageStats
} from '../../api/admin'
import { useVisiblePolling } from '../../utils/useVisiblePolling'

const loading = ref(false)
const statsLoading = ref(false)
const metrics = ref<StorageMetrics | null>(null)
const stats = ref<StorageStats | null>(null)

useVisiblePolling(load, 30000)

async function load(): Promise<void> {
  loading.value = true
  try {
    metrics.value = await getStorageMetrics()
  } catch (error) {
    ElMessage.error((error as Error).message || '加载存储监控失败')
  } finally {
    loading.value = false
  }
}

async function loadStats(): Promise<void> {
  statsLoading.value = true
  try {
    stats.value = await getStorageStats()
    if (!stats.value.available) {
      ElMessage.warning('对象存储不可用，无法统计对象')
    }
  } catch (error) {
    ElMessage.error((error as Error).message || '统计对象失败')
  } finally {
    statsLoading.value = false
  }
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

function formatTime(value: string): string {
  return value ? value.replace('T', ' ').slice(0, 19) : '—'
}
</script>

<template>
  <div v-loading="loading">
    <div class="monitor-toolbar">
      <span class="monitor-hint">兼容 MinIO、S3Mock 与 AWS S3；健康检查每 30 秒自动刷新</span>
      <el-button @click="load">刷新状态</el-button>
      <el-button
        type="primary"
        :loading="statsLoading"
        :disabled="!metrics?.configured || !metrics?.reachable"
        @click="loadStats"
      >
        刷新统计
      </el-button>
    </div>

    <template v-if="metrics">
      <div class="monitor-cards">
        <div class="monitor-card">
          <div class="monitor-card-label">连通状态</div>
          <div class="monitor-card-value">
            <el-tag :type="metrics.reachable ? 'success' : 'danger'" size="large">
              {{ metrics.reachable ? '可访问' : '不可访问' }}
            </el-tag>
          </div>
          <div class="monitor-card-sub">探测延迟 {{ metrics.latencyMs }} ms</div>
        </div>
        <div class="monitor-card">
          <div class="monitor-card-label">对象数量</div>
          <div class="monitor-card-value">{{ stats ? stats.objectCount : '—' }}</div>
          <div class="monitor-card-sub">点击「刷新统计」按需计算</div>
        </div>
        <div class="monitor-card">
          <div class="monitor-card-label">总大小</div>
          <div class="monitor-card-value">{{ stats ? formatBytes(stats.totalBytes) : '—' }}</div>
          <div class="monitor-card-sub">{{ stats ? formatTime(stats.measuredAt) : '尚未统计' }}</div>
        </div>
      </div>

      <el-alert
        v-if="stats?.truncated"
        type="warning"
        :closable="false"
        show-icon
        title="对象数量超过扫描上限（10,000），统计结果不完整"
        style="margin-bottom: 12px"
      />

      <el-alert
        v-if="metrics.message"
        type="error"
        :closable="false"
        show-icon
        :title="metrics.message"
        style="margin-bottom: 12px"
      />

      <div class="monitor-block">
        <h4>存储配置</h4>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="Provider">{{ metrics.provider }}</el-descriptions-item>
          <el-descriptions-item label="Bucket">{{ metrics.bucket || '—' }}</el-descriptions-item>
          <el-descriptions-item label="Endpoint">{{ metrics.endpoint || '—' }}</el-descriptions-item>
          <el-descriptions-item label="Path Style">{{ metrics.pathStyle ? '是' : '否' }}</el-descriptions-item>
          <el-descriptions-item label="配置状态">
            <el-tag :type="metrics.configured ? 'success' : 'warning'" size="small">
              {{ metrics.configured ? '已配置' : '未配置或缺少凭据' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="健康检查">HEAD Bucket</el-descriptions-item>
        </el-descriptions>
      </div>
    </template>
  </div>
</template>