<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createAnnouncement,
  deleteAnnouncement,
  getAdminAnnouncement,
  listAdminAnnouncements,
  offlineAnnouncement,
  publishAnnouncement,
  updateAnnouncement,
  type AdminAnnouncementDetailView,
  type AdminAnnouncementView,
  type AnnouncementBody,
  type AnnouncementStatus,
  type AnnouncementType
} from '../api/admin'
import AdminPageIntro from '../components/AdminPageIntro.vue'

const loading = ref(false)
const saving = ref(false)
const list = ref<AdminAnnouncementView[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const status = ref<AnnouncementStatus | ''>('')
const type = ref<AnnouncementType | ''>('')
const keyword = ref('')

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const form = reactive<AnnouncementBody>({
  title: '',
  content: '',
  type: 'NOTICE',
  pinned: false,
  publishAt: null,
  expireAt: null
})

const previewVisible = ref(false)
const previewItem = ref<AdminAnnouncementDetailView | null>(null)

const STATUS_LABELS: Record<AnnouncementStatus, string> = {
  DRAFT: '草稿',
  PUBLISHED: '已发布',
  OFFLINE: '已下线'
}
const STATUS_TAG: Record<AnnouncementStatus, 'info' | 'success' | 'warning'> = {
  DRAFT: 'info',
  PUBLISHED: 'success',
  OFFLINE: 'warning'
}
const TYPE_LABELS: Record<AnnouncementType, string> = {
  NOTICE: '通知',
  ANNOUNCEMENT: '公告'
}

onMounted(load)

async function load(): Promise<void> {
  loading.value = true
  try {
    const result = await listAdminAnnouncements({
      status: status.value || undefined,
      type: type.value || undefined,
      keyword: keyword.value.trim() || undefined,
      page: page.value,
      size: size.value
    })
    list.value = result.list
    total.value = result.total
  } catch (error) {
    ElMessage.error((error as Error).message || '加载公告失败')
  } finally {
    loading.value = false
  }
}

function search(): void {
  page.value = 1
  void load()
}

function reset(): void {
  status.value = ''
  type.value = ''
  keyword.value = ''
  search()
}

function openCreate(): void {
  editingId.value = null
  form.title = ''
  form.content = ''
  form.type = 'NOTICE'
  form.pinned = false
  form.publishAt = null
  form.expireAt = null
  dialogVisible.value = true
}

async function openEdit(row: AdminAnnouncementView): Promise<void> {
  try {
    const detail = await getAdminAnnouncement(row.id)
    editingId.value = detail.id
    form.title = detail.title
    form.content = detail.content
    form.type = detail.type
    form.pinned = detail.pinned
    form.publishAt = detail.publishAt
    form.expireAt = detail.expireAt
    dialogVisible.value = true
  } catch (error) {
    ElMessage.error((error as Error).message || '加载公告详情失败')
  }
}

async function submit(): Promise<void> {
  if (!form.title.trim()) {
    ElMessage.warning('请填写标题')
    return
  }
  if (!form.content.trim()) {
    ElMessage.warning('请填写正文')
    return
  }
  if (form.publishAt && form.expireAt && form.expireAt <= form.publishAt) {
    ElMessage.warning('结束时间必须晚于开始时间')
    return
  }
  saving.value = true
  try {
    const body: AnnouncementBody = {
      title: form.title.trim(),
      content: form.content,
      type: form.type,
      pinned: form.pinned,
      publishAt: form.publishAt,
      expireAt: form.expireAt
    }
    if (editingId.value) {
      await updateAnnouncement(editingId.value, body)
      ElMessage.success('公告已更新')
    } else {
      await createAnnouncement(body)
      ElMessage.success('公告已保存为草稿')
    }
    dialogVisible.value = false
    await load()
  } catch (error) {
    ElMessage.error((error as Error).message || '保存公告失败')
  } finally {
    saving.value = false
  }
}

async function preview(row: AdminAnnouncementView): Promise<void> {
  try {
    previewItem.value = await getAdminAnnouncement(row.id)
    previewVisible.value = true
  } catch (error) {
    ElMessage.error((error as Error).message || '加载公告详情失败')
  }
}

async function publish(row: AdminAnnouncementView): Promise<void> {
  try {
    await publishAnnouncement(row.id)
    ElMessage.success('公告已发布')
    await load()
  } catch (error) {
    ElMessage.error((error as Error).message || '发布公告失败')
  }
}

async function offline(row: AdminAnnouncementView): Promise<void> {
  try {
    await offlineAnnouncement(row.id)
    ElMessage.success('公告已下线')
    await load()
  } catch (error) {
    ElMessage.error((error as Error).message || '下线公告失败')
  }
}

async function remove(row: AdminAnnouncementView): Promise<void> {
  try {
    await ElMessageBox.confirm(`确定删除公告「${row.title}」吗？已读记录会保留。`, '删除公告', {
      type: 'warning'
    })
    await deleteAnnouncement(row.id)
    ElMessage.success('公告已删除')
    await load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error((error as Error).message || '删除公告失败')
    }
  }
}

function typeLabel(value: AnnouncementType): string {
  return TYPE_LABELS[value]
}

function statusLabel(value: AnnouncementStatus): string {
  return STATUS_LABELS[value]
}

function statusTag(value: AnnouncementStatus): 'info' | 'success' | 'warning' {
  return STATUS_TAG[value]
}

