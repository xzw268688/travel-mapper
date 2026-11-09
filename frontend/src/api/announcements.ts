import { apiGet, apiPost } from './http'

export type AnnouncementType = 'NOTICE' | 'ANNOUNCEMENT'

export interface AnnouncementView {
  id: number
  title: string
  content: string
  type: AnnouncementType
  pinned: boolean
  publishAt: string | null
  expireAt: string | null
  read: boolean
}

export interface ActiveAnnouncements {
  total: number
  unread: number
  list: AnnouncementView[]
}

export function getActiveAnnouncements(): Promise<ActiveAnnouncements> {
  return apiGet<ActiveAnnouncements>('/announcements/active')
}

export function markAnnouncementRead(id: number): Promise<void> {
  return apiPost<void>(`/announcements/${id}/read`)
}