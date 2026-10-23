<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { cancelPolish, polishStream } from '../api/ai'
import { newRequestId } from '../utils/uuid'

const text = ref('')
const output = ref('')
const style = ref('vivid')
const target = ref('selected')
const streaming = ref(false)
const status = ref('')
let controller: AbortController | null = null
let currentRequestId = ''

async function polish(): Promise<void> {
  if (!text.value.trim()) {
    ElMessage.warning('请输入要润色的文本')
    return
  }
  streaming.value = true
  output.value = ''
  status.value = '正在连接 AI...'
  controller = new AbortController()
  currentRequestId = newRequestId()
  try {
    await polishStream(
      currentRequestId,
      text.value,
      style.value,
      target.value,
      {
        onMeta: (source) => {
          status.value = `流式输出中 · 扣费来源 ${source}`
        },
        onDelta: (delta) => {
          output.value += delta
        },
        onDone: (totalChars) => {
          streaming.value = false
          status.value = `润色完成，共 ${totalChars} 字`
        },
        onCancelled: () => {
          streaming.value = false
          status.value = '已取消（不退回额度）'
        },
        onError: (message) => {
          streaming.value = false
          status.value = message
          ElMessage.error(message)
        }
      },
      controller.signal
    )
  } catch (error) {
    if ((error as Error).name === 'AbortError') {
      status.value = '已取消（不退回额度）'
    } else {
      status.value = (error as Error).message || '润色失败'
      ElMessage.error(status.value)
    }
  } finally {
    streaming.value = false
    controller = null
  }
}

async function cancel(): Promise<void> {
  if (!streaming.value || !currentRequestId) return
  try {
    await cancelPolish(currentRequestId)
  } catch (error) {
    ElMessage.error((error as Error).message || '取消失败')
  }
  controller?.abort()
  streaming.value = false
  status.value = '已取消（不退回额度）'
}
</script>

<template>
  <div class="plan-page">
    <el-card>
      <template #header>
        <div style="display: flex; align-items: center; gap: 12px; flex-wrap: wrap">
          <span class="trip-title">AI 流式润色</span>
          <el-select v-model="style" style="width: 140px">
            <el-option label="生动" value="vivid" />
            <el-option label="简洁" value="concise" />
            <el-option label="优雅" value="elegant" />
          </el-select>
          <el-select v-model="target" style="width: 140px">
            <el-option label="选中文本" value="selected" />
            <el-option label="全文" value="full" />
          </el-select>
          <el-button type="primary" :loading="streaming" @click="polish">开始润色</el-button>
          <el-button v-if="streaming" type="danger" plain @click="cancel">取消生成</el-button>
          <span class="meta">{{ status }}</span>
        </div>
      </template>
      <el-row :gutter="16">
        <el-col :xs="24" :md="12">
          <el-input v-model="text" type="textarea" :rows="12" maxlength="5000" show-word-limit placeholder="粘贴要润色的文本" />
        </el-col>
        <el-col :xs="24" :md="12">
          <el-input :model-value="output" type="textarea" :rows="12" readonly placeholder="润色结果会逐字出现" />
        </el-col>
      </el-row>
      <p class="meta" style="margin-top: 12px">
        取消按钮会先调用 /api/ai/cancel 再中断连接；主动取消或已输出部分内容后断开不退回额度。
      </p>
    </el-card>
  </div>
</template>
