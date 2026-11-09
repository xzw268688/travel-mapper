import { apiDelete, apiDownload, apiGet, apiPost, apiPut } from './http'
import type { FeedbackStatus, FeedbackType } from './feedback'
import type { ReportReason, ReportStatus } from './reports'

export interface PageResult<T> {
  total: number
  page: number
  size: number
  list: T[]
}

export interface ExchangeRateView {
  id: number
  currency: string
  rateToCny: string
  updatedAt: string
}

export interface RateUpdateResponse {
  currency: string
  rateToCny: string
  previousRate: string | null
  created: boolean
  affectedUnconfiguredRecords: number
}

export interface LlmConfig {
  provider: string
  baseUrl: string
  model: string
  apiKey: string
  timeoutSeconds: number
  maxRetry: number
}

export interface SearchConfig {
  provider: string
  apiKey: string
  dailyLimit: number
  timeoutSeconds: number
  cacheHours: number
  domainWhitelist: string
}

export interface QuotaConfig {
  freeLimit: number
}

export function getQuotaConfig(): Promise<QuotaConfig> {
  return apiGet<QuotaConfig>('/admin/config/quota')
}

export function updateQuotaConfig(body: QuotaConfig): Promise<QuotaConfig> {
  return apiPut<QuotaConfig>('/admin/config/quota', body)
}

export function getLlmConfig(): Promise<LlmConfig> {
  return apiGet<LlmConfig>('/admin/config/llm')
}

export function updateLlmConfig(body: Partial<LlmConfig>): Promise<LlmConfig> {
  return apiPut<LlmConfig>('/admin/config/llm', body)
}

export function getSearchConfig(): Promise<SearchConfig> {
  return apiGet<SearchConfig>('/admin/config/search')
}

export function updateSearchConfig(body: Partial<SearchConfig>): Promise<SearchConfig> {
  return apiPut<SearchConfig>('/admin/config/search', body)
}

export interface StorageConfig {
  provider: string
  endpoint: string
  region: string
  bucket: string
  accessKey: string
  secretKey: string
  pathStyle: boolean
  publicDomain: string
}

export interface MailConfig {
  enabled: boolean
  host: string
  port: number
  username: string
  password: string
  from: string
  smtpAuth: boolean
  starttls: boolean
}

export function getStorageConfig(): Promise<StorageConfig> {
  return apiGet<StorageConfig>('/admin/config/oss')
}

export function updateStorageConfig(body: Partial<StorageConfig>): Promise<StorageConfig> {
  return apiPut<StorageConfig>('/admin/config/oss', body)
}

export interface StorageTestResult {
  ok: boolean
  latencyMs: number
  message: string
}

export function testStorageConfig(body: Partial<StorageConfig>): Promise<StorageTestResult> {
  return apiPost<StorageTestResult>('/admin/config/oss/test', body)
}

export function getMailConfig(): Promise<MailConfig> {
  return apiGet<MailConfig>('/admin/config/mail')
}

export function updateMailConfig(body: Partial<MailConfig>): Promise<MailConfig> {
  return apiPut<MailConfig>('/admin/config/mail', body)
}

export interface AdminUserView {
  id: number
  username: string
  nickname: string
  email: string
  role: string
  status: string
  vipExpireAt: string | null
  freeUsedToday: number
  packBalance: number
  createdAt: string
}

export interface CardKeyView {
  id: number
  batchId: string
  code: string
  type: string
  value: number
  priceCny: string | null
  status: string
  expireAt: string | null
  usedBy: number | null
  usedAt: string | null
  createdAt: string
}

export interface CardRedemptionView {
  cardId: number
  code: string
  batchId: string
  type: string
  value: number
  priceCny: string | null
  userId: number | null
  username: string | null
  usedAt: string | null
}

export interface CardBatchStatsView {
  batchId: string
  type: string
  value: number
  priceCny: string | null
  createdAt: string | null
  total: number
  used: number
  unused: number
  voided: number
  expired: number
  estimatedRevenueCny: string | null
}

export interface AdminUserDetailView {
  id: number
  username: string
  nickname: string
  email: string
  role: string
  status: string
  vipExpireAt: string | null
  freeUsedToday: number
  packBalance: number
  noteCount: number
  itineraryCount: number
  tripCount: number
  createdAt: string
  recentRedemptions: CardRedemptionView[]
}

