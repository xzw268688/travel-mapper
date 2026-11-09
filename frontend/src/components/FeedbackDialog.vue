<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { submitFeedback, type FeedbackType } from '../api/feedback'

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  submitted: []
}>()

const route = useRoute()
const submitting = ref(false)
const form = reactive({
  type: 'BUG' as FeedbackType,
  title: '',
  content: '',
  contact: ''
})

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value)
})

function reset(): void {
  form.type = 'BUG'
  form.title = ''
  form.content = ''
  form.contact = ''
}

async function submit(): Promise<void> {
  const title = form.title.trim()
  const content = form.content.trim()
  const contact = form.contact.trim()
  if (!title) {
    ElMessage.warning('请填写反馈标题')
    return
  }
  if (title.length > 50) {
    ElMessage.warning('标题不能超过 50 字')
    return
  }
  if (content.length < 10 || content.length > 1000) {
    ElMessage.warning('正文需要 10~1000 字')
    return
  }
  if (contact.length > 100) {
    ElMessage.warning('联系方式不能超过 100 字')
    return
  }

  submitting.value = true
  try {
    await submitFeedback({
      type: form.type,
      title,
      content,
      contact: contact || null,
      pagePath: route.fullPath,
      appVersion: __APP_VERSION__
    })
    ElMessage.success('感谢反馈，我们会尽快处理')
    visible.value = false
    reset()
    emit('submitted')
  } catch (error) {
    ElMessage.error((error as Error).message || '提交失败')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog v-model="visible" title="意见反馈" width="560px" :close-on-click-modal="false">
    <el-form label-width="80px">
      <el-form-item label="分类" required>
        <el-select v-model="form.type" style="width: 100%">
          <el-option label="问题反馈" value="BUG" />
          <el-option label="功能建议" value="FEATURE" />
          <el-option label="体验问题" value="UX" />
          <el-option label="其他" value="OTHER" />
        </el-select>
      </el-form-item>
      <el-form-item label="标题" required>
        <el-input v-model="form.title" maxlength="50" show-word-limit placeholder="一句话概括问题或建议" />
      </el-form-item>
      <el-form-item label="正文" required>
        <el-input
          v-model="form.content"
          type="textarea"
          :rows="6"
          maxlength="1000"
          show-word-limit
          placeholder="请描述具体场景、操作步骤和结果（10~1000 字）"
        />
      </el-form-item>
      <el-form-item label="联系方式">
        <el-input v-model="form.contact" maxlength="100" placeholder="邮箱 / 微信，可选" />
      </el-form-item>
      <el-form-item label="提交位置">
        <span class="meta">{{ route.fullPath }}</span>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">提交反馈</el-button>
    </template>
  </el-dialog>
</template>