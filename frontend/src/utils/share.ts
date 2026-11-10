/** 面向读者的分享页地址（前端 /share/:token 路由）。 */
export function sharePageUrl(token: string): string {
  return `${window.location.origin}/share/${encodeURIComponent(token)}`
}
