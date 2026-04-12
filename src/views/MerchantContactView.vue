<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../utils/request'
import { showAppMessage } from '../utils/appMessage'
import PaginationBar from '../components/PaginationBar.vue'

const router = useRouter()
const route = useRoute()
const userId = ref(Number(localStorage.getItem('userId') || 0))
const loading = ref(false)
const errorMsg = ref('')
const orders = ref([])
const merchantCards = ref([])
const page = ref(1)
const pageSize = ref(9)

/**
 * @param {string|undefined|null} t
 * @returns {number}
 */
function parseOrderTimeMs(t) {
  const ms = new Date(t || 0).getTime()
  return Number.isFinite(ms) ? ms : 0
}

/**
 * 从订单详情列表构建联系商家卡片：每个商家保留「最近一笔」关联订单，并带上该订单中该店的首个商品图文。
 * @param {Array<Record<string, unknown>>} details
 * @returns {Array<{
 *   merchantId: number,
 *   merchantName: string,
 *   lastOrderId: unknown,
 *   lastOrderNo: unknown,
 *   lastTime: unknown,
 *   productTitle: string,
 *   productImageUrl: string,
 *   merchantLineCount: number
 * }>}
 */
function buildMerchantCardsFromOrderDetails(details) {
  /** @type {Map<number, Record<string, unknown>>} */
  const byMid = new Map()
  for (const od of details || []) {
    const items = Array.isArray(od.items) ? od.items : []
    const lastTime = od.updatedAt || od.createdAt
    const tMs = parseOrderTimeMs(lastTime == null ? '' : String(lastTime))
    /** @type {Map<number, Array<Record<string, unknown>>>} */
    const linesByMid = new Map()
    for (const it of items) {
      const mid = Number(it.merchantId || 0)
      if (mid <= 0) continue
      if (!linesByMid.has(mid)) linesByMid.set(mid, [])
      linesByMid.get(mid).push(it)
    }
    for (const [mid, lines] of linesByMid) {
      const prev = byMid.get(mid)
      if (prev && parseOrderTimeMs(prev.lastTime == null ? '' : String(prev.lastTime)) >= tMs) continue
      const primary = lines[0]
      const title = String(primary.title || '').trim() || '商品'
      const imageUrl = String(primary.imageUrl || '').trim()
      byMid.set(mid, {
        merchantId: mid,
        merchantName: `商家${mid}`,
        lastOrderId: od.orderId,
        lastOrderNo: od.orderNo,
        lastTime,
        productTitle: title,
        productImageUrl: imageUrl,
        merchantLineCount: lines.length,
      })
    }
  }
  return Array.from(byMid.values()).sort(
    (a, b) => parseOrderTimeMs(b.lastTime == null ? '' : String(b.lastTime)) - parseOrderTimeMs(a.lastTime == null ? '' : String(a.lastTime)),
  )
}

/**
 * 批量拉取店铺资料，用于卡片标题展示店铺名（失败时保留占位「商家{id}」）。
 * @param {Array<Record<string, unknown>>} cards
 */
async function enrichMerchantShopNames(cards) {
  const ids = [...new Set(cards.map((c) => Number(c.merchantId || 0)).filter((x) => x > 0))]
  const pairs = await Promise.all(
    ids.map(async (mid) => {
      const pr = await api.merchantGetProfile(mid)
      if (pr.code !== 200 || !pr.data) return [mid, '']
      const d = pr.data
      const name = String(d.shopName || '').trim() || String(d.username || '').trim()
      return [mid, name]
    }),
  )
  /** @type {Map<number, string>} */
  const nameByMid = new Map(pairs)
  return cards.map((c) => ({
    ...c,
    merchantName: nameByMid.get(Number(c.merchantId)) || c.merchantName,
  }))
}

/**
 * 按店铺合并接口返回的未读商家回复，用于卡片小红点（与顶栏口径一致）。
 */
async function refreshUnreadFlags() {
  const uid = userId.value
  if (!uid) return
  const res = await api.userChatUnreadMerchants(uid)
  /** @type {Set<number>} */
  const unreadMids = new Set()
  if (res.code === 200 && Array.isArray(res.data?.merchantIds)) {
    for (const id of res.data.merchantIds) unreadMids.add(Number(id))
  }
  merchantCards.value = (merchantCards.value || []).map((c) => ({
    ...c,
    hasUnreadReply: unreadMids.has(Number(c.merchantId)),
  }))
}

async function load() {
  loading.value = true
  errorMsg.value = ''
  merchantCards.value = []
  page.value = 1

  const res = await api.userGetOrders(userId.value)
  if (res.code !== 200) {
    loading.value = false
    errorMsg.value = res.message || '加载失败'
    return
  }
  orders.value = res.data || []

  const details = []
  for (const o of orders.value.slice(0, 20)) {
    const d = await api.userGetOrder(o.orderId, userId.value)
    if (d.code === 200 && d.data) details.push(d.data)
  }

  const rawCards = buildMerchantCardsFromOrderDetails(details)
  merchantCards.value = await enrichMerchantShopNames(rawCards)
  await refreshUnreadFlags()

  loading.value = false
}

