import { apiGet } from './http'
import type { City, Country } from './types'

export function getCountries(): Promise<Country[]> {
  return apiGet<Country[]>('/countries')
}

export function getCities(countryCode: string): Promise<City[]> {
  return apiGet<City[]>('/cities', { countryCode })
}
