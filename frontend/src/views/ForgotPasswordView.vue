<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { forgotPassword } from '../api/auth'

const email = ref('')
const loading = ref(false)
const sent = ref<boolean | null>(null)

async function submit(): Promise<void> {
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.value.trim())) {
    ElMessage.warning('请输入正确的邮箱')
    return
  }
  loading.value = true
  try {
    const result = await forgotPassword(email.value.trim())
    sent.value = result.emailSent
    ElMessage.success('如果邮箱已注册，重置令牌已生成')
  } catch (error) {
    ElMessage.error((error as Error).message || '发送失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <el-card class="login-card">
      <template #header><div class="login-title">找回密码</div></template>
      <el-form label-position="top" @submit.prevent="submit">
        <el-form-item label="注册邮箱">
          <el-input v-model="email" placeholder="you@example.com" @keyup.enter="submit" />
        </el-form-item>
        <el-button type="primary" :loading="loading" style="width: 100%" @click="submit">
          发送重置令牌
        </el-button>
      </el-form>

      <el-alert
        v-if="sent === false"
        type="warning"
        :closable="false"
        show-icon
        style="margin-top: 12px"
        title="开发环境未发送邮件：重置令牌在 email_token 表（type=RESET），查询后到“重置密码”页粘贴。"
      />
      <el-alert
        v-else-if="sent === true"
        type="success"
        :closable="false"
        show-icon
        style="margin-top: 12px"
        title="重置邮件已发送，请查收邮件中的令牌。"
      />

      <div class="meta" style="margin-top: 16px; display: flex; justify-content: space-between">
        <router-link to="/reset-password">已有令牌？重置密码</router-link>
        <router-link to="/login">返回登录</router-link>
      </div>
    </el-card>
  </div>
</template>