function formatTime(value: string | null): string {
  return value ? value.replace('T', ' ').slice(0, 16) : '—'
}

function readRateText(row: AdminAnnouncementView): string {
  if (!row.totalUsers) {
    return `${row.readCount} 人（暂无普通用户）`
  }
  return `${row.readCount} / ${row.totalUsers}（${(row.readRate * 100).toFixed(1)}%）`
}
</script>

<template>
  <div class="plan-page">
    <el-card>
      <template #header>
        <div class="trip-title">通知公告</div>
      </template>
      <AdminPageIntro title="这个页面是干什么的？">
        发布维护通知、功能介绍或活动公告。用户会在地图首页看到当前生效公告的横幅；发布/下线不会删除历史记录，删除为软删除并保留已读人数。
      </AdminPageIntro>

      <div class="announcement-filters">
        <el-input v-model="keyword" placeholder="标题关键字" style="width: 200px" @keyup.enter="search" />
        <el-select v-model="status" placeholder="全部状态" clearable style="width: 130px">
          <el-option label="草稿" value="DRAFT" />
          <el-option label="已发布" value="PUBLISHED" />
          <el-option label="已下线" value="OFFLINE" />
        </el-select>
        <el-select v-model="type" placeholder="全部类型" clearable style="width: 130px">
          <el-option label="通知" value="NOTICE" />
          <el-option label="公告" value="ANNOUNCEMENT" />
        </el-select>
        <el-button type="primary" :loading="loading" @click="search">查询</el-button>
        <el-button @click="reset">重置</el-button>
        <el-button type="success" @click="openCreate">新建公告</el-button>
      </div>

      <el-empty v-if="!loading && list.length === 0" description="暂无公告" />
      <el-table v-else v-loading="loading" :data="list" stripe>
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.type === 'ANNOUNCEMENT' ? 'success' : 'info'">
              {{ typeLabel(row.type) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="置顶" width="80">
          <template #default="{ row }">
            <el-tag v-if="row.pinned" size="small" type="warning">置顶</el-tag>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="95">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTag(row.status)">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="生效时间" width="180">
          <template #default="{ row }">
            <div>{{ formatTime(row.publishAt) }}</div>
            <div class="announcement-time">至 {{ formatTime(row.expireAt) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="已读" width="180">
          <template #default="{ row }">{{ readRateText(row) }}</template>
        </el-table-column>
        <el-table-column label="更新时间" width="150">
          <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button text size="small" @click="preview(row)">预览</el-button>
            <el-button text size="small" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="row.status !== 'PUBLISHED'" text size="small" type="success" @click="publish(row)">
              发布
            </el-button>
            <el-button v-else text size="small" type="warning" @click="offline(row)">下线</el-button>
            <el-button text size="small" type="danger" @click="remove(row)">删除</el-button>
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

    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑公告' : '新建公告'"
      width="640px"
      destroy-on-close
    >
      <el-form label-position="top">
        <el-form-item label="标题" required>
          <el-input v-model="form.title" maxlength="100" show-word-limit placeholder="例如：系统维护通知" />
        </el-form-item>
        <el-form-item label="类型">
          <el-radio-group v-model="form.type">
            <el-radio value="NOTICE">通知</el-radio>
            <el-radio value="ANNOUNCEMENT">公告</el-radio>
          </el-radio-group>
          <el-checkbox v-model="form.pinned" style="margin-left: 16px">置顶</el-checkbox>
        </el-form-item>
        <el-form-item label="正文（纯文本，保留换行）" required>
          <el-input v-model="form.content" type="textarea" :rows="10" maxlength="5000" show-word-limit />
        </el-form-item>
        <el-form-item label="生效时间（可选）">
          <div style="display: flex; gap: 12px; flex-wrap: wrap">
            <el-date-picker
              v-model="form.publishAt"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm:ss"
              placeholder="开始时间，留空为发布即生效"
              style="width: 230px"
            />
            <el-date-picker
              v-model="form.expireAt"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm:ss"
              placeholder="结束时间，留空为长期有效"
              style="width: 230px"
            />
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="previewVisible" title="公告预览" width="600px">
      <div v-if="previewItem">
        <div class="announcement-preview-title">
          <el-tag size="small" :type="previewItem.type === 'ANNOUNCEMENT' ? 'success' : 'info'">
            {{ typeLabel(previewItem.type) }}
          </el-tag>
          <strong>{{ previewItem.title }}</strong>
          <el-tag v-if="previewItem.pinned" size="small" type="warning">置顶</el-tag>
        </div>
        <div class="announcement-preview-meta">
          {{ formatTime(previewItem.publishAt) }} 至 {{ formatTime(previewItem.expireAt) }}
        </div>
        <pre class="announcement-preview-content">{{ previewItem.content }}</pre>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.announcement-filters {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.announcement-time {
  font-size: 12px;
  color: var(--tm-text-3, #909399);
}

.announcement-preview-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 17px;
}

.announcement-preview-meta {
  margin: 8px 0 16px;
  font-size: 12px;
  color: var(--tm-text-3, #909399);
}

.announcement-preview-content {
  margin: 0;
  padding: 14px 16px;
  max-height: 420px;
  overflow: auto;
  background: var(--tm-brand-soft, #f5f7ff);
  border-radius: 8px;
  font-family: inherit;
  font-size: 14px;
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>