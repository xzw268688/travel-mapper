<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  deleteAccount,
  getProfile,
  redeemCard,
  updateProfile,
  type CardRedeemResponse,
  type ProfileResponse
} from '../api/user'
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
const deleteDialog = reactive({ visible: false, password: '', confirmText: '', deleting: false })

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

onMounted(load)

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
    auth.logout()
    ElMessage.success('账号已注销')
    await router.push('/login')
  } catch (error) {
    ElMessage.error((error as Error).message || '注销失败')
  } finally {
    deleteDialog.deleting = false
  }
}

function formatTime(value: string | null | undefined): string {
  return value ? value.replace('T', ' ').slice(0, 16) : '—'
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
</style>
