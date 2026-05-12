/**
 * 与后端 {@code AuthService} 邮箱规则对齐（宽松 RFC 子集）。
 * @param {string} email
 * @returns {boolean}
 */
export function isValidEmailFormat(email) {
  const s = String(email || '').trim().toLowerCase()
  if (s.length < 5 || s.length > 128) return false
  return /^[\w.!#$%&'*+/=?^`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*$/.test(s)
}
