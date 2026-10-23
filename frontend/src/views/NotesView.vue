<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import CountryCitySelector from '../components/CountryCitySelector.vue'
import RichTextEditor from '../components/RichTextEditor.vue'
import { createNote } from '../api/notes'
import { hasContent } from '../utils/noteContent'

const router = useRouter()
const country = ref('JP')
const city = ref('')
const title = ref('')
const content = ref('')
const visibility = ref<'PRIVATE' | 'SHARED'>('PRIVATE')
const images = ref<string[]>([])
const cover = ref<string | null>(null)
const saving = ref(false)
const openId = ref('')
const recentIds = ref<number[]>([])

const RECENT_KEY = 'tm_note_recent_ids'

onMounted(() => {
  try {
    const parsed = JSON.parse(localStorage.getItem(RECENT_KEY) ?? '[]')
    recentIds.value = Array.isArray(parsed) ? parsed.filter((id) => typeof id === 'number').slice(0, 10) : []
  } catch {
    recentIds.value = []
  }
})

function addImage(objectKey: string): void {
  if (!images.value.includes(objectKey)) {
    images.value.push(objectKey)
  }
  if (!cover.value) {
    cover.value = objectKey
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
      coverImage: cover.value || images.value[0] || null,
      countryCode: country.value,
      cityCode: city.value,
      visibility: visibility.value
    })
    remember(result.id)
    ElMessage.success('游记已保存')
    await router.push(`/notes/${result.id}`)
  } catch (error) {
    ElMessage.error((error as Error).message || '保存失败')
  } finally {
    saving.value = false
  }
}

function remember(id: number): void {
  recentIds.value = [id, ...recentIds.value.filter((item) => item !== id)].slice(0, 10)
  localStorage.setItem(RECENT_KEY, JSON.stringify(recentIds.value))
}

function openNote(): void {
  const id = Number(openId.value)
  if (!Number.isInteger(id) || id <= 0) {
    ElMessage.warning('请输入正确的游记 ID')
    return
  }
  void router.push(`/notes/${id}`)
}
</script>

<template>
  <div class="plan-page">
    <el-card>
      <template #header><div class="trip-title">发布游记</div></template>
      <CountryCitySelector v-model:country="country" v-model:city="city" />
      <el-input
        v-model="title"
        placeholder="标题"
        maxlength="50"
        show-word-limit
        style="margin-top: 12px"
      />
      <div style="margin-top: 12px; display: flex; gap: 12px; align-items: center">
        <el-select v-model="visibility" style="width: 160px">
          <el-option label="私密" value="PRIVATE" />
          <el-option label="已分享" value="SHARED" />
        </el-select>
        <span class="meta">默认私密；创建分享后仍只按白名单展示。</span>
      </div>
      <RichTextEditor
        v-model="content"
        :max-images="20"
        @image-uploaded="addImage"
        @error="(message: string) => ElMessage.error(message)"
      />
      <div class="meta" style="margin-top: 8px">
        已上传 {{ images.length }}/20 张；单图 ≤5MB；支持粘贴或拖拽图片，前端压缩并移除 EXIF。
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

    <el-card style="margin-top: 16px">
      <template #header><div class="trip-title">打开已有游记</div></template>
      <div style="display: flex; gap: 8px; align-items: center">
        <el-input v-model="openId" placeholder="游记 ID" style="width: 160px" @keyup.enter="openNote" />
        <el-button @click="openNote">打开</el-button>
      </div>
      <div v-if="recentIds.length" style="margin-top: 8px">
        <span class="meta">最近保存：</span>
        <el-button
          v-for="id in recentIds"
          :key="id"
          link
          type="primary"
          @click="router.push(`/notes/${id}`)"
        >
          #{{ id }}
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
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
