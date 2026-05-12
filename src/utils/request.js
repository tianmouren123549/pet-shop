import { mockProducts, mockReviews, mockCategories, mockCart } from '../mock/data.js'
import { NETWORK_ERROR, toFriendlyApiMessage } from './apiFriendlyMessage.js'
import { formatYuan } from './formatYuan.js'
import { clearAuthLocalStorage, dispatchAuthClearedEvent } from './authStorage.js'
import { isValidEmailFormat } from './emailFormat.js'

/**
 * 是否走内置离线数据（USE_MOCK=true）。联调后端请保持 false；BASE_URL 为空时 Vite 将 `/api`、`/uploads` 代理到 8080。
 * @type {boolean}
 */
const USE_MOCK = false
/** @type {string} API 根路径；生产可设为完整后端地址。 */
const BASE_URL = import.meta.env.VITE_API_BASE ?? ''

/** JWT：登录成功后由后端写入 {@code localStorage.accessToken} */
const ACCESS_TOKEN_KEY = 'accessToken'
/**
 * 商品列表短时缓存：减少首页/商品列表切换时重复全量拉取。
 * 仅缓存成功响应，TTL 取较短值以兼顾新鲜度。
 */
const PRODUCTS_CACHE_TTL_MS = 8000
let productsCache = {
  expireAt: 0,
  data: null,
}
/** 避免短时间内并发触发多次相同商品请求 */
let productsInFlight = null

/**
 * 构建带 Bearer 的请求头（联调后端 JWT 时使用）。
 * @returns {Record<string, string>}
 */
function authHeaders() {
  try {
    const t = localStorage.getItem(ACCESS_TOKEN_KEY)
    return t ? { Authorization: `Bearer ${t}` } : {}
  } catch {
    return {}
  }
}

const NOTICE_KEY_V1 = 'petshop_mock_notifications_v1'
const NOTICE_KEY_V2 = 'petshop_mock_notices_v2'
const SUB_KEY = 'petshop_mock_restock_subscriptions_v1'

function safeJsonParse(s, fb) {
  try {
    return JSON.parse(s)
  } catch {
    return fb
  }
}

function loadNotices() {
  try {
    const v2 = localStorage.getItem(NOTICE_KEY_V2)
    if (v2) {
      const data = safeJsonParse(v2, [])
      return Array.isArray(data) ? data : []
    }
    const legacy = safeJsonParse(localStorage.getItem(NOTICE_KEY_V1) || '[]', [])
    if (!Array.isArray(legacy)) return []
    const list = legacy.map((n) => ({
      noticeId: Number(n.noticeId || 0),
      receiverType: 'MERCHANT',
      receiverId: Number(n.merchantId || 0),
      title: '补货提醒',
      content: `商品「${n.productTitle || ''}」${n.reason || ''}（库存 ${Number(n.stock || 0)}）`,
      productId: Number(n.productId || 0),
      productTitle: String(n.productTitle || ''),
      stock: Number(n.stock || 0),
      reason: String(n.reason || ''),
      note: String(n.note || ''),
      readStatus: Number(n.readStatus || 0),
      createdAt: n.createdAt || new Date().toISOString(),
    }))
    localStorage.setItem(NOTICE_KEY_V2, JSON.stringify(list))
    return list
  } catch {
    return []
  }
}

function saveNotices(list) {
  try {
    localStorage.setItem(NOTICE_KEY_V2, JSON.stringify(Array.isArray(list) ? list : []))
  } catch {
    /* ignore */
  }
}

function loadSubs() {
  try {
    const data = safeJsonParse(localStorage.getItem(SUB_KEY) || '[]', [])
    return Array.isArray(data) ? data : []
  } catch {
    return []
  }
}

function saveSubs(list) {
  try {
    localStorage.setItem(SUB_KEY, JSON.stringify(Array.isArray(list) ? list : []))
  } catch {
    /* ignore */
  }
}

const state = {
  products: JSON.parse(JSON.stringify(mockProducts)),
  reviews: JSON.parse(JSON.stringify(mockReviews)),
  categories: JSON.parse(JSON.stringify(mockCategories)),
  cart: JSON.parse(JSON.stringify(mockCart)),
  users: [
    {
      userId: 1,
      nickname: '测试用户',
      phone: '',
      password: '123456',
      email: 'test@example.com',
      avatarUrl: '',
      petPreference: 'both',
      status: 1,
    },
  ],
  admins: [{ adminId: 1, username: 'admin123', password: '123456', status: 1, role: 'SUPER' }],
  merchants: [
    {
      merchantId: 1,
      username: 'merchant123',
      password: '123456',
      role: 'MERCHANT',
      shopName: '测试宠物店',
      contactName: '张店长',
      phone: '18800001111',
      email: '',
      avatarUrl: '',
      salesTargetWeekly: 5000,
      status: 1,
    },
  ],
  orders: [
    {
      orderId: 1,
      orderNo: 'ORD202603240001',
      userId: 1,
      payAmount: 458.0,
      status: 'PAID',
      items: [
        { productId: 1, title: '比瑞吉金毛专用粮 10kg 天然粮', price: 368.0, quantity: 1, subtotal: 368.0 },
        { productId: 3, title: '冻干零食组合装', price: 90.0, quantity: 1, subtotal: 90.0 },
      ],
      createdAt: new Date(Date.now() - 1000 * 60 * 60 * 24 * 2).toISOString(),
      updatedAt: new Date(Date.now() - 1000 * 60 * 60 * 24 * 2).toISOString(),
    },
    {
      orderId: 2,
      orderNo: 'ORD202603240002',
      userId: 1,
      payAmount: 59.8,
      status: 'SHIPPED',
      items: [{ productId: 4, title: '宠物湿巾 80抽', price: 59.8, quantity: 1, subtotal: 59.8 }],
      createdAt: new Date(Date.now() - 1000 * 60 * 60 * 24 * 5).toISOString(),
      updatedAt: new Date(Date.now() - 1000 * 60 * 60 * 24 * 4).toISOString(),
    },
    {
      orderId: 3,
      orderNo: 'ORD202603240003',
      userId: 1,
      payAmount: 688.0,
      status: 'CREATED',
      items: [{ productId: 2, title: '渴望六种鱼全犬粮 11.4kg 无谷配方', price: 688.0, quantity: 1, subtotal: 688.0 }],
      createdAt: new Date(Date.now() - 1000 * 60 * 60 * 2).toISOString(),
      updatedAt: new Date(Date.now() - 1000 * 60 * 60 * 2).toISOString(),
    },
  ],
  chatSessions: [
    {
      sessionId: 2,
      userId: 1,
      agentAdminId: null,
      orderId: 1,
      sessionType: 'USER_TO_MERCHANT',
      merchantId: 1,
      status: 'OPEN',
      createdAt: new Date(Date.now() - 1000 * 60 * 30).toISOString(),
      updatedAt: new Date(Date.now() - 1000 * 60 * 5).toISOString(),
    },
  ],
  chatMessages: [
    {
      messageId: 2,
      sessionId: 2,
      senderType: 'USER',
      senderId: 1,
      content: '请问这个狗粮什么时候补货？',
      attachmentUrl: null,
      readStatus: 1,
      readByMerchant: 1,
      createdAt: new Date(Date.now() - 1000 * 60 * 12).toISOString(),
    },
    {
      messageId: 3,
      sessionId: 2,
      senderType: 'MERCHANT',
      senderId: 1,
      content: '预计明天上午补货，您可以先收藏商品。',
      attachmentUrl: null,
      readStatus: 0,
      readByMerchant: 1,
      createdAt: new Date(Date.now() - 1000 * 60 * 11).toISOString(),
    },
  ],
  /** mock：用户收货地址（多地址），key 为 userId 字符串 */
  userAddresses: {
    1: [
      {
        addressId: 101,
        label: '家',
        receiverName: '测试用户',
        receiverPhone: '13800138000',
        receiverRegion: '贵州省 贵阳市 云岩区',
        receiverDetail: '某某路 88 号 101 室',
        isDefault: 1,
      },
      {
        addressId: 102,
        label: '公司',
        receiverName: '测试用户',
        receiverPhone: '13900139000',
        receiverRegion: '北京市 朝阳区',
        receiverDetail: '望京 SOHO T1 座',
        isDefault: 0,
      },
    ],
  },
}

for (const p of state.products) {
  if (!p.merchantId) p.merchantId = 1
}
for (const o of state.orders) {
  if (!Array.isArray(o.items)) continue
  for (const it of o.items) {
    if (!it.merchantId) {
      const p = state.products.find((sp) => Number(sp.productId) === Number(it.productId))
      it.merchantId = Number(p?.merchantId || 1)
    }
    const p2 = state.products.find((sp) => Number(sp.productId) === Number(it.productId))
    if (!it.imageUrl) it.imageUrl = String(p2?.detail?.imageUrl || '')
  }
}

function mockAddressListForUser(userId) {
  const uid = Number(userId)
  const key = String(uid)
  if (!state.userAddresses) state.userAddresses = {}
  if (!Array.isArray(state.userAddresses[key])) state.userAddresses[key] = []
  return state.userAddresses[key]
}

function nextMockAddressId() {
  const all = []
  const map = state.userAddresses || {}
  for (const k of Object.keys(map)) {
    all.push(...(map[k] || []))
  }
  return nextId(all, 'addressId')
}

/**
 * @returns {{ receiverName: string, receiverPhone: string, receiverRegion: string, receiverAddress: string } | null}
 */
function resolveMockReceiverFromOrderPayload(userId, data) {
  const uid = Number(userId)
  const aid = Number(data?.addressId || 0)
  const u = (state.users || []).find((x) => Number(x.userId) === uid)
  let receiverName = String(data?.receiverName ?? '').trim()
  let receiverPhone = String(data?.receiverPhone ?? '').trim()
  let receiverRegion = String(data?.receiverRegion ?? '').trim()
  let receiverAddress = String(data?.receiverAddress ?? '').trim()
  if (aid > 0) {
    const row = mockAddressListForUser(uid).find((a) => Number(a.addressId) === aid)
    if (!row) return null
    receiverName = String(row.receiverName || '').trim()
    receiverPhone = String(row.receiverPhone || '').trim()
    receiverRegion = String(row.receiverRegion || '').trim()
    receiverAddress = String(row.receiverDetail || '').trim()
  }
  if (!receiverName && u?.nickname) receiverName = String(u.nickname).trim()
  if (!receiverPhone && u?.phone) receiverPhone = String(u.phone || '').trim()
  return { receiverName, receiverPhone, receiverRegion, receiverAddress }
}

function pad2(n) {
  return String(n).padStart(2, '0')
}

function seedMerchantsForPagination() {
  const extra = [
    {
      merchantId: 2,
      username: 'merchant002',
      password: '123456',
      role: 'MERCHANT',
      shopName: '汪喵优选店',
      contactName: '李店长',
      phone: '18800002222',
      email: '',
      avatarUrl: '',
      status: 1,
    },
    {
      merchantId: 3,
      username: 'merchant003',
      password: '123456',
      role: 'MERCHANT',
      shopName: '萌宠生活馆',
      contactName: '王店长',
      phone: '18800003333',
      email: '',
      avatarUrl: '',
      status: 1,
    },
    {
      merchantId: 4,
      username: 'merchant004',
      password: '123456',
      role: 'MERCHANT',
      shopName: '宠物用品仓',
      contactName: '赵店长',
      phone: '18800004444',
      email: '',
      avatarUrl: '',
      status: 1,
    },
  ]
  for (const m of extra) {
    if (!state.merchants.some((x) => Number(x.merchantId) === Number(m.merchantId))) state.merchants.push(m)
  }
}

function seedProductsForPagination() {
  const base = Array.isArray(state.products) ? state.products : []
  const targetCount = 48
  if (base.length >= targetCount) return
  const nextProductId = () => (base.length ? Math.max(...base.map((x) => Number(x.productId) || 0)) + 1 : 1)
  const catIds = Array.from(new Set((state.categories || []).map((c) => Number(c.categoryId || 0)).filter((x) => x > 0)))
  const merchantCycle = [1, 2, 3, 4]
  let idx = 0
  while (base.length < targetCount) {
    const src = base[idx % Math.min(base.length, 10)] || base[0]
    const pid = nextProductId()
    const merchantId = merchantCycle[idx % merchantCycle.length]
    const categoryId = catIds[idx % (catIds.length || 1)] || Number(src?.categoryId || 1)
    const stock = idx % 9 === 0 ? 0 : idx % 5 === 0 ? 8 + (idx % 10) : 35 + (idx % 120)
    const price = Number(src?.price || 39.9) + (idx % 11) * 6
    base.push({
      ...JSON.parse(JSON.stringify(src || {})),
      productId: pid,
      title: `${String(src?.title || '宠物商品')}（测试扩展${pid}）`,
      categoryId,
      categoryName:
        state.categories.find((c) => Number(c.categoryId) === Number(categoryId))?.name || String(src?.categoryName || ''),
      merchantId,
      price: Number(price.toFixed(2)),
      stock,
      status: idx % 13 === 0 ? 0 : 1,
      createdAt: new Date(Date.now() - idx * 1000 * 60 * 35).toISOString(),
      updatedAt: new Date(Date.now() - idx * 1000 * 60 * 10).toISOString(),
      detail: {
        ...(src?.detail || {}),
        imageUrl: String(src?.detail?.imageUrl || ''),
      },
    })
    idx += 1
  }
}

