<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import RichTextEditor from '../components/RichTextEditor.vue'
import {
  deleteNote,
  getNote,
  revokeShare,
  shareNote,
  updateNote,
  type NoteResponse
} from '../api/notes'
import { contentToPreview, hasContent } from '../utils/noteContent'
import { sharePageUrl } from '../utils/share'

const route = useRoute()
const router = useRouter()
const noteId = computed(() => Number(route.params.id))

const note = ref<NoteResponse | null>(null)
const loading = ref(true)
const error = ref('')
const editing = ref(false)
const saving = ref(false)
const deleting = ref(false)
const sharing = ref(false)
const expireDays = ref(7)

const editTitle = ref('')
const editContent = ref('')
const editVisibility = ref<'PRIVATE' | 'SHARED'>('PRIVATE')
const editImages = ref<string[]>([])
const editCover = ref<string | null>(null)

const imageUrlByKey = computed<Record<string, string>>(() => {
  const map: Record<string, string> = {}
  note.value?.images.forEach((image) => {
    if (image.url) {
      map[image.objectKey] = image.url
    }
  })
  return map
})

const renderedContent = computed(() =>
  note.value ? contentToPreview(note.value.content, imageUrlByKey.value) : ''
)

const shareUrl = computed(() => {
  const token = note.value?.share.token
  return token ? sharePageUrl(token) : ''
})

onMounted(load)

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    note.value = await getNote(noteId.value)
  } catch (err) {
    error.value = (err as Error).message || '游记不存在'
  } finally {
    loading.value = false
  }
}

function startEdit(): void {
  if (!note.value) {
    return
  }
  editTitle.value = note.value.title
  editContent.value = note.value.content
  editVisibility.value = note.value.visibility === 'SHARED' ? 'SHARED' : 'PRIVATE'
  editImages.value = note.value.images.map((image) => image.objectKey)
  editCover.value = note.value.coverImage
  editing.value = true
}

function cancelEdit(): void {
  editing.value = false
}

function onImageUploaded(objectKey: string): void {
  if (!editImages.value.includes(objectKey)) {
    editImages.value.push(objectKey)
  }
  if (!editCover.value) {
    editCover.value = objectKey
  }
}

