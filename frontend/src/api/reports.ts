import { getStoredToken } from '../utils/authStorage'

export type ReportReason = 'SPAM' | 'ABUSE' | 'PORN' | 'ILLEGAL' | 'PRIVACY' | 'OTHER'
export type ReportStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED'

export interface ReportReceipt {
  reportId: number | null
  duplicate: boolean
  createdAt: string | null
}

/** 分享页是公开路径，不能走带 /api 前缀的 axios 实例。 */
export async function submitReport(
  token: string,
  body: { reason: ReportReason; description?: string | null }
): Promise<ReportReceipt> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' }
  const authToken = getStoredToken()
  if (authToken) {
    headers.Authorization = `Bearer ${authToken}`
  }
  const response = await fetch(`/s/${encodeURIComponent(token)}/reports`, {
    method: 'POST',
    headers,
    body: JSON.stringify(body)
  })
  const payload = await response.json().catch(() => ({}))
  if (!response.ok || payload.code !== 0) {
    throw new Error(payload.message || '举报提交失败')
  }
  return payload.data as ReportReceipt
}

export function reportReasonLabel(value: ReportReason): string {
  switch (value) {
    case 'SPAM':
      return '垃圾广告'
    case 'ABUSE':
      return '辱骂攻击'
    case 'PORN':
      return '色情低俗'
    case 'ILLEGAL':
      return '违法信息'
    case 'PRIVACY':
      return '侵犯隐私'
    default:
      return '其他'
  }
}

export function reportStatusLabel(value: ReportStatus): string {
  switch (value) {
    case 'ACCEPTED':
      return '已采纳'
    case 'REJECTED':
      return '已驳回'
    default:
      return '待处理'
  }
}

export function reportStatusTagType(value: ReportStatus): 'info' | 'warning' | 'success' | 'danger' {
  if (value === 'ACCEPTED') {
    return 'success'
  }
  if (value === 'REJECTED') {
    return 'danger'
  }
  return 'warning'
}