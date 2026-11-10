export interface Country {
  code: string
  nameZh: string
  nameEn: string
  continent: string | null
}

export interface City {
  cityCode: string
  name: string
  level: string
  displayName: string
}

export interface UserView {
  id: number
  username: string
  nickname: string
  avatarPreset: string
  role: string
  plan: string
  vipExpireAt: string | null
}

export interface LoginResponse {
  token: string | null
  expiresIn: number
  user: UserView | null
  requires2fa: boolean
  challengeToken: string | null
}

export interface CountryStat {
  countryCode: string
  countryName: string
  status: 'GREEN' | 'YELLOW'
  itineraryCount: number
  noteCount: number
  totalCount: number
  expired: boolean
}

export interface MapStats {
  countries: CountryStat[]
}

export interface ItineraryItem {
  id: number
  title: string
  startDate: string
  endDate: string
  expired: boolean
}

export interface NoteItem {
  id: number
  title: string
  visibility: string
  createdAt: string
}

export interface ExpenseItem {
  id: number
  currency: string
  amount: string
  expenseDate: string
  category: string
}

export interface CityRef {
  cityCode: string
  cityName: string
}

export interface CityGroup {
  cityCode: string
  cityName: string
  cityLabel: 'COMPLETED' | 'PLANNED'
  itineraries: ItineraryItem[]
  notes: NoteItem[]
  expenseCny: string
}

export interface TripGroup {
  tripId: number
  tripName: string
  startDate: string | null
  endDate: string | null
  effectiveStartDate: string | null
  effectiveEndDate: string | null
  status: 'PLANNED' | 'COMPLETED'
  effectiveStatus: 'PLANNED' | 'IN_PROGRESS' | 'COMPLETED'
  statusManual: boolean
  expired: boolean
  subtotalCny: string
  cities: CityGroup[]
}

export interface UngroupedItinerary {
  id: number
  title: string
  startDate: string
  endDate: string
  expired: boolean
  cities: CityRef[]
}

export interface Ungrouped {
  itineraries: UngroupedItinerary[]
  notes: NoteItem[]
  expenses: ExpenseItem[]
  expenseCny: string
}

export interface Summary {
  itineraryCount: number
  noteCount: number
  totalExpenseCny: string
  hasUnconfiguredCurrency: boolean
  unconfiguredCurrencies: string[]
}

export interface CountryDrawer {
  countryCode: string
  countryName: string
  status: string
  summary: Summary
  trips: TripGroup[]
  ungrouped: Ungrouped
}

export interface TripView {
  id: number
  countryCode: string
  name: string
  startDate: string | null
  endDate: string | null
  effectiveStartDate: string | null
  effectiveEndDate: string | null
  itineraryCount: number
  noteCount: number
  expenseCount: number
  expenseCny: string
  status: 'PLANNED' | 'COMPLETED'
  effectiveStatus: 'PLANNED' | 'IN_PROGRESS' | 'COMPLETED'
  statusManual: boolean
  expired: boolean
  createdAt: string
}

export interface GuideLink {
  title: string
  url: string
  source: string
}

export interface Preparations {
  visa: string
  currency: string
  powerPlug: string
  bestSeason: string
  notes: string[]
}
