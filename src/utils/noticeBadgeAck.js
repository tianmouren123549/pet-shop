/**
 * 顶栏通知红点与后端「已读」分离：
 * 用户进入通知中心后，将当前未读通知 ID 记入本地，红点不再统计这些条；
 * 列表仍按接口返回的 readStatus 展示未读样式，直至用户点击「标记已读」同步后端。
 */

const PREFIX_USER = 'petshop_notice_badge_ack_u_'
const PREFIX_MERCHANT = 'petshop_notice_badge_ack_m_'

/**
 * @param {'user' | 'merchant'} kind
 * @param {number} accountId
 * @returns {string}
 */
function storageKey(kind, accountId) {
  const id = Number(accountId) || 0
  return kind === 'merchant' ? `${PREFIX_MERCHANT}${id}` : `${PREFIX_USER}${id}`
}

/**
 * 读取「已消除红点」的通知 ID 集合。
 * @param {'user' | 'merchant'} kind
 * @param {number} accountId
 * @returns {Set<number>}
 */
export function getNoticeBadgeAckSet(kind, accountId) {
  const key = storageKey(kind, accountId)
  try {
    const raw = localStorage.getItem(key)
    if (!raw) return new Set()
    const arr = JSON.parse(raw)
    if (!Array.isArray(arr)) return new Set()
    return new Set(arr.map((x) => Number(x)).filter((x) => x > 0))
  } catch {
    return new Set()
  }
}

/**
 * 将通知 ID 合并进本地 ack 集合。
 * @param {'user' | 'merchant'} kind
 * @param {number} accountId
 * @param {Iterable<number>} noticeIds
 */
export function mergeNoticeBadgeAck(kind, accountId, noticeIds) {
  const s = getNoticeBadgeAckSet(kind, accountId)
  for (const id of noticeIds) {
    const n = Number(id)
    if (n > 0) s.add(n)
  }
  localStorage.setItem(storageKey(kind, accountId), JSON.stringify([...s]))
}

/**
 * 顶栏红点数量：未读且未在本地 ack 中的条数。
 * @param {Array<{ noticeId?: number, readStatus?: number }>} notifications
 * @param {'user' | 'merchant'} kind
 * @param {number} accountId
 * @returns {number}
 */
export function countNoticeBadgeUnread(notifications, kind, accountId) {
  const ack = getNoticeBadgeAckSet(kind, accountId)
  const arr = Array.isArray(notifications) ? notifications : []
  return arr.filter((n) => Number(n.readStatus) !== 1 && !ack.has(Number(n.noticeId))).length
}

/**
 * 进入通知页并成功拉取列表后调用：把当前所有未读 ID 写入 ack，并通知顶栏刷新计数。
 * @param {'user' | 'merchant'} kind
 * @param {number} accountId
 * @param {Array<{ noticeId?: number, readStatus?: number }>} list
 */
export function dismissNoticeBadgeForCurrentUnread(kind, accountId, list) {
  const arr = Array.isArray(list) ? list : []
  const unreadIds = arr
    .filter((n) => Number(n.readStatus) !== 1 && Number(n.noticeId || 0) > 0)
    .map((n) => Number(n.noticeId))
  mergeNoticeBadgeAck(kind, accountId, unreadIds)
  window.dispatchEvent(new Event('petshop-notice-updated'))
}