function seedOrdersForPagination() {
  const targetCount = 40
  if ((state.orders || []).length >= targetCount) return
  const statusCycle = ['CREATED', 'PAID', 'SHIPPED', 'COMPLETED', 'CANCELLED']
  const products = state.products || []
  const nextOrderId = () => (state.orders.length ? Math.max(...state.orders.map((x) => Number(x.orderId) || 0)) + 1 : 1)
  let idx = 0
  while (state.orders.length < targetCount) {
    const oid = nextOrderId()
    const baseIdx = (idx * 3) % Math.max(1, products.length)
    const p1 = products[baseIdx]
    const p2 = products[(baseIdx + 1) % Math.max(1, products.length)]
    const q1 = (idx % 2) + 1
    const q2 = (idx % 3) + 1
    const item1 = {
      productId: Number(p1?.productId || 1),
      merchantId: Number(p1?.merchantId || 1),
      title: String(p1?.title || '测试商品A'),
      price: Number(p1?.price || 39.9),
      quantity: q1,
      subtotal: Number((Number(p1?.price || 39.9) * q1).toFixed(2)),
      imageUrl: String(p1?.detail?.imageUrl || ''),
    }
    const item2 = {
      productId: Number(p2?.productId || 1),
      merchantId: Number(p2?.merchantId || 1),
      title: String(p2?.title || '测试商品B'),
      price: Number(p2?.price || 59.9),
      quantity: q2,
      subtotal: Number((Number(p2?.price || 59.9) * q2).toFixed(2)),
      imageUrl: String(p2?.detail?.imageUrl || ''),
    }
    const payAmount = Number((item1.subtotal + item2.subtotal).toFixed(2))
    const daysAgo = 1 + idx
    const createdAt = new Date(Date.now() - daysAgo * 1000 * 60 * 60 * 12).toISOString()
    const updatedAt = new Date(Date.now() - daysAgo * 1000 * 60 * 60 * 8).toISOString()
    state.orders.push({
      orderId: oid,
      orderNo: `ORD2026${pad2(((idx % 12) + 1))}${pad2(((idx % 27) + 1))}${String(oid).padStart(4, '0')}`,
      userId: 1,
      payAmount,
      status: statusCycle[idx % statusCycle.length],
      items: [item1, item2],
      createdAt,
      updatedAt,
      receiverName: '张晓明',
      receiverPhone: '13800135678',
      receiverRegion: '广东省 深圳市 南山区',
      receiverAddress: '科技园科苑路15号 502室',
      logisticsNo: idx % 5 === 0 ? `SF${String(oid).padStart(10, '0')}` : '',
      paidAt: ['PAID', 'SHIPPED', 'COMPLETED'].includes(statusCycle[idx % statusCycle.length])
        ? new Date(Date.now() - daysAgo * 1000 * 60 * 60 * 10).toISOString()
        : null,
    })
    idx += 1
  }
}

function seedNoticesForPagination() {
  const notices = loadNotices()
  const hasSeed = notices.some((n) => String(n?.note || '').includes('[seed-pagination]'))
  if (hasSeed) return
  const now = Date.now()
  const generated = []
  let nid = notices.length ? Math.max(...notices.map((n) => Number(n.noticeId || 0))) + 1 : 1
  for (let i = 0; i < 20; i += 1) {
    const p = state.products[(i * 2) % Math.max(1, state.products.length)]
    generated.push({
      noticeId: nid++,
      receiverType: 'USER',
      receiverId: 1,
      title: i % 2 === 0 ? '到货通知' : '订单提醒',
      content:
        i % 2 === 0
          ? `你关注的商品「${String(p?.title || '')}」已补货（库存 ${Number(p?.stock || 0)}）`
          : `订单 ${String(state.orders[i]?.orderNo || '')} 状态已更新，请及时查看。`,
      productId: Number(p?.productId || 0) || null,
      productTitle: String(p?.title || ''),
      stock: Number(p?.stock || 0),
      reason: i % 2 === 0 ? '到货' : '状态更新',
      note: '[seed-pagination]',
      readStatus: i % 3 === 0 ? 1 : 0,
      createdAt: new Date(now - i * 1000 * 60 * 45).toISOString(),
    })
  }
  for (let i = 0; i < 20; i += 1) {
    const p = state.products[(i * 3) % Math.max(1, state.products.length)]
    generated.push({
      noticeId: nid++,
      receiverType: 'MERCHANT',
      receiverId: Number(p?.merchantId || 1),
      title: '补货提醒',
      content: `商品「${String(p?.title || '')}」库存偏低（库存 ${Number(p?.stock || 0)}），建议尽快补货。`,
      productId: Number(p?.productId || 0) || null,
      productTitle: String(p?.title || ''),
      stock: Number(p?.stock || 0),
      reason: Number(p?.stock || 0) <= 0 ? '售罄' : '库存紧张',
      note: '[seed-pagination]',
      readStatus: i % 4 === 0 ? 1 : 0,
      createdAt: new Date(now - i * 1000 * 60 * 35).toISOString(),
    })
  }
  saveNotices([...generated, ...notices])
}

function seedMerchantNoticeSamples() {
  const notices = loadNotices()
  const hasSeed = notices.some((n) => String(n?.note || '').includes('[seed-merchant-notice]'))
  if (hasSeed) return
  const now = Date.now()
  const merchantSamples = []
  let nid = notices.length ? Math.max(...notices.map((n) => Number(n.noticeId || 0))) + 1 : 1
  const picks = (state.products || []).slice(0, 8)
  for (let i = 0; i < picks.length; i += 1) {
    const p = picks[i] || {}
    const stock = Number(p?.stock || 0)
    merchantSamples.push({
      noticeId: nid++,
      receiverType: 'MERCHANT',
      receiverId: 1, // merchant123 重点给多几条，便于测试
      title: '补货提醒',
      content: `商品「${String(p?.title || '')}」${stock <= 0 ? '已售罄' : '库存紧张'}（库存 ${stock}），请及时处理。`,
      productId: Number(p?.productId || 0) || null,
      productTitle: String(p?.title || ''),
      stock,
      reason: stock <= 0 ? '售罄' : '库存紧张',
      note: '[seed-merchant-notice]',
      readStatus: i % 3 === 0 ? 1 : 0,
      createdAt: new Date(now - i * 1000 * 60 * 18).toISOString(),
    })
  }
  saveNotices([...merchantSamples, ...notices])
}

seedMerchantsForPagination()
seedProductsForPagination()
seedOrdersForPagination()
seedNoticesForPagination()
seedMerchantNoticeSamples()

function ok(data = null, message = 'success') {
  return Promise.resolve({ code: 200, message, data })
}

function fail(message = '请求失败', code = 400) {
  return Promise.resolve({ code, message, data: null })
}

/**
 * 解析 fetch 响应：兼容非 JSON 体，并对失败 message 做统一脱敏。
 * @param {Response} res
 * @returns {Promise<{ code: number, message: string, data: unknown }>}
 */
async function parseApiResponse(res) {
  const text = await res.text()
  /** @type {Record<string, unknown> | null} */
  let data = null
  if (text) {
    try {
      data = JSON.parse(text)
    } catch {
      data = null
    }
  }
  const unauthorized =
    res.status === 401 || (data != null && typeof data.code === 'number' && data.code === 401)
  if (unauthorized) {
    clearAuthLocalStorage()
    dispatchAuthClearedEvent()
  }
  if (typeof data?.code === 'number') {
    if (data.code !== 200) {
      return {
        ...data,
        message: toFriendlyApiMessage(data.message, data.code),
      }
    }
    return data
  }
  const code = res.ok ? 200 : res.status
  const rawMsg = data && typeof data.message === 'string' ? data.message : ''
  return {
    code,
    message: code === 200 ? (rawMsg || 'success') : toFriendlyApiMessage(rawMsg, code),
    data: data?.data ?? null,
  }
}

async function requestJson(url, options = {}) {
  try {
    const { headers: optHeaders, ...rest } = options
    const res = await fetch(BASE_URL + url, {
      ...rest,
      headers: {
        'Content-Type': 'application/json',
        ...authHeaders(),
        ...(optHeaders || {}),
      },
    })
    return await parseApiResponse(res)
  } catch {
    return { code: 500, message: NETWORK_ERROR, data: null }
  }
}

async function requestFormData(url, formData, options = {}) {
  try {
    const res = await fetch(BASE_URL + url, {
      ...options,
      headers: { ...authHeaders(), ...(options.headers || {}) },
      body: formData,
    })
    return await parseApiResponse(res)
  } catch {
    return { code: 500, message: NETWORK_ERROR, data: null }
  }
}

function getJson(url) {
  return requestJson(url, { method: 'GET' })
}
function postJson(url, body) {
  return requestJson(url, { method: 'POST', body: JSON.stringify(body ?? {}) })
}
function putJson(url, body) {
  return requestJson(url, { method: 'PUT', body: JSON.stringify(body ?? {}) })
}
function deleteJson(url) {
  return requestJson(url, { method: 'DELETE' })
}

function nextId(list, key) {
  if (!list.length) return 1
  return Math.max(...list.map((x) => Number(x[key]) || 0)) + 1
}

function buildMockCategoryTree() {
  const list = state.categories || []
  const nodes = new Map()
  for (const c of list) {
    nodes.set(Number(c.categoryId), {
      categoryId: c.categoryId,
      parentId: c.parentId,
      name: c.name,
      path: c.path,
      children: [],
    })
  }
  const roots = []
  for (const c of list) {
    const id = Number(c.categoryId)
    const n = nodes.get(id)
    const p = Number(c.parentId) || 0
    if (p === 0) {
      roots.push(n)
    } else {
      const parent = nodes.get(p)
      if (parent) parent.children.push(n)
      else roots.push(n)
    }
  }
  const sortRec = (arr) => {
    arr.sort((a, b) => Number(a.categoryId) - Number(b.categoryId))
    arr.forEach((x) => sortRec(x.children))
  }
  sortRec(roots)
  return roots
}

function findCategoryChildrenIds(categoryId) {
  const hit = state.categories.find((c) => c.categoryId === Number(categoryId))
  if (!hit) return [Number(categoryId)]
  if (hit.parentId !== 0) return [hit.categoryId]
  const children = state.categories
    .filter((c) => c.path.startsWith(`${hit.categoryId}/`))
    .map((c) => c.categoryId)
  return [hit.categoryId, ...children]
}

function merchantBaseInfo(merchantId) {
  const m = state.merchants.find((x) => Number(x.merchantId) === Number(merchantId))
  if (!m) return { merchantId: Number(merchantId || 0), shopName: '', contactName: '' }
  return { merchantId: m.merchantId, shopName: m.shopName || '', contactName: m.contactName || '' }
}

function pushNotice(data) {
  const now = new Date().toISOString()
  const list = loadNotices()
  const id = nextId(list, 'noticeId')
  const item = {
    noticeId: id,
    receiverType: String(data?.receiverType || '').toUpperCase(),
    receiverId: Number(data?.receiverId || 0),
    title: String(data?.title || '').trim() || '通知',
    content: String(data?.content || '').trim(),
    productId: typeof data?.productId !== 'undefined' ? Number(data.productId) : null,
    productTitle: typeof data?.productTitle !== 'undefined' ? String(data.productTitle || '') : '',
    stock: typeof data?.stock !== 'undefined' ? Number(data.stock || 0) : null,
    reason: typeof data?.reason !== 'undefined' ? String(data.reason || '') : '',
    note: typeof data?.note !== 'undefined' ? String(data.note || '') : '',
    readStatus: 0,
    createdAt: now,
  }
  list.unshift(item)
  saveNotices(list)
  return id
}

/** Mock：用户下单待支付 → 消息中心（与后端 NotificationService 语义一致） */
function mockNotifyOrderPendingPay(order) {
  const uid = Number(order?.userId || 0)
  if (!uid) return
  const no = String(order.orderNo || '').trim() || `订单号 ${order.orderId}`
  const payNum = Number(order.payAmount)
  const payStr = Number.isFinite(payNum) ? payNum.toFixed(2) : String(order.payAmount ?? '0.00')
  pushNotice({
    receiverType: 'USER',
    receiverId: uid,
    title: '待支付订单',
    content: `订单「${no}」已创建，应付 ¥${payStr}。请尽快前往「我的订单」完成支付；超时未支付将自动取消。`,
    reason: 'ORDER_PENDING_PAY',
  })
}

/** Mock：订单已发货 → 消息中心 */
function mockNotifyOrderShipped(order) {
  const uid = Number(order?.userId || 0)
  if (!uid) return
  const no = String(order.orderNo || '').trim() || `订单号 ${order.orderId}`
  const logNo = order.logisticsNo != null && String(order.logisticsNo).trim()
  const logHint = logNo ? `物流单号：${String(order.logisticsNo).trim()}。` : '请在订单详情查看物流信息。'
  pushNotice({
    receiverType: 'USER',
    receiverId: uid,
    title: '订单已发货',
    content: `订单「${no}」已发货。${logHint}收到货后请及时确认收货。`,
    reason: 'ORDER_SHIPPED',
  })
}

function toCartViewItem(item) {
  const product = state.products.find((p) => p.productId === item.productId)
  if (!product) return null
  const price = Number(product.price)
  const mid = Number(product.merchantId || 0)
  const merchant = (state.merchants || []).find((m) => Number(m.merchantId) === mid)
  const merchantShopName = merchant?.shopName || (mid ? `商家${mid}` : '')
  return {
    cartId: item.cartId,
    userId: item.userId,
    productId: item.productId,
    title: product.title,
    imageUrl: String(product.detail?.imageUrl || ''),
    price: formatYuan(price),
    quantity: item.quantity,
    subtotal: formatYuan(price * item.quantity),
    merchantId: mid || null,
    merchantShopName,
  }
}

function toOrderSummary(o) {
  const u = (state.users || []).find((x) => Number(x.userId) === Number(o.userId))
  const nick =
    u?.nickname && String(u.nickname).trim() ? u.nickname : o.userId != null ? `用户${o.userId}` : '—'
  return {
    orderId: o.orderId,
    orderNo: o.orderNo,
    userId: o.userId,
    userNickname: nick,
    payAmount: formatYuan(o.payAmount),
    status: o.status,
    createdAt: o.createdAt,
    updatedAt: o.updatedAt,
    itemCount: Array.isArray(o.items) ? o.items.reduce((sum, it) => sum + (Number(it.quantity) || 0), 0) : 0,
  }
}

function genOrderNo() {
  const d = new Date()
  const yyyy = d.getFullYear()
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  const n = String(Math.floor(Math.random() * 9000 + 1000))
  return `ORD${yyyy}${mm}${dd}${n}`
}

