import { apiPost } from './http'
import type { LoginResponse } from './types'

export interface RegisterRequest {
  username: string
  email: string
  password: string
  nickname?: string
  agreeTerms: boolean
}

export interface RegisterResponse {
  userId: number
  emailSent: boolean
  verifyExpireAt: string
}

export function login(account: string, password: string): Promise<LoginResponse> {
  return apiPost<LoginResponse>('/auth/login', { account, password })
}

export function register(body: RegisterRequest): Promise<RegisterResponse> {
  return apiPost<RegisterResponse>('/auth/register', body)
}

export function verifyEmail(token: string): Promise<void> {
  return apiPost<void>('/auth/verify-email', { token })
}

export function resendVerify(email: string): Promise<{ emailSent: boolean }> {
  return apiPost<{ emailSent: boolean }>('/auth/resend-verify', { email })
}

export function forgotPassword(email: string): Promise<{ emailSent: boolean }> {
  return apiPost<{ emailSent: boolean }>('/auth/forgot-password', { email })
}

export function resetPassword(token: string, newPassword: string): Promise<void> {
  return apiPost<void>('/auth/reset-password', { token, newPassword })
}
