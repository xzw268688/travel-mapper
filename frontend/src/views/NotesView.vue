<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import CountryCitySelector from '../components/CountryCitySelector.vue'
import RichTextEditor from '../components/RichTextEditor.vue'
import { createNote, deleteNote, listNotes, type NoteListItem } from '../api/notes'
import { hasContent } from '../utils/noteContent'

const router = useRouter()
const country = ref('JP')
const city = ref('')
const title = ref('')
const content = ref('')
const visibility = ref<'PRIVATE' | 'SHARED'>('PRIVATE')
const images = ref<string[]>([])
const videos = ref<string[]>([])
const cover = ref<string | null>(null)
const saving = ref(false)

const keyword = ref('')
const dateRange = ref<[string, string] | null>(null)
const notes = ref<NoteListItem[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 10
const loadingNotes = ref(false)
const failedCovers = ref(new Set<number>())
const failedThumbs = ref(new Set<number>())

onMounted(() => {
  void loadNotes()
})

async function loadNotes(resetPage = false): Promise<void> {
  if (resetPage) {
    page.value = 1
  }
  loadingNotes.value = true
  try {
    const result = await listNotes({
      keyword: keyword.value.trim() || undefined,
      from: dateRange.value?.[0],
      to: dateRange.value?.[1],
      page: page.value,
      size: pageSize
    })
    notes.value = result.list
    total.value = result.total
  } catch (error) {
    ElMessage.error((error as Error).message || '加载游记失败')
  } finally {
    loadingNotes.value = false
  }
}

function resetSearch(): void {
  keyword.value = ''
  dateRange.value = null
  void loadNotes(true)
}

function coverSrc(item: NoteListItem): string {
  if (failedCovers.value.has(item.id)) {
    return ''
  }
  if (failedThumbs.value.has(item.id)) {
    return item.coverImageUrl || ''
  }
  return item.coverThumbUrl || item.coverImageUrl || ''
}

function markCoverFailed(item: NoteListItem): void {
  if (!failedThumbs.value.has(item.id) && item.coverThumbUrl) {
    failedThumbs.value.add(item.id)
    return
  }
  failedCovers.value.add(item.id)
}

function formatDate(value: string): string {
  return value ? value.slice(0, 10) : ''
}

function visibilityLabel(item: NoteListItem): string {
  if (item.takenDown) {
    return '已下架'
  }
  return item.visibility === 'SHARED' ? '已分享' : '私密'
}

async function removeNote(item: NoteListItem): Promise<void> {
  try {
    await ElMessageBox.confirm(`删除游记「${item.title}」？`, '删除游记', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    await deleteNote(item.id)
    ElMessage.success('游记已删除')
    await loadNotes()
  } catch (error) {
    ElMessage.error((error as Error).message || '删除失败')
  }
}

function addImage(objectKey: string): void {
  if (!images.value.includes(objectKey)) {
    images.value.push(objectKey)
  }
  if (!cover.value) {
    cover.value = objectKey
  }
}

function addVideo(objectKey: string): void {
  if (!videos.value.includes(objectKey)) {
    videos.value.push(objectKey)
  }
}

async function save(): Promise<void> {
  if (!country.value || !city.value || !title.value.trim()) {
    ElMessage.warning('请填写国家、城市和标题')
    return
  }
  if (!hasContent(content.value)) {
    ElMessage.warning('请填写正文')
    return
  }
  saving.value = true
  try {
    const result = await createNote({
      title: title.value,
      content: content.value,
      images: images.value,
      videos: videos.value,
      coverImage: cover.value || images.value[0] || null,
      countryCode: country.value,
      cityCode: city.value,
      visibility: visibility.value
    })
    ElMessage.success('游记已保存')
    await router.push(`/notes/${result.id}`)
  } catch (error) {
    ElMessage.error((error as Error).message || '保存失败')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="plan-page">
    <el-card>
      <template #header>
        <div class="notes-head">
          <span class="trip-title">最近游记</span>
          <span class="meta">共 {{ total }} 篇</span>
        </div>
      </template>
      <div class="notes-search">
        <el-input
          v-model="keyword"
          placeholder="搜索标题或正文"
          clearable
          style="width: 240px"
          @keyup.enter="loadNotes(true)"
        />
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          style="width: 260px"
        />
        <el-button type="primary" :loading="loadingNotes" @click="loadNotes(true)">查询</el-button>
        <el-button @click="resetSearch">重置</el-button>
      </div>

      <div v-loading="loadingNotes">
        <el-empty v-if="!loadingNotes && notes.length === 0" description="还没有游记，先在下方发布一篇吧" />
        <div v-for="item in notes" :key="item.id" class="note-row">
          <img
            v-if="coverSrc(item)"
            :src="coverSrc(item)"
            class="note-cover"
            alt=""
            @error="markCoverFailed(item)"
          />
          <div v-else class="note-cover note-cover-empty">无封面</div>
          <div class="note-main">
            <div class="note-title-row">
              <strong>{{ item.title }}</strong>
              <el-tag v-if="item.takenDown" type="danger" size="small" effect="plain">已下架</el-tag>
            </div>
            <div v-if="item.summary" class="meta note-summary">{{ item.summary }}</div>
            <div class="meta">
              {{ item.countryName }} · {{ item.cityName }} · {{ formatDate(item.createdAt) }} ·
              {{ visibilityLabel(item) }}
            </div>
          </div>
          <div class="note-actions">
            <el-button text type="primary" @click="router.push(`/notes/${item.id}`)">打开</el-button>
            <el-button text type="danger" @click="removeNote(item)">删除</el-button>
          </div>
        </div>
      </div>

      <el-pagination
        v-if="total > pageSize"
        v-model:current-page="page"
        :page-size="pageSize"
        :total="total"
        layout="prev, pager, next"
        style="margin-top: 12px"
        @current-change="loadNotes()"
      />
    </el-card>

    <el-card style="margin-top: 16px">
      <template #header><div class="trip-title">发布游记</div></template>
      <CountryCitySelector v-model:country="country" v-model:city="city" />
      <el-input
        v-model="title"
        placeholder="标题"
        maxlength="50"
        show-word-limit
        style="margin-top: 12px"
      />
      <div style="margin-top: 12px; display: flex; gap: 12px; align-items: center; flex-wrap: wrap">
        <el-select v-model="visibility" style="width: 160px">
          <el-option label="私密" value="PRIVATE" />
          <el-option label="已分享" value="SHARED" />
        </el-select>
        <span class="meta">默认私密；创建分享后仍只按白名单展示。</span>
      </div>
      <RichTextEditor
        v-model="content"
        :max-images="20"
        :max-videos="3"
        @image-uploaded="addImage"
        @video-uploaded="addVideo"
        @error="(message: string) => ElMessage.error(message)"
      />
      <div class="meta" style="margin-top: 8px">
        图片 {{ images.length }}/20 张（单图 ≤5MB，自动压缩并移除 EXIF）；视频 {{ videos.length }}/3 个（mp4/webm/mov，单个 ≤100MB；MOV 在部分安卓/Chrome 设备可能无法播放，建议导出 MP4 后分享）。
      </div>
      <el-radio-group v-if="images.length" v-model="cover" class="cover-list">
        <div v-for="(key, index) in images" :key="key" class="cover-item">
          <span class="meta">图片 {{ index + 1 }}</span>
          <el-radio :value="key">设为封面</el-radio>
        </div>
      </el-radio-group>
      <div style="margin-top: 12px">
        <el-button type="primary" :loading="saving" @click="save">保存并查看详情</el-button>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.notes-head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.notes-search {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.note-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 4px;
  border-bottom: 1px solid var(--tm-border);
}

.note-cover {
  width: 64px;
  height: 48px;
  object-fit: cover;
  border-radius: 6px;
  flex: none;
  background: var(--tm-brand-soft);
}

.note-cover-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  color: var(--tm-text-muted);
}

.note-main {
  flex: 1;
  min-width: 0;
}

.note-title-row {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.note-summary {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.note-actions {
  display: flex;
  gap: 4px;
  flex: none;
}

@media (max-width: 640px) {
  .note-row {
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .note-actions {
    width: 100%;
    justify-content: flex-end;
  }
}

.cover-list {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 8px;
}

.cover-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
}
</style>