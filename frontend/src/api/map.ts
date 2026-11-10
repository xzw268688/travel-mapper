import { apiGet } from './http'
import type { CountryDrawer, MapStats } from './types'

export function getMapStats(): Promise<MapStats> {
  return apiGet<MapStats>('/map/stats')
}

export function getCountryDrawer(countryCode: string): Promise<CountryDrawer> {
  return apiGet<CountryDrawer>(`/map/country/${encodeURIComponent(countryCode)}`)
}
