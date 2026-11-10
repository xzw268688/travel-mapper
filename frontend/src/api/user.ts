import { apiGet, apiPost, apiPostWithHeaders, apiPut } from './http'
import { newRequestId } from '../utils/uuid'

export interface QuotaSummary {
  plan: string
  vipExpireAt: string | null
  freeUsedToday: number
  freeLimit: number
  packBalance: number
  packExpiringSoon: { count: number; expireAt: string } | null
  vipDailyLimit: number
  vipDailyUsed: number
  resetAt: string
}

export interface ProfileResponse {
  id: number
  username: string
  email: string
  nickname: string
  avatarPreset: string
  role: string
  twoFactorEnabled: boolean
  vipExpireAt: string | null
  createdAt: string
  quota: QuotaSummary
}

export interface TokenResponse {
  token: string
  expiresIn: number
}

export interface TwoFactorSetupResponse {
  secret: string
  otpauthUri: string
}

export interface CardRedeemResponse {
  cardType: string
  value: number
  vipExpireAtAfter: string | null
  packBalanceAfter: number
  idempotentReplay: boolean
}

export function getProfile(): Promise<ProfileResponse> {
  return apiGet<ProfileResponse>('/user/profile')
}

export function updateProfile(body: { nickname?: string; avatarPreset?: string }): Promise<unknown> {
  return apiPut('/user/profile', body)
}

export function getQuota(): Promise<QuotaSummary> {
  return apiGet<QuotaSummary>('/quota/me')
}

export function redeemCard(code: string): Promise<CardRedeemResponse> {
  return apiPostWithHeaders<CardRedeemResponse>(
    '/card/redeem',
    { code },
    { 'Idempotency-Key': newRequestId() }
  )
}

export function deleteAccount(body: { password: string; confirmText: string }): Promise<unknown> {
  return apiPost('/user/logout-account', body)
}

export function changePassword(body: {
  currentPassword: string
  newPassword: string
}): Promise<TokenResponse> {
  return apiPut<TokenResponse>('/user/password', body)
}

export function sendEmailChangeCode(email: string): Promise<{ emailSent: boolean }> {
  return apiPost<{ emailSent: boolean }>('/user/email/send-code', { email })
}

export function changeEmail(body: {
  newEmail: string
  password: string
  code: string
}): Promise<TokenResponse> {
  return apiPut<TokenResponse>('/user/email', body)
}

export function setupTwoFactor(): Promise<TwoFactorSetupResponse> {
  return apiPost<TwoFactorSetupResponse>('/user/2fa/setup')
}

export function enableTwoFactor(code: string): Promise<{ recoveryCodes: string[] }> {
  return apiPost<{ recoveryCodes: string[] }>('/user/2fa/enable', { code })
}

export function disableTwoFactor(body: {
  password: string
  code: string
}): Promise<unknown> {
  return apiPost('/user/2fa/disable', body)
}

export function regenerateRecoveryCodes(body: {
  password: string
  code: string
}): Promise<{ recoveryCodes: string[] }> {
  return apiPost<{ recoveryCodes: string[] }>('/user/2fa/recovery-codes', body)
}
