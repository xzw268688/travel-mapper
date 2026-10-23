import { apiDelete, apiGet, apiPost, apiPut } from './http'

export interface NoteImage {
  objectKey: string
  url: string | null
}

export interface NoteCreate {
  title: string
  content: string
  images: string[]
  coverImage: string | null
  countryCode: string
  cityCode: string
  itineraryId?: number | null
  tripId?: number | null
  visibility: 'PRIVATE' | 'SHARED'
}

export interface NoteResponse {
  id: number
  title: string
  content: string
  images: NoteImage[]
  coverImage: string | null
  countryCode: string
  countryName: string
  cityCode: string
  cityName: string
  cityDisabled: boolean
  itineraryId: number | null
  tripId: number | null
  tripName: string | null
  visibility: string
  share: { active: boolean; token: string | null; url: string | null; expireAt: string | null }
  takedown: boolean
  createdAt: string
  updatedAt: string
}

export interface ShareResponse {
  token: string
  url: string
  fullUrl: string
  expireAt: string | null
  created: boolean
}

export interface SharePage {
  title: string
  summary: string
  coverImageUrl: string | null
  images: string[]
  countryCode: string
  countryName: string
  cityCode: string
  cityName: string
  createdAt: string
  nicknameMasked: string
}

export interface PresignResponse {
  objectKey: string
  uploadUrl: string
  method: string
  headers: Record<string, string>
  expireAt: string
}

export function presignUpload(contentType: string, size: number): Promise<PresignResponse> {
  return apiPost<PresignResponse>('/upload/presign', {
    bizType: 'NOTE_IMAGE',
    contentType,
    size
  })
}

export async function uploadImage(file: File): Promise<string> {
  const presigned = await presignUpload(file.type, file.size)
  const response = await fetch(presigned.uploadUrl, {
    method: presigned.method || 'PUT',
    headers: presigned.headers,
    body: file
  })
  if (!response.ok) {
    throw new Error('图片上传失败')
  }
  return presigned.objectKey
}

export function createNote(body: NoteCreate): Promise<{ id: number; tripId: number | null; tripInherited: boolean; countryStatusAfter: string }> {
  return apiPost('/notes', body)
}

export function getNote(id: number): Promise<NoteResponse> {
  return apiGet<NoteResponse>(`/notes/${id}`)
}

export function updateNote(id: number, body: Record<string, unknown>): Promise<NoteResponse> {
  return apiPut<NoteResponse>(`/notes/${id}`, body)
}

export function deleteNote(id: number): Promise<any> {
  return apiDelete(`/notes/${id}`)
}

export function shareNote(id: number, expireDays: number): Promise<ShareResponse> {
  return apiPost<ShareResponse>(`/notes/${id}/share`, { expireDays })
}

export function revokeShare(id: number): Promise<{ revoked: number }> {
  return apiDelete<{ revoked: number }>(`/notes/${id}/share`)
}

export async function fetchSharePage(token: string): Promise<SharePage> {
  const response = await fetch(`/s/${encodeURIComponent(token)}`)
  const body = await response.json().catch(() => ({}))
  if (!response.ok || body.code !== 0) {
    throw new Error(body.message || '分享已失效')
  }
  return body.data as SharePage
}
