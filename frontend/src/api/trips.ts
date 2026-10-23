import { apiGet } from './http'
import type { TripView } from './types'

export function getTrips(countryCode: string): Promise<TripView[]> {
  return apiGet<TripView[]>('/trips', { countryCode })
}
