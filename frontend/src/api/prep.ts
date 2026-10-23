import { apiGet, apiPut } from './http'

export interface ChecklistItem {
  itemKey: string
  label: string
  checked: boolean
}

export interface ChecklistResponse {
  itineraryId: number
  countryCode: string
  items: ChecklistItem[]
}

export interface OfficialLink {
  title: string
  url: string
}

export interface OfficialLinkResponse {
  countryCode: string
  fallback: boolean
  links: OfficialLink[]
  searchUrl: string | null
}

export function getChecklist(itineraryId: number): Promise<ChecklistResponse> {
  return apiGet<ChecklistResponse>('/prep/checklist', { itineraryId })
}

export function updateChecklist(itineraryId: number, itemKey: string, checked: boolean): Promise<any> {
  return apiPut<any>('/prep/checklist', { itineraryId, itemKey, checked })
}

export function getOfficialLinks(countryCode: string): Promise<OfficialLinkResponse> {
  return apiGet<OfficialLinkResponse>('/prep/official-links', { countryCode })
}
