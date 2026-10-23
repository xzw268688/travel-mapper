import axios from 'axios'

const http = axios.create({
  baseURL: '/api',
  timeout: 20000
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('tm_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('tm_token')
      if (window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
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
