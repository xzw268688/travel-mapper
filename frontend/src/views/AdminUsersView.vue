<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  adjustUserQuota,
  getUserDetail,
  listAdminUsers,
  setUserVip,
  updateUserStatus,
  type AdminUserDetailView,
  type AdminUserView
} from '../api/admin'
import AdminPageIntro from '../components/AdminPageIntro.vue'

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

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<AdminUserDetailView | null>(null)
const vipDate = ref('')
const vipSaving = ref(false)

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

async function openDetail(user: AdminUserView): Promise<void> {
  detailVisible.value = true
  detail.value = null
  detailLoading.value = true
  try {
    detail.value = await getUserDetail(user.id)
    vipDate.value = detail.value.vipExpireAt ? detail.value.vipExpireAt.slice(0, 19) : ''
  } catch (error) {
    ElMessage.error((error as Error).message || '加载用户详情失败')
  } finally {
    detailLoading.value = false
  }
}

async function submitVip(expireAt: string | null): Promise<void> {
  if (!detail.value) return
  vipSaving.value = true
  try {
    const result = await setUserVip(detail.value.id, expireAt)
    detail.value.vipExpireAt = result.vipExpireAt
    vipDate.value = result.vipExpireAt ? result.vipExpireAt.slice(0, 19) : ''
    await load()
    ElMessage.success(expireAt ? 'VIP 到期日已更新' : 'VIP 已清除')
  } catch (error) {
    ElMessage.error((error as Error).message || '更新 VIP 失败')
  } finally {
    vipSaving.value = false
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
      <AdminPageIntro title="这个页面是干什么的？">
        检索用户、查看额度与最近卡密兑换，并执行封禁/解封、调整 AI 次数或 VIP 到期日。封禁会让该用户现有登录立即失效；额度与 VIP 调整会写入额度流水和审计日志。
      </AdminPageIntro>
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
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button text size="small" @click="openDetail(row)">详情</el-button>
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

    <el-drawer v-model="detailVisible" title="用户详情" size="560px" :direction="'rtl'">
      <div v-loading="detailLoading">
        <template v-if="detail">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="ID">{{ detail.id }}</el-descriptions-item>
            <el-descriptions-item label="用户名">{{ detail.username }}</el-descriptions-item>
            <el-descriptions-item label="昵称">{{ detail.nickname || '—' }}</el-descriptions-item>
            <el-descriptions-item label="邮箱">{{ detail.email }}</el-descriptions-item>
            <el-descriptions-item label="角色">{{ detail.role }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ detail.status }}</el-descriptions-item>
            <el-descriptions-item label="今日免费">{{ detail.freeUsedToday }}</el-descriptions-item>
            <el-descriptions-item label="次数包">{{ detail.packBalance }}</el-descriptions-item>
            <el-descriptions-item label="游记">{{ detail.noteCount }}</el-descriptions-item>
            <el-descriptions-item label="行程">{{ detail.itineraryCount }}</el-descriptions-item>
            <el-descriptions-item label="旅行">{{ detail.tripCount }}</el-descriptions-item>
            <el-descriptions-item label="注册时间">{{ formatTime(detail.createdAt) }}</el-descriptions-item>
          </el-descriptions>

          <div class="detail-section">
            <div class="trip-title" style="font-size: 14px">VIP</div>
            <div class="meta">当前到期：{{ formatTime(detail.vipExpireAt) }}</div>
            <div style="display: flex; gap: 8px; align-items: center; margin-top: 8px; flex-wrap: wrap">
              <el-date-picker
                v-model="vipDate"
                type="datetime"
                value-format="YYYY-MM-DDTHH:mm:ss"
                placeholder="设置到期时间"
              />
              <el-button
                type="primary"
                :loading="vipSaving"
                :disabled="!vipDate"
                @click="submitVip(vipDate)"
              >
                保存到期日
              </el-button>
              <el-button :loading="vipSaving" @click="submitVip(null)">清除 VIP</el-button>
            </div>
            <div class="meta" style="margin-top: 6px">
              设为过去时间即立即收回；按天数调整请使用列表中的「调整额度」。
            </div>
          </div>

          <div class="detail-section">
            <div class="trip-title" style="font-size: 14px">最近兑换</div>
            <el-empty
              v-if="detail.recentRedemptions.length === 0"
              description="暂无兑换记录"
              :image-size="60"
            />
            <el-table v-else :data="detail.recentRedemptions" size="small" stripe>
              <el-table-column prop="code" label="卡密" min-width="200" />
              <el-table-column prop="type" label="类型" width="110" />
              <el-table-column prop="value" label="面额" width="80" />
              <el-table-column label="时间" width="150">
                <template #default="{ row }">{{ formatTime(row.usedAt) }}</template>
              </el-table-column>
            </el-table>
          </div>
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.detail-section {
  margin-top: 20px;
}
</style>
