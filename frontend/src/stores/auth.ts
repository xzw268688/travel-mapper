import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { login as loginApi } from '../api/auth'
import { apiGet } from '../api/http'
import type { LoginResponse, UserView } from '../api/types'
import { clearStoredToken, getStoredToken, storeToken } from '../utils/authStorage'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string>(getStoredToken())
  const user = ref<UserView | null>(null)
  const isAuthenticated = computed(() => Boolean(token.value))

  async function login(
    account: string,
    password: string,
    remember = true
  ): Promise<LoginResponse> {
    const response = await loginApi(account, password)
    token.value = response.token
    user.value = response.user
    storeToken(response.token, remember)
    return response
  }

  /** 管理员入口使用：角色不符时立即清除登录态。 */
  function rejectNonAdmin(): void {
    logout()
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
      logout()
    }
  }

  function logout(): void {
    token.value = ''
    user.value = null
    clearStoredToken()
  }

  return { token, user, isAuthenticated, login, rejectNonAdmin, loadProfile, logout }
})