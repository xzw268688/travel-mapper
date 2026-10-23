<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const router = useRouter()
const account = ref('')
const password = ref('')
const loading = ref(false)

async function submit(): Promise<void> {
  if (!account.value || !password.value) {
    ElMessage.warning('请输入账号和密码')
    return
  }
  loading.value = true
  try {
    await auth.login(account.value, password.value)
    await router.push('/')
  } catch (error) {
    ElMessage.error((error as Error).message || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <el-card class="login-card">
      <template #header>
        <div class="login-title">登录 TravelMapper</div>
      </template>
      <el-form label-position="top" @submit.prevent="submit">
        <el-form-item label="用户名或邮箱">
          <el-input v-model="account" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="password"
            type="password"
            show-password
            autocomplete="current-password"
            @keyup.enter="submit"
          />
        </el-form-item>
        <el-button type="primary" :loading="loading" style="width: 100%" @click="submit">
          登录
        </el-button>
      </el-form>
      <p class="meta" style="margin-top: 16px">
        开发环境预置管理员 admin / Admin@123456。普通用户需先注册并完成邮箱验证。
      </p>
      <div class="meta" style="margin-top: 8px; display: flex; justify-content: space-between">
        <router-link to="/register">注册新账号</router-link>
        <router-link to="/forgot-password">忘记密码</router-link>
      </div>
      <div class="meta" style="margin-top: 6px; text-align: right">
        <router-link to="/verify-email">邮箱验证 / 重新发送</router-link>
      </div>
    </el-card>
  </div>
</template>
