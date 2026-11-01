<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import AuthShell from '../components/AuthShell.vue'

const auth = useAuthStore()
const router = useRouter()
const account = ref('')
const password = ref('')
const loading = ref(false)
const accountError = ref('')
const passwordError = ref('')
const formError = ref('')

async function submit(): Promise<void> {
  formError.value = ''
  accountError.value = account.value.trim() ? '' : '请输入管理员账号'
  passwordError.value = password.value ? '' : '请输入密码'
  if (accountError.value || passwordError.value) {
    return
  }
  loading.value = true
  try {
    const response = await auth.login(account.value.trim(), password.value, true)
    if (response.user.role !== 'ADMIN') {
      auth.rejectNonAdmin()
      formError.value = '该账号没有管理员权限'
      return
    }
    await router.push('/admin/users')
  } catch (error) {
    formError.value = (error as Error).message || '登录失败'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <AuthShell title="管理员登录" subtitle="仅限平台管理员使用">
    <el-alert
      v-if="formError"
      type="error"
      :closable="false"
      show-icon
      :title="formError"
      style="margin-bottom: 16px"
    />
    <el-form label-position="top" @submit.prevent="submit">
      <el-form-item label="管理员账号" :error="accountError">
        <el-input v-model="account" autocomplete="username" @input="accountError = ''" />
      </el-form-item>
      <el-form-item label="密码" :error="passwordError">
        <el-input
          v-model="password"
          type="password"
          show-password
          autocomplete="current-password"
          @input="passwordError = ''"
          @keyup.enter="submit"
        />
      </el-form-item>
      <el-button type="primary" :loading="loading" style="width: 100%" @click="submit">
        登录后台
      </el-button>
    </el-form>
  </AuthShell>
</template>