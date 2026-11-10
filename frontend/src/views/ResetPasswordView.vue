<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { resetPassword, sendCode } from '../api/auth'
import AuthShell from '../components/AuthShell.vue'
import { isValidEmail, normalizeEmail } from '../utils/email'

const route = useRoute()
const router = useRouter()
const email = ref('')
const code = ref('')
const password = ref('')
const confirmPassword = ref('')
const sending = ref(false)
const submitting = ref(false)
const cooldown = ref(0)
const emailError = ref('')
const codeError = ref('')
const passwordError = ref('')
const confirmError = ref('')
const formError = ref('')
const hint = ref('')
let timer: number | undefined

onMounted(() => {
  email.value = String(route.query.email || '')
})

onBeforeUnmount(() => {
  if (timer) {
    window.clearInterval(timer)
  }
})

function validEmail(): boolean {
  emailError.value = isValidEmail(email.value) ? '' : '请输入正确的邮箱'
  return !emailError.value
}

async function requestCode(): Promise<void> {
  formError.value = ''
  if (!validEmail()) {
    return
  }
  sending.value = true
  try {
    const result = await sendCode(normalizeEmail(email.value), 'RESET')
    if (!result.emailSent) {
      formError.value = '验证码发送失败，请检查邮件配置或稍后重试'
      return
    }
    hint.value = '如果该邮箱已注册，验证码将发送到邮箱；开发环境请查看后端服务日志。'
    startCooldown()
  } catch (error) {
    formError.value = (error as Error).message || '发送失败'
  } finally {
    sending.value = false
  }
}

async function submit(): Promise<void> {
  formError.value = ''
  if (!validEmail()) {
    return
  }
  codeError.value = /^\d{6}$/.test(code.value.trim()) ? '' : '请输入 6 位数字验证码'
  passwordError.value = /^(?=.*[A-Za-z])(?=.*\d).{8,32}$/.test(password.value)
    ? ''
    : '密码必须为 8-32 位且同时包含字母与数字'
  confirmError.value = password.value === confirmPassword.value ? '' : '两次输入的密码不一致'
  if (codeError.value || passwordError.value || confirmError.value) {
    return
  }
  submitting.value = true
  try {
    await resetPassword(normalizeEmail(email.value), code.value.trim(), password.value)
    await router.push({ name: 'login', query: { reset: '1' } })
  } catch (error) {
    formError.value = (error as Error).message || '重置失败'
  } finally {
    submitting.value = false
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
  <AuthShell title="找回密码" subtitle="验证邮箱后设置新密码">
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
      <el-form-item label="邮箱" required :error="emailError">
        <el-input v-model="email" autocomplete="email" @input="emailError = ''" />
      </el-form-item>
      <el-form-item label="验证码" required :error="codeError">
        <div style="display: flex; gap: 8px; width: 100%">
          <el-input
            v-model="code"
            maxlength="6"
            placeholder="6 位数字"
            @input="codeError = ''"
          />
          <el-button :loading="sending" :disabled="cooldown > 0" @click="requestCode">
            {{ cooldown > 0 ? `${cooldown}s` : '发送验证码' }}
          </el-button>
        </div>
      </el-form-item>
      <el-form-item label="新密码" required :error="passwordError">
        <el-input
          v-model="password"
          type="password"
          show-password
          autocomplete="new-password"
          placeholder="8-32 位，需同时包含字母和数字"
          @input="passwordError = ''"
        />
      </el-form-item>
      <el-form-item label="确认新密码" required :error="confirmError">
        <el-input
          v-model="confirmPassword"
          type="password"
          show-password
          autocomplete="new-password"
          @input="confirmError = ''"
          @keyup.enter="submit"
        />
      </el-form-item>
      <el-button type="primary" :loading="submitting" style="width: 100%" @click="submit">
        重置密码
      </el-button>
    </el-form>
    <div class="meta auth-links">
      <router-link to="/login">返回登录</router-link>
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