function goChat(card) {
  if (!card?.merchantId) return
  const q = [`merchantId=${encodeURIComponent(card.merchantId)}`]
  if (card.lastOrderId) q.push(`orderId=${encodeURIComponent(card.lastOrderId)}`)
  router.push(`/support?${q.join('&')}`)
}

const hasData = computed(() => (merchantCards.value || []).length > 0)
const total = computed(() => (Array.isArray(merchantCards.value) ? merchantCards.value.length : 0))
const pagedCards = computed(() => {
  const list = Array.isArray(merchantCards.value) ? merchantCards.value : []
  const p = Math.max(1, Number(page.value || 1))
  const ps = Math.max(1, Number(pageSize.value || 1))
  const start = (p - 1) * ps
  return list.slice(start, start + ps)
})

function setPageSize(n) {
  pageSize.value = Number(n || 9)
  page.value = 1
}

function onChatUnreadUpdated() {
  if (route.path === '/merchant-contact') refreshUnreadFlags()
}

watch(
  () => route.path,
  (p) => {
    if (p === '/merchant-contact' && !loading.value && (merchantCards.value || []).length) refreshUnreadFlags()
  },
)

onMounted(async () => {
  if (!userId.value) {
    showAppMessage('请先登录', '提示')
    router.push('/login')
    return
  }
  window.addEventListener('petshop-chat-unread-updated', onChatUnreadUpdated)
  await load()
})

onUnmounted(() => {
  window.removeEventListener('petshop-chat-unread-updated', onChatUnreadUpdated)
})
</script>

<template>
  <div class="pw-page">
    <section class="pw-hero">
      <h1 class="pw-title">联系商家</h1>
      <p class="pw-lead">从已购订单中选择商家，进入会话继续沟通售后与商品问题。</p>
    </section>

    <section class="pw-section">
      <div class="pw-toolbar pw-toolbar--tight">
        <button type="button" class="pw-btn pw-btn-sm" @click="load">刷新</button>
        <button type="button" class="pw-btn-ghost pw-btn-sm" @click="router.push('/orders')">我的订单</button>
      </div>

      <div v-if="loading" class="pw-state">加载中...</div>
      <div v-else-if="errorMsg" class="pw-state pw-state--error">{{ errorMsg }}</div>
      <div v-else-if="!hasData" class="pw-state pw-state--empty">暂无可联系的商家（暂无带商家的订单）。</div>

      <div v-else class="pw-grid">
        <div v-for="m in pagedCards" :key="m.merchantId" class="pw-card">
          <div class="mc-card-head">
            <div class="pw-card-title mc-card-title-grow">{{ m.merchantName }}</div>
            <span
              v-if="m.hasUnreadReply"
              class="mc-unread-dot"
              title="该店铺有新回复"
              aria-label="有新回复"
            />
          </div>
          <div class="mc-product">
            <div class="mc-product-thumb">
              <img
                v-if="m.productImageUrl"
                :src="m.productImageUrl"
                class="mc-product-img"
                :alt="m.productTitle"
              />
              <div v-else class="mc-product-placeholder">暂无图片</div>
            </div>
            <div class="mc-product-body">
              <div class="mc-product-title">{{ m.productTitle }}</div>
              <div v-if="m.merchantLineCount > 1" class="mc-product-extra">
                本单该店铺还有 {{ m.merchantLineCount - 1 }} 件商品
              </div>
            </div>
          </div>
          <div class="pw-card-meta">
            <span>最近订单：{{ m.lastOrderNo || '-' }}</span>
            <span>更新时间：{{ m.lastTime ? new Date(m.lastTime).toLocaleString() : '-' }}</span>
          </div>
          <div class="pw-card-actions">
            <button type="button" class="pw-btn pw-btn-sm" @click="goChat(m)">进入对话</button>
          </div>
        </div>
      </div>

      <PaginationBar
        :page="page"
        :page-size="pageSize"
        :total="total"
        :page-size-options="[6, 9, 12]"
        @update:page="page = $event"
        @update:page-size="setPageSize"
    />
  </section>
  </div>
</template>

<style scoped>
.mc-card-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.mc-card-title-grow {
  flex: 1;
  min-width: 0;
}

.mc-unread-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #ff4d4f;
  flex-shrink: 0;
  box-shadow: 0 0 0 1px #fcfdff;
}

.mc-product {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  padding: 10px 0;
  border-top: 1px solid #e8eef6;
  border-bottom: 1px solid #e8eef6;
}

.mc-product-thumb {
  flex: 0 0 72px;
  width: 72px;
  height: 72px;
  border-radius: 2px;
  overflow: hidden;
  border: 1px solid #dbe3ee;
  background: #f4f7fb;
}

.mc-product-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.mc-product-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  color: #8a96a8;
  text-align: center;
  padding: 4px;
}

.mc-product-body {
  flex: 1;
  min-width: 0;
}

.mc-product-title {
  font-size: 14px;
  font-weight: 700;
  color: #1a2433;
  line-height: 1.45;
  word-break: break-word;
}

.mc-product-extra {
  margin-top: 6px;
  font-size: 12px;
  color: #6b7b91;
}
</style>
