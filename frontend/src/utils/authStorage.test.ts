import { beforeEach, describe, expect, it } from 'vitest'
import { clearStoredToken, getStoredToken, storeToken } from './authStorage'

const TOKEN_KEY = 'tm_token'

describe('authStorage', () => {
  beforeEach(() => {
    localStorage.clear()
    sessionStorage.clear()
  })

  it('returns an empty string when no token is stored', () => {
    expect(getStoredToken()).toBe('')
  })

  it('stores remembered tokens in localStorage and clears sessionStorage', () => {
    sessionStorage.setItem(TOKEN_KEY, 'old-session')
    storeToken('local-token', true)

    expect(localStorage.getItem(TOKEN_KEY)).toBe('local-token')
    expect(sessionStorage.getItem(TOKEN_KEY)).toBeNull()
    expect(getStoredToken()).toBe('local-token')
  })

  it('stores non-remembered tokens in sessionStorage and clears localStorage', () => {
    localStorage.setItem(TOKEN_KEY, 'old-local')
    storeToken('session-token', false)

    expect(sessionStorage.getItem(TOKEN_KEY)).toBe('session-token')
    expect(localStorage.getItem(TOKEN_KEY)).toBeNull()
    expect(getStoredToken()).toBe('session-token')
  })

  it('prefers the localStorage token when both stores contain one', () => {
    localStorage.setItem(TOKEN_KEY, 'local-token')
    sessionStorage.setItem(TOKEN_KEY, 'session-token')

    expect(getStoredToken()).toBe('local-token')
  })

  it('clears the token from both stores', () => {
    localStorage.setItem(TOKEN_KEY, 'local-token')
    sessionStorage.setItem(TOKEN_KEY, 'session-token')

    clearStoredToken()

    expect(localStorage.getItem(TOKEN_KEY)).toBeNull()
    expect(sessionStorage.getItem(TOKEN_KEY)).toBeNull()
  })
})