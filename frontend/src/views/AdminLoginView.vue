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
const step = ref<'credentials' | '2fa'>('credentials')
const challengeToken = ref('')
const twoFactorCode = ref('')

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
    if (response.requires2fa) {
      challengeToken.value = response.challengeToken ?? ''
      twoFactorCode.value = ''
      step.value = '2fa'
      return
    }
    if (response.user?.role !== 'ADMIN') {
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

async function submitTwoFactor(): Promise<void> {
  formError.value = ''
  if (!twoFactorCode.value.trim()) {
    formError.value = '请输入验证码或恢复码'
    return
  }
  loading.value = true
  try {
    const response = await auth.completeTwoFactor(
      challengeToken.value,
      twoFactorCode.value.trim()
    )
    if (response.user?.role !== 'ADMIN') {
      auth.rejectNonAdmin()
      formError.value = '该账号没有管理员权限'
      step.value = 'credentials'
      return
    }
    await router.push('/admin/users')
  } catch (error) {
    formError.value = (error as Error).message || '二次验证失败'
  } finally {
    loading.value = false
  }
}

function backToCredentials(): void {
  step.value = 'credentials'
  challengeToken.value = ''
  twoFactorCode.value = ''
  formError.value = ''
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
    <el-form v-if="step === 'credentials'" label-position="top" @submit.prevent="submit">
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
    <el-form v-else label-position="top" @submit.prevent="submitTwoFactor">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="该管理员账号已开启二次验证，请输入认证器中的 6 位动态码或恢复码。"
        style="margin-bottom: 16px"
      />
      <el-form-item label="动态码或恢复码">
        <el-input
          v-model="twoFactorCode"
          maxlength="20"
          placeholder="6 位动态码或 XXXX-XXXX"
          @keyup.enter="submitTwoFactor"
        />
      </el-form-item>
      <el-button type="primary" :loading="loading" style="width: 100%" @click="submitTwoFactor">
        验证并进入后台
      </el-button>
      <div style="text-align: center; margin-top: 12px">
        <el-button link type="primary" @click="backToCredentials">返回重新输入账号密码</el-button>
      </div>
    </el-form>
  </AuthShell>
</template>