<script setup lang="ts">
import { formatDateTime } from '../utils/datetime'
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  acceptReport,
  getAdminReport,
  getReportStats,
  listAdminReports,
  rejectReport,
  type AdminReportDetailView,
  type AdminReportView,
  type ReportStatsView
} from '../api/admin'
import AdminPageIntro from '../components/AdminPageIntro.vue'
import {
  reportReasonLabel,
  reportStatusLabel,
  reportStatusTagType
} from '../api/reports'

const keyword = ref('')
const reason = ref('')
const status = ref('')
const range = ref<[string, string] | null>(null)
const page = ref(1)
const size = ref(20)
const total = ref(0)
const rows = ref<AdminReportView[]>([])
const loading = ref(false)
const stats = ref<ReportStatsView>({
  total: 0,
  pendingCount: 0,
  acceptedCount: 0,
  rejectedCount: 0
})

const drawerVisible = ref(false)
const detail = ref<AdminReportDetailView | null>(null)
const detailLoading = ref(false)
const acting = ref(false)
const noteReason = ref('')

onMounted(() => {
  void loadStats()
  void load()
})

async function loadStats(): Promise<void> {
  try {
    stats.value = await getReportStats()
  } catch (error) {
    ElMessage.error((error as Error).message || '加载举报统计失败')
  }
}

