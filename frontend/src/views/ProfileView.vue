<script setup lang="ts">
import { formatDateTime } from '../utils/datetime'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import QRCode from 'qrcode'
import {
  changeEmail,
  changePassword,
  deleteAccount,
  disableTwoFactor,
  enableTwoFactor,
  getProfile,
  redeemCard,
  regenerateRecoveryCodes,
  sendEmailChangeCode,
  setupTwoFactor,
  updateProfile,
  type CardRedeemResponse,
  type ProfileResponse
} from '../api/user'
import {
  feedbackStatusLabel,
  feedbackStatusTagType,
  feedbackTypeLabel,
  listMyFeedback,
  type FeedbackHistoryItem
} from '../api/feedback'
import FeedbackDialog from '../components/FeedbackDialog.vue'
import { logoutAll } from '../api/auth'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const router = useRouter()

const profile = ref<ProfileResponse | null>(null)
const loading = ref(false)
const saving = ref(false)
const nickname = ref('')
const avatarPreset = ref('preset_01')
const cardCode = ref('')
const redeeming = ref(false)
const redeemResult = ref<CardRedeemResponse | null>(null)
const loggingOutAll = ref(false)
const passwordDialog = reactive({ visible: false, current: '', next: '', saving: false })
const emailDialog = reactive({
  visible: false,
  email: '',
  password: '',
  code: '',
  sending: false,
  saving: false
})
const twoFactorSetup = reactive({ visible: false, secret: '', qr: '', code: '', saving: false })
const recoveryDialog = reactive({ visible: false, codes: [] as string[] })
const disableDialog = reactive({ visible: false, password: '', code: '', saving: false })
const regenerateDialog = reactive({ visible: false, password: '', code: '', saving: false })
const deleteDialog = reactive({ visible: false, password: '', confirmText: '', deleting: false })

const feedbackDialogVisible = ref(false)
const feedbackLoading = ref(false)
const feedbackPage = ref(1)
const feedbackSize = ref(5)
const feedbackTotal = ref(0)
const feedbackList = ref<FeedbackHistoryItem[]>([])
const feedbackDetailVisible = ref(false)
const selectedFeedback = ref<FeedbackHistoryItem | null>(null)

const AVATARS = [
  { value: 'preset_01', color: '#5c7cfa' },
  { value: 'preset_02', color: '#20c997' },
  { value: 'preset_03', color: '#f59f00' },
  { value: 'preset_04', color: '#e64980' },
  { value: 'preset_05', color: '#7950f2' },
  { value: 'preset_06', color: '#15aabf' },
  { value: 'preset_07', color: '#fa5252' },
  { value: 'preset_08', color: '#82c91e' },
  { value: 'preset_09', color: '#4c6ef5' },
  { value: 'preset_10', color: '#fd7e14' },
  { value: 'preset_11', color: '#12b886' },
  { value: 'preset_12', color: '#868e96' }
]

const quota = computed(() => profile.value?.quota)
const isAdmin = computed(() => profile.value?.role === 'ADMIN')

onMounted(() => {
  const notice = sessionStorage.getItem('tm_notice')
  if (notice) {
    sessionStorage.removeItem('tm_notice')
    ElMessage.warning(notice)
  }
  void load()
  void loadFeedback()
})

async function load(): Promise<void> {
  loading.value = true
  try {
    profile.value = await getProfile()
    nickname.value = profile.value.nickname
    avatarPreset.value = profile.value.avatarPreset
  } catch (error) {
    ElMessage.error((error as Error).message || '加载个人资料失败')
  } finally {
    loading.value = false
  }
}