export interface AdminNoteView {
  id: number
  title: string
  authorId: number | null
  authorName: string | null
  countryCode: string
  countryName: string
  cityCode: string
  cityName: string
  visibility: string
  takenDown: boolean
  takedownReason: string | null
  createdAt: string
  updatedAt: string
}

export interface AdminAuditView {
  id: number
  userId: number | null
  actorRole: string | null
  username: string | null
  action: string
  targetType: string | null
  targetId: number | null
  detail: string | null
  ip: string | null
  createdAt: string
}

export interface AdminLoginLogView {
  id: number
  userId: number | null
  username: string
  role: string | null
  success: boolean
  reason: string | null
  ip: string | null
  userAgent: string | null
  createdAt: string
}

export interface AdminCityView {
  id: number
  countryCode: string
  cityCode: string
  name: string
  level: string
  status: string
  referenced: boolean
  createdAt: string
}

export interface AdminLinkView {
  id: number
  countryCode: string
  title: string
  url: string
  sortOrder: number | null
}

export function listAdminUsers(params: Record<string, unknown>): Promise<PageResult<AdminUserView>> {
  return apiGet<PageResult<AdminUserView>>('/admin/users', params)
}

export function updateUserStatus(
  id: number,
  status: string,
  reason?: string
): Promise<{ userId: number; status: string; tokenVersion: number }> {
  return apiPut(`/admin/users/${id}/status`, { status, reason })
}

export function adjustUserQuota(
  id: number,
  body: { type: string; delta: number; reason: string }
): Promise<{ userId: number; type: string; packBalanceAfter: number; vipExpireAtAfter: string | null }> {
  return apiPut(`/admin/users/${id}/quota`, body)
}

export function getUserDetail(id: number): Promise<AdminUserDetailView> {
  return apiGet<AdminUserDetailView>(`/admin/users/${id}`)
}

export function setUserVip(
  id: number,
  expireAt: string | null
): Promise<{ userId: number; vipExpireAt: string | null }> {
  return apiPut(`/admin/users/${id}/vip`, { expireAt })
}

export function generateCards(body: {
  type: string
  value: number
  priceCny?: number | null
  quantity: number
  expireAt?: string | null
  prefix?: string
}): Promise<{ batchId: string; quantity: number; codes: string[] }> {
  return apiPost('/admin/cards/generate', body)
}

export function getCardBatchStats(): Promise<CardBatchStatsView[]> {
  return apiGet<CardBatchStatsView[]>('/admin/cards/stats')
}

export function listCardRedemptions(
  params: Record<string, unknown>
): Promise<PageResult<CardRedemptionView>> {
  return apiGet<PageResult<CardRedemptionView>>('/admin/cards/redemptions', params)
}

export function listAdminNotes(params: Record<string, unknown>): Promise<PageResult<AdminNoteView>> {
  return apiGet<PageResult<AdminNoteView>>('/admin/notes', params)
}

export function listAuditLogs(params: Record<string, unknown>): Promise<PageResult<AdminAuditView>> {
  return apiGet<PageResult<AdminAuditView>>('/admin/audit', params)
}

export function listLoginLogs(params: Record<string, unknown>): Promise<PageResult<AdminLoginLogView>> {
  return apiGet<PageResult<AdminLoginLogView>>('/admin/login-logs', params)
}

export function listCards(params: Record<string, unknown>): Promise<PageResult<CardKeyView>> {
  return apiGet<PageResult<CardKeyView>>('/admin/cards', params)
}

export function voidCard(id: number): Promise<{ id: number; status: string }> {
  return apiPut(`/admin/cards/${id}/void`, {})
}

export function exportCards(batchId: string): Promise<{ blob: Blob; filename: string | null }> {
  return apiDownload('/admin/cards/export', { batchId })
}

export function listAdminCities(params: Record<string, unknown>): Promise<PageResult<AdminCityView>> {
  return apiGet<PageResult<AdminCityView>>('/admin/cities', params)
}

export function createAdminCity(body: {
  countryCode: string
  cityCode: string
  name: string
  level: string
}): Promise<AdminCityView> {
  return apiPost('/admin/cities', body)
}

export function updateAdminCity(
  id: number,
  body: { name?: string; status?: string }
): Promise<AdminCityView> {
  return apiPut(`/admin/cities/${id}`, body)
}

export function deleteAdminCity(id: number): Promise<{ id: number; deleted: boolean }> {
  return apiDelete(`/admin/cities/${id}`)
}

