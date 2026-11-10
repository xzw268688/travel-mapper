const TOKEN_KEY = 'tm_token'

/**
 * “保持登录”勾选时写 localStorage（跨浏览器重启保留）；
 * 不勾选时写 sessionStorage（关闭标签页后失效）。
 * JWT 服务端有效期固定 7 天，这里只控制客户端是否保留。
 */
export function getStoredToken(): string {
  return localStorage.getItem(TOKEN_KEY) ?? sessionStorage.getItem(TOKEN_KEY) ?? ''
}

export function storeToken(token: string, remember: boolean): void {
  localStorage.removeItem(TOKEN_KEY)
  sessionStorage.removeItem(TOKEN_KEY)
  if (remember) {
    localStorage.setItem(TOKEN_KEY, token)
  } else {
    sessionStorage.setItem(TOKEN_KEY, token)
  }
}

export function clearStoredToken(): void {
  localStorage.removeItem(TOKEN_KEY)
  sessionStorage.removeItem(TOKEN_KEY)
}