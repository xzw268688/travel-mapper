import { apiGet, apiPut } from './http'

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