export function listOfficialLinks(countryCode?: string): Promise<AdminLinkView[]> {
  return apiGet<AdminLinkView[]>('/admin/official-links', { countryCode })
}

export function createOfficialLink(body: {
  countryCode: string
  title: string
  url: string
  sortOrder?: number | null
}): Promise<AdminLinkView> {
  return apiPost('/admin/official-links', body)
}

export function updateOfficialLink(
  id: number,
  body: { countryCode: string; title: string; url: string; sortOrder?: number | null }
): Promise<AdminLinkView> {
  return apiPut(`/admin/official-links/${id}`, body)
}

export function deleteOfficialLink(id: number): Promise<void> {
  return apiDelete(`/admin/official-links/${id}`)
}

export interface TakedownResponse {
  noteId: number
  takedown: boolean
  sharesRevoked: number
}

export interface RestoreResponse {
  noteId: number
  takedown: boolean
}

export function listExchangeRates(): Promise<ExchangeRateView[]> {
  return apiGet<ExchangeRateView[]>('/admin/exchange-rates')
}

export function upsertExchangeRate(currency: string, rateToCny: string): Promise<RateUpdateResponse> {
  return apiPut<RateUpdateResponse>('/admin/exchange-rates', { currency, rateToCny })
}

export function takedownNote(noteId: number, reason: string): Promise<TakedownResponse> {
  return apiPut<TakedownResponse>(`/admin/notes/${noteId}/takedown`, { reason })
}

export function restoreNote(noteId: number): Promise<RestoreResponse> {
  return apiPut<RestoreResponse>(`/admin/notes/${noteId}/restore`)
}

export interface AdminFeedbackView {
  id: number
  userId: number | null
  submitter: string
  type: FeedbackType
  title: string
  status: FeedbackStatus
  contact: string | null
  createdAt: string
  updatedAt: string
  handledAt: string | null
}

export interface AdminFeedbackDetailView {
  id: number
  userId: number | null
  submitter: string
  type: FeedbackType
  title: string
  content: string
  contact: string | null
  pagePath: string | null
  userAgent: string | null
  appVersion: string | null
  status: FeedbackStatus
  adminNote: string | null
  handledBy: number | null
  handledByName: string | null
  handledAt: string | null
  createdAt: string
  updatedAt: string
}

export interface FeedbackStatsView {
  total: number
  newCount: number
  processingCount: number
  resolvedCount: number
}

export function listAdminFeedback(params: {
  type?: string
  status?: string
  keyword?: string
  from?: string
  to?: string
  page?: number
  size?: number
}): Promise<PageResult<AdminFeedbackView>> {
  return apiGet<PageResult<AdminFeedbackView>>('/admin/feedback', params)
}

export function getFeedbackStats(): Promise<FeedbackStatsView> {
  return apiGet<FeedbackStatsView>('/admin/feedback/stats')
}

export function getAdminFeedback(id: number): Promise<AdminFeedbackDetailView> {
  return apiGet<AdminFeedbackDetailView>(`/admin/feedback/${id}`)
}

export function updateAdminFeedback(
  id: number,
  body: { status?: FeedbackStatus; adminNote?: string | null }
): Promise<AdminFeedbackDetailView> {
  return apiPut<AdminFeedbackDetailView>(`/admin/feedback/${id}`, body)
}

export interface AdminNoteImageView {
  objectKey: string
  url: string | null
}

export interface AdminNoteDetailView {
  id: number
  title: string
  content: string
  contentText: string
  images: AdminNoteImageView[]
  videos: AdminNoteImageView[]
  coverImageUrl: string | null
  authorId: number | null
  authorName: string
  countryCode: string
  countryName: string
  cityCode: string
  cityName: string
  visibility: string
  hasActiveShare: boolean
  shareExpireAt: string | null
  takedown: boolean
  takedownReason: string | null
  takedownAt: string | null
  deleted: boolean
  createdAt: string
  updatedAt: string
}

export function getAdminNoteDetail(id: number): Promise<AdminNoteDetailView> {
  return apiGet<AdminNoteDetailView>(`/admin/notes/${id}`)
}

export interface AdminReportView {
  id: number
  noteId: number
  reporterName: string
  reason: ReportReason
  status: ReportStatus
  description: string | null
  noteTitle: string
  currentNoteTitle: string | null
  authorName: string
  createdAt: string
  handledAt: string | null
}

