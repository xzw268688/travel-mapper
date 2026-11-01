<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  adjustUserQuota,
  listAdminUsers,
  updateUserStatus,
  type AdminUserView
} from '../api/admin'

const keyword = ref('')
const status = ref('')
const page = ref(1)
const size = ref(20)
const total = ref(0)
const users = ref<AdminUserView[]>([])
const loading = ref(false)

const statusDialog = reactive({
  visible: false,
  userId: 0,
  username: '',
  status: 'BANNED',
  reason: '',
  saving: false
})
const quotaDialog = reactive({
  visible: false,
  userId: 0,
  username: '',
  type: 'AI_QUOTA',
  delta: 10,
  reason: '',
  saving: false
})

onMounted(load)

async function load(): Promise<void> {
  loading.value = true
  try {
    const result = await listAdminUsers({
      keyword: keyword.value.trim() || undefined,
      status: status.value || undefined,
      page: page.value,
      size: size.value
    })
    users.value = result.list
    total.value = result.total
  } catch (error) {
    ElMessage.error((error as Error).message || '加载用户失败')
  } finally {
    loading.value = false
  }
}

function search(): void {
  page.value = 1
  void load()
}

function openStatus(user: AdminUserView): void {
  statusDialog.userId = user.id
  statusDialog.username = user.username
  statusDialog.status = user.status === 'BANNED' ? 'NORMAL' : 'BANNED'
  statusDialog.reason = ''
  statusDialog.visible = true
}

async function submitStatus(): Promise<void> {
  if (statusDialog.status === 'BANNED' && !statusDialog.reason.trim()) {
    ElMessage.warning('请填写封禁原因')
    return
  }
  statusDialog.saving = true
  try {
    const result = await updateUserStatus(
      statusDialog.userId,
      statusDialog.status,
      statusDialog.reason.trim() || undefined
    )
    statusDialog.visible = false
    await load()
    ElMessage.success(`用户状态已更新为 ${result.status}`)
  } catch (error) {
    ElMessage.error((error as Error).message || '更新失败')
  } finally {
    statusDialog.saving = false
  }
}

function openQuota(user: AdminUserView): void {
  quotaDialog.userId = user.id
  quotaDialog.username = user.username
  quotaDialog.type = 'AI_QUOTA'
  quotaDialog.delta = 10
  quotaDialog.reason = ''
  quotaDialog.visible = true
}

async function submitQuota(): Promise<void> {
  if (!quotaDialog.reason.trim()) {
    ElMessage.warning('请填写调整原因')
    return
  }
  if (!quotaDialog.delta) {
    ElMessage.warning('调整数量不能为 0')
    return
  }
  quotaDialog.saving = true
  try {
    const result = await adjustUserQuota(quotaDialog.userId, {
      type: quotaDialog.type,
      delta: quotaDialog.delta,
      reason: quotaDialog.reason.trim()
    })
    quotaDialog.visible = false
    await load()
    ElMessage.success(
      `调整完成：次数包 ${result.packBalanceAfter}，VIP 到期 ${formatTime(result.vipExpireAtAfter)}`
    )
  } catch (error) {
    ElMessage.error((error as Error).message || '调整失败')
  } finally {
    quotaDialog.saving = false
  }
}

function formatTime(value: string | null): string {
  return value ? value.replace('T', ' ').slice(0, 16) : '—'
}
</script>

<template>
  <div class="plan-page">
    <el-card>
      <template #header><div class="trip-title">用户管理</div></template>
      <div style="display: flex; gap: 8px; align-items: center; flex-wrap: wrap">
        <el-input v-model="keyword" placeholder="用户名 / 邮箱 / 昵称" style="width: 260px" @keyup.enter="search" />
        <el-select v-model="status" placeholder="全部状态" clearable style="width: 150px">
          <el-option label="正常" value="NORMAL" />
          <el-option label="封禁" value="BANNED" />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="load">刷新</el-button>
      </div>
    </el-card>

    <el-card style="margin-top: 16px" v-loading="loading">
      <el-table :data="users" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="username" label="用户名" width="140" />
        <el-table-column prop="nickname" label="昵称" width="110" />
        <el-table-column prop="email" label="邮箱" min-width="210" show-overflow-tooltip />
        <el-table-column prop="role" label="角色" width="90" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'BANNED' ? 'danger' : row.status === 'DELETED' ? 'info' : 'success'">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="freeUsedToday" label="今日免费" width="90" />
        <el-table-column prop="packBalance" label="次数包" width="90" />
        <el-table-column label="VIP 到期" width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ formatTime(row.vipExpireAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button
              text
              size="small"
              :type="row.status === 'BANNED' ? 'success' : 'danger'"
              :disabled="row.role === 'ADMIN'"
              @click="openStatus(row)"
            >
              {{ row.status === 'BANNED' ? '解封' : '封禁' }}
            </el-button>
            <el-button text size="small" type="primary" @click="openQuota(row)">调整额度</el-button>
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
    </el-card>

    <el-dialog v-model="statusDialog.visible" :title="`${statusDialog.status === 'BANNED' ? '封禁' : '解封'}用户 ${statusDialog.username}`" width="440px">
      <el-form label-width="80px">
        <el-form-item label="目标状态">
          <el-tag :type="statusDialog.status === 'BANNED' ? 'danger' : 'success'">{{ statusDialog.status }}</el-tag>
        </el-form-item>
        <el-form-item label="原因">
          <el-input
            v-model="statusDialog.reason"
            type="textarea"
            :rows="3"
            maxlength="200"
            placeholder="封禁时必填，解封可选"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="statusDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="statusDialog.saving" @click="submitStatus">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="quotaDialog.visible" :title="`调整额度：${quotaDialog.username}`" width="440px">
      <el-form label-width="90px">
        <el-form-item label="类型">
          <el-select v-model="quotaDialog.type" style="width: 100%">
            <el-option label="AI 次数包" value="AI_QUOTA" />
            <el-option label="VIP 天数" value="VIP" />
          </el-select>
        </el-form-item>
        <el-form-item label="调整数量">
          <el-input-number v-model="quotaDialog.delta" :min="-100000" :max="100000" style="width: 100%" />
        </el-form-item>
        <el-form-item label="原因" required>
          <el-input v-model="quotaDialog.reason" type="textarea" :rows="2" maxlength="200" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="quotaDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="quotaDialog.saving" @click="submitQuota">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>
