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
