import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { InternalAxiosRequestConfig } from 'axios'
import { attachAuthHeader, handleResponseError } from './http'

const originalLocation = window.location

describe('attachAuthHeader', () => {
  beforeEach(() => {
    localStorage.clear()
    sessionStorage.clear()
  })

  it('does not add an Authorization header without a token', () => {
    const config = { headers: {} } as InternalAxiosRequestConfig

    const result = attachAuthHeader(config)

    expect(result.headers.Authorization).toBeUndefined()
  })

  it('adds a bearer token from storage', () => {
    localStorage.setItem('tm_token', 'jwt-token')
    const config = { headers: {} } as InternalAxiosRequestConfig

    const result = attachAuthHeader(config)

    expect(result.headers.Authorization).toBe('Bearer jwt-token')
  })
})

describe('handleResponseError', () => {
  beforeEach(() => {
    localStorage.clear()
    sessionStorage.clear()
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: { pathname: '/admin/users', href: '' }
    })
  })

  afterEach(() => {
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: originalLocation
    })
  })

  it('clears the token and redirects to login on 401', async () => {
    localStorage.setItem('tm_token', 'jwt-token')
    const error = { response: { status: 401, data: {} } }

    await expect(handleResponseError(error)).rejects.toBe(error)

    expect(localStorage.getItem('tm_token')).toBeNull()
    expect(window.location.href).toBe('/login')
  })

  it('does not redirect again when already on the login page', async () => {
    window.location.pathname = '/login'
    const error = { response: { status: 401, data: {} } }

    await expect(handleResponseError(error)).rejects.toBe(error)

    expect(window.location.href).toBe('')
  })

  it('guides users to the profile page when two-factor setup is required', async () => {
    const error = { response: { status: 403, data: { code: 1009 } } }

    await expect(handleResponseError(error)).rejects.toBe(error)

    expect(sessionStorage.getItem('tm_notice')).toContain('二次验证')
    expect(window.location.href).toBe('/profile')
  })

  it('prefers the backend business message over the axios message', async () => {
    const error = {
      message: 'Request failed with status code 400',
      response: { status: 400, data: { code: 2001, message: '参数不合法' } }
    }

    await expect(handleResponseError(error)).rejects.toBe(error)

    expect(error.message).toBe('参数不合法')
  })

  it('keeps the original message when the backend provides none', async () => {
    const error = {
      message: 'Network Error',
      response: { status: 500, data: {} }
    }

    await expect(handleResponseError(error)).rejects.toBe(error)

    expect(error.message).toBe('Network Error')
  })
})