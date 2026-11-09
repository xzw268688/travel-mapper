import { apiDelete, apiGet, apiPost, apiPut } from './http'
import type { GuideLink, Preparations } from './types'

export interface GenerateRequest {
  requestId: string
  countryCode: string
  cityCodes: string[]
  days: number
  startDate: string
  preference?: string
}

export interface SpotExtendedFields {
  category?: string
  ticketPrice?: string
  highlights?: string
  bestTime?: string
  nearestStation?: string
  reservationRequired?: boolean
  area?: string
  warningNotes?: string
}

export interface SpotPlan extends SpotExtendedFields {
  name: string
  description: string
  suggestedDuration: string
  source: 'AI' | 'MANUAL'
}

export interface DayPlan {
  dayIndex: number
  date: string
  cityCode: string
  cityName: string
  spots: SpotPlan[]
}

export interface QuotaInfo {
  source: string
  freeUsedToday: number
  freeLimit: number
  packBalance: number
}

export interface GenerateResponse {
  requestId: string
  countryCode: string
  startDate: string
  endDate: string
  days: DayPlan[]
  guides: GuideLink[]
  preparations: Preparations
  warnings: string[]
  deduplicated: boolean
  quota: QuotaInfo
}

export interface SaveRequest {
  countryCode: string
  title: string
  tripId?: number | null
  tripName?: string
  startDate: string
  endDate: string
  guides: GuideLink[]
  preparations?: Preparations
  days: Array<{
    dayIndex: number
    date: string
    cityCode: string
    spots: SpotPlan[]
  }>
}

export interface SaveResponse {
  itineraryId: number
  tripId: number
  tripName: string
  tripCreated: boolean
  countryStatusAfter: string
  manualSpotCount: number
}

export interface SpotView extends Required<SpotExtendedFields> {
  spotId: number
  sortOrder: number
  source: string
  name: string
  description: string
  suggestedDuration: string
}

export interface DayView {
  dayId: number
  dayIndex: number
  date: string
  cityCode: string
  cityName: string
  cityDisabled: boolean
  spots: SpotView[]
}

export interface ItineraryDetail {
  id: number
  tripId: number | null
  tripName: string | null
  countryCode: string
  countryName: string
  title: string
  startDate: string
  endDate: string
  status: string
  expired: boolean
  guides: GuideLink[]
  preparations: Preparations
  days: DayView[]
  manualSpotCount: number
  createdAt: string
  updatedAt: string
}

export interface DeleteResponse {
  itineraryId: number
  countryStatusAfter: string
  tripDeleted: boolean
}

export interface RegenerateResponse {
  itinerary: ItineraryDetail
  mergedManualSpots: number
  mergeWarnings: string[]
  warnings: string[]
  quota: QuotaInfo
}

// 生成走大模型，推理模型可能需要 60-90 秒，单独放宽到 120 秒。
// 推理模型单次约 50–80 秒；后端解析失败会重试一次，留足两次调用的余量。
export const AI_REQUEST_TIMEOUT_MS = 180_000

export function generateItinerary(body: GenerateRequest): Promise<GenerateResponse> {
  return apiPost<GenerateResponse>('/itinerary/generate', body, AI_REQUEST_TIMEOUT_MS)
}

export function saveItinerary(body: SaveRequest): Promise<SaveResponse> {
  return apiPost<SaveResponse>('/itinerary/save', body)
}

export function getItinerary(id: number): Promise<ItineraryDetail> {
  return apiGet<ItineraryDetail>(`/itinerary/${id}`)
}


export function updateItinerary(
  id: number,
  body: { title?: string; startDate?: string; endDate?: string }
): Promise<ItineraryDetail> {
  return apiPut<ItineraryDetail>(`/itinerary/${id}`, body)
}

export function deleteItinerary(id: number): Promise<DeleteResponse> {
  return apiDelete<DeleteResponse>(`/itinerary/${id}`)
}

export function regenerateItinerary(
  id: number,
  body: { requestId: string; keepManualSpots: boolean; preference?: string }
): Promise<RegenerateResponse> {
  return apiPost<RegenerateResponse>(`/itinerary/${id}/regenerate`, body, AI_REQUEST_TIMEOUT_MS)
}

export function addSpot(
  itineraryId: number,
  dayId: number,
  body: SpotExtendedFields & {
    name: string
    description?: string
    suggestedDuration?: string
    insertIndex?: number
  }
): Promise<{ spotId: number; dayId: number; sortOrder: number; source: string; manualSpotCount: number }> {
  return apiPost(`/itinerary/${itineraryId}/days/${dayId}/spots`, body)
}

export function updateSpot(
  spotId: number,
  body: SpotExtendedFields & { name: string; description?: string; suggestedDuration?: string }
): Promise<{ spotId: number; source: string; name: string }> {
  return apiPut(`/itinerary/spots/${spotId}`, body)
}

export function deleteSpot(spotId: number): Promise<{ spotId: number; manualSpotCount: number }> {
  return apiDelete(`/itinerary/spots/${spotId}`)
}

export function moveSpot(
  spotId: number,
  body: { targetDayId: number; targetIndex?: number }
): Promise<{ spotId: number; fromDayId: number; toDayId: number }> {
  return apiPut(`/itinerary/spots/${spotId}/move`, body)
}

export function addDay(
  itineraryId: number,
  body: { insertIndex: number; date: string; cityCode: string }
): Promise<ItineraryDetail> {
  return apiPost(`/itinerary/${itineraryId}/days`, body)
}

export function updateDay(
  itineraryId: number,
  dayId: number,
  body: { date?: string; cityCode?: string }
): Promise<ItineraryDetail> {
  return apiPut(`/itinerary/${itineraryId}/days/${dayId}`, body)
}

export function deleteDay(
  itineraryId: number,
  dayId: number
): Promise<{
  itineraryId: number
  deletedSpots: number
  dayCount: number
  startDate: string
  endDate: string
}> {
  return apiDelete(`/itinerary/${itineraryId}/days/${dayId}`)
}
