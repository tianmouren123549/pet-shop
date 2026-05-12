/**
 * 人民币金额展示：仅显示整数元（四舍五入，不带小数）。
 * @param {unknown} value
 * @returns {string}
 */
export function formatYuan(value) {
  const n = Number(value)
  if (!Number.isFinite(n)) return '0'
  return String(Math.round(n))
}
