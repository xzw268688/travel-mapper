import { apiDelete, apiDownload, apiGet, apiPost, apiPut } from './http'

export interface PageResult<T> {
  total: number
  page: number
  size: number
  list: T[]
}

export interface ExchangeRateView {
  id: number
  currency: string
  rateToCny: string
  updatedAt: string
}

export interface RateUpdateResponse {
  currency: string
  rateToCny: string
  previousRate: string | null
  created: boolean
  affectedUnconfiguredRecords: number
}

export interface LlmConfig {
  provider: string
  baseUrl: string
  model: string
  apiKey: string
  timeoutSeconds: number
  maxRetry: number
}

export interface SearchConfig {
  provider: string
  apiKey: string
  dailyLimit: number
  timeoutSeconds: number
  cacheHours: number
  domainWhitelist: string
}

export function getLlmConfig(): Promise<LlmConfig> {
  return apiGet<LlmConfig>('/admin/config/llm')
}

export function updateLlmConfig(body: Partial<LlmConfig>): Promise<LlmConfig> {
  return apiPut<LlmConfig>('/admin/config/llm', body)
}

export function getSearchConfig(): Promise<SearchConfig> {
  return apiGet<SearchConfig>('/admin/config/search')
}

export function updateSearchConfig(body: Partial<SearchConfig>): Promise<SearchConfig> {
  return apiPut<SearchConfig>('/admin/config/search', body)
}

export interface StorageConfig {
  provider: string
  endpoint: string
  region: string
  bucket: string
  accessKey: string
  secretKey: string
  pathStyle: boolean
  publicDomain: string
}

export interface MailConfig {
  enabled: boolean
  host: string
  port: number
  username: string
  password: string
  from: string
  smtpAuth: boolean
  starttls: boolean
}

export function getStorageConfig(): Promise<StorageConfig> {
  return apiGet<StorageConfig>('/admin/config/oss')
}

export function updateStorageConfig(body: Partial<StorageConfig>): Promise<StorageConfig> {
  return apiPut<StorageConfig>('/admin/config/oss', body)
}

export function getMailConfig(): Promise<MailConfig> {
  return apiGet<MailConfig>('/admin/config/mail')
}

export function updateMailConfig(body: Partial<MailConfig>): Promise<MailConfig> {
  return apiPut<MailConfig>('/admin/config/mail', body)
}

export interface AdminUserView {
  id: number
  username: string
  nickname: string
  email: string
  role: string
  status: string
  vipExpireAt: string | null
  freeUsedToday: number
  packBalance: number
  createdAt: string
}

export interface CardKeyView {
  id: number
  batchId: string
  code: string
  type: string
  value: number
  status: string
  expireAt: string | null
  usedBy: number | null
  usedAt: string | null
  createdAt: string
}

export interface AdminCityView {
  id: number
  countryCode: string
  cityCode: string
  name: string
  level: string
  status: string
  referenced: boolean
  createdAt: string
}

export interface AdminLinkView {
  id: number
  countryCode: string
  title: string
  url: string
  sortOrder: number | null
}

export function listAdminUsers(params: Record<string, unknown>): Promise<PageResult<AdminUserView>> {
  return apiGet<PageResult<AdminUserView>>('/admin/users', params)
}

export function updateUserStatus(
  id: number,
  status: string,
  reason?: string
): Promise<{ userId: number; status: string; tokenVersion: number }> {
  return apiPut(`/admin/users/${id}/status`, { status, reason })
}

export function adjustUserQuota(
  id: number,
  body: { type: string; delta: number; reason: string }
): Promise<{ userId: number; type: string; packBalanceAfter: number; vipExpireAtAfter: string | null }> {
  return apiPut(`/admin/users/${id}/quota`, body)
}

export function generateCards(body: {
  type: string
  value: number
  quantity: number
  expireAt?: string | null
  prefix?: string
}): Promise<{ batchId: string; quantity: number; codes: string[] }> {
  return apiPost('/admin/cards/generate', body)
}

export function listCards(params: Record<string, unknown>): Promise<PageResult<CardKeyView>> {
  return apiGet<PageResult<CardKeyView>>('/admin/cards', params)
}

export function voidCard(id: number): Promise<{ id: number; status: string }> {
  return apiPut(`/admin/cards/${id}/void`, {})
}

export function exportCards(batchId: string): Promise<{ blob: Blob; filename: string | null }> {
  return apiDownload('/admin/cards/export', { batchId })
}

export function listAdminCities(params: Record<string, unknown>): Promise<PageResult<AdminCityView>> {
  return apiGet<PageResult<AdminCityView>>('/admin/cities', params)
}

export function createAdminCity(body: {
  countryCode: string
  cityCode: string
  name: string
  level: string
}): Promise<AdminCityView> {
  return apiPost('/admin/cities', body)
}

export function updateAdminCity(
  id: number,
  body: { name?: string; status?: string }
): Promise<AdminCityView> {
  return apiPut(`/admin/cities/${id}`, body)
}

export function deleteAdminCity(id: number): Promise<{ id: number; deleted: boolean }> {
  return apiDelete(`/admin/cities/${id}`)
}

export function listOfficialLinks(countryCode?: string): Promise<AdminLinkView[]> {
  return apiGet<AdminLinkView[]>('/admin/official-links', { countryCode })
}

export function createOfficialLink(body: {
  countryCode: string
  title: string
  url: string
  sortOrder?: number | null
}): Promise<AdminLinkView> {
  return apiPost('/admin/official-links', body)
}

export function updateOfficialLink(
  id: number,
  body: { countryCode: string; title: string; url: string; sortOrder?: number | null }
): Promise<AdminLinkView> {
  return apiPut(`/admin/official-links/${id}`, body)
}

export function deleteOfficialLink(id: number): Promise<void> {
  return apiDelete(`/admin/official-links/${id}`)
}

export interface TakedownResponse {
  noteId: number
  takedown: boolean
  sharesRevoked: number
}

export interface RestoreResponse {
  noteId: number
  takedown: boolean
}

export function listExchangeRates(): Promise<ExchangeRateView[]> {
  return apiGet<ExchangeRateView[]>('/admin/exchange-rates')
}

export function upsertExchangeRate(currency: string, rateToCny: string): Promise<RateUpdateResponse> {
  return apiPut<RateUpdateResponse>('/admin/exchange-rates', { currency, rateToCny })
}

export function takedownNote(noteId: number, reason: string): Promise<TakedownResponse> {
  return apiPut<TakedownResponse>(`/admin/notes/${noteId}/takedown`, { reason })
}

export function restoreNote(noteId: number): Promise<RestoreResponse> {
  return apiPut<RestoreResponse>(`/admin/notes/${noteId}/restore`)
}
