import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import {
  login as loginApi,
  logout as logoutApi,
  twoFactorLogin
} from '../api/auth'
import { apiGet } from '../api/http'
import type { LoginResponse, UserView } from '../api/types'
import { clearStoredToken, getStoredToken, storeToken } from '../utils/authStorage'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string>(getStoredToken())
  const user = ref<UserView | null>(null)
  const isAuthenticated = computed(() => Boolean(token.value))
  let pendingRemember = true

  async function login(
    account: string,
    password: string,
    remember = true
  ): Promise<LoginResponse> {
    const response = await loginApi(account, password)
    pendingRemember = remember
    if (!response.requires2fa && response.token && response.user) {
      applySession(response.token, response.user, remember)
    }
    return response
  }

  /** 两步登录第二步：用挑战凭证换正式 JWT。 */
  async function completeTwoFactor(
    challengeToken: string,
    code: string
  ): Promise<LoginResponse> {
    const response = await twoFactorLogin(challengeToken, code)
    if (response.token && response.user) {
      applySession(response.token, response.user, pendingRemember)
    }
    return response
  }

  function applySession(newToken: string, newUser: UserView, remember: boolean): void {
    token.value = newToken
    user.value = newUser
    storeToken(newToken, remember)
  }

  /** 改密/改邮箱后服务端换发新 Token，保持原来的持久化方式。 */
  function updateToken(newToken: string): void {
    const remember = localStorage.getItem('tm_token') !== null
    token.value = newToken
    storeToken(newToken, remember)
  }

  /** 管理员入口使用：角色不符时立即清除登录态。 */
  function rejectNonAdmin(): void {
    void logout()
  }

  async function loadProfile(): Promise<void> {
    if (!token.value) return
    try {
      const profile = await apiGet<any>('/user/profile')
      user.value = {
        id: profile.id,
        username: profile.username,
        nickname: profile.nickname,
        avatarPreset: profile.avatarPreset,
        role: profile.role,
        plan: profile.quota?.plan ?? 'FREE',
        vipExpireAt: profile.vipExpireAt
      }
    } catch {
      clearLocalSession()
    }
  }

  function clearLocalSession(): void {
    token.value = ''
    user.value = null
    clearStoredToken()
  }

  /** 先请求服务端吊销当前 Token；网络失败也必须清除本地会话。 */
  async function logout(): Promise<void> {
    try {
      if (token.value) {
        await logoutApi()
      }
    } catch {
      // 退出接口失败不阻止本地退出
    } finally {
      clearLocalSession()
    }
  }

  return {
    token,
    user,
    isAuthenticated,
    login,
    completeTwoFactor,
    updateToken,
    rejectNonAdmin,
    loadProfile,
    clearLocalSession,
    logout
  }
})