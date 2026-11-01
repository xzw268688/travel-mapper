import { apiGet } from './http'
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

export interface TripDetail {
  id: number
  name: string
  countryCode: string
  countryName: string
  startDate: string | null
  endDate: string | null
  summary: TripSummary
  itineraries: TripItineraryItem[]
  notes: TripNoteItem[]
  recentExpenses: TripExpenseItem[]
}

export function getTrips(countryCode: string): Promise<TripView[]> {
  return apiGet<TripView[]>('/trips', { countryCode })
}

export function getTripDetail(id: number): Promise<TripDetail> {
  return apiGet<TripDetail>(`/trips/${id}`)
}