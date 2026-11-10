<script setup lang="ts">
import { formatDateTime } from '../utils/datetime'
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getAdminNoteDetail,
  listAdminNotes,
  markNoteMediaReviewed,
  restoreNote,
  takedownNote,
  type AdminNoteDetailView,
  type AdminNoteView
} from '../api/admin'
import AdminPageIntro from '../components/AdminPageIntro.vue'
import { contentToPreview } from '../utils/noteContent'

const keyword = ref('')
const status = ref('')
const mediaReview = ref('')
const range = ref<[string, string] | null>(null)
const page = ref(1)
const size = ref(20)
const total = ref(0)
const notes = ref<AdminNoteView[]>([])
const loading = ref(false)
const acting = ref(false)
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<AdminNoteDetailView | null>(null)

const detailMedia = computed<Record<string, string>>(() => {
  const map: Record<string, string> = {}
  detail.value?.images.forEach((image) => {
    if (image.url) {
      map[image.objectKey] = image.url
    }
  })
  detail.value?.videos.forEach((video) => {
    if (video.url) {
      map[video.objectKey] = video.url
    }
  })
  return map
})

const detailContent = computed(() =>
  detail.value ? contentToPreview(detail.value.content, detailMedia.value) : ''
)

onMounted(load)

async function load(): Promise<void> {
  loading.value = true
  try {
    const result = await listAdminNotes({
      keyword: keyword.value.trim() || undefined,
      status: status.value || undefined,
      mediaReview: mediaReview.value || undefined,
      from: range.value?.[0],
      to: range.value?.[1],
      page: page.value,
      size: size.value
    })
    notes.value = result.list
    total.value = result.total
  } catch (error) {
    ElMessage.error((error as Error).message || '加载游记失败')
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
  status.value = ''
  mediaReview.value = ''
  range.value = null
  search()
}

async function markReviewed(note: { id: number }): Promise<void> {
  acting.value = true
  try {
    await markNoteMediaReviewed(note.id)
    await load()
    await refreshDetail(note.id)
    ElMessage.success('已标记媒体审核通过')
  } catch (error) {
    ElMessage.error((error as Error).message || '标记审核失败')
  } finally {
    acting.value = false
  }
}

async function openDetail(note: { id: number }): Promise<void> {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await getAdminNoteDetail(note.id)
  } catch (error) {
    ElMessage.error((error as Error).message || '加载游记内容失败')
  } finally {
    detailLoading.value = false
  }
}

async function refreshDetail(id: number): Promise<void> {
  if (detailVisible.value && detail.value?.id === id) {
    detail.value = await getAdminNoteDetail(id)
  }
}

async function takedown(note: { id: number; title: string }): Promise<void> {
  let reason = ''
  try {
    const result = await ElMessageBox.prompt(
      `下架《${note.title}》后全部分享链接会立即失效，请填写原因。`,
      '内容下架',
      {
        type: 'warning',
        confirmButtonText: '下架',
        cancelButtonText: '取消',
        inputPlaceholder: '下架原因（必填，最多 100 字）',
        inputValidator: (value) => {
          if (!value || !value.trim()) {
            return '请填写下架原因'
          }
          return value.length <= 100 || '原因最多 100 字'
        }
      }
    )
    reason = result.value.trim()
  } catch {
    return
  }
  acting.value = true
  try {
    await takedownNote(note.id, reason)
    await load()
    await refreshDetail(note.id)
    ElMessage.success('已下架')
  } catch (error) {
    ElMessage.error((error as Error).message || '下架失败')
  } finally {
    acting.value = false
  }
}

async function restore(note: { id: number; title: string }): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `恢复《${note.title}》？旧分享链接不会恢复，作者需要重新创建分享。`,
      '内容恢复',
      { type: 'warning', confirmButtonText: '恢复', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  acting.value = true
  try {
    await restoreNote(note.id)
    await load()
    await refreshDetail(note.id)
    ElMessage.success('已恢复')
  } catch (error) {
    ElMessage.error((error as Error).message || '恢复失败')
  } finally {
    acting.value = false
  }
}

function formatTime(value: string | null | undefined): string {
  return formatDateTime(value) || '—'
}

function visibilityLabel(value: string): string {
  return value === 'SHARED' ? '已分享' : '私密'
}
</script>

