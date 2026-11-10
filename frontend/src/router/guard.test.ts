import { describe, expect, it, vi } from 'vitest'
import { createAuthGuard, type AuthGuardDeps, type GuardRoute } from './guard'

function setup(overrides: Partial<AuthGuardDeps> = {}) {
  const deps: AuthGuardDeps = {
    getToken: () => '',
    getAuthStore: () => ({ user: null, loadProfile: vi.fn().mockResolvedValue(undefined) }),
    notify: vi.fn(),
    ...overrides
  }
  return { guard: createAuthGuard(deps), deps }
}

const route = (name: string, meta: GuardRoute['meta'] = {}): GuardRoute => ({ name, meta })

describe('createAuthGuard', () => {
  it('allows public routes without a token', async () => {
    const { guard } = setup()

    await expect(guard(route('share', { public: true }))).resolves.toBe(true)
  })

  it('redirects unauthenticated users from protected routes to login', async () => {
    const { guard } = setup()

    await expect(guard(route('map'))).resolves.toEqual({ name: 'login' })
  })

  it('allows the login route when no token exists', async () => {
    const { guard } = setup()

    await expect(guard(route('login'))).resolves.toBe(true)
  })

  it('redirects authenticated users away from the login route', async () => {
    const { guard } = setup({ getToken: () => 'jwt' })

    await expect(guard(route('login'))).resolves.toEqual({ name: 'map' })
  })

  it('loads the profile before checking admin access', async () => {
    const loadProfile = vi.fn().mockImplementation(async function (this: {
      user: { role: string } | null
    }) {
      this.user = { role: 'ADMIN' }
    })
    const store = { user: null as { role?: string } | null, loadProfile }
    const { guard } = setup({
      getToken: () => 'jwt',
      getAuthStore: () => store
    })

    await expect(guard(route('admin-users', { admin: true }))).resolves.toBe(true)
    expect(loadProfile).toHaveBeenCalledOnce()
  })

  it('rejects non-admin users and notifies them', async () => {
    const notify = vi.fn()
    const { guard } = setup({
      getToken: () => 'jwt',
      getAuthStore: () => ({
        user: { role: 'USER' },
        loadProfile: vi.fn().mockResolvedValue(undefined)
      }),
      notify
    })

    await expect(guard(route('admin-users', { admin: true }))).resolves.toEqual({
      name: 'map'
    })
    expect(notify).toHaveBeenCalledWith('需要管理员权限')
  })

  it('skips the admin store check for regular routes', async () => {
    const getAuthStore = vi.fn()
    const { guard } = setup({ getToken: () => 'jwt', getAuthStore })

    await expect(guard(route('notes'))).resolves.toBe(true)
    expect(getAuthStore).not.toHaveBeenCalled()
  })
})