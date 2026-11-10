import { apiPost } from './http'
import type { LoginResponse } from './types'

export type CodeScene = 'VERIFY' | 'RESET'

export interface RegisterRequest {
  username: string
  email: string
  password: string
  nickname?: string
  agreeTerms: boolean
  code: string
}

export interface RegisterResponse {
  userId: number
}

export function login(account: string, password: string): Promise<LoginResponse> {
  return apiPost<LoginResponse>('/auth/login', { account, password })
}

export function twoFactorLogin(challengeToken: string, code: string): Promise<LoginResponse> {
  return apiPost<LoginResponse>('/auth/login/2fa', { challengeToken, code })
}

export function register(body: RegisterRequest): Promise<RegisterResponse> {
  return apiPost<RegisterResponse>('/auth/register', body)
}

export function sendCode(email: string, scene: CodeScene): Promise<{ emailSent: boolean }> {
  return apiPost<{ emailSent: boolean }>('/auth/send-code', { email, scene })
}

export function resetPassword(email: string, code: string, newPassword: string): Promise<void> {
  return apiPost<void>('/auth/reset-password', { email, code, newPassword })
}

/** 吊销当前设备 Token；失败时调用方仍需清除本地登录态。 */
export function logout(): Promise<void> {
  return apiPost<void>('/auth/logout')
}

/** 使该用户在所有设备上的 Token 失效。 */
export function logoutAll(): Promise<void> {
  return apiPost<void>('/auth/logout-all')
}