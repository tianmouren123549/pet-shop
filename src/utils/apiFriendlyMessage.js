/**
 * 将接口返回的 message 转为用户/商家可见的安全中文提示，
 * 过滤英文异常、堆栈、SQL 等技术描述；HTTP 状态与业务 code 做兜底映射。
 */

/** 通用失败提示（无更具体信息时使用） */
export const DEFAULT_API_ERROR = '操作失败，请稍后重试'

/** 浏览器 fetch 抛错、断网等 */
export const NETWORK_ERROR = '网络异常，请稍后重试'

/** @type {Record<number, string>} */
const BY_HTTP = {
  401: '请先登录',
  403: '没有权限执行此操作',
  404: '请求的资源不存在',
  413: '上传内容过大，请缩小文件后重试',
  429: '请求过于频繁，请稍后再试',
  502: '服务暂时不可用，请稍后重试',
  503: '服务维护中，请稍后重试',
}

/**
 * 判断是否像技术异常/框架错误文案（需隐藏）。
 * @param {string} s
 * @returns {boolean}
 */
function looksTechnical(s) {
  const low = s.toLowerCase()
  return (
    /exception|error|stack|trace|sql|jdbc|mysql|deadlock|timeout|refused|econn|socket|undefined|nullpointer|internal server|nginx|payload too large|bad request|forbidden|unauthorized/i.test(
      low,
    ) || /\bat\s+[\w.$]+\(/i.test(s)
  )
}

/**
 * @param {unknown} raw 原始 message
 * @param {number} [code] 业务 code 或 HTTP 状态码
 * @returns {string}
 */
export function toFriendlyApiMessage(raw, code = 500) {
  const c = Number(code)
  const co = Number.isFinite(c) ? c : 500
  const s = String(raw ?? '').trim()

  if (!s) {
    if (BY_HTTP[co]) return BY_HTTP[co]
    if (co === 200) return 'success'
    return DEFAULT_API_ERROR
  }

  if (looksTechnical(s)) {
    return BY_HTTP[co] || DEFAULT_API_ERROR
  }

  const hasCjk = /[\u4e00-\u9fff]/.test(s)
  if (!hasCjk) {
    if (/^(success|ok)$/i.test(s) && co === 200) return s
    return BY_HTTP[co] || DEFAULT_API_ERROR
  }

  if (s.length > 200) {
    return DEFAULT_API_ERROR
  }

  return s
}

/**
 * 到货提醒订阅成功后的弹层正文（兼容接口仍返回 success / ok 等英文占位）。
 * @param {unknown} raw 接口 message
 * @returns {string}
 */
export function restockSubscribeSuccessBody(raw) {
  const s = String(raw ?? '').trim()
  if (!s || /^(success|ok)$/i.test(s)) {
    return '到货提醒订阅成功。商品补货上架后，我们将在消息中心第一时间通知您。'
  }
  return s
}
