<script setup lang="ts">
import { formatDateTime } from '../utils/datetime'
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getAdminFeedback,
  getFeedbackStats,
  listAdminFeedback,
  updateAdminFeedback,
  type AdminFeedbackDetailView,
  type AdminFeedbackView,
  type FeedbackStatsView
} from '../api/admin'
import AdminPageIntro from '../components/AdminPageIntro.vue'
import {
  feedbackStatusLabel,
  feedbackStatusTagType,
  feedbackTypeLabel,
  type FeedbackStatus
} from '../api/feedback'

const keyword = ref('')
const type = ref('')
const status = ref('')
const range = ref<[string, string] | null>(null)
const page = ref(1)
const size = ref(20)
const total = ref(0)
const rows = ref<AdminFeedbackView[]>([])
const loading = ref(false)
const stats = ref<FeedbackStatsView>({
  total: 0,
  newCount: 0,
  processingCount: 0,
  resolvedCount: 0
})

const drawerVisible = ref(false)
const detail = ref<AdminFeedbackDetailView | null>(null)
const detailLoading = ref(false)
const saving = ref(false)
const editStatus = ref<FeedbackStatus>('NEW')
const adminNote = ref('')

onMounted(() => {
  void loadStats()
  void load()
})

async function loadStats(): Promise<void> {
  try {
    stats.value = await getFeedbackStats()
  } catch (error) {
    ElMessage.error((error as Error).message || '加载反馈统计失败')
  }
}

async function load(): Promise<void> {
  loading.value = true
  try {
    const result = await listAdminFeedback({
      keyword: keyword.value.trim() || undefined,
      type: type.value || undefined,
      status: status.value || undefined,
      from: range.value?.[0],
      to: range.value?.[1],
      page: page.value,
      size: size.value
    })
    rows.value = result.list
    total.value = result.total
  } catch (error) {
    ElMessage.error((error as Error).message || '加载反馈失败')
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
  type.value = ''
  status.value = ''
  range.value = null
  search()
}

async function openDetail(row: AdminFeedbackView): Promise<void> {
  drawerVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    const result = await getAdminFeedback(row.id)
    detail.value = result
    editStatus.value = result.status
    adminNote.value = result.adminNote ?? ''
  } catch (error) {
    ElMessage.error((error as Error).message || '加载反馈详情失败')
  } finally {
    detailLoading.value = false
  }
}

async function save(): Promise<void> {
  if (!detail.value) {
    return
  }
  saving.value = true
  try {
    const updated = await updateAdminFeedback(detail.value.id, {
      status: editStatus.value,
      adminNote: adminNote.value
    })
    detail.value = updated
    adminNote.value = updated.adminNote ?? ''
    await Promise.all([loadStats(), load()])
    ElMessage.success('反馈已更新')
  } catch (error) {
    ElMessage.error((error as Error).message || '保存失败')
  } finally {
    saving.value = false
  }
}

function formatTime(value: string | null | undefined): string {
  return formatDateTime(value) || '—'
}
</script>