export interface AdminReportDetailView {
  id: number
  noteId: number
  reporterUserId: number | null
  reporterName: string
  reason: ReportReason
  description: string | null
  noteTitle: string
  noteContentText: string | null
  noteUpdatedAt: string | null
  status: ReportStatus
  handledBy: number | null
  handledByName: string | null
  handledAt: string | null
  createdAt: string
  authorId: number | null
  authorName: string
  currentNoteTitle: string | null
  currentNoteVisibility: string | null
  currentNoteUpdatedAt: string | null
  currentNoteTakedown: boolean
  currentNoteDeleted: boolean
  authorAcceptedReportCount: number
}

export interface ReportStatsView {
  total: number
  pendingCount: number
  acceptedCount: number
  rejectedCount: number
}

export function listAdminReports(params: {
  status?: string
  reason?: string
  keyword?: string
  from?: string
  to?: string
  page?: number
  size?: number
}): Promise<PageResult<AdminReportView>> {
  return apiGet<PageResult<AdminReportView>>('/admin/reports', params)
}

export function getReportStats(): Promise<ReportStatsView> {
  return apiGet<ReportStatsView>('/admin/reports/stats')
}

export function getAdminReport(id: number): Promise<AdminReportDetailView> {
  return apiGet<AdminReportDetailView>(`/admin/reports/${id}`)
}

export function acceptReport(id: number, noteReason: string): Promise<AdminReportDetailView> {
  return apiPut<AdminReportDetailView>(`/admin/reports/${id}/accept`, { noteReason })
}

export function rejectReport(id: number): Promise<AdminReportDetailView> {
  return apiPut<AdminReportDetailView>(`/admin/reports/${id}/reject`)
}

export interface ExchangeRateRefreshItem {
  currency: string
  oldRate: string
  newRate: string | null
  status: 'CHANGED' | 'UNCHANGED' | 'UNSUPPORTED'
}

export interface ExchangeRateRefreshResponse {
  source: string
  fetchedAt: string
  applied: boolean
  updatedCount: number
  unchangedCount: number
  unsupportedCount: number
  items: ExchangeRateRefreshItem[]
}

export function previewExchangeRateRefresh(): Promise<ExchangeRateRefreshResponse> {
  return apiGet<ExchangeRateRefreshResponse>('/admin/exchange-rates/refresh-preview')
}

export function applyExchangeRateRefresh(): Promise<ExchangeRateRefreshResponse> {
  return apiPost<ExchangeRateRefreshResponse>('/admin/exchange-rates/refresh')
}

// ---------- 通知公告 ----------

export type AnnouncementType = 'NOTICE' | 'ANNOUNCEMENT'
export type AnnouncementStatus = 'DRAFT' | 'PUBLISHED' | 'OFFLINE'

export interface AdminAnnouncementView {
  id: number
  title: string
  type: AnnouncementType
  pinned: boolean
  status: AnnouncementStatus
  publishAt: string | null
  expireAt: string | null
  readCount: number
  totalUsers: number
  readRate: number
  createdAt: string
  updatedAt: string
}

export interface AdminAnnouncementDetailView extends AdminAnnouncementView {
  content: string
  createdBy: number | null
}

export interface AnnouncementBody {
  title: string
  content: string
  type: AnnouncementType
  pinned: boolean
  publishAt: string | null
  expireAt: string | null
}

export function listAdminAnnouncements(params: Record<string, unknown>): Promise<PageResult<AdminAnnouncementView>> {
  return apiGet<PageResult<AdminAnnouncementView>>('/admin/announcements', params)
}

export function getAdminAnnouncement(id: number): Promise<AdminAnnouncementDetailView> {
  return apiGet<AdminAnnouncementDetailView>(`/admin/announcements/${id}`)
}

export function createAnnouncement(body: AnnouncementBody): Promise<AdminAnnouncementDetailView> {
  return apiPost<AdminAnnouncementDetailView>('/admin/announcements', body)
}

export function updateAnnouncement(id: number, body: AnnouncementBody): Promise<AdminAnnouncementDetailView> {
  return apiPut<AdminAnnouncementDetailView>(`/admin/announcements/${id}`, body)
}

export function publishAnnouncement(id: number): Promise<AdminAnnouncementDetailView> {
  return apiPut<AdminAnnouncementDetailView>(`/admin/announcements/${id}/publish`)
}

export function offlineAnnouncement(id: number): Promise<AdminAnnouncementDetailView> {
  return apiPut<AdminAnnouncementDetailView>(`/admin/announcements/${id}/offline`)
}

