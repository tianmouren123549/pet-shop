import { mockProducts, mockReviews, mockCategories, mockCart } from '../mock/data.js'

const USE_MOCK = true
const BASE_URL = 'http://localhost:8080'

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
      phone: 'user123',
      password: '123456',
      email: null,
      avatarUrl: '',
      status: 1,
    },
  ],
  admins: [{ adminId: 1, username: 'admin123', password: '123456', status: 1, role: 'ADMIN' }],
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
      sessionId: 1,
      userId: 1,
      agentAdminId: null,
      orderId: null,
      sessionType: 'USER_TO_ADMIN',
      merchantId: null,
      status: 'OPEN',
      createdAt: new Date(Date.now() - 1000 * 60 * 20).toISOString(),
      updatedAt: new Date(Date.now() - 1000 * 60 * 2).toISOString(),
    },
    {
      sessionId: 2,
      userId: 1,
      agentAdminId: null,
      orderId: null,
      sessionType: 'USER_TO_MERCHANT',
      merchantId: 1,
      status: 'OPEN',
      createdAt: new Date(Date.now() - 1000 * 60 * 30).toISOString(),
      updatedAt: new Date(Date.now() - 1000 * 60 * 5).toISOString(),
    },
  ],
  chatMessages: [
    {
      messageId: 1,
      sessionId: 1,
      senderType: 'ADMIN',
      senderId: 1,
      content: '您好，这里是客服，请问有什么可以帮您？',
      attachmentUrl: null,
      readStatus: 1,
      createdAt: new Date(Date.now() - 1000 * 60 * 18).toISOString(),
    },
    {
      messageId: 2,
      sessionId: 2,
      senderType: 'USER',
      senderId: 1,
      content: '请问这个狗粮什么时候补货？',
      attachmentUrl: null,
      readStatus: 1,
      createdAt: new Date(Date.now() - 1000 * 60 * 12).toISOString(),
    },
    {
      messageId: 3,
      sessionId: 2,
      senderType: 'MERCHANT',
      senderId: 1,
      content: '预计明天上午补货，您可以先收藏商品。',
      attachmentUrl: null,
      readStatus: 1,
      createdAt: new Date(Date.now() - 1000 * 60 * 11).toISOString(),
    },
  ],
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

async function requestJson(url, options = {}) {
  try {
    const res = await fetch(BASE_URL + url, {
      headers: { 'Content-Type': 'application/json' },
      ...options,
    })
    const data = await res.json()
    if (typeof data?.code === 'number') return data
    return { code: res.ok ? 200 : res.status, message: data?.message || '请求失败', data: data?.data ?? null }
  } catch {
    return { code: 500, message: '网络异常，请检查后端服务是否启动', data: null }
  }
}