function calcOrderItems(items) {
  const safe = (items || []).map((x) => ({
    productId: Number(x.productId),
    merchantId: Number(x.merchantId || 1),
    title: String(x.title || ''),
    imageUrl: String(x.imageUrl || ''),
    price: Number(x.price) || 0,
    quantity: Math.max(1, Number(x.quantity) || 1),
  }))
  const normalized = safe.map((it) => ({
    ...it,
    subtotal: Number((it.price * it.quantity).toFixed(2)),
  }))
  const payAmount = Number(normalized.reduce((s, it) => s + it.subtotal, 0).toFixed(2))
  return { items: normalized, payAmount }
}

function canSellProduct(p) {
  return !!p && Number(p.status) === 1
}

function hasEnoughStock(p, needQty) {
  return Number(p?.stock || 0) >= Math.max(1, Number(needQty || 1))
}

function normalizeNonNegativePrice(v) {
  const n = Number(v)
  if (!Number.isFinite(n) || n < 0) return null
  return Number(n.toFixed(2))
}

function normalizeNonNegativeStock(v) {
  const n = Number(v)
  if (!Number.isFinite(n) || n < 0) return null
  return Math.floor(n)
}

function isOrderOwnedByUser(order, userId) {
  return !!order && Number(order.userId || 0) === Number(userId || 0)
}

function isOrderStockDeducted(order) {
  if (!order) return false
  if (typeof order.stockDeducted === 'boolean') return order.stockDeducted
  return !['CREATED', 'CANCELLED'].includes(String(order.status || ''))
}

async function applyOrderStockChange(order, direction) {
  const delta = direction === 'decrease' ? -1 : 1
  const items = Array.isArray(order?.items) ? order.items : []
  const actions = []
  for (const it of items) {
    const pid = Number(it?.productId || 0)
    const qty = Math.max(1, Number(it?.quantity || 1))
    const p = state.products.find((sp) => Number(sp.productId) === pid)
    if (!p) return fail('商品不存在', 404)
    if (delta < 0) {
      if (!canSellProduct(p)) return fail('商品已下架')
      if (!hasEnoughStock(p, qty)) return fail('库存不足')
    }
    actions.push({ product: p, qty })
  }
  if (delta !== 0 && actions.length > 0) {
    const now = new Date().toISOString()
    for (const act of actions) {
      const p = act.product
      const qty = act.qty
      if (delta < 0) {
        p.stock = Number(p.stock || 0) - qty
      } else {
        p.stock = Number(p.stock || 0) + qty
      }
      p.updatedAt = now
    }
  }
  return ok(true)
}

function nextReviewId() {
  let max = 0
  for (const pid of Object.keys(state.reviews)) {
    for (const r of state.reviews[pid] || []) {
      max = Math.max(max, Number(r.reviewId || 0))
    }
  }
  return max + 1
}

