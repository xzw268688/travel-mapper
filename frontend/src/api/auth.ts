import { apiPost } from './http'
import type { LoginResponse } from './types'

export function login(account: string, password: string): Promise<LoginResponse> {
  return apiPost<LoginResponse>('/auth/login', { account, password })
}
