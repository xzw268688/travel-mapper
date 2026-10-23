<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { register } from '../api/auth'

const router = useRouter()
const loading = ref(false)
const form = reactive({
  username: '',
  email: '',
  nickname: '',
  password: '',
  confirmPassword: '',
  agreeTerms: false
})

function validate(): boolean {
  if (!/^[A-Za-z0-9_]{4,20}$/.test(form.username.trim())) {
    ElMessage.warning('用户名必须为 4-20 位字母、数字或下划线')
    return false
  }
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) {
    ElMessage.warning('请输入正确的邮箱')
    return false
  }
  if (!/^(?=.*[A-Za-z])(?=.*\d).{8,32}$/.test(form.password)) {
    ElMessage.warning('密码必须为 8-32 位且同时包含字母与数字')
    return false
  }
  if (form.password !== form.confirmPassword) {
    ElMessage.warning('两次输入的密码不一致')
    return false
  }
  if (!form.agreeTerms) {
    ElMessage.warning('请先同意用户协议')
    return false
  }
  return true
}

async function submit(): Promise<void> {
  if (!validate()) {
    return
  }
  loading.value = true
  try {
    const result = await register({
      username: form.username.trim(),
      email: form.email.trim(),
      password: form.password,
      nickname: form.nickname.trim() || undefined,
      agreeTerms: form.agreeTerms
    })
    ElMessage.success(
      result.emailSent ? '注册成功，验证邮件已发送' : '注册成功，开发环境未发送邮件，请使用令牌验证'
    )
    await router.push({
      name: 'verify-email',
      query: { email: form.email.trim(), sent: result.emailSent ? '1' : '0' }
    })
  } catch (error) {
    ElMessage.error((error as Error).message || '注册失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <el-card class="login-card">
      <template #header>
        <div class="login-title">注册 TravelMapper</div>
      </template>
      <el-form label-position="top" @submit.prevent="submit">
        <el-form-item label="用户名" required>
          <el-input v-model="form.username" maxlength="20" placeholder="4-20 位字母、数字或下划线" />
        </el-form-item>
        <el-form-item label="邮箱" required>
          <el-input v-model="form.email" autocomplete="email" placeholder="用于接收验证令牌" />
        </el-form-item>
        <el-form-item label="昵称（可选）">
          <el-input v-model="form.nickname" maxlength="20" />
        </el-form-item>
        <el-form-item label="密码" required>
          <el-input
            v-model="form.password"
            type="password"
            show-password
            autocomplete="new-password"
            placeholder="8-32 位，需同时包含字母和数字"
          />
        </el-form-item>
        <el-form-item label="确认密码" required>
          <el-input
            v-model="form.confirmPassword"
            type="password"
            show-password
            autocomplete="new-password"
            @keyup.enter="submit"
          />
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="form.agreeTerms">我已阅读并同意用户协议</el-checkbox>
        </el-form-item>
        <el-button type="primary" :loading="loading" style="width: 100%" @click="submit">
          注册
        </el-button>
      </el-form>
      <div class="meta" style="margin-top: 16px; display: flex; justify-content: space-between">
        <router-link to="/login">已有账号？去登录</router-link>
        <router-link to="/verify-email">邮箱验证</router-link>
      </div>
    </el-card>
  </div>
</template>