export function deleteAnnouncement(id: number): Promise<void> {
  return apiDelete<void>(`/admin/announcements/${id}`)
}

// ---------- 监控中心 ----------

export interface OnlineUserView {
  userId: number
  username: string | null
  nickname: string | null
  role: string | null
  ip: string | null
  userAgent: string | null
  lastActiveAt: string
}

export interface DiskView {
  name: string
  mount: string
  totalBytes: number
  usableBytes: number
}

export interface JvmView {
  heapUsedBytes: number
  heapMaxBytes: number
  heapCommittedBytes: number
  nonHeapUsedBytes: number
  threadCount: number
  peakThreadCount: number
  gcCount: number
  gcTimeMs: number
  uptimeMs: number
  javaVersion: string
  javaVendor: string
  startTime: string
}

export interface ServerMetrics {
  osName: string
  osVersion: string
  osArch: string
  availableProcessors: number
  logicalCpuCount: number
  physicalCpuCount: number
  systemCpuUsage: number
  processCpuUsage: number
  totalMemoryBytes: number
  freeMemoryBytes: number
  systemUptimeSeconds: number
  disks: DiskView[]
  jvm: JvmView
  partial: boolean
}

export interface DatabaseMetrics {
  activeConnections: number
  idleConnections: number
  totalConnections: number
  waitingThreads: number
  maxPoolSize: number
  minIdle: number
  connectionTimeoutMs: number
  acquireCount: number
  acquireAvgMs: number
  acquireMaxMs: number
  partial: boolean
}

export interface CacheOverview {
  usedMemory: string
  maxMemory: string
  connectedClients: number
  hitRate: number
  keyspaceHits: number
  keyspaceMisses: number
  dbKeys: number
}

export interface CacheGroupView {
  key: string
  label: string
  keyCount: number
  minTtlSeconds: number | null
  maxTtlSeconds: number | null
  truncated: boolean
  clearable: boolean
}

export interface CacheMetrics {
  overview: CacheOverview
  groups: CacheGroupView[]
}

export interface CacheClearResponse {
  group: string
  deleted: number
}

export interface StorageMetrics {
  provider: string
  endpoint: string
  bucket: string
  pathStyle: boolean
  configured: boolean
  reachable: boolean
  latencyMs: number
  message: string | null
}

export interface StorageStats {
  objectCount: number
  totalBytes: number
  truncated: boolean
  available: boolean
  measuredAt: string
}

export function listOnlineUsers(params: Record<string, unknown>): Promise<PageResult<OnlineUserView>> {
  return apiGet<PageResult<OnlineUserView>>('/admin/monitor/online-users', params)
}

export function kickOnlineUser(userId: number): Promise<void> {
  return apiDelete<void>(`/admin/monitor/online-users/${userId}`)
}

export function getServerMetrics(): Promise<ServerMetrics> {
  return apiGet<ServerMetrics>('/admin/monitor/server')
}

export function getDatabaseMetrics(): Promise<DatabaseMetrics> {
  return apiGet<DatabaseMetrics>('/admin/monitor/database')
}

export function getCacheMetrics(): Promise<CacheMetrics> {
  return apiGet<CacheMetrics>('/admin/monitor/cache')
}

export function clearCacheGroup(group: string): Promise<CacheClearResponse> {
  return apiDelete<CacheClearResponse>(`/admin/monitor/cache/${group}`)
}

export function getStorageMetrics(): Promise<StorageMetrics> {
  return apiGet<StorageMetrics>('/admin/monitor/storage')
}

export function getStorageStats(): Promise<StorageStats> {
  return apiGet<StorageStats>('/admin/monitor/storage/stats')
}

export function getApiDocs(): Promise<Record<string, unknown>> {
  return apiGet<Record<string, unknown>>('/admin/monitor/api-docs')
}

// ---------- 故障提醒配置 ----------

export interface AlertConfig {
  enabled: boolean
  webhookUrl: string
  minIntervalSeconds: number
}

export function getAlertConfig(): Promise<AlertConfig> {
  return apiGet<AlertConfig>('/admin/config/alert')
}

export function updateAlertConfig(body: {
  enabled: boolean
  webhookUrl: string
  minIntervalSeconds: number
}): Promise<AlertConfig> {
  return apiPut<AlertConfig>('/admin/config/alert', body)
}

export function testAlert(): Promise<{ sent: boolean }> {
  return apiPost<{ sent: boolean }>('/admin/config/alert/test')
}