async function load(): Promise<void> {
  loading.value = true
  try {
    const result = await listAdminReports({
      keyword: keyword.value.trim() || undefined,
      reason: reason.value || undefined,
      status: status.value || undefined,
      from: range.value?.[0],
      to: range.value?.[1],
      page: page.value,
      size: size.value
    })
    rows.value = result.list
    total.value = result.total
  } catch (error) {
    ElMessage.error((error as Error).message || '加载举报失败')
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
  reason.value = ''
  status.value = ''
  range.value = null
  search()
}

async function openDetail(row: AdminReportView): Promise<void> {
  drawerVisible.value = true
  detailLoading.value = true
  detail.value = null
  noteReason.value = ''
  try {
    detail.value = await getAdminReport(row.id)
  } catch (error) {
    ElMessage.error((error as Error).message || '加载举报详情失败')
  } finally {
    detailLoading.value = false
  }
}

async function refresh(id: number): Promise<void> {
  detail.value = await getAdminReport(id)
  await Promise.all([loadStats(), load()])
}

async function accept(): Promise<void> {
  if (!detail.value) {
    return
  }
  const trimmed = noteReason.value.trim()
  if (!trimmed) {
    ElMessage.warning('请填写下架原因')
    return
  }
  acting.value = true
  try {
    detail.value = await acceptReport(detail.value.id, trimmed)
    noteReason.value = ''
    await Promise.all([loadStats(), load()])
    ElMessage.success('已采纳并下架游记')
  } catch (error) {
    ElMessage.error((error as Error).message || '采纳失败')
  } finally {
    acting.value = false
  }
}

async function reject(): Promise<void> {
  if (!detail.value) {
    return
  }
  try {
    await ElMessageBox.confirm('驳回该举报？游记内容不会受到影响。', '驳回举报', {
      type: 'warning',
      confirmButtonText: '驳回',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  acting.value = true
  try {
    const id = detail.value.id
    await rejectReport(id)
    await refresh(id)
    ElMessage.success('举报已驳回')
  } catch (error) {
    ElMessage.error((error as Error).message || '驳回失败')
  } finally {
    acting.value = false
  }
}

function formatTime(value: string | null | undefined): string {
  return formatDateTime(value) || '—'
}
</script>

<template>
  <div class="plan-page">
    <el-card>
      <template #header><div class="trip-title">举报处理</div></template>
      <AdminPageIntro title="这个页面是干什么的？">
        处理分享页收到的游记举报。先核对举报快照与当前内容；采纳会下架游记并关闭该游记全部待处理举报，驳回只处理当前一条。作者历史被采纳数仅作判断参考，不会自动封禁。
      </AdminPageIntro>
      <el-row :gutter="12">
        <el-col :span="6">
          <el-card shadow="never">
            <div class="meta">待处理</div>
            <div style="font-size: 26px; font-weight: 700">{{ stats.pendingCount }}</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="never">
            <div class="meta">已采纳</div>
            <div style="font-size: 26px; font-weight: 700">{{ stats.acceptedCount }}</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="never">
            <div class="meta">已驳回</div>
            <div style="font-size: 26px; font-weight: 700">{{ stats.rejectedCount }}</div>
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
          placeholder="标题 / 说明 / 作者"
          style="width: 220px"
          @keyup.enter="search"
        />
        <el-select v-model="reason" placeholder="全部原因" clearable style="width: 150px">
          <el-option label="垃圾广告" value="SPAM" />
          <el-option label="辱骂攻击" value="ABUSE" />
          <el-option label="色情低俗" value="PORN" />
          <el-option label="违法信息" value="ILLEGAL" />
          <el-option label="侵犯隐私" value="PRIVACY" />
          <el-option label="其他" value="OTHER" />
        </el-select>
        <el-select v-model="status" placeholder="全部状态" clearable style="width: 140px">
          <el-option label="待处理" value="PENDING" />
          <el-option label="已采纳" value="ACCEPTED" />
          <el-option label="已驳回" value="REJECTED" />
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

      <el-empty v-if="!loading && rows.length === 0" description="暂无举报" />
      <el-table v-else v-loading="loading" :data="rows" stripe @row-click="openDetail">
        <el-table-column prop="noteTitle" label="标题快照" min-width="200" show-overflow-tooltip />
        <el-table-column prop="reporterName" label="举报人" width="130" show-overflow-tooltip />
        <el-table-column label="原因" width="110">
          <template #default="{ row }">{{ reportReasonLabel(row.reason) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="reportStatusTagType(row.status)" size="small">
              {{ reportStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="authorName" label="作者" width="130" show-overflow-tooltip />
        <el-table-column label="举报时间" width="160">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="处理时间" width="160">
          <template #default="{ row }">{{ formatTime(row.handledAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
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

    <el-drawer v-model="drawerVisible" title="举报详情" size="600px">
      <div v-loading="detailLoading">
        <template v-if="detail">
          <el-alert
            v-if="detail.noteUpdatedAt && detail.currentNoteUpdatedAt
              && detail.noteUpdatedAt !== detail.currentNoteUpdatedAt"
            type="warning"
            :closable="false"
            show-icon
            title="举报后内容已修改，请同时核对标题快照与当前内容。"
            style="margin-bottom: 12px"
          />
          <el-descriptions :column="1" border>
            <el-descriptions-item label="举报人">
              {{ detail.reporterName }}
              <span v-if="detail.reporterUserId">（#{{ detail.reporterUserId }}）</span>
            </el-descriptions-item>
            <el-descriptions-item label="原因">{{ reportReasonLabel(detail.reason) }}</el-descriptions-item>
            <el-descriptions-item label="补充说明">{{ detail.description || '—' }}</el-descriptions-item>
            <el-descriptions-item label="举报时间">{{ formatTime(detail.createdAt) }}</el-descriptions-item>
            <el-descriptions-item label="标题快照">{{ detail.noteTitle }}</el-descriptions-item>
            <el-descriptions-item label="正文快照">
              <div style="white-space: pre-wrap">{{ detail.noteContentText || '—' }}</div>
            </el-descriptions-item>
            <el-descriptions-item label="当前游记">
              {{ detail.currentNoteTitle || '已不可用' }}
              <span class="meta">
                · {{ detail.currentNoteVisibility === 'SHARED' ? '已分享' : '私密' }}
                <template v-if="detail.currentNoteDeleted"> · 作者已删除</template>
                <template v-else-if="detail.currentNoteTakedown"> · 已下架</template>
              </span>
            </el-descriptions-item>
            <el-descriptions-item label="当前作者">{{ detail.authorName }}</el-descriptions-item>
            <el-descriptions-item label="作者历史被采纳">
              {{ detail.authorAcceptedReportCount }} 条
            </el-descriptions-item>
            <el-descriptions-item label="处理状态">
              <el-tag :type="reportStatusTagType(detail.status)" size="small">
                {{ reportStatusLabel(detail.status) }}
              </el-tag>
              <span v-if="detail.handledAt" class="meta" style="margin-left: 8px">
                {{ detail.handledByName || '管理员' }} · {{ formatTime(detail.handledAt) }}
              </span>
            </el-descriptions-item>
          </el-descriptions>

          <div v-if="detail.status === 'PENDING'" style="margin-top: 16px">
            <el-form label-width="90px">
              <el-form-item label="下架原因" required>
                <el-input
                  v-model="noteReason"
                  maxlength="100"
                  show-word-limit
                  placeholder="采纳后写入游记下架原因，作者可见"
                />
              </el-form-item>
              <el-form-item>
                <el-button type="danger" :loading="acting" @click="accept">采纳并下架</el-button>
                <el-button :loading="acting" @click="reject">驳回举报</el-button>
              </el-form-item>
            </el-form>
            <el-alert
              v-if="detail.currentNoteDeleted"
              type="info"
              :closable="false"
              show-icon
              title="作者已删除该游记；采纳只关闭举报，不会恢复内容。"
            />
          </div>
        </template>
      </div>
    </el-drawer>
  </div>
</template>