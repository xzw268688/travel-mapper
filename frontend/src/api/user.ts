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
  vipExpireAt: string | null
  createdAt: string
  quota: QuotaSummary
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