async function requestFormData(url, formData, options = {}) {
  try {
    const res = await fetch(BASE_URL + url, { ...options, body: formData })
    const data = await res.json().catch(() => null)
    if (typeof data?.code === 'number') return data
    return { code: res.ok ? 200 : res.status, message: data?.message || '请求失败', data: data?.data ?? null }
  } catch {
    return { code: 500, message: '网络异常，请检查后端服务是否启动', data: null }
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

function findCategoryChildrenIds(categoryId) {
  const hit = state.categories.find((c) => c.categoryId === Number(categoryId))
  if (!hit) return [Number(categoryId)]
  if (hit.parentId !== 0) return [hit.categoryId]
  return state.categories.filter((c) => c.path.startsWith(`${hit.categoryId}/`)).map((c) => c.categoryId)
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

function toCartViewItem(item) {
  const product = state.products.find((p) => p.productId === item.productId)
  if (!product) return null
  const price = Number(product.price)
  return {
    cartId: item.cartId,
    userId: item.userId,
    productId: item.productId,
    title: product.title,
    imageUrl: String(product.detail?.imageUrl || ''),
    price: price.toFixed(2),
    quantity: item.quantity,
    subtotal: (price * item.quantity).toFixed(2),
  }
}

function toOrderSummary(o) {
  return {
    orderId: o.orderId,
    orderNo: o.orderNo,
    userId: o.userId,
    payAmount: Number(o.payAmount).toFixed(2),
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
      if (!hasEnoughStock(p, qty)) return fail(`商品「${String(p.title || '')}」库存不足`)
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
    const user = state.users.find((u) => u.phone === String(data?.phone || '').trim())
    if (!user || user.password !== String(data?.password || '')) return fail('用户名或密码错误')
    return ok({ userId: user.userId, nickname: user.nickname }, '登录成功')
  },
  register: async (data) => {
    const phone = String(data?.phone || '').trim()
    const nickname = String(data?.nickname || '').trim()
    const password = String(data?.password || '')
    const email = String(data?.email || '').trim()
    if (!phone) return fail('手机号不能为空')
    if (password.length < 6) return fail('密码至少 6 位')
    if (state.users.some((u) => u.phone === phone)) return fail('该手机号已注册')
    if (email && state.users.some((u) => u.email && u.email === email)) return fail('该邮箱已被注册')
    const user = {
      userId: nextId(state.users, 'userId'),
      nickname: nickname || '用户',
      phone,
      password,
      email: email || null,
      avatarUrl: '',
      status: 1,
    }
    state.users.push(user)
    return ok({ userId: user.userId, nickname: user.nickname }, '注册成功')
  },
  adminLogin: async (data) => {
    const admin = state.admins.find((a) => a.username === String(data?.username || '').trim())
    if (!admin || admin.password !== String(data?.password || '')) return fail('账号或密码错误')
    return ok({ adminId: admin.adminId, username: admin.username, role: admin.role || 'ADMIN' }, '登录成功')
  },
  merchantLogin: async (data) => {
    const merchant = state.merchants.find((m) => m.username === String(data?.username || '').trim())
    if (!merchant || merchant.password !== String(data?.password || '')) return fail('账号或密码错误')
    return ok({ adminId: merchant.merchantId, username: merchant.username, role: merchant.role || 'MERCHANT' }, '登录成功')
  },

  adminGetProducts: async () => {
    const data = (state.products || []).map((p) => {
      const m = merchantBaseInfo(p.merchantId)
      return { ...p, merchantId: m.merchantId, merchantShopName: m.shopName, merchantContactName: m.contactName }
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
    return ok(true, '内容更新成功')
  },

  adminGetOrders: async (status = '') => {
    const data = status ? state.orders.filter((o) => o.status === status) : state.orders
    return ok(data.map(toOrderSummary))
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

  merchantGetOrders: async (merchantId, status = '') => {
    const mid = Number(merchantId || 0)
    if (!mid) return fail('商家ID无效', 400)
    const data = (status ? state.orders.filter((o) => o.status === status) : state.orders)
      .filter((o) => Array.isArray(o.items) && o.items.some((it) => Number(it.merchantId || 0) === mid))
      .map(toOrderSummary)
    return ok(data)
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
      items: order.items || [],
    })
  },
  userCreateOrderFromCart: async (data) => {
    const userId = Number(data?.userId)
    if (!userId) return fail('请先登录')
    const cart = state.cart.filter((x) => x.userId === userId).map(toCartViewItem).filter(Boolean)
    if (!cart.length) return fail('购物车为空')
    const prepared = []
    for (const c of cart) {
      const p = state.products.find((sp) => Number(sp.productId) === Number(c.productId))
      if (!p) return fail(`商品「${String(c.title || '')}」不存在`, 404)
      if (!canSellProduct(p)) return fail(`商品「${String(p.title || '')}」已下架`)
      if (!hasEnoughStock(p, c.quantity)) return fail(`商品「${String(p.title || '')}」库存不足`)
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
    if (mids.size > 1) return fail('暂不支持跨店合并结算，请按商家分开下单')
    const { items, payAmount } = calcOrderItems(prepared)
    const now = new Date().toISOString()
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
    }
    state.orders.push(order)
    state.cart = state.cart.filter((x) => x.userId !== userId)
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
    }
    state.orders.push(order)
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
    if (exists) return ok(true, '已关注过该商品')
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
    return ok(true, '已开启到货提醒')
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

  userGetSupportSession: async (userId) => {
    const uid = Number(userId)
    if (!uid) return fail('请先登录')
    let session = state.chatSessions.find((s) => s.userId === uid && s.status === 'OPEN' && s.sessionType === 'USER_TO_ADMIN')
    if (!session) {
      const now = new Date().toISOString()
      session = {
        sessionId: nextId(state.chatSessions, 'sessionId'),
        userId: uid,
        agentAdminId: null,
        orderId: null,
        sessionType: 'USER_TO_ADMIN',
        merchantId: null,
        status: 'OPEN',
        createdAt: now,
        updatedAt: now,
      }
      state.chatSessions.push(session)
    }
    return ok(session)
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
  userGetSupportMessages: async (sessionId) => {
    const sid = Number(sessionId)
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
      readStatus: 0,
      createdAt: now,
    }
    state.chatMessages.push(msg)
    session.updatedAt = now
    if (session.sessionType === 'USER_TO_ADMIN') {
      const autoReply = {
        messageId: nextId(state.chatMessages, 'messageId'),
        sessionId,
        senderType: 'ADMIN',
        senderId: 1,
        content: '已收到您的消息，我们会尽快处理。',
        attachmentUrl: null,
        readStatus: 0,
        createdAt: new Date(Date.now() + 300).toISOString(),
      }
      state.chatMessages.push(autoReply)
      session.updatedAt = autoReply.createdAt
    }
    return ok(msg, '发送成功')
  },
  merchantGetSupportSessions: async (merchantId) => {
    const mid = Number(merchantId)
    if (!mid) return fail('请先登录商家账号')
    const sessions = state.chatSessions
      .filter((s) => s.sessionType === 'USER_TO_MERCHANT' && Number(s.merchantId) === mid)
      .sort((a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime())
      .map((s) => {
        const user = state.users.find((u) => u.userId === Number(s.userId))
        return {
          ...s,
          userNickname: user?.nickname || `用户${s.userId}`,
        }
      })
    return ok(sessions)
  },
  merchantGetSupportMessages: async (sessionId) => {
    const sid = Number(sessionId)
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
    const now = new Date().toISOString()
    const msg = {
      messageId: nextId(state.chatMessages, 'messageId'),
      sessionId,
      senderType: 'MERCHANT',
      senderId: merchantId,
      content,
      attachmentUrl: null,
      readStatus: 0,
      createdAt: now,
    }
    state.chatMessages.push(msg)
    session.updatedAt = now
    return ok(msg, '发送成功')
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
    })
  },
  userUpdateProfile: async (userId, data) => {
    const user = state.users.find((u) => u.userId === Number(userId))
    if (!user) return fail('用户不存在', 404)
    const nickname = String(data?.nickname || '').trim()
    const phone = String(data?.phone || '').trim()
    const email = String(data?.email || '').trim()
    if (!nickname) return fail('昵称不能为空')
    if (!phone) return fail('手机号不能为空')
    user.nickname = nickname
    user.phone = phone
    user.email = email || null
    if (typeof data?.avatarUrl !== 'undefined') user.avatarUrl = String(data.avatarUrl || '')
    return ok(true, '保存成功')
  },
  merchantGetProfile: async (merchantId) => {
    const merchant = state.merchants.find((m) => m.merchantId === Number(merchantId))
    if (!merchant) return fail('商家不存在', 404)
    return ok({
      merchantId: merchant.merchantId,
      username: merchant.username || '',
      shopName: merchant.shopName || '',
      contactName: merchant.contactName || '',
      phone: merchant.phone || '',
      email: merchant.email || '',
      avatarUrl: merchant.avatarUrl || '',
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
    merchant.shopName = shopName
    merchant.contactName = contactName
    merchant.phone = phone
    merchant.email = String(data?.email || '').trim()
    if (typeof data?.avatarUrl !== 'undefined') merchant.avatarUrl = String(data.avatarUrl || '')
    return ok(true, '保存成功')
  },
}

export const api = {
  getProducts: async () => (USE_MOCK ? mockApi.getProducts() : getJson('/api/products')),
  getProduct: async (id) => (USE_MOCK ? mockApi.getProduct(id) : getJson(`/api/products/${id}`)),
  getProductsByCategory: async (categoryId) =>
    USE_MOCK ? mockApi.getProductsByCategory(categoryId) : getJson(`/api/products/category/${categoryId}`),
  getCategories: async () => (USE_MOCK ? mockApi.getCategories() : getJson('/api/categories')),
  getRootCategories: async () => (USE_MOCK ? mockApi.getRootCategories() : getJson('/api/categories/root')),
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
  merchantCreateProduct: async (data) => (USE_MOCK ? mockApi.merchantCreateProduct(data) : fail('当前后端尚未实现商品创建接口')),
  merchantUpdateProductContent: async (productId, data) => {
    if (USE_MOCK) return mockApi.merchantUpdateProductContent(productId, data)
    const formData = new FormData()
    formData.append('description', String(data?.description ?? ''))
    formData.append('specJson', JSON.stringify(data?.specJson ?? {}))
    if (data?.imageFile) formData.append('imageFile', data.imageFile)
    return requestFormData(`/api/merchant/products/${productId}/content`, formData, { method: 'PUT' })
  },
  adminGetOrders: async (status = '') => {
    if (USE_MOCK) return mockApi.adminGetOrders(status)
    const q = status ? `?status=${encodeURIComponent(status)}` : ''
    return getJson(`/api/admin/orders${q}`)
  },
  adminUpdateOrderStatus: async (orderId, data) =>
    USE_MOCK ? mockApi.adminUpdateOrderStatus(orderId, data) : putJson(`/api/admin/orders/${orderId}/status`, data),
  merchantGetOrders: async (merchantId, status = '') => {
    if (USE_MOCK) return mockApi.merchantGetOrders(merchantId, status)
    const q = status ? `&status=${encodeURIComponent(status)}` : ''
    return getJson(`/api/merchant/orders?merchantId=${encodeURIComponent(merchantId)}${q}`)
  },
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
  userGetSupportSession: async (userId) =>
    USE_MOCK ? mockApi.userGetSupportSession(userId) : getJson(`/api/chat/session/user/${userId}`),
  userGetMerchantSession: async (data) =>
    USE_MOCK ? mockApi.userGetMerchantSession(data) : postJson('/api/chat/session/merchant', data),
  userGetSupportMessages: async (sessionId) =>
    USE_MOCK ? mockApi.userGetSupportMessages(sessionId) : getJson(`/api/chat/session/${sessionId}/messages`),
  userSendSupportMessage: async (data) => (USE_MOCK ? mockApi.userSendSupportMessage(data) : postJson('/api/chat/messages', data)),
  merchantGetSupportSessions: async (merchantId) =>
    USE_MOCK ? mockApi.merchantGetSupportSessions(merchantId) : getJson(`/api/merchant/chat/sessions/${merchantId}`),
  merchantGetSupportMessages: async (sessionId) =>
    USE_MOCK ? mockApi.merchantGetSupportMessages(sessionId) : getJson(`/api/merchant/chat/session/${sessionId}/messages`),
  merchantSendSupportMessage: async (data) =>
    USE_MOCK ? mockApi.merchantSendSupportMessage(data) : postJson('/api/merchant/chat/messages', data),
  userGetProfile: async (userId) => (USE_MOCK ? mockApi.userGetProfile(userId) : getJson(`/api/users/${userId}/profile`)),
  userUpdateProfile: async (userId, data) =>
    USE_MOCK ? mockApi.userUpdateProfile(userId, data) : putJson(`/api/users/${userId}/profile`, data),
  merchantGetProfile: async (merchantId) =>
    USE_MOCK ? mockApi.merchantGetProfile(merchantId) : getJson(`/api/merchant/profile/${merchantId}`),
  merchantUpdateProfile: async (merchantId, data) =>
    USE_MOCK ? mockApi.merchantUpdateProfile(merchantId, data) : putJson(`/api/merchant/profile/${merchantId}`, data),
}
