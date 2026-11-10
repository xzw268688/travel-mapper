<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  clearCacheGroup,
  getCacheMetrics,
  type CacheGroupView,
  type CacheMetrics
} from '../../api/admin'
import { useVisiblePolling } from '../../utils/useVisiblePolling'

const loading = ref(false)
const clearing = ref('')
const metrics = ref<CacheMetrics | null>(null)

useVisiblePolling(load, 15000)

async function load(): Promise<void> {
  loading.value = true
  try {
    metrics.value = await getCacheMetrics()
  } catch (error) {
    ElMessage.error((error as Error).message || '加载缓存监控失败')
  } finally {
    loading.value = false
  }
}

async function clearGroup(group: CacheGroupView): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确定清理「${group.label}」缓存吗？将删除 ${group.keyCount} 个 key，可能影响正在进行的验证码、限流或幂等请求。`,
      '清理缓存',
      { type: 'warning' }
    )
    clearing.value = group.key
    const result = await clearCacheGroup(group.key)
    ElMessage.success(`已清理 ${result.deleted} 个 key`)
    await load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error((error as Error).message || '清理缓存失败')
    }
  } finally {
    clearing.value = ''
  }
}

function formatTtl(value: number | null): string {
  if (value === null) {
    return '—'
  }
  if (value < 60) {
    return `${value} 秒`
  }
  if (value < 3600) {
    return `${Math.floor(value / 60)} 分 ${value % 60} 秒`
  }
  if (value < 86400) {
    return `${Math.floor(value / 3600)} 小时 ${Math.floor((value % 3600) / 60)} 分`
  }
  return `${Math.floor(value / 86400)} 天 ${Math.floor((value % 86400) / 3600)} 小时`
}

function ttlRange(group: CacheGroupView): string {
  if (group.minTtlSeconds === null && group.maxTtlSeconds === null) {
    return '—'
  }
  if (group.minTtlSeconds !== null && group.maxTtlSeconds !== null
      && group.minTtlSeconds !== group.maxTtlSeconds) {
    return `${formatTtl(group.minTtlSeconds)} ~ ${formatTtl(group.maxTtlSeconds)}`
  }
  return formatTtl(group.maxTtlSeconds ?? group.minTtlSeconds)
}
</script>

<template>
  <div v-loading="loading">
    <div class="monitor-toolbar">
      <span class="monitor-hint">Redis 缓存概览与业务分组；不展示原始 value，每 15 秒自动刷新</span>
      <el-button @click="load">刷新</el-button>
    </div>

    <template v-if="metrics">
      <div class="monitor-cards">
        <div class="monitor-card">
          <div class="monitor-card-label">已用内存</div>
          <div class="monitor-card-value">{{ metrics.overview.usedMemory }}</div>
          <div class="monitor-card-sub">上限 {{ metrics.overview.maxMemory }}</div>
        </div>
        <div class="monitor-card">
          <div class="monitor-card-label">连接客户端</div>
          <div class="monitor-card-value">{{ metrics.overview.connectedClients }}</div>
        </div>
        <div class="monitor-card">
          <div class="monitor-card-label">命中率</div>
          <div class="monitor-card-value">{{ (metrics.overview.hitRate * 100).toFixed(1) }}%</div>
          <div class="monitor-card-sub">
            命中 {{ metrics.overview.keyspaceHits }} / 未命中 {{ metrics.overview.keyspaceMisses }}
          </div>
        </div>
        <div class="monitor-card">
          <div class="monitor-card-label">db0 key 数</div>
          <div class="monitor-card-value">{{ metrics.overview.dbKeys }}</div>
        </div>
      </div>

      <div class="monitor-block">
        <h4>缓存分组</h4>
        <el-table :data="metrics.groups" stripe size="small">
          <el-table-column label="分组" min-width="150">
            <template #default="{ row }">
              <div>{{ row.label }}</div>
              <div class="monitor-sub">{{ row.key }}</div>
            </template>
          </el-table-column>
          <el-table-column label="key 数量" width="130">
            <template #default="{ row }">
              {{ row.keyCount }}
              <el-tag v-if="row.truncated" size="small" type="warning" style="margin-left: 4px">已达上限</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="TTL 概况" width="190">
            <template #default="{ row }">{{ ttlRange(row) }}</template>
          </el-table-column>
          <el-table-column label="清理" width="150" fixed="right">
            <template #default="{ row }">
              <el-button
                v-if="row.clearable"
                text
                type="danger"
                size="small"
                :loading="clearing === row.key"
                @click="clearGroup(row)"
              >
                清理
              </el-button>
              <span v-else class="monitor-sub">关键缓存，不可清理</span>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </template>
  </div>
</template>