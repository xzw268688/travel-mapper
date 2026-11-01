import axios from 'axios'
import { clearStoredToken, getStoredToken } from '../utils/authStorage'

const http = axios.create({
  baseURL: '/api',
  timeout: 20000
})

http.interceptors.request.use((config) => {
  const token = getStoredToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      clearStoredToken()
      if (window.location.pathname !== '/login') {
        window.location.href = '/login'
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
)

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

export function apiPost<T>(url: string, data?: unknown): Promise<T> {
  return unwrap<T>(http.post(url, data))
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
