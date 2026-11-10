<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { fetchSharePage, type SharePage } from '../api/notes'
import { submitReport, type ReportReason } from '../api/reports'
import { formatDate } from '../utils/datetime'

const route = useRoute()
const page = ref<SharePage | null>(null)
const error = ref('')
const loading = ref(true)
const failedThumbs = ref(new Set<number>())
const coverThumbFailed = ref(false)
const reportDialog = reactive({
  visible: false,
  submitting: false,
  reason: 'ABUSE' as ReportReason,
  description: ''
})

onMounted(async () => {
  const token = String(route.params.token || '')
  try {
    page.value = await fetchSharePage(token)
  } catch (err) {
    error.value = (err as Error).message || '分享已失效'
  } finally {
    loading.value = false
  }
})

function coverSrc(): string {
  if (!page.value) {
    return ''
  }
  if (!coverThumbFailed.value && page.value.coverThumbUrl) {
    return page.value.coverThumbUrl
  }
  return page.value.coverImageUrl || ''
}

function onCoverError(): void {
  coverThumbFailed.value = true
}

function imageSrc(index: number): string {
  const thumbs = page.value?.imageThumbs ?? []
  if (!failedThumbs.value.has(index) && thumbs[index]) {
    return thumbs[index]
  }
  return page.value?.images[index] || ''
}

function onImageError(index: number): void {
  failedThumbs.value.add(index)
}

function openReport(): void {
  reportDialog.reason = 'ABUSE'
  reportDialog.description = ''
  reportDialog.visible = true
}

async function submit(): Promise<void> {
  const description = reportDialog.description.trim()
  if (reportDialog.reason === 'OTHER' && !description) {
    ElMessage.warning('选择「其他」时请填写说明')
    return
  }
  if (description.length > 200) {
    ElMessage.warning('补充说明不能超过 200 字')
    return
  }
  reportDialog.submitting = true
  try {
    const result = await submitReport(String(route.params.token || ''), {
      reason: reportDialog.reason,
      description: description || null
    })
    reportDialog.visible = false
    ElMessage.success(result.duplicate ? '已收到举报，请勿重复提交' : '已收到举报，感谢你的反馈')
  } catch (err) {
    ElMessage.error((err as Error).message || '举报提交失败')
  } finally {
    reportDialog.submitting = false
  }
}
</script>

<template>
  <div class="plan-page" v-loading="loading">
    <el-result
      v-if="error"
      icon="error"
      title="分享已失效"
      :sub-title="error"
    />
    <el-card v-else-if="page">
      <template #header>
        <div class="trip-title">{{ page.title }}</div>
      </template>
      <div class="meta">
        {{ page.countryName }} · {{ page.cityName }} · {{ formatDate(page.createdAt) }} ·
        来自 {{ page.nicknameMasked }}
      </div>
      <p style="line-height: 1.8">{{ page.summary }}</p>
      <img
        v-if="coverSrc()"
        :src="coverSrc()"
        alt="封面"
        style="max-width: 100%; border-radius: 8px"
        @error="onCoverError"
      />
      <div style="display: flex; gap: 8px; flex-wrap: wrap; margin-top: 8px">
        <el-image
          v-for="(image, index) in page.images"
          :key="image"
          :src="imageSrc(index)"
          lazy
          fit="cover"
          style="width: 140px; height: 100px; border-radius: 6px"
          @error="onImageError(index)"
        />
      </div>
      <div style="margin-top: 16px; text-align: right">
        <el-button text type="danger" size="small" @click="openReport">举报该游记</el-button>
      </div>
    </el-card>

    <el-dialog v-model="reportDialog.visible" title="举报游记" width="480px">
      <el-form label-width="90px">
        <el-form-item label="举报原因" required>
          <el-select v-model="reportDialog.reason" style="width: 100%">
            <el-option label="垃圾广告" value="SPAM" />
            <el-option label="辱骂攻击" value="ABUSE" />
            <el-option label="色情低俗" value="PORN" />
            <el-option label="违法信息" value="ILLEGAL" />
            <el-option label="侵犯隐私" value="PRIVACY" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item
          label="补充说明"
          :required="reportDialog.reason === 'OTHER'"
        >
          <el-input
            v-model="reportDialog.description"
            type="textarea"
            :rows="4"
            maxlength="200"
            show-word-limit
            placeholder="可补充具体问题，最多 200 字"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reportDialog.visible = false">取消</el-button>
        <el-button type="danger" :loading="reportDialog.submitting" @click="submit">
          提交举报
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>
