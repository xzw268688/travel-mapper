import { apiGet, apiPost } from './http'

export type FeedbackType = 'FEATURE' | 'BUG' | 'UX' | 'OTHER'
export type FeedbackStatus = 'NEW' | 'PROCESSING' | 'RESOLVED'

export interface FeedbackPageResult<T> {
  total: number
  page: number
  size: number
  list: T[]
}

export interface FeedbackCreated {
  id: number
  type: FeedbackType
  title: string
  content: string
  contact: string | null
  status: FeedbackStatus
  createdAt: string
}

export interface FeedbackHistoryItem {
  id: number
  type: FeedbackType
  title: string
  content: string
  contact: string | null
  status: FeedbackStatus
  createdAt: string
  updatedAt: string
}

export interface SubmitFeedbackBody {
  type: FeedbackType
  title: string
  content: string
  contact?: string | null
  pagePath?: string | null
  appVersion?: string | null
}

export function submitFeedback(body: SubmitFeedbackBody): Promise<FeedbackCreated> {
  return apiPost<FeedbackCreated>('/feedback', body)
}

export function listMyFeedback(params: {
  page?: number
  size?: number
}): Promise<FeedbackPageResult<FeedbackHistoryItem>> {
  return apiGet<FeedbackPageResult<FeedbackHistoryItem>>('/feedback/mine', params)
}

export function feedbackTypeLabel(value: FeedbackType): string {
  switch (value) {
    case 'FEATURE':
      return '功能建议'
    case 'BUG':
      return '问题反馈'
    case 'UX':
      return '体验问题'
    default:
      return '其他'
  }
}

export function feedbackStatusLabel(value: FeedbackStatus): string {
  switch (value) {
    case 'PROCESSING':
      return '处理中'
    case 'RESOLVED':
      return '已处理'
    default:
      return '待处理'
  }
}

export function feedbackStatusTagType(value: FeedbackStatus): 'info' | 'warning' | 'success' {
  if (value === 'RESOLVED') {
    return 'success'
  }
  if (value === 'PROCESSING') {
    return 'warning'
  }
  return 'info'
}