async function saveProfile(): Promise<void> {
  if (!nickname.value.trim()) {
    ElMessage.warning('昵称不能为空')
    return
  }
  saving.value = true
  try {
    await updateProfile({ nickname: nickname.value.trim(), avatarPreset: avatarPreset.value })
    await Promise.all([load(), auth.loadProfile()])
    ElMessage.success('个人资料已保存')
  } catch (error) {
    ElMessage.error((error as Error).message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function redeem(): Promise<void> {
  if (!cardCode.value.trim()) {
    ElMessage.warning('请输入卡密')
    return
  }
  redeeming.value = true
  try {
    redeemResult.value = await redeemCard(cardCode.value.trim())
    cardCode.value = ''
    await Promise.all([load(), auth.loadProfile()])
    ElMessage.success(
      redeemResult.value.idempotentReplay ? '该卡密已兑换过，结果已复用' : '卡密兑换成功'
    )
  } catch (error) {
    ElMessage.error((error as Error).message || '兑换失败')
  } finally {
    redeeming.value = false
  }
}

function openDelete(): void {
  if (isAdmin.value) {
    ElMessage.warning('管理员账号不能在此注销')
    return
  }
  deleteDialog.password = ''
  deleteDialog.confirmText = ''
  deleteDialog.visible = true
}

async function submitDelete(): Promise<void> {
  if (!deleteDialog.password) {
    ElMessage.warning('请输入密码')
    return
  }
  if (deleteDialog.confirmText !== '注销账号') {
    ElMessage.warning('请完整输入“注销账号”')
    return
  }
  try {
    await ElMessageBox.confirm(
      '注销后账号将匿名化，行程、游记、消费与分享链接全部失效，且无法恢复。',
      '确认注销账号',
      { type: 'error', confirmButtonText: '确认注销', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  deleteDialog.deleting = true
  try {
    await deleteAccount({ password: deleteDialog.password, confirmText: deleteDialog.confirmText })
    auth.clearLocalSession()
    ElMessage.success('账号已注销')
    await router.push('/login')
  } catch (error) {
    ElMessage.error((error as Error).message || '注销失败')
  } finally {
    deleteDialog.deleting = false
  }
}

async function logoutAllDevices(): Promise<void> {
  try {
    await ElMessageBox.confirm(
      '将退出所有设备，当前设备也需要重新登录。',
      '退出所有设备',
      { type: 'warning', confirmButtonText: '确认退出', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  loggingOutAll.value = true
  try {
    await logoutAll()
    ElMessage.success('已退出所有设备')
    auth.clearLocalSession()
    await router.push('/login')
  } catch (error) {
    ElMessage.error((error as Error).message || '退出所有设备失败')
  } finally {
    loggingOutAll.value = false
  }
}

function openPasswordDialog(): void {
  passwordDialog.current = ''
  passwordDialog.next = ''
  passwordDialog.visible = true
}

async function submitPassword(): Promise<void> {
  if (!passwordDialog.current || !passwordDialog.next) {
    ElMessage.warning('请填写当前密码与新密码')
    return
  }
  passwordDialog.saving = true
  try {
    const response = await changePassword({
      currentPassword: passwordDialog.current,
      newPassword: passwordDialog.next
    })
    auth.updateToken(response.token)
    passwordDialog.visible = false
    ElMessage.success('密码已修改，其他设备已下线')
    await Promise.all([load(), auth.loadProfile()])
  } catch (error) {
    ElMessage.error((error as Error).message || '修改密码失败')
  } finally {
    passwordDialog.saving = false
  }
}

function openEmailDialog(): void {
  emailDialog.email = ''
  emailDialog.password = ''
  emailDialog.code = ''
  emailDialog.visible = true
}

async function sendNewEmailCode(): Promise<void> {
  if (!emailDialog.email.trim()) {
    ElMessage.warning('请输入新邮箱')
    return
  }
  emailDialog.sending = true
  try {
    const result = await sendEmailChangeCode(emailDialog.email.trim())
    ElMessage.success(result.emailSent ? '验证码已发送，请查收新邮箱' : '验证码发送失败，请稍后重试')
  } catch (error) {
    ElMessage.error((error as Error).message || '发送验证码失败')
  } finally {
    emailDialog.sending = false
  }
}

async function submitEmail(): Promise<void> {
  if (!emailDialog.email.trim() || !emailDialog.password || !emailDialog.code.trim()) {
    ElMessage.warning('请填写新邮箱、当前密码与验证码')
    return
  }
  emailDialog.saving = true
  try {
    const response = await changeEmail({
      newEmail: emailDialog.email.trim(),
      password: emailDialog.password,
      code: emailDialog.code.trim()
    })
    auth.updateToken(response.token)
    emailDialog.visible = false
    ElMessage.success('邮箱已修改，其他设备已下线')
    await Promise.all([load(), auth.loadProfile()])
  } catch (error) {
    ElMessage.error((error as Error).message || '修改邮箱失败')
  } finally {
    emailDialog.saving = false
  }
}

async function openTwoFactorSetup(): Promise<void> {
  try {
    const setup = await setupTwoFactor()
    twoFactorSetup.secret = setup.secret
    twoFactorSetup.qr = await QRCode.toDataURL(setup.otpauthUri, { width: 220, margin: 1 })
    twoFactorSetup.code = ''
    twoFactorSetup.visible = true
  } catch (error) {
    ElMessage.error((error as Error).message || '初始化二次验证失败')
  }
}

async function confirmTwoFactor(): Promise<void> {
  if (!twoFactorSetup.code.trim()) {
    ElMessage.warning('请输入认证器中的 6 位动态码')
    return
  }
  twoFactorSetup.saving = true
  try {
    const result = await enableTwoFactor(twoFactorSetup.code.trim())
    twoFactorSetup.visible = false
    recoveryDialog.codes = result.recoveryCodes
    recoveryDialog.visible = true
    ElMessage.success('二次验证已开启')
    await Promise.all([load(), auth.loadProfile()])
  } catch (error) {
    ElMessage.error((error as Error).message || '开启二次验证失败')
  } finally {
    twoFactorSetup.saving = false
  }
}

function openDisableDialog(): void {
  disableDialog.password = ''
  disableDialog.code = ''
  disableDialog.visible = true
}

async function submitDisable(): Promise<void> {
  if (!disableDialog.password || !disableDialog.code.trim()) {
    ElMessage.warning('请输入当前密码与动态码')
    return
  }
  disableDialog.saving = true
  try {
    await disableTwoFactor({
      password: disableDialog.password,
      code: disableDialog.code.trim()
    })
    disableDialog.visible = false
    ElMessage.success('二次验证已关闭')
    await Promise.all([load(), auth.loadProfile()])
  } catch (error) {
    ElMessage.error((error as Error).message || '关闭二次验证失败')
  } finally {
    disableDialog.saving = false
  }
}

function openRegenerateDialog(): void {
  regenerateDialog.password = ''
  regenerateDialog.code = ''
  regenerateDialog.visible = true
}

async function submitRegenerate(): Promise<void> {
  if (!regenerateDialog.password || !regenerateDialog.code.trim()) {
    ElMessage.warning('请输入当前密码与动态码')
    return
  }
  regenerateDialog.saving = true
  try {
    const result = await regenerateRecoveryCodes({
      password: regenerateDialog.password,
      code: regenerateDialog.code.trim()
    })
    regenerateDialog.visible = false
    recoveryDialog.codes = result.recoveryCodes
    recoveryDialog.visible = true
    ElMessage.success('恢复码已重新生成，旧恢复码已失效')
  } catch (error) {
    ElMessage.error((error as Error).message || '重新生成恢复码失败')
  } finally {
    regenerateDialog.saving = false
  }
}

async function copyRecoveryCodes(): Promise<void> {
  try {
    await navigator.clipboard.writeText(recoveryDialog.codes.join('\n'))
    ElMessage.success('恢复码已复制')
  } catch {
    ElMessage.warning('复制失败，请手动保存')
  }
}

async function loadFeedback(): Promise<void> {
  feedbackLoading.value = true
  try {
    const result = await listMyFeedback({
      page: feedbackPage.value,
      size: feedbackSize.value
    })
    feedbackList.value = result.list
    feedbackTotal.value = result.total
  } catch (error) {
    ElMessage.error((error as Error).message || '加载反馈记录失败')
  } finally {
    feedbackLoading.value = false
  }
}

function onFeedbackSubmitted(): void {
  feedbackPage.value = 1
  void loadFeedback()
}

function openFeedbackDetail(item: FeedbackHistoryItem): void {
  selectedFeedback.value = item
  feedbackDetailVisible.value = true
}

function formatTime(value: string | null | undefined): string {
  return formatDateTime(value) || '—'
}

function planLabel(plan: string | undefined): string {
  return plan === 'VIP' ? 'VIP' : plan === 'PACK' ? '次数包' : '免费'
}
</script>

<template>
  <div class="plan-page" v-loading="loading">
    <el-card v-if="profile">
      <template #header><div class="trip-title">个人资料</div></template>
      <div style="display: flex; align-items: center; gap: 16px">
        <div
          class="avatar-circle"
          :style="{ background: AVATARS.find((item) => item.value === avatarPreset)?.color || '#5c7cfa' }"
        >
          {{ (profile.nickname || profile.username).slice(0, 1).toUpperCase() }}
        </div>
        <div>
          <div style="font-size: 18px; font-weight: 600">{{ profile.nickname || profile.username }}</div>
          <div class="meta">{{ profile.email }} · {{ profile.role === 'ADMIN' ? '管理员' : '普通用户' }}</div>
          <div class="meta">注册时间：{{ formatTime(profile.createdAt) }}</div>
        </div>
      </div>

      <el-form label-width="90px" style="margin-top: 16px; max-width: 520px">
        <el-form-item label="昵称">
          <el-input v-model="nickname" maxlength="20" show-word-limit />
        </el-form-item>
        <el-form-item label="头像">
          <div class="avatar-grid">
            <button
              v-for="avatar in AVATARS"
              :key="avatar.value"
              type="button"
              class="avatar-option"
              :class="{ active: avatarPreset === avatar.value }"
              :style="{ background: avatar.color }"
              @click="avatarPreset = avatar.value"
            />
          </div>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="saving" @click="saveProfile">保存资料</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card v-if="profile && quota" style="margin-top: 16px">
      <template #header><div class="trip-title">我的额度</div></template>
      <div class="quota-grid">
        <div class="quota-item">
          <span class="meta">当前方案</span>
          <strong>{{ planLabel(quota.plan) }}</strong>
        </div>
        <div class="quota-item">
          <span class="meta">今日免费次数</span>
          <strong>{{ quota.freeUsedToday }} / {{ quota.freeLimit }}</strong>
        </div>
        <div class="quota-item">
          <span class="meta">次数包余额</span>
          <strong>{{ quota.packBalance }}</strong>
        </div>
        <div class="quota-item">
          <span class="meta">VIP 到期</span>
          <strong>{{ formatTime(quota.vipExpireAt) }}</strong>
        </div>
        <div class="quota-item">
          <span class="meta">VIP 今日用量</span>
          <strong>{{ quota.vipDailyUsed }} / {{ quota.vipDailyLimit }}</strong>
        </div>
        <div class="quota-item">
          <span class="meta">额度重置</span>
          <strong>{{ formatTime(quota.resetAt) }}</strong>
        </div>
      </div>
      <el-alert
        v-if="quota.packExpiringSoon"
        type="warning"
        :closable="false"
        show-icon
        style="margin-top: 12px"
        :title="`${quota.packExpiringSoon.count} 次次数包将在 ${formatTime(quota.packExpiringSoon.expireAt)} 到期`"
      />
    </el-card>

    <el-card style="margin-top: 16px">
      <template #header><div class="trip-title">卡密兑换</div></template>
      <div style="display: flex; gap: 8px; align-items: center; flex-wrap: wrap">
        <el-input v-model="cardCode" placeholder="输入卡密，如 TM-XXXX-XXXX-XXXX" style="width: 300px" />
        <el-button type="primary" :loading="redeeming" @click="redeem">兑换</el-button>
      </div>
      <div class="meta" style="margin-top: 8px">卡密可兑换 AI 次数或 VIP，兑换后立即生效。</div>
      <el-alert
        v-if="redeemResult"
        type="success"
        :closable="false"
        show-icon
        style="margin-top: 8px"
        :title="`兑换成功：${redeemResult.cardType} +${redeemResult.value}，次数包余额 ${redeemResult.packBalanceAfter}，VIP 到期 ${formatTime(redeemResult.vipExpireAtAfter)}`"
      />
    </el-card>

    <el-card style="margin-top: 16px">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <div class="trip-title">我的反馈</div>
          <el-button type="primary" size="small" @click="feedbackDialogVisible = true">
            提交反馈
          </el-button>
        </div>
      </template>
      <el-empty
        v-if="!feedbackLoading && feedbackList.length === 0"
        description="还没有提交过反馈"
      />
      <el-table
        v-else
        v-loading="feedbackLoading"
        :data="feedbackList"
        stripe
        @row-click="openFeedbackDetail"
      >
        <el-table-column label="分类" width="110">
          <template #default="{ row }">{{ feedbackTypeLabel(row.type) }}</template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="feedbackStatusTagType(row.status)" size="small">
              {{ feedbackStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="提交时间" width="160">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="feedbackPage"
        v-model:page-size="feedbackSize"
        :total="feedbackTotal"
        :page-sizes="[5, 10, 20]"
        layout="total, sizes, prev, pager, next"
        style="margin-top: 12px; justify-content: flex-end"
        @current-change="loadFeedback"
        @size-change="loadFeedback"
      />
    </el-card>

    <el-card v-if="profile" style="margin-top: 16px">
      <template #header><div class="trip-title">账号安全</div></template>
      <el-descriptions :column="1" border>
        <el-descriptions-item label="密码">
          <el-button size="small" @click="openPasswordDialog">修改密码</el-button>
        </el-descriptions-item>
        <el-descriptions-item label="邮箱">
          <span>{{ profile.email }}</span>
          <el-button size="small" style="margin-left: 12px" @click="openEmailDialog">
            修改邮箱
          </el-button>
        </el-descriptions-item>
        <el-descriptions-item label="二次验证">
          <el-tag :type="profile.twoFactorEnabled ? 'success' : 'info'" size="small">
            {{ profile.twoFactorEnabled ? '已开启' : '未开启' }}
          </el-tag>
          <template v-if="!profile.twoFactorEnabled">
            <el-button
              size="small"
              type="primary"
              style="margin-left: 12px"
              @click="openTwoFactorSetup"
            >
              开启二次验证
            </el-button>
          </template>
          <template v-else>
            <el-button size="small" style="margin-left: 12px" @click="openRegenerateDialog">
              重新生成恢复码
            </el-button>
            <el-button size="small" type="danger" plain @click="openDisableDialog">
              关闭二次验证
            </el-button>
          </template>
        </el-descriptions-item>
        <el-descriptions-item label="登录会话">
          <span class="meta">退出所有设备后，包括当前设备在内的全部登录会话都会失效。</span>
          <el-button style="margin-left: 12px" :loading="loggingOutAll" @click="logoutAllDevices">
            退出所有设备
          </el-button>
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card style="margin-top: 16px">
      <template #header><div class="trip-title">危险操作</div></template>
      <el-alert
        v-if="isAdmin"
        type="info"
        :closable="false"
        show-icon
        title="管理员账号不能通过此页面注销。"
      />
      <template v-else>
        <span class="meta">注销后账号将匿名化，所有数据与分享链接失效。</span>
        <el-button type="danger" plain style="margin-left: 12px" @click="openDelete">注销账号</el-button>
      </template>
    </el-card>

    <el-dialog v-model="deleteDialog.visible" title="注销账号" width="460px">
      <el-alert
        type="error"
        :closable="false"
        show-icon
        title="此操作不可恢复。请输入密码并输入“注销账号”确认。"
        style="margin-bottom: 12px"
      />
      <el-form label-width="80px">
        <el-form-item label="密码" required>
          <el-input v-model="deleteDialog.password" type="password" show-password />
        </el-form-item>
        <el-form-item label="确认文案" required>
          <el-input v-model="deleteDialog.confirmText" placeholder="请输入：注销账号" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="deleteDialog.visible = false">取消</el-button>
        <el-button type="danger" :loading="deleteDialog.deleting" @click="submitDelete">
          确认注销
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="passwordDialog.visible" title="修改密码" width="460px">
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="修改成功后其他设备将下线，当前设备保持登录。"
        style="margin-bottom: 12px"
      />
      <el-form label-width="90px">
        <el-form-item label="当前密码" required>
          <el-input
            v-model="passwordDialog.current"
            type="password"
            show-password
            autocomplete="current-password"
          />
        </el-form-item>
        <el-form-item label="新密码" required>
          <el-input
            v-model="passwordDialog.next"
            type="password"
            show-password
            autocomplete="new-password"
            placeholder="8-32 位，同时包含字母与数字"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="passwordDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="passwordDialog.saving" @click="submitPassword">
          确认修改
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="emailDialog.visible" title="修改邮箱" width="480px">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="需要验证新邮箱；修改成功后旧邮箱会收到通知，其他设备将下线。"
        style="margin-bottom: 12px"
      />
      <el-form label-width="90px">
        <el-form-item label="新邮箱" required>
          <div style="display: flex; gap: 8px; width: 100%">
            <el-input v-model="emailDialog.email" type="email" placeholder="new@example.com" />
            <el-button :loading="emailDialog.sending" @click="sendNewEmailCode">
              发送验证码
            </el-button>
          </div>
        </el-form-item>
        <el-form-item label="验证码" required>
          <el-input v-model="emailDialog.code" maxlength="6" placeholder="6 位数字" />
        </el-form-item>
        <el-form-item label="当前密码" required>
          <el-input v-model="emailDialog.password" type="password" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="emailDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="emailDialog.saving" @click="submitEmail">
          确认修改
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="twoFactorSetup.visible" title="开启二次验证" width="480px">
      <div style="text-align: center">
        <img :src="twoFactorSetup.qr" alt="二次验证二维码" style="width: 220px; height: 220px" />
      </div>
      <div class="meta" style="margin: 8px 0">
        用认证器 App 扫码，或手动输入密钥：<code>{{ twoFactorSetup.secret }}</code>
      </div>
      <el-form label-width="90px">
        <el-form-item label="动态码" required>
          <el-input
            v-model="twoFactorSetup.code"
            maxlength="6"
            placeholder="认证器中的 6 位数字"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="twoFactorSetup.visible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="twoFactorSetup.saving"
          @click="confirmTwoFactor"
        >
          确认开启
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="recoveryDialog.visible" title="恢复码（仅显示一次）" width="480px">
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="请立即保存这些恢复码。关闭本窗口后无法再次查看，每个恢复码只能使用一次。"
        style="margin-bottom: 12px"
      />
      <div class="recovery-codes">
        <code v-for="code in recoveryDialog.codes" :key="code">{{ code }}</code>
      </div>
      <template #footer>
        <el-button @click="copyRecoveryCodes">复制全部</el-button>
        <el-button type="primary" @click="recoveryDialog.visible = false">我已保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="disableDialog.visible" title="关闭二次验证" width="460px">
      <el-alert
        type="error"
        :closable="false"
        show-icon
        title="关闭后账号将只剩密码一道防线。"
        style="margin-bottom: 12px"
      />
      <el-form label-width="90px">
        <el-form-item label="当前密码" required>
          <el-input v-model="disableDialog.password" type="password" show-password />
        </el-form-item>
        <el-form-item label="动态码" required>
          <el-input v-model="disableDialog.code" maxlength="6" placeholder="6 位数字" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="disableDialog.visible = false">取消</el-button>
        <el-button type="danger" :loading="disableDialog.saving" @click="submitDisable">
          确认关闭
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="regenerateDialog.visible" title="重新生成恢复码" width="460px">
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="重新生成后，旧的恢复码全部失效。"
        style="margin-bottom: 12px"
      />
      <el-form label-width="90px">
        <el-form-item label="当前密码" required>
          <el-input v-model="regenerateDialog.password" type="password" show-password />
        </el-form-item>
        <el-form-item label="动态码" required>
          <el-input v-model="regenerateDialog.code" maxlength="6" placeholder="6 位数字" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="regenerateDialog.visible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="regenerateDialog.saving"
          @click="submitRegenerate"
        >
          重新生成
        </el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="feedbackDetailVisible" title="反馈详情" size="480px">
      <el-descriptions v-if="selectedFeedback" :column="1" border>
        <el-descriptions-item label="分类">
          {{ feedbackTypeLabel(selectedFeedback.type) }}
        </el-descriptions-item>
        <el-descriptions-item label="标题">{{ selectedFeedback.title }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="feedbackStatusTagType(selectedFeedback.status)" size="small">
            {{ feedbackStatusLabel(selectedFeedback.status) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="正文">
          <div style="white-space: pre-wrap">{{ selectedFeedback.content }}</div>
        </el-descriptions-item>
        <el-descriptions-item label="联系方式">
          {{ selectedFeedback.contact || '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="提交时间">
          {{ formatTime(selectedFeedback.createdAt) }}
        </el-descriptions-item>
        <el-descriptions-item label="更新时间">
          {{ formatTime(selectedFeedback.updatedAt) }}
        </el-descriptions-item>
      </el-descriptions>
    </el-drawer>

    <FeedbackDialog v-model="feedbackDialogVisible" @submitted="onFeedbackSubmitted" />
  </div>
</template>

<style scoped>
.avatar-circle {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  color: #fff;
  font-size: 26px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

.avatar-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.avatar-option {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  border: 2px solid transparent;
  cursor: pointer;
}

.avatar-option.active {
  border-color: #303133;
  box-shadow: 0 0 0 2px #fff inset;
}

.quota-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 12px;
}

.quota-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px;
  background: #f5f7fa;
  border-radius: 8px;
}

.quota-item strong {
  font-size: 18px;
}

.recovery-codes {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.recovery-codes code {
  padding: 8px 10px;
  background: #f5f7fa;
  border-radius: 6px;
  text-align: center;
  letter-spacing: 1px;
}
</style>
