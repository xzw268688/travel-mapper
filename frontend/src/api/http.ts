import axios, { type InternalAxiosRequestConfig } from 'axios'
import { clearStoredToken, getStoredToken } from '../utils/authStorage'

const http = axios.create({
  baseURL: '/api',
  timeout: 20000
})

/** 为每个请求附加当前 token；单独导出便于单测。 */
export function attachAuthHeader(
  config: InternalAxiosRequestConfig
): InternalAxiosRequestConfig {
  const token = getStoredToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
}

/** 统一处理 401、二次验证引导与业务错误信息；单独导出便于单测。 */
export function handleResponseError(error: any): Promise<never> {
  if (error.response?.status === 401) {
    clearStoredToken()
    if (window.location.pathname !== '/login') {
      window.location.href = '/login'
    }
  }
  // 管理员被要求先绑定二次验证：引导到个人中心账号安全区块。
  if (error.response?.data?.code === 1009) {
    sessionStorage.setItem('tm_notice', '请先绑定二次验证后再访问后台')
    if (window.location.pathname !== '/profile') {
      window.location.href = '/profile'
    }
  }
  // 非 2xx 时 axios 只给出 "Request failed with status code xxx"，
  // 优先展示后端 ApiResponse 里的业务 message。
  const message = error.response?.data?.message
  if (typeof message === 'string' && message.trim()) {
    error.message = message
  }
  return Promise.reject(error)
}

http.interceptors.request.use(attachAuthHeader)

http.interceptors.response.use((response) => response, handleResponseError)

async function unwrap<T>(promise: Promise<{ data: { code: number; message: string; data: T } }>): Promise<T> {
  const response = await promise
  const body = response.data
  if (body.code !== 0) {
    throw new Error(body.message || '请求失败')
  }
  return body.data
}

export function apiGet<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  return unwrap<T>(http.get(url, { params }))
}

/** timeoutMs 用于 AI 等长耗时接口，未传时使用全局 20 秒。 */
export function apiPost<T>(url: string, data?: unknown, timeoutMs?: number): Promise<T> {
  return unwrap<T>(http.post(url, data, timeoutMs ? { timeout: timeoutMs } : undefined))
}

export function apiPut<T>(url: string, data?: unknown): Promise<T> {
  return unwrap<T>(http.put(url, data))
}

export function apiDelete<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  return unwrap<T>(http.delete(url, { params }))
}

export function apiPostWithHeaders<T>(
  url: string,
  data: unknown,
  headers: Record<string, string>
): Promise<T> {
  return unwrap<T>(http.post(url, data, { headers }))
}

/** 下载 CSV 等二进制内容（自动携带 JWT）。 */
export async function apiDownload(
  url: string,
  params?: Record<string, unknown>
): Promise<{ blob: Blob; filename: string | null }> {
  const response = await http.get(url, { params, responseType: 'blob' })
  const disposition = response.headers['content-disposition'] as string | undefined
  const match = disposition?.match(/filename="?([^";]+)"?/)
  return { blob: response.data as Blob, filename: match ? match[1] : null }
}
