export interface GuardRoute {
  name?: string | symbol | null
  meta?: {
    public?: boolean
    admin?: boolean
  }
}

export interface GuardAuthStore {
  user: { role?: string } | null
  loadProfile: () => Promise<void>
}

export interface AuthGuardDeps {
  getToken: () => string
  getAuthStore: () => GuardAuthStore
  notify: (message: string) => void
}

export type GuardResult = true | { name: string }

/**
 * 路由守卫的纯逻辑，依赖通过参数注入，便于在单测中替换 token/store/提示。
 */
export function createAuthGuard(deps: AuthGuardDeps) {
  return async (to: GuardRoute): Promise<GuardResult> => {
    if (to.meta?.public) {
      return true
    }
    const token = deps.getToken()
    if (to.name !== 'login' && !token) {
      return { name: 'login' }
    }
    if (to.name === 'login' && token) {
      return { name: 'map' }
    }
    if (to.meta?.admin && token) {
      const auth = deps.getAuthStore()
      if (!auth.user) {
        await auth.loadProfile()
      }
      if (auth.user?.role !== 'ADMIN') {
        deps.notify('需要管理员权限')
        return { name: 'map' }
      }
    }
    return true
  }
}