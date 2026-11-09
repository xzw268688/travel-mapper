import { apiDelete, apiGet, apiPut } from './http'
import type { TripView } from './types'

export interface TripItineraryItem {
  id: number
  title: string
  startDate: string
  endDate: string
  status: string
  expired: boolean
}

export interface TripNoteItem {
  id: number
  title: string
  visibility: string
  cityCode: string
  cityName: string
  takedown: boolean
  createdAt: string
}

export interface TripExpenseItem {
  id: number
  expenseDate: string
  category: string | null
  amount: string
  currency: string
  amountCny: string | null
  rateMissing: boolean
  remark: string | null
}

export interface TripSummary {
  itineraryCount: number
  noteCount: number
  expenseCount: number
  expenseCny: string
  hasUnconfiguredCurrency: boolean
  unconfiguredCurrencies: string[]
}

export type TripStatus = 'PLANNED' | 'COMPLETED'
export type EffectiveTripStatus = TripStatus | 'IN_PROGRESS'

export interface TripStatusResponse {
  id: number
  name: string
  status: TripStatus
  effectiveStatus: EffectiveTripStatus
  statusManual: boolean
  expired: boolean
  effectiveStartDate: string | null
  effectiveEndDate: string | null
}

export interface TripDetail {
  id: number
  name: string
  countryCode: string
  countryName: string
  startDate: string | null
  endDate: string | null
  effectiveStartDate: string | null
  effectiveEndDate: string | null
  status: TripStatus
  effectiveStatus: EffectiveTripStatus
  statusManual: boolean
  expired: boolean
  summary: TripSummary
  itineraries: TripItineraryItem[]
  notes: TripNoteItem[]
  recentExpenses: TripExpenseItem[]
}

export interface TripTimelineItem {
  id: number
  name: string
  countryCode: string
  countryName: string
  startDate: string | null
  endDate: string | null
  status: EffectiveTripStatus
  itineraryCount: number
  noteCount: number
  expenseCount: number
  expenseCny: string
}

export function getTimeline(): Promise<TripTimelineItem[]> {
  return apiGet<TripTimelineItem[]>('/trips/timeline')
}

export function getTrips(countryCode: string): Promise<TripView[]> {
  return apiGet<TripView[]>('/trips', { countryCode })
}

export function getTripDetail(id: number): Promise<TripDetail> {
  return apiGet<TripDetail>(`/trips/${id}`)
}

export function updateTripStatus(id: number, status: TripStatus): Promise<TripStatusResponse> {
  return apiPut(`/trips/${id}/status`, { status })
}

export function resetTripStatus(id: number): Promise<TripStatusResponse> {
  return apiDelete<TripStatusResponse>(`/trips/${id}/status`)
}