const mockApi = {
  postUserEvent: async () => ok(true),
  getUserRecommendations: async (userId, topN = 10) => {
    const uid = Number(userId || 0)
    if (!uid) return fail('请先登录')
    const n = Math.max(1, Math.min(Number(topN || 10), 50))
    const data = (state.products || [])
      .filter((p) => Number(p.status) === 1)
      .sort((a, b) => Number(b.stock || 0) - Number(a.stock || 0))
      .slice(0, n)
      .map((p, idx) => ({
        productId: Number(p.productId),
        score: Number((0.95 - idx * 0.01).toFixed(4)),
        rankNo: idx + 1,
        modelVersion: 'mock-xgb-v1',
        product: p,
      }))
    return ok(data)
  },
  getProducts: async () => ok(state.products.filter((p) => Number(p.status) === 1)),
  getProduct: async (id) => {
    const p = state.products.find((x) => x.productId === Number(id))
    if (!p) return fail('商品不存在', 404)
    if (Number(p.status) !== 1) return fail('商品不存在', 404)
    return ok(p)
  },
  getProductsByCategory: async (categoryId) => {
    const ids = new Set(findCategoryChildrenIds(categoryId))
    const data = state.products.filter((p) => ids.has(p.categoryId) && Number(p.status) === 1)
    return ok(data)
  },
  getCategories: async () => ok(state.categories),
  getRootCategories: async () => ok(state.categories.filter((c) => c.parentId === 0)),
  getCategoryTree: async () => ok(buildMockCategoryTree()),

  getCart: async (userId) => {
    const uid = Number(userId)
    const list = state.cart.filter((x) => x.userId === uid).map(toCartViewItem).filter(Boolean)
    return ok(list)
  },
  addToCart: async (data) => {
    const userId = Number(data?.userId)
    const productId = Number(data?.productId)
    const quantity = Math.max(1, Number(data?.quantity) || 1)
    if (!userId) return fail('请先登录')
    const product = state.products.find((p) => p.productId === productId)
    if (!product) return fail('商品不存在', 404)
    if (Number(product.status) !== 1) return fail('商品已下架')
    if (Number(product.stock) <= 0) return fail('商品已售罄')
    const exist = state.cart.find((c) => c.userId === userId && c.productId === productId)
    if (exist) {
      const nextQty = Number(exist.quantity || 0) + quantity
      if (nextQty > Number(product.stock || 0)) return fail('库存不足')
      exist.quantity = nextQty
    } else {
      if (quantity > Number(product.stock || 0)) return fail('库存不足')
      state.cart.push({ cartId: nextId(state.cart, 'cartId'), userId, productId, quantity })
    }
    return ok(true, '已加入购物车')
  },
  updateCart: async (data) => {
    const cartId = Number(data?.cartId)
    const quantity = Math.max(1, Number(data?.quantity) || 1)
    const row = state.cart.find((c) => c.cartId === cartId)
    if (!row) return fail('购物车项不存在', 404)
    const product = state.products.find((p) => Number(p.productId) === Number(row.productId))
    if (!product) return fail('商品不存在', 404)
    if (!canSellProduct(product)) return fail('商品已下架')
    if (!hasEnoughStock(product, quantity)) return fail('库存不足')
    row.quantity = quantity
    return ok(true, '已更新')
  },
  removeFromCart: async (cartId) => {
    const id = Number(cartId)
    const before = state.cart.length
    state.cart = state.cart.filter((c) => c.cartId !== id)
    if (state.cart.length === before) return fail('购物车项不存在', 404)
    return ok(true, '已删除')
  },

  getReviews: async (productId) => ok(state.reviews[Number(productId)] || []),
  addReview: async (data) => {
    const userId = Number(data?.userId)
    const productId = Number(data?.productId)
    const content = String(data?.content || '').trim()
    if (!userId) return fail('请先登录')
    if (!content) return fail('评价内容不能为空')
    const list = state.reviews[productId] || (state.reviews[productId] = [])
    const review = {
      reviewId: nextReviewId(),
      userId,
      productId,
      rating: Number(data?.rating) || 5,
      content,
      goldenRetrieverScore: content.includes('金毛') ? 0.9 : 0.1,
      status: 1,
      createdAt: new Date().toISOString(),
    }
    list.unshift(review)
    return ok(review, '评价成功')
  },

  login: async (data) => {
    const email = String(data?.email || '').trim().toLowerCase()
    const user = state.users.find((u) => String(u.email || '').trim().toLowerCase() === email)
    if (!user || user.password !== String(data?.password || '')) return fail('邮箱或密码错误')
    return ok(
      { userId: user.userId, nickname: user.nickname, token: `mock-jwt-USER-${user.userId}` },
      '登录成功',
    )
  },
  register: async (data) => {
    const nickname = String(data?.nickname || '').trim()
    const password = String(data?.password || '')
    const email = String(data?.email || '').trim().toLowerCase()
    const phone = String(data?.phone || '').trim()
    if (!email) return fail('邮箱不能为空')
    if (!isValidEmailFormat(email)) return fail('邮箱格式不正确')
    if (password.length < 6) return fail('密码至少 6 位')
    if (state.users.some((u) => String(u.email || '').trim().toLowerCase() === email)) return fail('该邮箱已被注册')
    const user = {
      userId: nextId(state.users, 'userId'),
      nickname: nickname || '用户',
      phone: phone || '',
      password,
      email,
      avatarUrl: '',
      petPreference: 'both',
      status: 1,
    }
    state.users.push(user)
    if (!state.userAddresses) state.userAddresses = {}
    state.userAddresses[String(user.userId)] = []
    return ok(
      { userId: user.userId, nickname: user.nickname, token: `mock-jwt-USER-${user.userId}` },
      '注册成功',
    )
  },
  adminLogin: async (data) => {
    const admin = state.admins.find((a) => a.username === String(data?.username || '').trim())
    if (!admin || admin.password !== String(data?.password || '')) return fail('账号或密码错误')
    const role = admin.role || 'SUPER'
    return ok(
      {
        adminId: admin.adminId,
        username: admin.username,
        role,
        token: `mock-jwt-${role}-${admin.adminId}`,
      },
      '登录成功',
    )
  },
  merchantLogin: async (data) => {
    const merchant = state.merchants.find((m) => m.username === String(data?.username || '').trim())
    if (!merchant || merchant.password !== String(data?.password || '')) return fail('账号或密码错误')
    return ok(
      {
        adminId: merchant.merchantId,
        username: merchant.username,
        role: merchant.role || 'MERCHANT',
        token: `mock-jwt-MERCHANT-${merchant.merchantId}`,
      },
      '登录成功',
    )
  },
  merchantRegister: async (data) => {
    const username = String(data?.username || '').trim()
    const password = String(data?.password || '')
    const shopName = String(data?.shopName || '').trim()
    const contactName = String(data?.contactName || '').trim()
    const phone = String(data?.phone || '').trim()
    const email = String(data?.email || '').trim()
    if (!username) return fail('商家账号不能为空')
    if (password.length < 6) return fail('密码至少 6 位')
    if (!shopName) return fail('店铺名称不能为空')
    if (!contactName) return fail('联系人不能为空')
    if (!phone) return fail('联系电话不能为空')
    if (email && !isValidEmailFormat(email)) return fail('联系邮箱格式不正确')
    if (state.merchants.some((m) => m.username === username)) return fail('该商家账号已被注册')
    const merchant = {
      merchantId: nextId(state.merchants, 'merchantId'),
      username,
      password,
      role: 'MERCHANT',
      shopName,
      contactName,
      phone,
      email: email || '',
      avatarUrl: '',
      status: 1,
    }
    state.merchants.push(merchant)
    return ok(
      {
        adminId: merchant.merchantId,
        username: merchant.username,
        role: 'MERCHANT',
        token: `mock-jwt-MERCHANT-${merchant.merchantId}`,
      },
      '注册成功',
    )
  },

  adminGetProducts: async () => {
    const data = (state.products || []).map((p) => {
      const m = merchantBaseInfo(p.merchantId)
      const imageUrl = String(p?.detail?.imageUrl ?? p?.imageUrl ?? '').trim()
      const categoryName = String(p?.categoryName ?? '').trim()
      return {
        ...p,
        merchantId: m.merchantId,
        merchantShopName: m.shopName,
        merchantContactName: m.contactName,
        imageUrl,
        categoryName,
      }
    })
    return ok(data)
  },
  adminUpdateProduct: async (productId, data) => {
    const product = state.products.find((p) => p.productId === Number(productId))
    if (!product) return fail('商品不存在', 404)
    if (typeof data?.status !== 'undefined') {
      const s = Number(data.status)
      if (![0, 1].includes(s)) return fail('商品状态无效')
      product.status = s
    }
    if (typeof data?.stock !== 'undefined') {
      const stock = normalizeNonNegativeStock(data.stock)
      if (stock === null) return fail('库存必须为非负整数')
      product.stock = stock
    }
    if (typeof data?.price !== 'undefined') {
      const price = normalizeNonNegativePrice(data.price)
      if (price === null) return fail('价格必须为非负数')
      product.price = price
    }
    product.updatedAt = new Date().toISOString()
    return ok(true, '更新成功')
  },
  adminNotifyRestock: async (productId, data) => {
    const product = state.products.find((p) => p.productId === Number(productId))
    if (!product) return fail('商品不存在', 404)
    const m = merchantBaseInfo(product.merchantId)
    if (!m.merchantId) return fail('商品未绑定商家', 400)
    const reason = String(data?.reason || '').trim()
    const note = String(data?.note || '').trim()
    pushNotice({
      receiverType: 'MERCHANT',
      receiverId: m.merchantId,
      title: '补货提醒',
      content: `商品「${String(product.title || '')}」${reason || (Number(product.stock) <= 0 ? '售罄' : '库存紧张')}（库存 ${Number(product.stock || 0)}）`,
      productId: product.productId,
      productTitle: product.title || '',
      stock: Number(product.stock || 0),
      reason: reason || (Number(product.stock) <= 0 ? '售罄' : '库存紧张'),
      note,
    })
    return ok(true, '已发送补货提醒')
  },

  merchantGetProducts: async (merchantId) => {
    const mid = Number(merchantId || 0)
    if (!mid) return fail('商家ID无效', 400)
    const data = state.products
      .filter((p) => Number(p.merchantId || 0) === mid)
      .map((p) => {
        const m = merchantBaseInfo(p.merchantId)
        return { ...p, merchantId: m.merchantId, merchantShopName: m.shopName, merchantContactName: m.contactName }
      })
    return ok(data)
  },
  merchantGetProduct: async (merchantId, productId) => {
    const mid = Number(merchantId || 0)
    if (!mid) return fail('商家ID无效', 400)
    const product = state.products.find((p) => Number(p.productId) === Number(productId))
    if (!product) return fail('商品不存在', 404)
    if (Number(product.merchantId || 0) !== mid) return fail('无权限查看该商品', 403)
    return ok(JSON.parse(JSON.stringify(product)))
  },
  merchantUpdateProduct: async (merchantId, productId, data) => {
    const mid = Number(merchantId || 0)
    if (!mid) return fail('商家ID无效', 400)
    const product = state.products.find((p) => p.productId === Number(productId))
    if (!product) return fail('商品不存在', 404)
    if (Number(product.merchantId || 0) !== mid) return fail('无权限操作该商品', 403)
    const prevStock = Number(product.stock || 0)
    if (typeof data?.status !== 'undefined') {
      const s = Number(data.status)
      if (![0, 1].includes(s)) return fail('商品状态无效')
      product.status = s
    }
    if (typeof data?.stock !== 'undefined') {
      const stock = normalizeNonNegativeStock(data.stock)
      if (stock === null) return fail('库存必须为非负整数')
      product.stock = stock
    }
    if (typeof data?.price !== 'undefined') {
      const price = normalizeNonNegativePrice(data.price)
      if (price === null) return fail('价格必须为非负数')
      product.price = price
    }
    product.updatedAt = new Date().toISOString()
    const nextStock = Number(product.stock || 0)
    if (prevStock <= 0 && nextStock > 0) {
      const subs = loadSubs()
      const hit = subs.filter((s) => Number(s.productId) === Number(product.productId) && Number(s.active) === 1)
      if (hit.length) {
        for (const s of hit) {
          pushNotice({
            receiverType: 'USER',
            receiverId: Number(s.userId || 0),
            title: '到货通知',
            content: `你关注的商品「${String(product.title || '')}」已补货（当前库存 ${nextStock}）`,
            productId: product.productId,
            productTitle: product.title || '',
            stock: nextStock,
            reason: '到货',
          })
        }
        const nextSubs = subs.map((s) => {
          if (Number(s.productId) === Number(product.productId) && Number(s.active) === 1) {
            return { ...s, active: 0, updatedAt: new Date().toISOString() }
          }
          return s
        })
        saveSubs(nextSubs)
      }
    }
    return ok(true, '更新成功')
  },
  merchantCreateProduct: async (data) => {
    const title = String(data?.title || '').trim()
    if (!title) return fail('商品名称不能为空')
    const categoryId = Number(data?.categoryId || 0)
    if (!categoryId) return fail('请选择类目')
    const productId = nextId(state.products, 'productId')
    const price = normalizeNonNegativePrice(data?.price ?? 0)
    if (price === null) return fail('价格必须为非负数')
    const stock = normalizeNonNegativeStock(data?.stock ?? 0)
    if (stock === null) return fail('库存必须为非负整数')
    const now = new Date().toISOString()
    const category = state.categories.find((c) => c.categoryId === categoryId)
    const merchantId = Number(data?.merchantId || 0)
    if (!merchantId) return fail('商家ID无效', 400)
    if (!state.merchants.some((m) => Number(m.merchantId) === merchantId)) return fail('商家不存在', 404)
    state.products.push({
      productId,
      merchantId,
      title,
      categoryId,
      categoryName: category?.name || '',
      brandId: null,
      brandName: null,
      price,
      stock,
      status: 1,
      createdAt: now,
      updatedAt: now,
      detail: {
        description: String(data?.description ?? ''),
        specJson: {},
        imageUrl: '',
      },
    })
    return ok({ productId }, '创建成功')
  },
  merchantUpdateProductContent: async (productId, data) => {
    const product = state.products.find((p) => p.productId === Number(productId))
    if (!product) return fail('商品不存在', 404)
    if (!product.detail) product.detail = {}
    if (typeof data?.description !== 'undefined') product.detail.description = String(data.description ?? '')
    if (typeof data?.specJson !== 'undefined') {
      if (typeof data.specJson === 'string') {
        try {
          product.detail.specJson = JSON.parse(data.specJson)
        } catch {
          return fail('规格参数 JSON 格式不正确')
        }
      } else {
        product.detail.specJson = data.specJson
      }
    }
    if (typeof data?.imageUrl !== 'undefined') product.detail.imageUrl = String(data.imageUrl ?? '')
    if (data?.imageFile && typeof data.imageFile === 'object' && data.imageFile.name) {
      product.detail.imageUrl = `/mock-upload/${String(data.imageFile.name).replace(/[^\w.-]+/g, '_')}`
    }
    return ok(true, '内容更新成功')
  },

  adminGetOrders: async (status = '') => {
    const data = status ? state.orders.filter((o) => o.status === status) : state.orders
    return ok(data.map(toOrderSummary))
  },
  adminGetOrderDetail: async (orderId) => {
    const order = state.orders.find((o) => o.orderId === Number(orderId))
    if (!order) return fail('订单不存在', 404)
    const u = (state.users || []).find((x) => Number(x.userId) === Number(order.userId))
    const items = (order.items || []).map((it) => {
      const p = state.products.find((sp) => Number(sp.productId) === Number(it.productId))
      const mid = Number(it.merchantId || p?.merchantId || 0)
      const m = (state.merchants || []).find((mer) => Number(mer.merchantId) === mid)
      const qty = Number(it.quantity) || 0
      const price = Number(it.price != null ? it.price : p?.price) || 0
      const sub = Number(it.subtotal != null ? it.subtotal : price * qty)
      return {
        productId: it.productId,
        merchantId: mid || null,
        merchantShopName: m?.shopName || (mid ? `商家${mid}` : '—'),
        title: it.title || p?.title || '',
        imageUrl: String(it.imageUrl || p?.detail?.imageUrl || ''),
        price,
        quantity: qty,
        subtotal: sub,
      }
    })
    const mids = [...new Set(items.map((x) => Number(x.merchantId || 0)).filter((x) => x > 0))]
    const merchants = mids.map((mid) => {
      const m = (state.merchants || []).find((mer) => Number(mer.merchantId) === mid)
      return {
        merchantId: mid,
        shopName: m?.shopName || `商家${mid}`,
        contactName: m?.contactName || '',
        phone: m?.phone || '',
      }
    })
    const itemCount = items.reduce((s, x) => s + Number(x.quantity || 0), 0)
    return ok({
      orderId: order.orderId,
      orderNo: order.orderNo,
      userId: order.userId,
      userNickname: u?.nickname?.trim() ? u.nickname : order.userId != null ? `用户${order.userId}` : '—',
      userEmail: u?.email || '',
      userPhone: u?.phone || '',
      payAmount: formatYuan(order.payAmount),
      status: order.status,
      statusReason: order.statusReason || '',
      createdAt: order.createdAt,
      updatedAt: order.updatedAt || order.createdAt,
      itemCount,
      merchants,
      items,
    })
  },
  adminListUsers: async () => {
    const list = (state.users || []).map((u) => ({
      userId: u.userId,
      nickname: u.nickname || '',
      email: u.email || '',
      phone: u.phone || '',
      status: u.status != null ? u.status : 1,
      createdAt: u.createdAt || new Date().toISOString(),
    }))
    return ok(list)
  },
  adminUpdateUserStatus: async (userId, data) => {
    const u = state.users.find((x) => x.userId === Number(userId))
    if (!u) return fail('用户不存在', 404)
    u.status = Number(data?.status) === 0 ? 0 : 1
    return ok(true)
  },
  adminResetUserPassword: async (userId, data) => {
    const u = state.users.find((x) => x.userId === Number(userId))
    if (!u) return fail('用户不存在', 404)
    const pwd = String(data?.newPassword || '')
    if (pwd.length < 6) return fail('新密码至少 6 位')
    u.password = pwd
    return ok(true)
  },
  adminResetUserPetPreference: async (userId) => {
    const u = state.users.find((x) => x.userId === Number(userId))
    if (!u) return fail('用户不存在', 404)
    u.petPreference = null
    return ok(true)
  },
  adminResetAllUserPetPreference: async () => {
    let affected = 0
    for (const u of state.users || []) {
      u.petPreference = null
      affected += 1
    }
    return ok({ affectedRows: affected })
  },
  adminListMerchants: async () => {
    const list = (state.merchants || []).map((m) => ({
      merchantId: m.merchantId,
      username: m.username || '',
      shopName: m.shopName || '',
      contactName: m.contactName || '',
      phone: m.phone || '',
      email: m.email || '',
      status: m.status != null ? m.status : 1,
      createdAt: m.createdAt || new Date().toISOString(),
      lastLoginAt: m.lastLoginAt || '',
    }))
    return ok(list)
  },
  adminUpdateMerchantStatus: async (merchantId, data) => {
    const m = state.merchants.find((x) => x.merchantId === Number(merchantId))
    if (!m) return fail('商家不存在', 404)
    m.status = Number(data?.status) === 0 ? 0 : 1
    return ok(true)
  },
  adminResetMerchantPassword: async (merchantId, data) => {
    const m = state.merchants.find((x) => x.merchantId === Number(merchantId))
    if (!m) return fail('商家不存在', 404)
    const pwd = String(data?.newPassword || '')
    if (pwd.length < 6) return fail('新密码至少 6 位')
    m.password = pwd
    return ok(true)
  },
  adminUpdateOrderStatus: async (orderId, data) => {
    const order = state.orders.find((o) => o.orderId === Number(orderId))
    if (!order) return fail('订单不存在', 404)
    const next = String(data?.status || '').toUpperCase()
    if (!['CREATED', 'PAID', 'SHIPPED', 'COMPLETED', 'CANCELLED'].includes(next)) return fail('不支持的状态操作')
    if (next === order.status) return ok(true, '状态未变化')
    if (next === 'CANCELLED') {
      if (!['CREATED', 'PAID'].includes(order.status)) return fail('当前状态无法取消')
      if (order.status === 'PAID' && isOrderStockDeducted(order)) {
        const changed = await applyOrderStockChange(order, 'increase')
        if (changed.code !== 200) return changed
        order.stockDeducted = false
      }
      order.status = 'CANCELLED'
      order.updatedAt = new Date().toISOString()
      return ok(true, '状态更新成功')
    }
    if (next === 'PAID') {
      if (order.status !== 'CREATED') return fail('仅待支付订单可改为已支付')
      if (!isOrderStockDeducted(order)) {
        const changed = await applyOrderStockChange(order, 'decrease')
        if (changed.code !== 200) return changed
        order.stockDeducted = true
      }
      order.status = 'PAID'
      order.updatedAt = new Date().toISOString()
      return ok(true, '状态更新成功')
    }
    if (next === 'SHIPPED') {
      if (order.status !== 'PAID') return fail('仅已支付订单可发货')
      order.status = 'SHIPPED'
      order.stockDeducted = true
      order.updatedAt = new Date().toISOString()
      mockNotifyOrderShipped(order)
      return ok(true, '状态更新成功')
    }
    if (next === 'COMPLETED') {
      if (order.status !== 'SHIPPED') return fail('仅已发货订单可完成')
      order.status = 'COMPLETED'
      order.stockDeducted = true
      order.updatedAt = new Date().toISOString()
      return ok(true, '状态更新成功')
    }
    if (next === 'CREATED') return fail('不支持回退到待支付')
    order.updatedAt = new Date().toISOString()
    return ok(true, '状态更新成功')
  },

  adminUrgeOrderShipment: async (orderId) => {
    const order = state.orders.find((o) => o.orderId === Number(orderId))
    if (!order) return fail('订单不存在', 404)
    if (order.status !== 'PAID') return fail('仅待发货（已支付）订单可催发货')
    const mids = [
      ...new Set((order.items || []).map((it) => Number(it.merchantId || 0)).filter((x) => x > 0)),
    ]
    if (!mids.length) return fail('订单无商家明细，无法通知')
    const no = String(order.orderNo || '').trim() || `#${order.orderId}`
    const content = `订单「${no}」已支付，客户等待发货。请及时在商家后台处理发货。`
    for (const mid of mids) {
      pushNotice({
        receiverType: 'MERCHANT',
        receiverId: mid,
        title: '平台催发货',
        content,
        reason: 'ADMIN_URGE_SHIP',
      })
    }
    return ok(true, '已通知商家尽快发货')
  },

  merchantGetOrders: async (merchantId, status = '') => {
    const mid = Number(merchantId || 0)
    if (!mid) return fail('商家ID无效', 400)
    const data = (status ? state.orders.filter((o) => o.status === status) : state.orders)
      .filter((o) => Array.isArray(o.items) && o.items.some((it) => Number(it.merchantId || 0) === mid))
      .map((o) => {
        const s = toOrderSummary(o)
        return {
          ...s,
          buyerNickname: s.userNickname,
          logisticsNo: o.logisticsNo != null ? String(o.logisticsNo) : '',
        }
      })
    return ok(data)
  },
  merchantGetOrderDetail: async (merchantId, orderId) => {
    const mid = Number(merchantId || 0)
    const oid = Number(orderId)
    if (!mid || !oid) return fail('参数无效', 400)
    const order = state.orders.find((o) => o.orderId === oid)
    if (!order) return fail('订单不存在', 404)
    const lines = (order.items || []).filter((it) => Number(it.merchantId) === mid)
    if (!lines.length) return fail('订单不存在或无权查看', 404)
    const payNum = Number(order.payAmount)
    const payStr = Number.isFinite(payNum) ? payNum.toFixed(2) : String(order.payAmount ?? '0.00')
    const u = (state.users || []).find((x) => Number(x.userId) === Number(order.userId))
    const buyerNickname =
      u?.nickname && String(u.nickname).trim() ? String(u.nickname).trim() : `用户 #${order.userId ?? '—'}`
    return ok({
      orderId: order.orderId,
      orderNo: order.orderNo,
      userId: order.userId,
      payAmount: payStr,
      status: order.status,
      createdAt: order.createdAt,
      updatedAt: order.updatedAt,
      paidAt: order.paidAt || null,
      buyerNickname,
      receiverName: order.receiverName != null ? String(order.receiverName) : '',
      receiverPhone: order.receiverPhone != null ? String(order.receiverPhone) : '',
      receiverRegion: order.receiverRegion != null ? String(order.receiverRegion) : '',
      receiverAddress: order.receiverAddress != null ? String(order.receiverAddress) : '',
      logisticsNo: order.logisticsNo != null ? String(order.logisticsNo) : '',
      lines: lines.map((it) => {
        const q = Number(it.quantity) || 0
        const p = Number(it.price) || 0
        const sub = it.subtotal != null ? Number(it.subtotal) : p * q
        const pid = Number(it.productId) || 0
        return {
          productId: it.productId,
          skuCode: pid > 0 ? `PW-${String(pid).padStart(5, '0')}` : '',
          title: String(it.title || ''),
          imageUrl: String(it.imageUrl || ''),
          quantity: q,
          unitPrice: p.toFixed(2),
          subtotal: Number(sub).toFixed(2),
        }
      }),
    })
  },
  merchantOrderTodoBadges: async (merchantId) => {
    const mid = Number(merchantId || 0)
    if (!mid) return fail('商家ID无效', 400)
    const mine = state.orders.filter(
      (o) => Array.isArray(o.items) && o.items.some((it) => Number(it.merchantId || 0) === mid),
    )
    const pendingShipment = mine.some((o) => o.status === 'PAID')
    return ok({ pendingShipment })
  },
  merchantUpdateOrderStatus: async (merchantId, orderId, status) => {
    const mid = Number(merchantId || 0)
    if (!mid) return fail('商家ID无效', 400)
    const order = state.orders.find((o) => o.orderId === Number(orderId))
    if (!order) return fail('订单不存在', 404)
    const own = Array.isArray(order.items) && order.items.some((it) => Number(it.merchantId || 0) === mid)
    if (!own) return fail('无权限操作该订单', 403)
    const allOwnedByMerchant = Array.isArray(order.items) && order.items.every((it) => Number(it.merchantId || 0) === mid)
    const next = String(status || '').toUpperCase()
    if (next === 'SHIPPED') {
      if (!allOwnedByMerchant) return fail('该订单包含其他商家商品，请由平台拆单后处理')
      if (order.status !== 'PAID') return fail('当前状态无法发货')
      order.status = 'SHIPPED'
      order.updatedAt = new Date().toISOString()
      mockNotifyOrderShipped(order)
      return ok(true, '已发货')
    }
    if (next === 'CANCELLED') return fail('商家端不支持取消订单')
    if (next === 'COMPLETED') return fail('完成状态由用户确认收货触发')
    return fail('不支持的状态操作')
  },

  userGetOrders: async (userId) => {
    const data = state.orders
      .filter((o) => o.userId === Number(userId))
      .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
      .map(toOrderSummary)
    return ok(data)
  },
  userGetOrder: async (orderId, userId) => {
    const uid = Number(userId || 0)
    if (!uid) return fail('请先登录')
    const order = state.orders.find((o) => o.orderId === Number(orderId))
    if (!order) return fail('订单不存在', 404)
    if (!isOrderOwnedByUser(order, uid)) return fail('无权限查看该订单', 403)
    return ok({
      ...toOrderSummary(order),
      paidAt: order.paidAt || null,
      receiverName: order.receiverName != null ? String(order.receiverName) : '',
      receiverPhone: order.receiverPhone != null ? String(order.receiverPhone) : '',
      receiverRegion: order.receiverRegion != null ? String(order.receiverRegion) : '',
      receiverAddress: order.receiverAddress != null ? String(order.receiverAddress) : '',
      logisticsNo: order.logisticsNo != null ? String(order.logisticsNo) : '',
      items: order.items || [],
    })
  },

  userListAddresses: async (userId) => {
    const uid = Number(userId)
    if (!uid) return fail('请先登录')
    const list = mockAddressListForUser(uid).slice().sort((a, b) => {
      if (Number(b.isDefault) !== Number(a.isDefault)) return Number(b.isDefault) - Number(a.isDefault)
      return Number(b.addressId) - Number(a.addressId)
    })
    return ok(list.map((row) => ({ ...row })))
  },

  userCreateAddress: async (userId, data) => {
    const uid = Number(userId)
    if (!uid) return fail('请先登录')
    const receiverName = String(data?.receiverName ?? '').trim()
    const receiverDetail = String(data?.receiverDetail ?? '').trim()
    if (!receiverName || !receiverDetail) return fail('收货人与详细地址不能为空')
    const wantDefault = Number(data?.isDefault) === 1
    const list = mockAddressListForUser(uid)
    if (wantDefault) {
      for (const x of list) x.isDefault = 0
    }
    const row = {
      addressId: nextMockAddressId(),
      label: String(data?.label ?? '').trim(),
      receiverName,
      receiverPhone: String(data?.receiverPhone ?? '').trim(),
      receiverRegion: String(data?.receiverRegion ?? '').trim(),
      receiverDetail,
      isDefault: wantDefault || list.length === 0 ? 1 : 0,
    }
    list.push(row)
    return ok({ ...row })
  },

  userUpdateAddress: async (userId, addressId, data) => {
    const uid = Number(userId)
    const aid = Number(addressId)
    if (!uid || !aid) return fail('参数无效')
    const list = mockAddressListForUser(uid)
    const idx = list.findIndex((x) => Number(x.addressId) === aid)
    if (idx < 0) return fail('地址不存在', 404)
    const receiverName = String(data?.receiverName ?? '').trim()
    const receiverDetail = String(data?.receiverDetail ?? '').trim()
    if (!receiverName || !receiverDetail) return fail('收货人与详细地址不能为空')
    const wantDefault = Number(data?.isDefault) === 1
    if (wantDefault) {
      for (const x of list) x.isDefault = 0
    }
    list[idx] = {
      ...list[idx],
      label: String(data?.label ?? '').trim(),
      receiverName,
      receiverPhone: String(data?.receiverPhone ?? '').trim(),
      receiverRegion: String(data?.receiverRegion ?? '').trim(),
      receiverDetail,
      isDefault: wantDefault ? 1 : Number(list[idx].isDefault) === 1 ? 1 : 0,
    }
    return ok(true)
  },

  userDeleteAddress: async (userId, addressId) => {
    const uid = Number(userId)
    const aid = Number(addressId)
    const list = mockAddressListForUser(uid)
    const idx = list.findIndex((x) => Number(x.addressId) === aid)
    if (idx < 0) return fail('地址不存在', 404)
    const wasDef = Number(list[idx].isDefault) === 1
    list.splice(idx, 1)
    if (wasDef && list.length) {
      list[0].isDefault = 1
    }
    return ok(true)
  },

  userSetDefaultAddress: async (userId, addressId) => {
    const uid = Number(userId)
    const aid = Number(addressId)
    const list = mockAddressListForUser(uid)
    const row = list.find((x) => Number(x.addressId) === aid)
    if (!row) return fail('地址不存在', 404)
    for (const x of list) x.isDefault = 0
    row.isDefault = 1
    return ok(true)
  },

  userCreateOrderFromCart: async (data) => {
    const userId = Number(data?.userId)
    const merchantId =
      data?.merchantId != null && data?.merchantId !== '' ? Number(data.merchantId) : null
    if (!userId) return fail('请先登录')
    let cart = state.cart.filter((x) => x.userId === userId).map(toCartViewItem).filter(Boolean)
    if (merchantId != null && merchantId > 0) {
      cart = cart.filter((c) => Number(c.merchantId || 0) === merchantId)
    }
    if (!cart.length) {
      return fail(merchantId != null && merchantId > 0 ? '该商家在购物车中没有可结算商品' : '购物车为空')
    }
    const prepared = []
    for (const c of cart) {
      const p = state.products.find((sp) => Number(sp.productId) === Number(c.productId))
      if (!p) return fail('商品不存在', 404)
      if (!canSellProduct(p)) return fail('商品已下架')
      if (!hasEnoughStock(p, c.quantity)) return fail('库存不足')
      prepared.push({
        productId: c.productId,
        merchantId: Number(p?.merchantId || 1),
        title: c.title,
        imageUrl: String(p?.detail?.imageUrl || ''),
        price: Number(c.price),
        quantity: c.quantity,
      })
    }
    const mids = new Set(prepared.map((it) => Number(it.merchantId || 0)).filter((x) => x > 0))
    if (mids.size > 1 && !(merchantId != null && merchantId > 0)) {
      return fail('暂不支持跨店合并结算，请按商家分开下单')
    }
    const { items, payAmount } = calcOrderItems(prepared)
    const now = new Date().toISOString()
    const u = (state.users || []).find((x) => Number(x.userId) === userId)
    const snap = resolveMockReceiverFromOrderPayload(userId, data)
    if (!snap) return fail('收货地址无效')
    const aid = Number(data?.addressId || 0)
    if (!(aid > 0) && (!snap.receiverRegion || !snap.receiverAddress)) {
      return fail('请选择地址簿或填写配送地区与详细地址')
    }
    const receiverName =
      snap.receiverName ||
      (u?.nickname && String(u.nickname).trim() ? String(u.nickname).trim() : `用户${userId}`)
    const receiverPhone = snap.receiverPhone || String(u?.phone || '')
    const receiverRegion = snap.receiverRegion
    const receiverAddress = snap.receiverAddress
    const order = {
      orderId: nextId(state.orders, 'orderId'),
      orderNo: genOrderNo(),
      userId,
      payAmount,
      status: 'CREATED',
      stockDeducted: false,
      items,
      createdAt: now,
      updatedAt: now,
      receiverName,
      receiverPhone,
      receiverRegion,
      receiverAddress,
      logisticsNo: '',
      paidAt: null,
    }
    state.orders.push(order)
    const usedCartIds = new Set(cart.map((c) => c.cartId))
    state.cart = state.cart.filter((x) => !(x.userId === userId && usedCartIds.has(x.cartId)))
    mockNotifyOrderPendingPay(order)
    return ok({ orderId: order.orderId }, '下单成功')
  },
  userCreateOrderDirect: async (data) => {
    const userId = Number(data?.userId)
    const productId = Number(data?.productId)
    const quantity = Math.max(1, Number(data?.quantity) || 1)
    if (!userId) return fail('请先登录')
    const p = state.products.find((x) => x.productId === productId)
    if (!p) return fail('商品不存在', 404)
    if (!canSellProduct(p)) return fail('商品已下架')
    if (!hasEnoughStock(p, quantity)) return fail('库存不足')
    const { items, payAmount } = calcOrderItems([
      {
        productId,
        merchantId: Number(p.merchantId || 1),
        title: p.title,
        imageUrl: String(p.detail?.imageUrl || ''),
        price: Number(p.price),
        quantity,
      },
    ])
    const now = new Date().toISOString()
    const u = (state.users || []).find((x) => Number(x.userId) === userId)
    const snap = resolveMockReceiverFromOrderPayload(userId, data)
    if (!snap) return fail('收货地址无效')
    const aid = Number(data?.addressId || 0)
    if (!(aid > 0) && (!snap.receiverRegion || !snap.receiverAddress)) {
      return fail('请选择地址簿或填写配送地区与详细地址')
    }
    const receiverName =
      snap.receiverName ||
      (u?.nickname && String(u.nickname).trim() ? String(u.nickname).trim() : `用户${userId}`)
    const receiverPhone = snap.receiverPhone || String(u?.phone || '')
    const receiverRegion = snap.receiverRegion
    const receiverAddress = snap.receiverAddress
    const order = {
      orderId: nextId(state.orders, 'orderId'),
      orderNo: genOrderNo(),
      userId,
      payAmount,
      status: 'CREATED',
      stockDeducted: false,
      items,
      createdAt: now,
      updatedAt: now,
      receiverName,
      receiverPhone,
      receiverRegion,
      receiverAddress,
      logisticsNo: '',
      paidAt: null,
    }
    state.orders.push(order)
    mockNotifyOrderPendingPay(order)
    return ok({ orderId: order.orderId }, '下单成功')
  },
  userPayOrder: async (orderId, userId) => {
    const uid = Number(userId || 0)
    if (!uid) return fail('请先登录')
    const order = state.orders.find((o) => o.orderId === Number(orderId))
    if (!order) return fail('订单不存在', 404)
    if (!isOrderOwnedByUser(order, uid)) return fail('无权限操作该订单', 403)
    if (order.status !== 'CREATED') return fail('当前状态无法支付')
    if (!isOrderStockDeducted(order)) {
      const changed = await applyOrderStockChange(order, 'decrease')
      if (changed.code !== 200) return changed
    }
    order.status = 'PAID'
    order.stockDeducted = true
    order.paidAt = new Date().toISOString()
    order.updatedAt = new Date().toISOString()
    return ok(true, '支付成功')
  },
  userCancelOrder: async (orderId, userId) => {
    const uid = Number(userId || 0)
    if (!uid) return fail('请先登录')
    const order = state.orders.find((o) => o.orderId === Number(orderId))
    if (!order) return fail('订单不存在', 404)
    if (!isOrderOwnedByUser(order, uid)) return fail('无权限操作该订单', 403)
    if (!['CREATED', 'PAID'].includes(order.status)) return fail('当前状态无法取消')
    if (order.status === 'PAID' && isOrderStockDeducted(order)) {
      const changed = await applyOrderStockChange(order, 'increase')
      if (changed.code !== 200) return changed
      order.stockDeducted = false
    }
    order.status = 'CANCELLED'
    order.updatedAt = new Date().toISOString()
    return ok(true, '已取消')
  },
  userConfirmOrder: async (orderId, userId) => {
    const uid = Number(userId || 0)
    if (!uid) return fail('请先登录')
    const order = state.orders.find((o) => o.orderId === Number(orderId))
    if (!order) return fail('订单不存在', 404)
    if (!isOrderOwnedByUser(order, uid)) return fail('无权限操作该订单', 403)
    if (order.status !== 'SHIPPED') return fail('当前状态无法确认收货')
    order.status = 'COMPLETED'
    order.stockDeducted = true
    order.updatedAt = new Date().toISOString()
    return ok(true, '确认收货成功')
  },

  userSubscribeRestock: async (data) => {
    const uid = Number(data?.userId || 0)
    const pid = Number(data?.productId || 0)
    if (!uid) return fail('请先登录')
    if (!pid) return fail('商品ID无效', 400)
    const product = state.products.find((p) => Number(p.productId) === pid)
    if (!product) return fail('商品不存在', 404)
    const subs = loadSubs()
    const exists = subs.some((s) => Number(s.userId) === uid && Number(s.productId) === pid && Number(s.active) === 1)
    if (exists) return ok(true, '您已订阅该商品的到货提醒，补货上架后我们将在消息中心通知您')
    const now = new Date().toISOString()
    subs.unshift({
      subId: nextId(subs, 'subId'),
      userId: uid,
      productId: pid,
      active: 1,
      createdAt: now,
      updatedAt: now,
    })
    saveSubs(subs)
    pushNotice({
      receiverType: 'USER',
      receiverId: uid,
      title: '到货提醒已开启',
      content: `已为你关注商品「${String(product.title || '')}」，到货后将通知你。`,
      productId: pid,
      productTitle: product.title || '',
      stock: Number(product.stock || 0),
      reason: '订阅',
    })
    const m = merchantBaseInfo(product.merchantId)
    if (m.merchantId) {
      pushNotice({
        receiverType: 'MERCHANT',
        receiverId: m.merchantId,
        title: '用户催补货',
        content: `有用户关注商品「${String(product.title || '')}」到货提醒，请尽快补货（当前库存 ${Number(product.stock || 0)}）。`,
        productId: pid,
        productTitle: product.title || '',
        stock: Number(product.stock || 0),
        reason: '用户订阅',
      })
    }
    return ok(true, '到货提醒订阅成功。当前商品暂无库存，补货后我们将第一时间通过消息中心提醒您')
  },
  userGetNotifications: async (userId) => {
    const uid = Number(userId || 0)
    if (!uid) return fail('请先登录')
    const data = loadNotices().filter((n) => String(n.receiverType) === 'USER' && Number(n.receiverId) === uid)
    return ok(data)
  },
  userMarkNotificationRead: async (userId, noticeId) => {
    const uid = Number(userId || 0)
    if (!uid) return fail('请先登录')
    const nid = Number(noticeId || 0)
    const list = loadNotices()
    const next = list.map((n) => {
      if (Number(n.noticeId) === nid && String(n.receiverType) === 'USER' && Number(n.receiverId) === uid) return { ...n, readStatus: 1 }
      return n
    })
    saveNotices(next)
    return ok(true, '已处理')
  },
  merchantGetNotifications: async (merchantId) => {
    const mid = Number(merchantId || 0)
    if (!mid) return fail('商家ID无效', 400)
    const data = loadNotices().filter((n) => String(n.receiverType) === 'MERCHANT' && Number(n.receiverId) === mid)
    return ok(data)
  },
  merchantMarkNotificationRead: async (merchantId, noticeId) => {
    const mid = Number(merchantId || 0)
    if (!mid) return fail('商家ID无效', 400)
    const nid = Number(noticeId || 0)
    const list = loadNotices()
    const next = list.map((n) => {
      if (Number(n.noticeId) === nid && String(n.receiverType) === 'MERCHANT' && Number(n.receiverId) === mid) return { ...n, readStatus: 1 }
      return n
    })
    saveNotices(next)
    return ok(true, '已处理')
  },

  userGetMerchantSession: async (data) => {
    const uid = Number(data?.userId)
    const merchantId = Number(data?.merchantId)
    const orderId = data?.orderId ? Number(data.orderId) : null
    if (!uid) return fail('请先登录')
    if (!merchantId) return fail('商家信息不存在')
    let session = state.chatSessions.find(
      (s) =>
        s.userId === uid &&
        s.sessionType === 'USER_TO_MERCHANT' &&
        Number(s.merchantId) === merchantId &&
        s.status === 'OPEN'
    )
    if (!session) {
      const now = new Date().toISOString()
      session = {
        sessionId: nextId(state.chatSessions, 'sessionId'),
        userId: uid,
        agentAdminId: null,
        orderId,
        sessionType: 'USER_TO_MERCHANT',
        merchantId,
        status: 'OPEN',
        createdAt: now,
        updatedAt: now,
      }
      state.chatSessions.push(session)
    } else if (orderId && !session.orderId) {
      session.orderId = orderId
    }
    const merchant = state.merchants.find((m) => Number(m.merchantId) === merchantId)
    return ok({
      ...session,
      merchantName: merchant?.shopName || merchant?.username || `商家${merchantId}`,
    })
  },
  userGetAdminSession: async (data) => {
    const uid = Number(data?.userId)
    if (!uid) return fail('请先登录')
    let session = state.chatSessions.find(
      (s) =>
        Number(s.userId) === uid &&
        s.sessionType === 'USER_TO_ADMIN' &&
        s.status === 'OPEN',
    )
    if (!session) {
      const now = new Date().toISOString()
      session = {
        sessionId: nextId(state.chatSessions, 'sessionId'),
        userId: uid,
        merchantId: null,
        agentAdminId: null,
        orderId: null,
        sessionType: 'USER_TO_ADMIN',
        status: 'OPEN',
        createdAt: now,
        updatedAt: now,
      }
      state.chatSessions.push(session)
    }
    return ok({
      ...session,
      merchantName: '平台客服',
    })
  },
  merchantGetAdminSession: async (data) => {
    const mid = Number(data?.merchantId)
    if (!mid) return fail('请先登录商家账号')
    const merchant = state.merchants.find((m) => Number(m.merchantId) === mid)
    let session = state.chatSessions.find(
      (s) =>
        Number(s.merchantId) === mid &&
        s.sessionType === 'MERCHANT_TO_ADMIN' &&
        s.status === 'OPEN',
    )
    if (!session) {
      const now = new Date().toISOString()
      session = {
        sessionId: nextId(state.chatSessions, 'sessionId'),
        userId: null,
        merchantId: mid,
        agentAdminId: null,
        orderId: null,
        sessionType: 'MERCHANT_TO_ADMIN',
        status: 'OPEN',
        createdAt: now,
        updatedAt: now,
      }
      state.chatSessions.push(session)
    }
    const label =
      merchant?.shopName || merchant?.username || merchant?.nickname || `商家${mid}`
    return ok({
      ...session,
      merchantName: `平台管理员 · ${label}`,
    })
  },
  userChatUnreadBadge: async (userId) => {
    const uid = Number(userId || 0)
    if (!uid) return fail('请先登录')
    const merchantSessionIds = state.chatSessions
      .filter((s) => Number(s.userId) === uid && s.sessionType === 'USER_TO_MERCHANT')
      .map((s) => s.sessionId)
    const hasMerchantUnread = state.chatMessages.some(
      (m) =>
        merchantSessionIds.includes(m.sessionId) &&
        m.senderType === 'MERCHANT' &&
        Number(m.readStatus) !== 1,
    )
    const adminSessionIds = state.chatSessions
      .filter((s) => Number(s.userId) === uid && s.sessionType === 'USER_TO_ADMIN')
      .map((s) => s.sessionId)
    const hasAdminUnread = state.chatMessages.some(
      (m) =>
        adminSessionIds.includes(m.sessionId) &&
        m.senderType === 'ADMIN' &&
        Number(m.readStatus) !== 1,
    )
    return ok({
      hasUnread: hasMerchantUnread || hasAdminUnread,
      hasUnreadPeer: hasMerchantUnread,
      hasUnreadPlatform: hasAdminUnread,
    })
  },
  userChatUnreadMerchants: async (userId) => {
    const uid = Number(userId || 0)
    if (!uid) return fail('请先登录')
    const sessions = state.chatSessions.filter((s) => Number(s.userId) === uid && s.sessionType === 'USER_TO_MERCHANT')
    const sessionIds = sessions.map((s) => s.sessionId)
    const bySid = new Map(sessions.map((s) => [s.sessionId, Number(s.merchantId) || 0]))
    const merchantSet = new Set()
    for (const m of state.chatMessages) {
      if (!sessionIds.includes(m.sessionId)) continue
      if (m.senderType !== 'MERCHANT') continue
      if (Number(m.readStatus) === 1) continue
      const mid = bySid.get(m.sessionId)
      if (mid > 0) merchantSet.add(mid)
    }
    return ok({ merchantIds: [...merchantSet] })
  },
  merchantChatUnreadBadge: async (merchantId) => {
    const mid = Number(merchantId || 0)
    if (!mid) return fail('请先登录商家账号')
    const sessionIds = state.chatSessions
      .filter((s) => Number(s.merchantId) === mid && s.sessionType === 'USER_TO_MERCHANT')
      .map((s) => s.sessionId)
    const hasUserUnread = state.chatMessages.some(
      (m) =>
        sessionIds.includes(m.sessionId) &&
        m.senderType === 'USER' &&
        Number(m.readByMerchant) !== 1,
    )
    const adminSids = state.chatSessions
      .filter((s) => Number(s.merchantId) === mid && s.sessionType === 'MERCHANT_TO_ADMIN')
      .map((s) => s.sessionId)
    const hasAdminUnread = state.chatMessages.some(
      (m) =>
        adminSids.includes(m.sessionId) &&
        m.senderType === 'ADMIN' &&
        Number(m.readByMerchant) !== 1,
    )
    return ok({
      hasUnread: hasUserUnread || hasAdminUnread,
      hasUnreadPeer: hasUserUnread,
      hasUnreadPlatform: hasAdminUnread,
    })
  },
  userGetSupportMessages: async (sessionId, userId) => {
    const sid = Number(sessionId)
    const uid = Number(userId || 0)
    if (!uid) return fail('请先登录')
    const session = state.chatSessions.find((s) => s.sessionId === sid)
    if (!session || Number(session.userId) !== uid) return fail('会话不存在', 404)
    if (session.sessionType === 'USER_TO_ADMIN') {
      state.chatMessages.forEach((m) => {
        if (m.sessionId === sid && m.senderType === 'ADMIN') m.readStatus = 1
      })
    } else {
      state.chatMessages.forEach((m) => {
        if (m.sessionId === sid && m.senderType === 'MERCHANT') m.readStatus = 1
      })
    }
    const list = state.chatMessages
      .filter((m) => m.sessionId === sid)
      .sort((a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime())
    return ok(list)
  },
  userSendSupportMessage: async (data) => {
    const sessionId = Number(data?.sessionId)
    const userId = Number(data?.userId)
    const content = String(data?.content || '').trim()
    if (!sessionId) return fail('会话不存在')
    if (!userId) return fail('请先登录')
    if (!content) return fail('消息不能为空')
    const session = state.chatSessions.find((s) => s.sessionId === sessionId)
    if (!session) return fail('会话不存在')
    const now = new Date().toISOString()
    const msg = {
      messageId: nextId(state.chatMessages, 'messageId'),
      sessionId,
      senderType: 'USER',
      senderId: userId,
      content,
      attachmentUrl: null,
      readStatus: 1,
      readByMerchant: 0,
      isReadByAdmin: session.sessionType === 'USER_TO_ADMIN' ? 0 : 1,
      createdAt: now,
    }
    state.chatMessages.push(msg)
    session.updatedAt = now
    return ok(msg, '发送成功')
  },
  merchantGetSupportSessions: async (merchantId) => {
    const mid = Number(merchantId)
    if (!mid) return fail('请先登录商家账号')
    const unreadSidSet = new Set()
    for (const m of state.chatMessages) {
      if (m.senderType !== 'USER') continue
      if (Number(m.readByMerchant) === 1) continue
      const sess = state.chatSessions.find(
        (x) =>
          x.sessionId === m.sessionId &&
          x.sessionType === 'USER_TO_MERCHANT' &&
          Number(x.merchantId) === mid,
      )
      if (sess) unreadSidSet.add(m.sessionId)
    }
    const sessions = state.chatSessions
      .filter((s) => s.sessionType === 'USER_TO_MERCHANT' && Number(s.merchantId) === mid)
      .sort((a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime())
      .map((s) => {
        const user = state.users.find((u) => u.userId === Number(s.userId))
        let orderNo = ''
        let productTitle = '咨询未关联订单'
        let productImageUrl = ''
        let relatedLineCount = 0
        const oid = s.orderId != null && s.orderId !== '' ? Number(s.orderId) : 0
        if (oid > 0) {
          const order = state.orders.find(
            (o) => Number(o.orderId) === oid && Number(o.userId) === Number(s.userId),
          )
          if (order) {
            orderNo = String(order.orderNo || '')
            const lines = (order.items || []).filter((it) => Number(it.merchantId || 0) === mid)
            relatedLineCount = lines.length
            if (lines.length > 0) {
              const first = lines[0]
              productTitle = String(first.title || '').trim() || '商品'
              productImageUrl = String(first.imageUrl || '').trim()
            } else {
              productTitle = orderNo ? `订单 ${orderNo}` : '订单（无本店商品）'
            }
          } else {
            productTitle = '订单信息不可用'
          }
        }
        return {
          ...s,
          userNickname: user?.nickname || `用户${s.userId}`,
          orderNo,
          productTitle,
          productImageUrl,
          relatedLineCount,
          unreadFromUser: unreadSidSet.has(s.sessionId),
        }
      })
    return ok(sessions)
  },
  merchantGetSupportMessages: async (sessionId, merchantId) => {
    const sid = Number(sessionId)
    const mid = Number(merchantId || 0)
    if (!mid) return fail('请先登录商家账号')
    const session = state.chatSessions.find((s) => s.sessionId === sid)
    if (!session || Number(session.merchantId) !== mid) {
      return fail('会话不存在', 404)
    }
    if (session.sessionType !== 'USER_TO_MERCHANT' && session.sessionType !== 'MERCHANT_TO_ADMIN') {
      return fail('会话不存在', 404)
    }
    if (session.sessionType === 'USER_TO_MERCHANT') {
      state.chatMessages.forEach((m) => {
        if (m.sessionId === sid && m.senderType === 'USER') m.readByMerchant = 1
      })
    } else {
      state.chatMessages.forEach((m) => {
        if (m.sessionId === sid && m.senderType === 'ADMIN') m.readByMerchant = 1
      })
    }
    const list = state.chatMessages
      .filter((m) => m.sessionId === sid)
      .sort((a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime())
    return ok(list)
  },
  merchantSendSupportMessage: async (data) => {
    const sessionId = Number(data?.sessionId)
    const merchantId = Number(data?.merchantId)
    const content = String(data?.content || '').trim()
    if (!sessionId) return fail('会话不存在')
    if (!merchantId) return fail('请先登录商家账号')
    if (!content) return fail('消息不能为空')
    const session = state.chatSessions.find((s) => s.sessionId === sessionId)
    if (!session) return fail('会话不存在')
    if (
      session.sessionType !== 'USER_TO_MERCHANT' &&
      session.sessionType !== 'MERCHANT_TO_ADMIN'
    ) {
      return fail('会话不存在')
    }
    const now = new Date().toISOString()
    const msg = {
      messageId: nextId(state.chatMessages, 'messageId'),
      sessionId,
      senderType: 'MERCHANT',
      senderId: merchantId,
      content,
      attachmentUrl: null,
      readStatus: 0,
      readByMerchant: 1,
      isReadByAdmin: session.sessionType === 'MERCHANT_TO_ADMIN' ? 0 : 1,
      createdAt: now,
    }
    state.chatMessages.push(msg)
    session.updatedAt = now
    return ok(msg, '发送成功')
  },

  adminListSupportSessions: async () => {
    const support = state.chatSessions.filter(
      (s) => s.sessionType === 'USER_TO_ADMIN' || s.sessionType === 'MERCHANT_TO_ADMIN',
    )
    const rows = support.map((s) => {
      let counterpartyTitle = '—'
      let counterpartySub = ''
      if (s.sessionType === 'USER_TO_ADMIN') {
        const u = state.users.find((x) => Number(x.userId) === Number(s.userId))
        counterpartyTitle = u?.nickname || `用户${s.userId}`
        counterpartySub = '用户咨询平台'
      } else {
        const m = state.merchants.find((x) => Number(x.merchantId) === Number(s.merchantId))
        counterpartyTitle = m?.shopName || m?.username || `商家${s.merchantId}`
        counterpartySub = '商家咨询平台'
      }
      const unreadFromCounterparty = state.chatMessages.some(
        (msg) =>
          msg.sessionId === s.sessionId &&
          (msg.senderType === 'USER' || msg.senderType === 'MERCHANT') &&
          Number(msg.isReadByAdmin ?? 1) !== 1,
      )
      return {
        sessionId: s.sessionId,
        sessionType: s.sessionType,
        counterpartyTitle,
        counterpartySub,
        updatedAt: s.updatedAt,
        unreadFromCounterparty,
      }
    })
    rows.sort(
      (a, b) =>
        new Date(b.updatedAt || 0).getTime() - new Date(a.updatedAt || 0).getTime(),
    )
    return ok(rows)
  },
  adminGetSupportMessages: async (sessionId) => {
    const sid = Number(sessionId)
    const session = state.chatSessions.find((s) => s.sessionId === sid)
    if (
      !session ||
      (session.sessionType !== 'USER_TO_ADMIN' && session.sessionType !== 'MERCHANT_TO_ADMIN')
    ) {
      return fail('会话不存在', 404)
    }
    state.chatMessages.forEach((m) => {
      if (m.sessionId === sid && (m.senderType === 'USER' || m.senderType === 'MERCHANT')) {
        m.isReadByAdmin = 1
      }
    })
    const list = state.chatMessages
      .filter((m) => m.sessionId === sid)
      .sort((a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime())
    return ok(list)
  },
  adminSendSupportMessage: async (data) => {
    const sessionId = Number(data?.sessionId)
    const content = String(data?.content || '').trim()
    const adminId = 1
    if (!sessionId) return fail('会话无效')
    if (!content) return fail('消息不能为空')
    const session = state.chatSessions.find((s) => s.sessionId === sessionId)
    if (
      !session ||
      (session.sessionType !== 'USER_TO_ADMIN' && session.sessionType !== 'MERCHANT_TO_ADMIN')
    ) {
      return fail('会话不存在')
    }
    const now = new Date().toISOString()
    const msg = {
      messageId: nextId(state.chatMessages, 'messageId'),
      sessionId,
      senderType: 'ADMIN',
      senderId: adminId,
      content,
      attachmentUrl: null,
      readStatus: 0,
      readByMerchant: 0,
      isReadByAdmin: 1,
      createdAt: now,
    }
    state.chatMessages.push(msg)
    session.updatedAt = now
    return ok(null)
  },
  adminSupportUnreadBadge: async () => {
    const n = state.chatMessages.filter(
      (m) =>
        (m.senderType === 'USER' || m.senderType === 'MERCHANT') &&
        Number(m.isReadByAdmin ?? 1) !== 1 &&
        state.chatSessions.some(
          (s) =>
            s.sessionId === m.sessionId &&
            (s.sessionType === 'USER_TO_ADMIN' || s.sessionType === 'MERCHANT_TO_ADMIN'),
        ),
    ).length
    return ok({ unread: n })
  },

  userGetProfile: async (userId) => {
    const user = state.users.find((u) => u.userId === Number(userId))
    if (!user) return fail('用户不存在', 404)
    return ok({
      userId: user.userId,
      nickname: user.nickname,
      phone: user.phone,
      email: user.email,
      avatarUrl: user.avatarUrl,
      petPreference: String(user.petPreference || 'both'),
    })
  },
  userUpdateProfile: async (userId, data) => {
    const user = state.users.find((u) => u.userId === Number(userId))
    if (!user) return fail('用户不存在', 404)
    const nickname = String(data?.nickname || '').trim()
    const phone = String(data?.phone || '').trim()
    const email = String(data?.email || '').trim().toLowerCase()
    if (!nickname) return fail('昵称不能为空')
    if (!email) return fail('邮箱不能为空')
    if (state.users.some((u) => u.userId !== user.userId && String(u.email || '').trim().toLowerCase() === email)) {
      return fail('该邮箱已被使用')
    }
    user.nickname = nickname
    user.phone = phone
    user.email = email
    if (typeof data?.avatarUrl !== 'undefined') user.avatarUrl = String(data.avatarUrl || '')
    if (typeof data?.petPreference !== 'undefined') {
      const pref = String(data.petPreference || '').trim().toLowerCase()
      user.petPreference = ['cat', 'dog', 'both'].includes(pref) ? pref : 'both'
    }
    return ok(true, '保存成功')
  },
  userGetPetPreference: async (userId) => {
    const user = state.users.find((u) => u.userId === Number(userId))
    if (!user) return fail('用户不存在', 404)
    return ok({ petPreference: String(user.petPreference || 'both') })
  },
  userUpdatePetPreference: async (userId, data) => {
    const user = state.users.find((u) => u.userId === Number(userId))
    if (!user) return fail('用户不存在', 404)
    const pref = String(data?.petPreference || '').trim().toLowerCase()
    user.petPreference = ['cat', 'dog', 'both'].includes(pref) ? pref : 'both'
    return ok(true, '保存成功')
  },
  merchantGetProfile: async (merchantId) => {
    const merchant = state.merchants.find((m) => m.merchantId === Number(merchantId))
    if (!merchant) return fail('商家不存在', 404)
    const tw =
      merchant.salesTargetWeekly != null && merchant.salesTargetWeekly !== ''
        ? formatYuan(merchant.salesTargetWeekly)
        : ''
    return ok({
      merchantId: merchant.merchantId,
      username: merchant.username || '',
      shopName: merchant.shopName || '',
      contactName: merchant.contactName || '',
      phone: merchant.phone || '',
      email: merchant.email || '',
      avatarUrl: merchant.avatarUrl || '',
      salesTargetWeekly: tw,
    })
  },
  merchantUpdateProfile: async (merchantId, data) => {
    const merchant = state.merchants.find((m) => m.merchantId === Number(merchantId))
    if (!merchant) return fail('商家不存在', 404)
    const shopName = String(data?.shopName || '').trim()
    const contactName = String(data?.contactName || '').trim()
    const phone = String(data?.phone || '').trim()
    if (!shopName) return fail('店铺名称不能为空')
    if (!contactName) return fail('联系人不能为空')
    if (!phone) return fail('联系电话不能为空')
    const email = String(data?.email || '').trim()
    if (email && !isValidEmailFormat(email)) return fail('联系邮箱格式不正确')
    merchant.shopName = shopName
    merchant.contactName = contactName
    merchant.phone = phone
    merchant.email = email
    if (typeof data?.avatarUrl !== 'undefined') merchant.avatarUrl = String(data.avatarUrl || '')
    if (typeof data?.salesTargetWeekly !== 'undefined') {
      const raw = String(data.salesTargetWeekly ?? '').trim()
      if (!raw) {
        merchant.salesTargetWeekly = null
      } else {
        const n = Number(raw)
        if (!Number.isFinite(n) || n < 0) return fail('销售额目标须为非负数字', 400)
        merchant.salesTargetWeekly = n
      }
    }
    return ok(true, '保存成功')
  },
}

export const api = {
  getUserRecommendations: async (userId, topN = 10) =>
    USE_MOCK
      ? mockApi.getUserRecommendations(userId, topN)
      : getJson(`/api/recommendations/user/${encodeURIComponent(userId)}?topN=${encodeURIComponent(topN)}`),
  /** 上报浏览/点击等行为，供后端在线重排推荐（失败不影响页面）。 */
  postUserEvent: async (data) => (USE_MOCK ? mockApi.postUserEvent(data) : postJson('/api/user/events', data)),
  getProducts: async () => {
    if (USE_MOCK) return mockApi.getProducts()
    const now = Date.now()
    if (productsCache.data && now < Number(productsCache.expireAt || 0)) {
      return { code: 200, message: 'success', data: productsCache.data }
    }
    if (productsInFlight) return productsInFlight
    productsInFlight = getJson('/api/products')
    const res = await productsInFlight
    productsInFlight = null
    if (res.code === 200 && Array.isArray(res.data)) {
      productsCache = {
        expireAt: now + PRODUCTS_CACHE_TTL_MS,
        data: res.data,
      }
    }
    return res
  },
  getProduct: async (id) => (USE_MOCK ? mockApi.getProduct(id) : getJson(`/api/products/${id}`)),
  getProductsByCategory: async (categoryId) =>
    USE_MOCK ? mockApi.getProductsByCategory(categoryId) : getJson(`/api/products/category/${categoryId}`),
  getCategories: async () => (USE_MOCK ? mockApi.getCategories() : getJson('/api/categories')),
  getRootCategories: async () => (USE_MOCK ? mockApi.getRootCategories() : getJson('/api/categories/root')),
  getCategoryTree: async () => (USE_MOCK ? mockApi.getCategoryTree() : getJson('/api/categories/tree')),
  getCart: async (userId) => (USE_MOCK ? mockApi.getCart(userId) : getJson(`/api/cart/${userId}`)),
  addToCart: async (data) => (USE_MOCK ? mockApi.addToCart(data) : postJson('/api/cart/add', data)),
  updateCart: async (data) => (USE_MOCK ? mockApi.updateCart(data) : putJson('/api/cart/update', data)),
  removeFromCart: async (cartId) => (USE_MOCK ? mockApi.removeFromCart(cartId) : deleteJson(`/api/cart/${cartId}`)),
  getReviews: async (productId) => (USE_MOCK ? mockApi.getReviews(productId) : getJson(`/api/reviews/product/${productId}`)),
  addReview: async (data) => (USE_MOCK ? mockApi.addReview(data) : postJson('/api/reviews', data)),
  login: async (data) => (USE_MOCK ? mockApi.login(data) : postJson('/api/auth/login', data)),
  register: async (data) => (USE_MOCK ? mockApi.register(data) : postJson('/api/auth/register', data)),
  adminLogin: async (data) => (USE_MOCK ? mockApi.adminLogin(data) : postJson('/api/admin/auth/login', data)),
  merchantLogin: async (data) => (USE_MOCK ? mockApi.merchantLogin(data) : postJson('/api/merchant/auth/login', data)),
  merchantRegister: async (data) =>
    USE_MOCK ? mockApi.merchantRegister(data) : postJson('/api/merchant/auth/register', data),
  adminGetProducts: async () => (USE_MOCK ? mockApi.adminGetProducts() : getJson('/api/admin/products')),
  adminUpdateProduct: async (productId, data) =>
    USE_MOCK ? mockApi.adminUpdateProduct(productId, data) : putJson(`/api/admin/products/${productId}`, data),
  adminNotifyRestock: async (productId, data) =>
    USE_MOCK ? mockApi.adminNotifyRestock(productId, data) : postJson(`/api/admin/products/${productId}/notify-restock`, data),
  merchantGetProducts: async (merchantId) =>
    USE_MOCK ? mockApi.merchantGetProducts(merchantId) : getJson(`/api/merchant/products?merchantId=${encodeURIComponent(merchantId)}`),
  merchantGetProduct: async (merchantId, productId) =>
    USE_MOCK
      ? mockApi.merchantGetProduct(merchantId, productId)
      : getJson(`/api/merchant/products/${productId}?merchantId=${encodeURIComponent(merchantId)}`),
  merchantUpdateProduct: async (merchantId, productId, data) =>
    USE_MOCK
      ? mockApi.merchantUpdateProduct(merchantId, productId, data)
      : putJson(`/api/merchant/products/${productId}`, { ...(data || {}), merchantId }),
  merchantCreateProduct: async (data) =>
    USE_MOCK ? mockApi.merchantCreateProduct(data) : postJson('/api/merchant/products', data),
  merchantUpdateProductContent: async (productId, data) => {
    if (USE_MOCK) return mockApi.merchantUpdateProductContent(productId, data)
    const formData = new FormData()
    formData.append('description', String(data?.description ?? ''))
    formData.append('specJson', JSON.stringify(data?.specJson ?? {}))
    const urlStr = typeof data?.imageUrl !== 'undefined' ? String(data.imageUrl ?? '') : ''
    const hasFile = Boolean(data?.imageFile)
    // 有本地文件时不要再附带 data: 预览 URL，否则与 imageFile 重复、体积翻倍易 413
    if (hasFile) {
      formData.append('imageFile', data.imageFile)
      if (urlStr && !urlStr.startsWith('data:')) {
        formData.append('imageUrl', urlStr)
      }
    } else if (typeof data?.imageUrl !== 'undefined') {
      formData.append('imageUrl', urlStr)
    }
    return requestFormData(`/api/merchant/products/${productId}/content`, formData, { method: 'PUT' })
  },
  adminGetOrders: async (status = '') => {
    if (USE_MOCK) return mockApi.adminGetOrders(status)
    const q = status ? `?status=${encodeURIComponent(status)}` : ''
    return getJson(`/api/admin/orders${q}`)
  },
  adminGetOrderDetail: async (orderId) =>
    USE_MOCK ? mockApi.adminGetOrderDetail(orderId) : getJson(`/api/admin/orders/${orderId}`),
  adminListUsers: async () => (USE_MOCK ? mockApi.adminListUsers() : getJson('/api/admin/users')),
  adminUpdateUserStatus: async (userId, data) =>
    USE_MOCK ? mockApi.adminUpdateUserStatus(userId, data) : putJson(`/api/admin/users/${userId}/status`, data),
  adminResetUserPassword: async (userId, data) =>
    USE_MOCK ? mockApi.adminResetUserPassword(userId, data) : putJson(`/api/admin/users/${userId}/password`, data),
  adminResetUserPetPreference: async (userId) =>
    USE_MOCK
      ? mockApi.adminResetUserPetPreference(userId)
      : putJson(`/api/admin/users/${userId}/pet-preference/reset`, {}),
  adminResetAllUserPetPreference: async () =>
    USE_MOCK
      ? mockApi.adminResetAllUserPetPreference()
      : putJson('/api/admin/users/pet-preference/reset-all', {}),
  adminListMerchants: async () => (USE_MOCK ? mockApi.adminListMerchants() : getJson('/api/admin/merchants')),
  adminUpdateMerchantStatus: async (merchantId, data) =>
    USE_MOCK
      ? mockApi.adminUpdateMerchantStatus(merchantId, data)
      : putJson(`/api/admin/merchants/${merchantId}/status`, data),
  adminResetMerchantPassword: async (merchantId, data) =>
    USE_MOCK
      ? mockApi.adminResetMerchantPassword(merchantId, data)
      : putJson(`/api/admin/merchants/${merchantId}/password`, data),
  adminUpdateOrderStatus: async (orderId, data) =>
    USE_MOCK ? mockApi.adminUpdateOrderStatus(orderId, data) : putJson(`/api/admin/orders/${orderId}/status`, data),
  adminUrgeOrderShipment: async (orderId) =>
    USE_MOCK ? mockApi.adminUrgeOrderShipment(orderId) : postJson(`/api/admin/orders/${orderId}/urge-shipment`, {}),
  merchantGetOrders: async (merchantId, status = '') => {
    if (USE_MOCK) return mockApi.merchantGetOrders(merchantId, status)
    const q = status ? `&status=${encodeURIComponent(status)}` : ''
    return getJson(`/api/merchant/orders?merchantId=${encodeURIComponent(merchantId)}${q}`)
  },
  merchantGetOrderDetail: async (merchantId, orderId) =>
    USE_MOCK
      ? mockApi.merchantGetOrderDetail(merchantId, orderId)
      : getJson(
          `/api/merchant/orders/${encodeURIComponent(orderId)}?merchantId=${encodeURIComponent(merchantId)}`,
        ),
  merchantOrderTodoBadges: async (merchantId) =>
    USE_MOCK
      ? mockApi.merchantOrderTodoBadges(merchantId)
      : getJson(`/api/merchant/orders/todo-badges?merchantId=${encodeURIComponent(merchantId)}`),
  merchantUpdateOrderStatus: async (merchantId, orderId, status) =>
    USE_MOCK
      ? mockApi.merchantUpdateOrderStatus(merchantId, orderId, status)
      : putJson(`/api/merchant/orders/${orderId}/status`, { merchantId, status }),
  userGetOrders: async (userId) => (USE_MOCK ? mockApi.userGetOrders(userId) : getJson(`/api/orders/user/${userId}`)),
  userGetOrder: async (orderId, userId) =>
    USE_MOCK ? mockApi.userGetOrder(orderId, userId) : getJson(`/api/orders/${orderId}?userId=${encodeURIComponent(userId)}`),
  userCreateOrderFromCart: async (data) =>
    USE_MOCK ? mockApi.userCreateOrderFromCart(data) : postJson('/api/orders/create-from-cart', data),
  userCreateOrderDirect: async (data) =>
    USE_MOCK ? mockApi.userCreateOrderDirect(data) : postJson('/api/orders/create-direct', data),
  userPayOrder: async (orderId, userId) =>
    USE_MOCK ? mockApi.userPayOrder(orderId, userId) : postJson(`/api/orders/${orderId}/pay`, { userId }),
  userCancelOrder: async (orderId, userId) =>
    USE_MOCK ? mockApi.userCancelOrder(orderId, userId) : postJson(`/api/orders/${orderId}/cancel`, { userId }),
  userConfirmOrder: async (orderId, userId) =>
    USE_MOCK ? mockApi.userConfirmOrder(orderId, userId) : postJson(`/api/orders/${orderId}/confirm`, { userId }),
  userSubscribeRestock: async (data) =>
    USE_MOCK ? mockApi.userSubscribeRestock(data) : postJson('/api/notifications/restock-subscribe', data),
  userGetNotifications: async (userId) =>
    USE_MOCK ? mockApi.userGetNotifications(userId) : getJson(`/api/notifications/user/${userId}`),
  userMarkNotificationRead: async (userId, noticeId) =>
    USE_MOCK ? mockApi.userMarkNotificationRead(userId, noticeId) : putJson(`/api/notifications/${noticeId}/read`, { userId }),
  merchantGetNotifications: async (merchantId) =>
    USE_MOCK
      ? mockApi.merchantGetNotifications(merchantId)
      : getJson(`/api/merchant/notifications?merchantId=${encodeURIComponent(merchantId)}`),
  merchantMarkNotificationRead: async (merchantId, noticeId) =>
    USE_MOCK
      ? mockApi.merchantMarkNotificationRead(merchantId, noticeId)
      : putJson(`/api/merchant/notifications/${noticeId}/read`, { merchantId }),
  userGetMerchantSession: async (data) =>
    USE_MOCK ? mockApi.userGetMerchantSession(data) : postJson('/api/chat/session/merchant', data),
  userChatUnreadBadge: async (userId) =>
    USE_MOCK
      ? mockApi.userChatUnreadBadge(userId)
      : getJson(`/api/chat/unread-badge?userId=${encodeURIComponent(userId)}`),
  userChatUnreadMerchants: async (userId) =>
    USE_MOCK
      ? mockApi.userChatUnreadMerchants(userId)
      : getJson(`/api/chat/unread-merchants?userId=${encodeURIComponent(userId)}`),
  merchantChatUnreadBadge: async (merchantId) =>
    USE_MOCK
      ? mockApi.merchantChatUnreadBadge(merchantId)
      : getJson(`/api/merchant/chat/unread-badge?merchantId=${encodeURIComponent(merchantId)}`),
  userGetSupportMessages: async (sessionId, userId) =>
    USE_MOCK
      ? mockApi.userGetSupportMessages(sessionId, userId)
      : getJson(`/api/chat/session/${sessionId}/messages?userId=${encodeURIComponent(userId)}`),
  userSendSupportMessage: async (data) => (USE_MOCK ? mockApi.userSendSupportMessage(data) : postJson('/api/chat/messages', data)),
  merchantGetSupportSessions: async (merchantId) =>
    USE_MOCK ? mockApi.merchantGetSupportSessions(merchantId) : getJson(`/api/merchant/chat/sessions/${merchantId}`),
  merchantGetSupportMessages: async (sessionId, merchantId) =>
    USE_MOCK
      ? mockApi.merchantGetSupportMessages(sessionId, merchantId)
      : getJson(
          `/api/merchant/chat/session/${sessionId}/messages?merchantId=${encodeURIComponent(merchantId)}`,
        ),
  merchantSendSupportMessage: async (data) =>
    USE_MOCK ? mockApi.merchantSendSupportMessage(data) : postJson('/api/merchant/chat/messages', data),
  userGetAdminSession: async (data) =>
    USE_MOCK ? mockApi.userGetAdminSession(data) : postJson('/api/chat/session/admin', data),
  merchantGetAdminSession: async (data) =>
    USE_MOCK ? mockApi.merchantGetAdminSession(data) : postJson('/api/merchant/chat/session/admin', data),
  adminListSupportSessions: async () =>
    USE_MOCK ? mockApi.adminListSupportSessions() : getJson('/api/admin/support/sessions'),
  adminGetSupportMessages: async (sessionId) =>
    USE_MOCK
      ? mockApi.adminGetSupportMessages(sessionId)
      : getJson(`/api/admin/support/sessions/${encodeURIComponent(sessionId)}/messages`),
  adminSendSupportMessage: async (data) =>
    USE_MOCK
      ? mockApi.adminSendSupportMessage(data)
      : postJson('/api/admin/support/messages', { sessionId: data?.sessionId, content: data?.content }),
  adminSupportUnreadBadge: async () =>
    USE_MOCK ? mockApi.adminSupportUnreadBadge() : getJson('/api/admin/support/unread-badge'),
  userGetProfile: async (userId) => (USE_MOCK ? mockApi.userGetProfile(userId) : getJson(`/api/users/${userId}/profile`)),
  userUpdateProfile: async (userId, data) =>
    USE_MOCK ? mockApi.userUpdateProfile(userId, data) : putJson(`/api/users/${userId}/profile`, data),
  userGetPetPreference: async (userId) =>
    USE_MOCK
      ? mockApi.userGetPetPreference(userId)
      : getJson(`/api/users/${userId}/pet-preference`),
  userUpdatePetPreference: async (userId, data) =>
    USE_MOCK
      ? mockApi.userUpdatePetPreference(userId, data)
      : putJson(`/api/users/${userId}/pet-preference`, data),
  merchantGetProfile: async (merchantId) =>
    USE_MOCK ? mockApi.merchantGetProfile(merchantId) : getJson(`/api/merchant/profile/${merchantId}`),
  merchantUpdateProfile: async (merchantId, data) =>
    USE_MOCK ? mockApi.merchantUpdateProfile(merchantId, data) : putJson(`/api/merchant/profile/${merchantId}`, data),

  userListAddresses: async (userId) =>
    USE_MOCK ? mockApi.userListAddresses(userId) : getJson(`/api/users/${encodeURIComponent(userId)}/addresses`),
  userCreateAddress: async (userId, data) =>
    USE_MOCK ? mockApi.userCreateAddress(userId, data) : postJson(`/api/users/${encodeURIComponent(userId)}/addresses`, data),
  userUpdateAddress: async (userId, addressId, data) =>
    USE_MOCK
      ? mockApi.userUpdateAddress(userId, addressId, data)
      : putJson(`/api/users/${encodeURIComponent(userId)}/addresses/${encodeURIComponent(addressId)}`, data),
  userDeleteAddress: async (userId, addressId) =>
    USE_MOCK
      ? mockApi.userDeleteAddress(userId, addressId)
      : deleteJson(`/api/users/${encodeURIComponent(userId)}/addresses/${encodeURIComponent(addressId)}`),
  userSetDefaultAddress: async (userId, addressId) =>
    USE_MOCK
      ? mockApi.userSetDefaultAddress(userId, addressId)
      : putJson(`/api/users/${encodeURIComponent(userId)}/addresses/${encodeURIComponent(addressId)}/default`, {}),
  userGetFrequentProducts: async (userId, limit = 10) =>
    USE_MOCK
      ? Promise.resolve({ code: 200, data: [] })
      : getJson(`/api/users/${encodeURIComponent(userId)}/frequent-products?limit=${encodeURIComponent(limit)}`),
  /** 主动追问推荐：首次 body 传 {}；后续传 sessionId + userMessage */
  proactiveRecommendTurn: async (userId, body) =>
    USE_MOCK
      ? Promise.resolve({ code: 200, data: { sessionId: null, assistantMessage: '', candidateProducts: [], done: false } })
      : postJson(`/api/recommendations/proactive/user/${encodeURIComponent(userId)}/turn`, body ?? {}),
  /** 恢复最近一次多轮导购会话 */
  proactiveLatestSession: async (userId) =>
    USE_MOCK
      ? Promise.resolve({ code: 200, data: { sessionId: null, messages: [], candidateProducts: [] } })
      : getJson(`/api/recommendations/proactive/user/${encodeURIComponent(userId)}/latest-session`),
  proactiveSessionSummaries: async (userId, limit = 5, includeSessionId) => {
    if (USE_MOCK) return Promise.resolve({ code: 200, data: [] })
    let url = `/api/recommendations/proactive/user/${encodeURIComponent(userId)}/sessions?limit=${encodeURIComponent(limit)}`
    if (includeSessionId != null && Number(includeSessionId) > 0) {
      url += `&includeSessionId=${encodeURIComponent(includeSessionId)}`
    }
    return getJson(url)
  },
  proactiveSessionRestoreById: async (userId, sessionId) =>
    USE_MOCK
      ? Promise.resolve({ code: 200, data: { sessionId: null, messages: [], candidateProducts: [] } })
      : getJson(
          `/api/recommendations/proactive/user/${encodeURIComponent(userId)}/session/${encodeURIComponent(sessionId)}`
        ),
  /** 口语问荐：AI 解析槽位 + 站内推荐模型排序后过滤 */
  nlRecommendQuery: async (userId, body) =>
    USE_MOCK
      ? Promise.resolve({
          code: 200,
          data: {
            interpretation: {},
            candidateProducts: [],
            replySource: 'HEURISTIC',
            rankingBackend: 'OFFLINE_TABLE',
          },
        })
      : postJson(`/api/recommendations/nl/user/${encodeURIComponent(userId)}/query`, body ?? {}),
  /** 智能导购：评论 BERT 分析 + XGB 融合重排（依赖后端 BERT 服务与库内评论） */
  aiGuideCommentRerank: async (userId, body) =>
    USE_MOCK
      ? Promise.resolve({ code: 200, data: [] })
      : postJson(`/api/recommendations/ai-guide/user/${encodeURIComponent(userId)}/comment-rerank`, body ?? {}),

  adminAuditLogs: async (page = 1, pageSize = 20) =>
    USE_MOCK
      ? Promise.resolve({ code: 200, data: { total: 0, page: 1, pageSize: 20, records: [] } })
      : getJson(`/api/admin/audit-logs?page=${encodeURIComponent(page)}&pageSize=${encodeURIComponent(pageSize)}`),
}