<template>
  <div class="plan-page">
    <el-card>
      <template #header><div class="trip-title">用户反馈</div></template>
      <AdminPageIntro title="这个页面是干什么的？">
        查看用户提交的问题与建议，按分类、状态和时间筛选，并更新处理状态与内部备注。内部备注仅管理员可见；用户只能看到状态变化，不会看到备注内容。
      </AdminPageIntro>
      <el-row :gutter="12">
        <el-col :span="6">
          <el-card shadow="never">
            <div class="meta">待处理</div>
            <div style="font-size: 26px; font-weight: 700">{{ stats.newCount }}</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="never">
            <div class="meta">处理中</div>
            <div style="font-size: 26px; font-weight: 700">{{ stats.processingCount }}</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="never">
            <div class="meta">已处理</div>
            <div style="font-size: 26px; font-weight: 700">{{ stats.resolvedCount }}</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="never">
            <div class="meta">全部</div>
            <div style="font-size: 26px; font-weight: 700">{{ stats.total }}</div>
          </el-card>
        </el-col>
      </el-row>
    </el-card>

    <el-card style="margin-top: 16px">
      <div style="display: flex; gap: 8px; align-items: center; flex-wrap: wrap; margin-bottom: 12px">
        <el-input
          v-model="keyword"
          placeholder="标题 / 正文 / 用户"
          style="width: 220px"
          @keyup.enter="search"
        />
        <el-select v-model="type" placeholder="全部分类" clearable style="width: 140px">
          <el-option label="功能建议" value="FEATURE" />
          <el-option label="问题反馈" value="BUG" />
          <el-option label="体验问题" value="UX" />
          <el-option label="其他" value="OTHER" />
        </el-select>
        <el-select v-model="status" placeholder="全部状态" clearable style="width: 140px">
          <el-option label="待处理" value="NEW" />
          <el-option label="处理中" value="PROCESSING" />
          <el-option label="已处理" value="RESOLVED" />
        </el-select>
        <el-date-picker
          v-model="range"
          type="daterange"
          value-format="YYYY-MM-DD"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          style="width: 260px"
        />
        <el-button type="primary" :loading="loading" @click="search">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>

      <el-empty v-if="!loading && rows.length === 0" description="暂无反馈" />
      <el-table v-else v-loading="loading" :data="rows" stripe @row-click="openDetail">
        <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
        <el-table-column label="分类" width="110">
          <template #default="{ row }">{{ feedbackTypeLabel(row.type) }}</template>
        </el-table-column>
        <el-table-column prop="submitter" label="提交人" width="140" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="feedbackStatusTagType(row.status)" size="small">
              {{ feedbackStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="联系方式" width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.contact || '—' }}</template>
        </el-table-column>
        <el-table-column label="提交时间" width="160">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="处理时间" width="160">
          <template #default="{ row }">{{ formatTime(row.handledAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click.stop="openDetail(row)">
              查看 / 处理
            </el-button>
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

    <el-drawer v-model="drawerVisible" title="反馈详情" size="560px">
      <div v-loading="detailLoading">
        <template v-if="detail">
          <el-descriptions :column="1" border>
            <el-descriptions-item label="提交人">
              {{ detail.submitter }}<span v-if="detail.userId">（#{{ detail.userId }}）</span>
            </el-descriptions-item>
            <el-descriptions-item label="分类">
              {{ feedbackTypeLabel(detail.type) }}
            </el-descriptions-item>
            <el-descriptions-item label="标题">{{ detail.title }}</el-descriptions-item>
            <el-descriptions-item label="正文">
              <div style="white-space: pre-wrap">{{ detail.content }}</div>
            </el-descriptions-item>
            <el-descriptions-item label="联系方式">{{ detail.contact || '—' }}</el-descriptions-item>
            <el-descriptions-item label="页面路径">{{ detail.pagePath || '—' }}</el-descriptions-item>
            <el-descriptions-item label="UA / 版本">
              {{ detail.userAgent || '—' }}<span v-if="detail.appVersion"> · v{{ detail.appVersion }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="提交时间">{{ formatTime(detail.createdAt) }}</el-descriptions-item>
            <el-descriptions-item label="最后处理">
              {{ detail.handledByName || '—' }} · {{ formatTime(detail.handledAt) }}
            </el-descriptions-item>
          </el-descriptions>

          <div style="margin-top: 16px">
            <el-form label-width="90px">
              <el-form-item label="处理状态">
                <el-select v-model="editStatus" style="width: 100%">
                  <el-option label="待处理" value="NEW" />
                  <el-option label="处理中" value="PROCESSING" />
                  <el-option label="已处理" value="RESOLVED" />
                </el-select>
              </el-form-item>
              <el-form-item label="内部备注">
                <el-input
                  v-model="adminNote"
                  type="textarea"
                  :rows="4"
                  maxlength="1000"
                  show-word-limit
                  placeholder="仅管理员可见，用户不会看到"
                />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" :loading="saving" @click="save">保存处理结果</el-button>
              </el-form-item>
            </el-form>
          </div>
        </template>
      </div>
    </el-drawer>
  </div>
</template>