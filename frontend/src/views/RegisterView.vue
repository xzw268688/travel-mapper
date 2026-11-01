<script setup lang="ts">
import { onBeforeUnmount, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { register, sendCode } from '../api/auth'
import { useAuthStore } from '../stores/auth'
import AuthShell from '../components/AuthShell.vue'

const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const sending = ref(false)
const cooldown = ref(0)
const formError = ref('')
const hint = ref('')
const errors = reactive({
  username: '',
  email: '',
  code: '',
  password: '',
  confirmPassword: '',
  agreeTerms: ''
})
const form = reactive({
  username: '',
  email: '',
  code: '',
  password: '',
  confirmPassword: '',
  agreeTerms: false
})
let timer: number | undefined

onBeforeUnmount(() => {
  if (timer) {
    window.clearInterval(timer)
  }
})

function validEmail(): boolean {
  errors.email = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim()) ? '' : '请输入正确的邮箱'
  return !errors.email
}

async function requestCode(): Promise<void> {
  formError.value = ''
  hint.value = ''
  if (!validEmail()) {
    return
  }
  sending.value = true
  try {
    await sendCode(form.email.trim(), 'VERIFY')
    hint.value = '验证码已发送；开发环境请查看后端服务日志。'
    startCooldown()
  } catch (error) {
    formError.value = (error as Error).message || '发送失败'
  } finally {
    sending.value = false
  }
}

function validate(): boolean {
  errors.username = /^[A-Za-z0-9_]{4,20}$/.test(form.username.trim())
    ? ''
    : '用户名必须为 4-20 位字母、数字或下划线'
  validEmail()
  errors.code = /^\d{6}$/.test(form.code.trim()) ? '' : '请输入 6 位数字验证码'
  errors.password = /^(?=.*[A-Za-z])(?=.*\d).{8,32}$/.test(form.password)
    ? ''
    : '密码必须为 8-32 位且同时包含字母与数字'
  errors.confirmPassword = form.password === form.confirmPassword ? '' : '两次输入的密码不一致'
  errors.agreeTerms = form.agreeTerms ? '' : '请先同意用户协议'
  return Object.values(errors).every((item) => !item)
}

async function submit(): Promise<void> {
  formError.value = ''
  if (!validate()) {
    return
  }
  loading.value = true
  try {
    await register({
      username: form.username.trim(),
      email: form.email.trim(),
      password: form.password,
      agreeTerms: form.agreeTerms,
      code: form.code.trim()
    })
    // 注册即已激活，直接登录进入首页
    await auth.login(form.username.trim(), form.password, true)
    await router.push('/')
  } catch (error) {
    formError.value = (error as Error).message || '注册失败'
  } finally {
    loading.value = false
  }
}

function startCooldown(): void {
  cooldown.value = 60
  if (timer) {
    window.clearInterval(timer)
  }
  timer = window.setInterval(() => {
    cooldown.value -= 1
    if (cooldown.value <= 0 && timer) {
      window.clearInterval(timer)
      timer = undefined
    }
  }, 1000)
}
</script>

<template>
  <AuthShell title="注册旅迹地图" subtitle="填写邮箱验证码后即可完成注册">
    <el-alert
      v-if="formError"
      type="error"
      :closable="false"
      show-icon
      :title="formError"
      style="margin-bottom: 16px"
    />
    <el-alert
      v-else-if="hint"
      type="success"
      :closable="false"
      show-icon
      :title="hint"
      style="margin-bottom: 16px"
    />
    <el-form label-position="top" @submit.prevent="submit">
      <el-form-item label="用户名" required :error="errors.username">
        <el-input
          v-model="form.username"
          maxlength="20"
          placeholder="4-20 位字母、数字或下划线"
          @input="errors.username = ''"
        />
      </el-form-item>
      <el-form-item label="邮箱" required :error="errors.email">
        <el-input
          v-model="form.email"
          autocomplete="email"
          placeholder="用于接收验证码"
          @input="errors.email = ''"
        />
      </el-form-item>
      <el-form-item label="验证码" required :error="errors.code">
        <div style="display: flex; gap: 8px; width: 100%">
          <el-input
            v-model="form.code"
            maxlength="6"
            placeholder="6 位数字"
            @input="errors.code = ''"
          />
          <el-button :loading="sending" :disabled="cooldown > 0" @click="requestCode">
            {{ cooldown > 0 ? `${cooldown}s` : '发送验证码' }}
          </el-button>
        </div>
      </el-form-item>
      <el-form-item label="密码" required :error="errors.password">
        <el-input
          v-model="form.password"
          type="password"
          show-password
          autocomplete="new-password"
          placeholder="8-32 位，需同时包含字母和数字"
          @input="errors.password = ''"
        />
      </el-form-item>
      <el-form-item label="确认密码" required :error="errors.confirmPassword">
        <el-input
          v-model="form.confirmPassword"
          type="password"
          show-password
          autocomplete="new-password"
          @input="errors.confirmPassword = ''"
          @keyup.enter="submit"
        />
      </el-form-item>
      <el-form-item :error="errors.agreeTerms">
        <el-checkbox v-model="form.agreeTerms" @change="errors.agreeTerms = ''">
          我已阅读并同意用户协议
        </el-checkbox>
      </el-form-item>
      <el-button type="primary" :loading="loading" style="width: 100%" @click="submit">
        注册
      </el-button>
    </el-form>
    <div class="meta auth-links">
      <router-link to="/login">已有账号？去登录</router-link>
      <router-link to="/reset-password">忘记密码</router-link>
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