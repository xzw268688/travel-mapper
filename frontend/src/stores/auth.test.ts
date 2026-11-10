import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from './auth'

const mocks = vi.hoisted(() => ({
  loginApi: vi.fn(),
  logoutApi: vi.fn(),
  twoFactorLogin: vi.fn(),
  apiGet: vi.fn()
}))

vi.mock('../api/auth', () => ({
  login: mocks.loginApi,
  logout: mocks.logoutApi,
  twoFactorLogin: mocks.twoFactorLogin
}))

vi.mock('../api/http', () => ({
  apiGet: mocks.apiGet
}))

const user = {
  id: 1,
  username: 'alice',
  nickname: 'Alice',
  avatarPreset: 'cat',
  role: 'USER',
  plan: 'FREE',
  vipExpireAt: null
}

describe('auth store', () => {
  beforeEach(() => {
    localStorage.clear()
    sessionStorage.clear()
    vi.clearAllMocks()
    setActivePinia(createPinia())
  })

  it('stores the session in localStorage when remember is enabled', async () => {
    mocks.loginApi.mockResolvedValue({ token: 'jwt-1', user, requires2fa: false })
    const store = useAuthStore()

    await store.login('alice', 'secret', true)

    expect(store.token).toBe('jwt-1')
    expect(store.user).toEqual(user)
    expect(store.isAuthenticated).toBe(true)
    expect(localStorage.getItem('tm_token')).toBe('jwt-1')
    expect(sessionStorage.getItem('tm_token')).toBeNull()
  })

  it('stores the session in sessionStorage when remember is disabled', async () => {
    mocks.loginApi.mockResolvedValue({ token: 'jwt-2', user, requires2fa: false })
    const store = useAuthStore()

    await store.login('alice', 'secret', false)

    expect(sessionStorage.getItem('tm_token')).toBe('jwt-2')
    expect(localStorage.getItem('tm_token')).toBeNull()
  })

  it('keeps the session pending until two-factor login completes', async () => {
    mocks.loginApi.mockResolvedValue({ requires2fa: true, challengeToken: 'challenge' })
    mocks.twoFactorLogin.mockResolvedValue({ token: 'jwt-3', user })
    const store = useAuthStore()

    await store.login('alice', 'secret', false)
    expect(store.isAuthenticated).toBe(false)
    expect(sessionStorage.getItem('tm_token')).toBeNull()

    await store.completeTwoFactor('challenge', '123456')

    expect(store.token).toBe('jwt-3')
    expect(sessionStorage.getItem('tm_token')).toBe('jwt-3')
  })

  it('preserves the original persistence mode when refreshing the token', async () => {
    mocks.loginApi.mockResolvedValue({ token: 'jwt-4', user, requires2fa: false })
    const store = useAuthStore()
    await store.login('alice', 'secret', true)

    store.updateToken('jwt-5')

    expect(store.token).toBe('jwt-5')
    expect(localStorage.getItem('tm_token')).toBe('jwt-5')
  })

  it('maps the profile quota plan and clears the session on failure', async () => {
    mocks.loginApi.mockResolvedValue({ token: 'jwt-6', user, requires2fa: false })
    mocks.apiGet.mockResolvedValue({
      id: 1,
      username: 'alice',
      nickname: 'Alice',
      avatarPreset: 'cat',
      role: 'ADMIN',
      quota: { plan: 'VIP' },
      vipExpireAt: '2027-01-01T00:00:00'
    })
    const store = useAuthStore()
    await store.login('alice', 'secret', true)

    await store.loadProfile()

    expect(store.user).toMatchObject({ role: 'ADMIN', plan: 'VIP' })

    mocks.apiGet.mockRejectedValue(new Error('expired'))
    await store.loadProfile()

    expect(store.token).toBe('')
    expect(store.user).toBeNull()
    expect(localStorage.getItem('tm_token')).toBeNull()
  })

  it('clears the local session even when the logout request fails', async () => {
    mocks.loginApi.mockResolvedValue({ token: 'jwt-7', user, requires2fa: false })
    mocks.logoutApi.mockRejectedValue(new Error('network'))
    const store = useAuthStore()
    await store.login('alice', 'secret', true)

    await store.logout()

    expect(mocks.logoutApi).toHaveBeenCalled()
    expect(store.token).toBe('')
    expect(store.user).toBeNull()
    expect(localStorage.getItem('tm_token')).toBeNull()
  })

  it('rejects non-admin users by logging them out', async () => {
    mocks.loginApi.mockResolvedValue({ token: 'jwt-8', user, requires2fa: false })
    mocks.logoutApi.mockResolvedValue(undefined)
    const store = useAuthStore()
    await store.login('alice', 'secret', true)

    store.rejectNonAdmin()

    await vi.waitFor(() => expect(store.token).toBe(''))
    expect(mocks.logoutApi).toHaveBeenCalled()
  })
})