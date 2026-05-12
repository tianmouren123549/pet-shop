/** 订单主状态 → 中文（与前台订单页一致） */
const ORDER_STATUS_CN = {
  CREATED: '待支付',
  PAID: '待发货',
  SHIPPED: '已发货',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
}

function orderStatusCn(s) {
  const k = String(s ?? '').trim().toUpperCase()
  return ORDER_STATUS_CN[k] || (k ? k : '—')
}

function accountStatusCn(v) {
  const n = Number(v)
  if (n === 1) return '启用'
  if (n === 0) return '禁用'
  return v != null && String(v).trim() !== '' ? String(v) : '—'
}

function productStatusCn(v) {
  const n = Number(v)
  if (n === 1) return '上架'
  if (n === 0) return '下架'
  return v != null && String(v).trim() !== '' ? String(v) : '—'
}

/**
 * @param {unknown} row
 * @returns {Record<string, unknown>|null}
 */
function parseDetailJson(row) {
  const raw = row?.detailJson
  if (raw == null || raw === '') return null
  try {
    const o = JSON.parse(String(raw))
    return o && typeof o === 'object' ? o : null
  } catch {
    return null
  }
}

function targetKindLabel(tt) {
  const t = String(tt || '').trim().toLowerCase()
  if (t === 'order') return '订单'
  if (t === 'product') return '商品'
  if (t === 'user') return '用户'
  if (t === 'merchant') return '商家'
  return t ? t : '对象'
}

/**
 * 审计列表「时间」列：可读时间，失败则原样返回。
 * @param {string} [iso]
 */
export function formatAuditTime(iso) {
  const s = String(iso || '').trim()
  if (!s) return '—'
  const d = new Date(s.includes('T') ? s : s.replace(' ', 'T'))
  if (Number.isNaN(d.getTime())) return s
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const hh = String(d.getHours()).padStart(2, '0')
  const mm = String(d.getMinutes()).padStart(2, '0')
  const ss = String(d.getSeconds()).padStart(2, '0')
  return `${y}-${m}-${day} ${hh}:${mm}:${ss}`
}

/**
 * @param {unknown} row
 */
export function formatAuditAdmin(row) {
  const id = row?.adminId
  if (id == null || id === '') return '—'
  return `管理员 #${id}`
}

/**
 * 将单条审计记录格式化为一句业务说明（不展示原始 JSON / 英文常量）。
 * @param {unknown} row
 */
export function formatAuditSummary(row) {
  const action = String(row?.action || '').trim()
  const tt = String(row?.targetType || '').trim().toLowerCase()
  const tid = row?.targetId
  const d = parseDetailJson(row) || {}
  const kind = targetKindLabel(tt)

  if (action === 'USER_PET_PREFERENCE_RESET_ALL') {
    const n = d.affectedRows != null ? Number(d.affectedRows) : NaN
    const cnt = Number.isFinite(n) ? `${n} 位用户` : '全部用户'
    return `批量清空用户首页「养宠偏好」：共影响 ${cnt}，用于统一再次弹出引导。`
  }

  const idPart = tid != null && tid !== '' && !(Number(tid) === 0 && kind === '用户') ? `#${tid}` : ''

  switch (action) {
    case 'ORDER_URGE_SHIPMENT':
      return `「催发货」：已向${kind}${idPart ? ` ${idPart}` : ''}所关联的商家发送尽快发货提醒；未修改订单状态。`
    case 'ORDER_STATUS_CHANGE': {
      const from = orderStatusCn(d.fromStatus)
      const to = orderStatusCn(d.toStatus)
      const reason = d.statusReason != null && String(d.statusReason).trim()
        ? ` 备注：${String(d.statusReason).trim().slice(0, 120)}`
        : ''
      return `变更${kind}状态${idPart ? `（${idPart}）` : ''}：由「${from}」改为「${to}」。${reason}`.trim()
    }
    case 'PRODUCT_UPDATE': {
      const parts = []
      if (d.price != null && String(d.price).trim() !== '') parts.push(`价格 ${String(d.price).trim()} 元`)
      if (d.stock != null && String(d.stock).trim() !== '') parts.push(`库存 ${String(d.stock).trim()}`)
      if (d.status != null && String(d.status).trim() !== '') parts.push(`上架状态为「${productStatusCn(d.status)}」`)
      const extra = parts.length ? `（${parts.join('，')}）` : ''
      return `编辑${kind}${idPart ? `（${idPart}）` : ''}${extra}。`.trim()
    }
    case 'PRODUCT_NOTIFY_RESTOCK': {
      const reason = d.reason != null && String(d.reason).trim() ? String(d.reason).trim() : ''
      const note = d.note != null && String(d.note).trim() ? String(d.note).trim().slice(0, 80) : ''
      let s = `向${kind}所属商家发送补货提醒${idPart ? `（${idPart}）` : ''}`
      if (reason) s += `，原因：${reason}`
      if (note) s += `；附言：${note}`
      s += '。'
      return s
    }
    case 'USER_STATUS_CHANGE': {
      const st = accountStatusCn(d.newStatus)
      return `将${kind}账号${idPart ? ` ${idPart}` : ''}设为「${st}」。`
    }
    case 'USER_PASSWORD_RESET':
      return `重置${kind}登录密码${idPart ? `（${idPart}）` : ''}（新密码仅用户本人可见）。`
    case 'USER_PET_PREFERENCE_RESET':
      return `清空${kind}首页「养宠偏好」记录${idPart ? `（${idPart}）` : ''}，用户再次进入首页可重新选择。`
    case 'MERCHANT_STATUS_CHANGE': {
      const st = accountStatusCn(d.newStatus)
      return `将${kind}账号${idPart ? ` ${idPart}` : ''}设为「${st}」。`
    }
    case 'MERCHANT_PASSWORD_RESET':
      return `重置${kind}登录密码${idPart ? `（${idPart}）` : ''}（新密码仅商家本人可见）。`
    default:
      return `执行了一项管理端操作${idPart ? `（涉及 ${kind} ${idPart}）` : kind ? `（涉及 ${kind}）` : ''}，明细已归档。`
  }
}