<template>
  <div class="plan-page">
    <el-card>
      <template #header><div class="trip-title">内容管理</div></template>
      <AdminPageIntro title="这个页面是干什么的？">
        查看全部用户游记，按状态、日期或关键词筛选，并打开「查看内容」核对正文与媒体后决定下架或恢复。下架会隐藏内容并撤销全部分享链接，但不会删除数据；恢复后作者需重新创建分享。
      </AdminPageIntro>
      <div style="display: flex; gap: 8px; align-items: center; flex-wrap: wrap; margin-bottom: 12px">
        <el-input v-model="keyword" placeholder="标题 / 作者" style="width: 220px" @keyup.enter="search" />
        <el-select v-model="status" placeholder="全部状态" clearable style="width: 150px">
          <el-option label="正常" value="NORMAL" />
          <el-option label="已下架" value="TAKEDOWN" />
        </el-select>
        <el-select v-model="mediaReview" placeholder="全部媒体" clearable style="width: 150px">
          <el-option label="待审核" value="PENDING" />
          <el-option label="已审核" value="REVIEWED" />
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

      <el-empty v-if="!loading && notes.length === 0" description="暂无游记" />
      <el-table v-else v-loading="loading" :data="notes" stripe>
        <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
        <el-table-column label="作者" width="140" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.authorName || '—' }}<span v-if="row.authorId">（#{{ row.authorId }}）</span>
          </template>
        </el-table-column>
        <el-table-column label="国家 / 城市" width="160">
          <template #default="{ row }">{{ row.countryName }} · {{ row.cityName }}</template>
        </el-table-column>
        <el-table-column label="可见性" width="100">
          <template #default="{ row }">{{ visibilityLabel(row.visibility) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.takenDown ? 'danger' : 'success'" size="small">
              {{ row.takenDown ? '已下架' : '正常' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="媒体审核" width="110">
          <template #default="{ row }">
            <span v-if="!row.hasMedia" class="meta">—</span>
            <el-tag v-else :type="row.mediaReviewedAt ? 'success' : 'warning'" size="small">
              {{ row.mediaReviewedAt ? '已审核' : '待审核' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="160">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click="openDetail(row)">查看内容</el-button>
            <el-button
              v-if="!row.takenDown"
              text
              type="danger"
              size="small"
              :loading="acting"
              @click="takedown(row)"
            >
              下架
            </el-button>
            <el-button v-else text type="success" size="small" :loading="acting" @click="restore(row)">
              恢复
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
      <el-alert
        type="info"
        :closable="false"
        show-icon
        style="margin-top: 12px"
        title="下架只隐藏内容并撤销分享，不删除数据；恢复后作者可继续编辑，但需要重新创建分享。"
      />
    </el-card>

    <el-drawer v-model="detailVisible" title="游记完整内容" size="720px">
      <div v-loading="detailLoading">
        <template v-if="detail">
          <el-alert
            v-if="detail.deleted"
            type="info"
            :closable="false"
            show-icon
            title="该游记已被作者删除，以下为删除前保留的内容。"
            style="margin-bottom: 12px"
          />
          <el-descriptions :column="2" border>
            <el-descriptions-item label="标题" :span="2">{{ detail.title }}</el-descriptions-item>
            <el-descriptions-item label="作者">
              {{ detail.authorName }}<span v-if="detail.authorId">（#{{ detail.authorId }}）</span>
            </el-descriptions-item>
            <el-descriptions-item label="国家 / 城市">
              {{ detail.countryName }} · {{ detail.cityName }}
            </el-descriptions-item>
            <el-descriptions-item label="可见性">
              {{ detail.visibility === 'SHARED' ? '已分享' : '私密' }}
            </el-descriptions-item>
            <el-descriptions-item label="分享状态">
              {{ detail.hasActiveShare ? `有效至 ${formatTime(detail.shareExpireAt)}` : '无有效分享' }}
            </el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ formatTime(detail.createdAt) }}</el-descriptions-item>
            <el-descriptions-item label="更新时间">{{ formatTime(detail.updatedAt) }}</el-descriptions-item>
            <el-descriptions-item label="媒体审核">
              <el-tag
                v-if="detail.images.length || detail.videos.length"
                :type="detail.mediaReviewedAt ? 'success' : 'warning'"
                size="small"
              >
                {{ detail.mediaReviewedAt ? `已审核 ${formatTime(detail.mediaReviewedAt)}` : '待审核' }}
              </el-tag>
              <span v-else class="meta">无媒体</span>
            </el-descriptions-item>
            <el-descriptions-item label="下架状态" :span="2">
              <el-tag :type="detail.takedown ? 'danger' : 'success'" size="small">
                {{ detail.takedown ? '已下架' : '正常' }}
              </el-tag>
              <span v-if="detail.takedown" class="meta" style="margin-left: 8px">
                {{ detail.takedownReason || '未填写原因' }} · {{ formatTime(detail.takedownAt) }}
              </span>
            </el-descriptions-item>
          </el-descriptions>

          <div class="admin-note-content" v-html="detailContent" />

          <div v-if="detail.images.length || detail.videos.length" style="margin-top: 16px">
            <div class="trip-title" style="font-size: 14px">媒体</div>
            <div style="display: flex; gap: 8px; flex-wrap: wrap; margin-top: 8px">
              <el-image
                v-for="image in detail.images"
                :key="image.objectKey"
                :src="image.thumbUrl || image.url || ''"
                fit="cover"
                style="width: 140px; height: 100px; border-radius: 6px"
              />
              <video
                v-for="video in detail.videos"
                :key="video.objectKey"
                :src="video.url || ''"
                controls
                style="width: 220px; border-radius: 6px"
              />
            </div>
            <el-button
              v-if="!detail.mediaReviewedAt"
              type="primary"
              plain
              size="small"
              style="margin-top: 8px"
              :loading="acting"
              @click="markReviewed(detail)"
            >
              标记已审核
            </el-button>
          </div>

          <div style="margin-top: 20px; display: flex; gap: 8px">
            <el-button
              v-if="!detail.takedown"
              type="danger"
              :loading="acting"
              @click="takedown(detail)"
            >
              下架该游记
            </el-button>
            <el-button
              v-else
              type="success"
              :loading="acting"
              @click="restore(detail)"
            >
              恢复该游记
            </el-button>
            <el-button @click="detailVisible = false">关闭</el-button>
          </div>
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.admin-note-content {
  margin-top: 16px;
  line-height: 1.8;
  word-break: break-word;
}

.admin-note-content :deep(img),
.admin-note-content :deep(video) {
  max-width: 100%;
  border-radius: 6px;
}
</style>