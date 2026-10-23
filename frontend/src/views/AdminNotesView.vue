<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { restoreNote, takedownNote } from '../api/admin'

const noteId = ref('')
const reason = ref('')
const submitting = ref(false)
const lastResult = ref('')

function parseId(): number | null {
  const id = Number(noteId.value)
  if (!Number.isInteger(id) || id <= 0) {
    ElMessage.warning('请输入正确的游记 ID')
    return null
  }
  return id
}

async function takedown(): Promise<void> {
  const id = parseId()
  if (id === null) {
    return
  }
  if (!reason.value.trim()) {
    ElMessage.warning('请填写下架原因')
    return
  }
  try {
    await ElMessageBox.confirm(`确定下架游记 #${id}？该游记的全部分享链接会立即失效。`, '内容下架', {
      type: 'warning',
      confirmButtonText: '下架',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  submitting.value = true
  try {
    const result = await takedownNote(id, reason.value.trim())
    lastResult.value = `游记 #${result.noteId} 已下架，撤销 ${result.sharesRevoked} 个分享链接`
    ElMessage.success('已下架')
  } catch (error) {
    ElMessage.error((error as Error).message || '下架失败')
  } finally {
    submitting.value = false
  }
}

async function restore(): Promise<void> {
  const id = parseId()
  if (id === null) {
    return
  }
  submitting.value = true
  try {
    const result = await restoreNote(id)
    lastResult.value = `游记 #${result.noteId} 已恢复，旧分享链接不会恢复，可让作者重新创建分享`
    ElMessage.success('已恢复')
  } catch (error) {
    ElMessage.error((error as Error).message || '恢复失败')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="plan-page">
    <el-card>
      <template #header><div class="trip-title">内容下架与恢复</div></template>
      <div style="display: flex; gap: 8px; align-items: center; flex-wrap: wrap">
        <el-input v-model="noteId" placeholder="游记 ID" style="width: 160px" />
        <el-input v-model="reason" maxlength="100" placeholder="下架原因（恢复时可不填）" style="width: 360px" />
        <el-button type="danger" :loading="submitting" @click="takedown">下架</el-button>
        <el-button type="success" plain :loading="submitting" @click="restore">恢复</el-button>
      </div>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        style="margin-top: 12px"
        title="下架只隐藏内容并撤销分享，不删除数据；恢复后作者可继续编辑，但需要重新创建分享。"
      />
      <el-alert
        v-if="lastResult"
        type="success"
        :closable="false"
        show-icon
        style="margin-top: 12px"
        :title="lastResult"
      />
    </el-card>
  </div>
</template>