async function saveEdit(): Promise<void> {
  if (!note.value) {
    return
  }
  if (!editTitle.value.trim()) {
    ElMessage.warning('标题不能为空')
    return
  }
  if (!hasContent(editContent.value)) {
    ElMessage.warning('正文不能为空')
    return
  }
  saving.value = true
  try {
    note.value = await updateNote(note.value.id, {
      title: editTitle.value,
      content: editContent.value,
      images: editImages.value,
      coverImage: editCover.value,
      visibility: editVisibility.value
    })
    editing.value = false
    ElMessage.success('游记已更新')
  } catch (err) {
    ElMessage.error((err as Error).message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function removeNote(): Promise<void> {
  if (!note.value) {
    return
  }
  try {
    await ElMessageBox.confirm('删除后游记与全部分享链接将失效，确定删除吗？', '删除游记', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  deleting.value = true
  try {
    await deleteNote(note.value.id)
    ElMessage.success('游记已删除')
    await router.push('/notes')
  } catch (err) {
    ElMessage.error((err as Error).message || '删除失败')
  } finally {
    deleting.value = false
  }
}

async function createShare(): Promise<void> {
  if (!note.value) {
    return
  }
  sharing.value = true
  try {
    const result = await shareNote(note.value.id, expireDays.value)
    ElMessage.success(result.created ? '分享已创建' : '已复用现有分享')
    await load()
  } catch (err) {
    ElMessage.error((err as Error).message || '创建分享失败')
  } finally {
    sharing.value = false
  }
}

async function revoke(): Promise<void> {
  if (!note.value) {
    return
  }
  sharing.value = true
  try {
    await revokeShare(note.value.id)
    ElMessage.success('分享已撤销')
    await load()
  } catch (err) {
    ElMessage.error((err as Error).message || '撤销失败')
  } finally {
    sharing.value = false
  }
}

async function copyShare(): Promise<void> {
  if (!shareUrl.value) {
    return
  }
  try {
    await navigator.clipboard.writeText(shareUrl.value)
    ElMessage.success('分享链接已复制')
  } catch {
    ElMessage.warning('复制失败，请手动复制')
  }
}

function previewUrl(key: string): string {
  return imageUrlByKey.value[key] ?? ''
}

function visibilityLabel(value: string): string {
  return value === 'SHARED' ? '已分享' : '私密'
}

function formatDate(value: string | null): string {
  return value ? value.slice(0, 10) : '—'
}
</script>

<template>
  <div class="plan-page" v-loading="loading">
    <el-result v-if="error" icon="error" title="无法打开游记" :sub-title="error">
      <template #extra>
        <el-button @click="router.push('/notes')">返回游记</el-button>
      </template>
    </el-result>

    <template v-else-if="note">
      <el-alert
        v-if="note.takedown"
        type="error"
        :closable="false"
        show-icon
        title="该游记已被下架：可查看但不可编辑或分享，请联系管理员处理。"
        style="margin-bottom: 16px"
      />
      <el-alert
        v-if="note.cityDisabled"
        type="warning"
        :closable="false"
        show-icon
        title="关联城市已停用，游记仍可查看，编辑时不能更换城市。"
        style="margin-bottom: 16px"
      />

      <el-card>
        <template #header>
          <div style="display: flex; align-items: center; gap: 12px; flex-wrap: wrap">
            <span class="trip-title" style="flex: 1">{{ note.title }}</span>
            <el-button v-if="!editing" :disabled="note.takedown" @click="startEdit">编辑</el-button>
            <el-button
              v-if="!editing"
              type="success"
              plain
              :disabled="note.takedown"
              @click="router.push(`/poster/${note.id}`)"
            >
              生成海报
            </el-button>
            <el-button v-if="editing" @click="cancelEdit">取消</el-button>
            <el-button v-if="editing" type="primary" :loading="saving" @click="saveEdit">保存</el-button>
            <el-button v-if="!editing" type="danger" plain :loading="deleting" @click="removeNote">删除</el-button>
            <el-button v-if="!editing" @click="router.push('/notes')">返回</el-button>
          </div>
        </template>

        <div class="note-meta">
          <el-tag size="small">{{ visibilityLabel(note.visibility) }}</el-tag>
          <span>{{ note.countryName }} · {{ note.cityName }}</span>
          <span>{{ formatDate(note.createdAt) }}</span>
          <span>旅行：{{ note.tripName || '未归组' }}</span>
          <span v-if="note.itineraryId">行程 #{{ note.itineraryId }}</span>
          <span>游记 #{{ note.id }}</span>
        </div>

        <template v-if="editing">
          <el-input
            v-model="editTitle"
            maxlength="50"
            show-word-limit
            placeholder="标题"
            style="margin-top: 12px"
          />
          <div style="margin-top: 12px">
            <el-select v-model="editVisibility" style="width: 160px">
              <el-option label="私密" value="PRIVATE" />
              <el-option label="已分享" value="SHARED" />
            </el-select>
          </div>
          <RichTextEditor
            v-model="editContent"
            :image-urls="imageUrlByKey"
            :max-images="20"
            @image-uploaded="onImageUploaded"
            @error="(message: string) => ElMessage.error(message)"
          />
          <div v-if="editImages.length" style="margin-top: 12px">
            <div class="meta">选择封面（已上传 {{ editImages.length }}/20）</div>
            <el-radio-group v-model="editCover" class="cover-list">
              <div v-for="(key, index) in editImages" :key="key" class="cover-item">
                <el-image
                  v-if="previewUrl(key)"
                  :src="previewUrl(key)"
                  fit="cover"
                  style="width: 72px; height: 52px; border-radius: 4px"
                />
                <span v-else class="meta">新图 {{ index + 1 }}</span>
                <el-radio :value="key">封面</el-radio>
              </div>
            </el-radio-group>
          </div>
        </template>

        <template v-else>
          <article class="note-content" v-html="renderedContent" />
          <div v-if="note.images.length" class="cover-list" style="margin-top: 16px">
            <el-image
              v-for="image in note.images"
              :key="image.objectKey"
              :src="image.url || ''"
              :preview-src-list="note.images.map((item) => item.url || '').filter(Boolean)"
              fit="cover"
              style="width: 140px; height: 100px; border-radius: 6px"
            />
          </div>
        </template>
      </el-card>

      <el-card v-if="!editing" style="margin-top: 16px">
        <template #header><div class="trip-title">分享</div></template>
        <template v-if="note.takedown">
          <span class="meta">游记下架期间不可创建分享。</span>
        </template>
        <template v-else-if="note.share.active && shareUrl">
          <div class="meta" style="word-break: break-all">分享链接：{{ shareUrl }}</div>
          <div class="meta" style="margin-top: 4px">
            有效期至：{{ note.share.expireAt ? formatDate(note.share.expireAt) : '永久' }}
          </div>
          <div style="margin-top: 8px; display: flex; gap: 8px">
            <el-button type="primary" @click="copyShare">复制链接</el-button>
            <el-button :loading="sharing" @click="revoke">撤销分享</el-button>
          </div>
        </template>
        <template v-else>
          <div style="display: flex; gap: 8px; align-items: center">
            <el-select v-model="expireDays" style="width: 140px">
              <el-option label="7 天" :value="7" />
              <el-option label="30 天" :value="30" />
              <el-option label="永久" :value="0" />
            </el-select>
            <el-button type="primary" :loading="sharing" @click="createShare">创建分享</el-button>
          </div>
          <div class="meta" style="margin-top: 6px">分享页只展示标题、摘要、封面和最多 6 张图片。</div>
        </template>
      </el-card>
    </template>
  </div>
</template>

<style scoped>
.note-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  color: #606266;
  font-size: 13px;
}

.note-content {
  margin-top: 16px;
  line-height: 1.8;
  word-break: break-word;
}

.note-content :deep(img) {
  max-width: 100%;
  border-radius: 6px;
}

.note-content :deep(blockquote) {
  margin: 8px 0;
  padding-left: 12px;
  border-left: 3px solid #c0c4cc;
  color: #606266;
}

.cover-list {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 8px;
}

.cover-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 6px;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
}
</style>
