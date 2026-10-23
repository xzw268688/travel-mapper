import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { login as loginApi } from '../api/auth'
import { apiGet } from '../api/http'
import type { LoginResponse, UserView } from '../api/types'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string>(localStorage.getItem('tm_token') ?? '')
  const user = ref<UserView | null>(null)
  const isAuthenticated = computed(() => Boolean(token.value))

  async function login(account: string, password: string): Promise<LoginResponse> {
    const response = await loginApi(account, password)
    token.value = response.token
    user.value = response.user
    localStorage.setItem('tm_token', response.token)
    return response
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
    localStorage.removeItem('tm_token')
  }

  return { token, user, isAuthenticated, login, loadProfile, logout }
})
