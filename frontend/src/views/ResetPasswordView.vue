<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { resetPassword } from '../api/auth'

const route = useRoute()
const router = useRouter()
const token = ref('')
const password = ref('')
const confirmPassword = ref('')
const loading = ref(false)

onMounted(() => {
  if (route.query.token) {
    token.value = String(route.query.token)
  }
})

async function submit(): Promise<void> {
  if (!token.value.trim()) {
    ElMessage.warning('请输入重置令牌')
    return
  }
  if (!/^(?=.*[A-Za-z])(?=.*\d).{8,32}$/.test(password.value)) {
    ElMessage.warning('密码必须为 8-32 位且同时包含字母与数字')
    return
  }
  if (password.value !== confirmPassword.value) {
    ElMessage.warning('两次输入的密码不一致')
    return
  }
  loading.value = true
  try {
    await resetPassword(token.value.trim(), password.value)
    ElMessage.success('密码已重置，请使用新密码登录')
    await router.push('/login')
  } catch (error) {
    ElMessage.error((error as Error).message || '重置失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <el-card class="login-card">
      <template #header><div class="login-title">重置密码</div></template>
      <el-form label-position="top" @submit.prevent="submit">
        <el-form-item label="重置令牌" required>
          <el-input v-model="token" placeholder="邮件或 email_token 表中的令牌" />
        </el-form-item>
        <el-form-item label="新密码" required>
          <el-input
            v-model="password"
            type="password"
            show-password
            autocomplete="new-password"
            placeholder="8-32 位，需同时包含字母和数字"
          />
        </el-form-item>
        <el-form-item label="确认新密码" required>
          <el-input
            v-model="confirmPassword"
            type="password"
            show-password
            autocomplete="new-password"
            @keyup.enter="submit"
          />
        </el-form-item>
        <el-button type="primary" :loading="loading" style="width: 100%" @click="submit">
          重置密码
        </el-button>
      </el-form>
      <div class="meta" style="margin-top: 16px; display: flex; justify-content: space-between">
        <router-link to="/forgot-password">重新获取令牌</router-link>
        <router-link to="/login">返回登录</router-link>
      </div>
    </el-card>
  </div>
</template>
