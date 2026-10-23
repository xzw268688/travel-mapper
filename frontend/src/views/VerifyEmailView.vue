<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { resendVerify, verifyEmail } from '../api/auth'

const route = useRoute()
const router = useRouter()
const token = ref('')
const email = ref('')
const sentByMail = ref(true)
const verifying = ref(false)
const resending = ref(false)
const cooldown = ref(0)
let timer: number | undefined

onMounted(() => {
  email.value = String(route.query.email || '')
  sentByMail.value = route.query.sent !== '0'
  if (route.query.token) {
    token.value = String(route.query.token)
  }
})

onBeforeUnmount(() => {
  if (timer) {
    window.clearInterval(timer)
  }
})

async function submit(): Promise<void> {
  if (!token.value.trim()) {
    ElMessage.warning('请输入邮件中的验证令牌')
    return
  }
  verifying.value = true
  try {
    await verifyEmail(token.value.trim())
    ElMessage.success('邮箱验证成功，请登录')
    await router.push('/login')
  } catch (error) {
    ElMessage.error((error as Error).message || '验证失败')
  } finally {
    verifying.value = false
  }
}

async function resend(): Promise<void> {
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.value.trim())) {
    ElMessage.warning('请输入正确的邮箱')
    return
  }
  resending.value = true
  try {
    const result = await resendVerify(email.value.trim())
    ElMessage.success(result.emailSent ? '验证邮件已重新发送' : '开发环境未发送邮件，请从数据库获取令牌')
    startCooldown()
  } catch (error) {
    ElMessage.error((error as Error).message || '发送失败')
  } finally {
    resending.value = false
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
  <div class="login-page">
    <el-card class="login-card">
      <template #header>
        <div class="login-title">验证邮箱</div>
      </template>
      <el-alert
        v-if="!sentByMail"
        type="warning"
        :closable="false"
        show-icon
        title="开发环境未发送邮件：验证令牌已写入 email_token 表，可在数据库中查询后粘贴到下方。"
        style="margin-bottom: 12px"
      />
      <el-form label-position="top" @submit.prevent="submit">
        <el-form-item label="验证令牌">
          <el-input
            v-model="token"
            placeholder="邮件中的令牌，或数据库 email_token 表中的 token"
            @keyup.enter="submit"
          />
        </el-form-item>
        <el-button type="primary" :loading="verifying" style="width: 100%" @click="submit">
          验证邮箱
        </el-button>
      </el-form>

      <el-divider />

      <el-form label-position="top">
        <el-form-item label="重新发送验证邮件">
          <el-input v-model="email" placeholder="注册邮箱" />
        </el-form-item>
        <el-button :loading="resending" :disabled="cooldown > 0" style="width: 100%" @click="resend">
          {{ cooldown > 0 ? `${cooldown} 秒后可重发` : '重新发送' }}
        </el-button>
      </el-form>

      <div class="meta" style="margin-top: 16px; display: flex; justify-content: space-between">
        <router-link to="/register">去注册</router-link>
        <router-link to="/login">返回登录</router-link>
      </div>
    </el-card>
  </div>
</template>
