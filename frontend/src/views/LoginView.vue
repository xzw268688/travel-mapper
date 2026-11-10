<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import AuthShell from '../components/AuthShell.vue'

const auth = useAuthStore()
const router = useRouter()
const account = ref('')
const password = ref('')
const remember = ref(true)
const loading = ref(false)
const accountError = ref('')
const passwordError = ref('')
const formError = ref('')
const step = ref<'credentials' | '2fa'>('credentials')
const challengeToken = ref('')
const twoFactorCode = ref('')

function validate(): boolean {
  accountError.value = account.value.trim() ? '' : '请输入用户名或邮箱'
  passwordError.value = password.value ? '' : '请输入密码'
  return !accountError.value && !passwordError.value
}

async function submit(): Promise<void> {
  formError.value = ''
  if (!validate()) {
    return
  }
  loading.value = true
  try {
    const response = await auth.login(account.value.trim(), password.value, remember.value)
    if (response.requires2fa) {
      challengeToken.value = response.challengeToken ?? ''
      twoFactorCode.value = ''
      step.value = '2fa'
      return
    }
    await router.push('/')
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
    await auth.completeTwoFactor(challengeToken.value, twoFactorCode.value.trim())
    await router.push('/')
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
  <AuthShell title="登录旅迹地图" subtitle="使用用户名或邮箱登录">
    <el-alert
      v-if="formError"
      type="error"
      :closable="false"
      show-icon
      :title="formError"
      style="margin-bottom: 16px"
    />
    <el-form v-if="step === 'credentials'" label-position="top" @submit.prevent="submit">
      <el-form-item label="用户名或邮箱" :error="accountError">
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
      <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px">
        <el-checkbox v-model="remember">保持登录</el-checkbox>
        <router-link to="/forgot-password" class="meta">忘记密码？</router-link>
      </div>
      <el-button type="primary" :loading="loading" style="width: 100%" @click="submit">
        登录
      </el-button>
    </el-form>
    <el-form v-else label-position="top" @submit.prevent="submitTwoFactor">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="该账号已开启二次验证，请输入认证器中的 6 位动态码；手机丢失时可使用恢复码。"
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
        验证并登录
      </el-button>
      <div class="meta auth-links" style="justify-content: center">
        <el-button link type="primary" @click="backToCredentials">返回重新输入账号密码</el-button>
      </div>
    </el-form>
    <div v-if="step === 'credentials'" class="meta auth-links">
      <router-link to="/register">注册新账号</router-link>
    </div>
  </AuthShell>
</template>

<style scoped>
.auth-links {
  display: flex;
  justify-content: space-between;
  margin-top: 18px;
}
</style>