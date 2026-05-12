/**
 * 浏览器端登录态：与后端 JWT 配套的 localStorage 键。
 * 集中清理可避免「只剩 role、token 已删」这类半登录状态。
 */

export const LAST_VISITED_ROUTE_KEY = 'petshop_last_visit_fullpath'

const AUTH_KEYS = ['role', 'userId', 'nickname', 'userAvatarUrl', 'accessToken', 'adminId', 'adminName']

export function clearAuthLocalStorage() {
  try {
    for (const k of AUTH_KEYS) {
      localStorage.removeItem(k)
    }
    localStorage.removeItem(LAST_VISITED_ROUTE_KEY)
  } catch {
    /* ignore */
  }
}

export function dispatchAuthClearedEvent() {
  try {
    window.dispatchEvent(new CustomEvent('petshop-auth-cleared'))
  } catch {
    /* ignore */
  }
}

/** 登录成功或手动刷新 localStorage 后通知壳层同步顶栏昵称/角色等 */
export function dispatchAuthUpdatedEvent() {
  try {
    window.dispatchEvent(new CustomEvent('petshop-auth-updated'))
  } catch {
    /* ignore */
  }
}
