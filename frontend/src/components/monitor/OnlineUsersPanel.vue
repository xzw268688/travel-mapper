<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { kickOnlineUser, listOnlineUsers, type OnlineUserView } from '../../api/admin'
import { useVisiblePolling } from '../../utils/useVisiblePolling'

const loading = ref(false)
const list = ref<OnlineUserView[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const keyword = ref('')
const role = ref('')
const ip = ref('')

useVisiblePolling(load, 15000)

async function load(): Promise<void> {
  loading.value = true
  try {
    const result = await listOnlineUsers({
      keyword: keyword.value.trim() || undefined,
      role: role.value || undefined,
      ip: ip.value.trim() || undefined,
      page: page.value,
      size: size.value
    })
    list.value = result.list
    total.value = result.total
  } catch (error) {
    ElMessage.error((error as Error).message || '加载在线用户失败')
  } finally {
    loading.value = false
  }
}

function search(): void {
  page.value = 1
  void load()
}

function reset(): void {
  keyword.value = ''
  role.value = ''
  ip.value = ''
  search()
}

async function kick(row: OnlineUserView): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确定强制下线用户「${row.username || row.nickname || row.userId}」吗？该用户所有设备上的登录都会立即失效。`,
      '强制下线',
      { type: 'warning' }
    )
    await kickOnlineUser(row.userId)
    ElMessage.success('已强制下线')
    await load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error((error as Error).message || '强制下线失败')
    }
  }
}

function roleLabel(value: string | null): string {
  if (value === 'ADMIN') return '管理员'
  if (value === 'USER') return '用户'
  return value || '—'
}

function formatTime(value: string): string {
  return value ? value.replace('T', ' ').slice(0, 19) : '—'
}
</script>

<template>
  <div>
    <div class="monitor-filters">
      <el-input v-model="keyword" placeholder="用户名 / 昵称" style="width: 180px" @keyup.enter="search" />
      <el-select v-model="role" placeholder="全部角色" clearable style="width: 130px">
        <el-option label="管理员" value="ADMIN" />
        <el-option label="用户" value="USER" />
      </el-select>
      <el-input v-model="ip" placeholder="IP" style="width: 150px" @keyup.enter="search" />
      <el-button type="primary" :loading="loading" @click="search">查询</el-button>
      <el-button @click="reset">重置</el-button>
      <el-button @click="load">刷新</el-button>
      <span class="monitor-hint">5 分钟内有请求视为在线，每 15 秒自动刷新</span>
    </div>

    <el-empty v-if="!loading && list.length === 0" description="当前没有在线用户" />
    <el-table v-else v-loading="loading" :data="list" stripe>
      <el-table-column label="用户" min-width="180">
        <template #default="{ row }">
          <div>{{ row.nickname || row.username || `#${row.userId}` }}</div>
          <div class="monitor-sub">
            {{ row.username || '—' }} · #{{ row.userId }}
          </div>
        </template>
      </el-table-column>
      <el-table-column label="角色" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="row.role === 'ADMIN' ? 'warning' : 'info'">
            {{ roleLabel(row.role) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="ip" label="IP" width="150">
        <template #default="{ row }">{{ row.ip || '—' }}</template>
      </el-table-column>
      <el-table-column prop="userAgent" label="User-Agent" min-width="220" show-overflow-tooltip>
        <template #default="{ row }">{{ row.userAgent || '—' }}</template>
      </el-table-column>
      <el-table-column label="最近活跃" width="170">
        <template #default="{ row }">{{ formatTime(row.lastActiveAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="110" fixed="right">
        <template #default="{ row }">
          <el-button text type="danger" size="small" @click="kick(row)">强制下线</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="page"
      v-model:page-size="size"
      :total="total"
      :page-sizes="[10, 20, 50]"
      layout="total, sizes, prev, pager, next"
      style="margin-top: 12px; justify-content: flex-end"
      @current-change="load"
      @size-change="search"
    />
  </div>
</template>

<style scoped>
.monitor-filters {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.monitor-hint {
  font-size: 12px;
  color: var(--tm-text-3, #909399);
}

.monitor-sub {
  font-size: 12px;
  color: var(--tm-text-3, #909399);
}
</style>