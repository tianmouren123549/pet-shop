<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../utils/request'
import { showAppMessage } from '../utils/appMessage'
import PaginationBar from '../components/PaginationBar.vue'
import AppImage from '../components/AppImage.vue'
import AppSkeletonCard from '../components/AppSkeletonCard.vue'

const router = useRouter()
const route = useRoute()
const userId = ref(Number(localStorage.getItem('userId') || 0))
const loading = ref(false)
const errorMsg = ref('')
const orders = ref([])
const merchantCards = ref([])
const page = ref(1)
const pageSize = ref(9)
const filterUnreadOnly = ref(false)
const MC_SKELETON_COUNT = 6

/**
 * @param {string|undefined|null} t
 * @returns {number}
 */
function parseOrderTimeMs(t) {
  const ms = new Date(t || 0).getTime()
  return Number.isFinite(ms) ? ms : 0
}

/**
 * @param {Array<Record<string, unknown>>} details
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
    (a, b) =>
      parseOrderTimeMs(b.lastTime == null ? '' : String(b.lastTime)) -
      parseOrderTimeMs(a.lastTime == null ? '' : String(a.lastTime)),
  )
}

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

  const detailTasks = orders.value.slice(0, 20).map((o) => api.userGetOrder(o.orderId, userId.value))
  const detailResults = await Promise.allSettled(detailTasks)
  const details = detailResults
    .filter((x) => x.status === 'fulfilled' && x.value?.code === 200 && x.value?.data)
    .map((x) => x.value.data)

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

function goOrder(card) {
  const oid = Number(card?.lastOrderId || 0)
  if (!oid) return
  router.push(`/order/${oid}`)
}

const hasData = computed(() => (merchantCards.value || []).length > 0)

const unreadMerchantCount = computed(
  () => (merchantCards.value || []).filter((c) => c.hasUnreadReply).length,
)

const filteredMerchantCards = computed(() => {
  const list = Array.isArray(merchantCards.value) ? merchantCards.value : []
  if (!filterUnreadOnly.value) return list
  return list.filter((c) => c.hasUnreadReply)
})

watch(filterUnreadOnly, () => {
  page.value = 1
})

const total = computed(() => filteredMerchantCards.value.length)

watch([total, pageSize], () => {
  const tp = Math.max(1, Math.ceil(Number(total.value || 0) / Math.max(1, Number(pageSize.value || 1))))
  if (page.value > tp) page.value = tp
})

const pagedCards = computed(() => {
  const list = filteredMerchantCards.value
  const p = Math.max(1, Number(page.value || 1))
  const ps = Math.max(1, Number(pageSize.value || 1))
  const start = (p - 1) * ps
  return list.slice(start, start + ps)
})

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
  <div class="pw-page mc-page mc-page--apex">
    <section class="layout-shell">
      <aside class="layout-sidebar">
        <div class="sidebar-head">
          <h2 class="sidebar-title">联系商家</h2>
          <p class="sidebar-lead">从已购订单中选择店铺，进入会话沟通售后与商品问题。</p>
        </div>

        <div class="facet-block">
          <div class="facet-title">筛选</div>
          <button
            type="button"
            :class="['facet-chip', { 'facet-chip--on': !filterUnreadOnly }]"
            @click="filterUnreadOnly = false"
          >
            全部店铺
          </button>
          <button
            type="button"
            :class="['facet-chip', { 'facet-chip--on': filterUnreadOnly }]"
            @click="filterUnreadOnly = true"
          >
            有未读回复
          </button>
        </div>

        <div class="sidebar-tip">
          <p class="sidebar-tip-label">提示</p>
          <p class="sidebar-tip-text">仅展示您下过单且系统已关联的店铺；新订单同步后请刷新列表。</p>
        </div>
      </aside>

      <div class="layout-main">
        <header class="mc-toolbar">
          <div class="mc-toolbar__left">
            <h1 class="mc-toolbar-title">会话入口</h1>
            <p class="mc-toolbar-meta">
              <template v-if="hasData">
                <span class="mc-toolbar-count">{{ merchantCards.length }}</span>
                家店铺
                <span v-if="unreadMerchantCount > 0" class="mc-toolbar-dot">·</span>
                <span v-if="unreadMerchantCount > 0" class="mc-toolbar-unread">{{ unreadMerchantCount }} 家有新回复</span>
              </template>
              <template v-else>登录后可查看可联系的店铺</template>
            </p>
          </div>
          <div class="mc-toolbar__actions">
            <button type="button" class="mc-btn-ghost" @click="router.push('/orders')">我的订单</button>
            <button type="button" class="mc-btn-ghost" @click="load">刷新</button>
          </div>
        </header>

        <div v-if="loading" class="mc-skeleton-grid">
          <AppSkeletonCard :count="MC_SKELETON_COUNT" />
        </div>
        <div v-else-if="errorMsg" class="mc-state mc-state--error">{{ errorMsg }}</div>
        <div v-else-if="!hasData" class="mc-empty">
          <p class="mc-empty-title">暂无可联系商家</p>
          <p class="mc-empty-desc">下单成功后，可在此进入对应店铺的咨询会话。</p>
          <button type="button" class="mc-btn-primary" @click="router.push('/products')">去逛逛</button>
        </div>
        <div v-else-if="filteredMerchantCards.length === 0" class="mc-empty">
          <p class="mc-empty-title">当前筛选下暂无店铺</p>
          <p class="mc-empty-desc">暂时没有未读回复，或可先查看全部店铺。</p>
          <button type="button" class="mc-btn-primary" @click="filterUnreadOnly = false">查看全部店铺</button>
        </div>

        <div v-else class="mc-card-grid">
          <article v-for="m in pagedCards" :key="m.merchantId" class="mc-card">
            <div class="mc-card__media">
              <AppImage
                v-if="m.productImageUrl"
                :src="m.productImageUrl"
                class="mc-card__img"
                :alt="m.productTitle"
                loading="lazy"
                decoding="async"
              />
              <div v-else class="mc-card__thumb-ph">暂无图</div>
            </div>

            <div class="mc-card__main">
              <div class="mc-card__head">
                <h3 class="mc-card__name">{{ m.merchantName }}</h3>
                <span
                  v-if="m.hasUnreadReply"
                  class="mc-unread-pill"
                  title="该店铺有新回复"
                  aria-label="有新回复"
                >
                  新回复
                </span>
              </div>
              <p class="mc-card__product-title">{{ m.productTitle }}</p>
              <p v-if="m.merchantLineCount > 1" class="mc-card__product-extra">
                本单该店铺还有 {{ m.merchantLineCount - 1 }} 件商品
              </p>
              <dl class="mc-card__meta">
                <div class="mc-card__meta-row">
                  <dt>最近订单</dt>
                  <dd>{{ m.lastOrderNo || '—' }}</dd>
                </div>
                <div class="mc-card__meta-row">
                  <dt>更新时间</dt>
                  <dd>{{ m.lastTime ? new Date(m.lastTime).toLocaleString() : '—' }}</dd>
                </div>
              </dl>
            </div>

            <div class="mc-card__actions">
              <button type="button" class="mc-btn-primary" @click="goChat(m)">进入对话</button>
              <button v-if="m.lastOrderId" type="button" class="mc-btn-outline" @click="goOrder(m)">订单详情</button>
            </div>
          </article>
        </div>

        <div v-if="total > pageSize" class="mc-pagination-wrap">
          <PaginationBar
            :page="page"
            :page-size="pageSize"
            :total="total"
            @update:page="page = $event"
          />
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.mc-page--apex {
  --mc-ink: #0a0a0a;
  --mc-muted: #737373;
  --mc-line: #e5e5e5;
  --mc-panel: #ffffff;
  --mc-soft: #fafafa;
  --mc-radius: 2px;
  font-family: 'Inter', 'Microsoft YaHei', 'PingFang SC', system-ui, sans-serif;
  padding: 0;
  background: transparent;
}

.mc-page--apex.pw-page {
  padding-bottom: clamp(24px, 3vh, 36px);
}

.layout-shell {
  display: grid;
  grid-template-columns: minmax(200px, 248px) minmax(0, 1fr);
  gap: clamp(16px, 2.5vw, 24px);
  align-items: start;
}

.layout-sidebar {
  position: sticky;
  top: 72px;
  background: var(--mc-panel);
  border: 1px solid var(--mc-line);
  border-radius: var(--mc-radius);
  padding: 16px 14px;
  display: flex;
  flex-direction: column;
  gap: 2px;
  box-shadow: 0 1px 0 rgba(0, 0, 0, 0.04);
}

.sidebar-head {
  margin-bottom: 8px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--mc-line);
}

.sidebar-title {
  margin: 0 0 6px;
  font-size: clamp(17px, 1.4vw, 20px);
  font-weight: 800;
  letter-spacing: -0.02em;
  color: var(--mc-ink);
}

.sidebar-lead {
  margin: 0;
  font-size: 12px;
  color: var(--mc-muted);
  line-height: 1.55;
}

.facet-block {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px 0 10px;
  border-top: 1px solid var(--mc-line);
}

.facet-block:first-of-type {
  border-top: none;
  padding-top: 4px;
}

.facet-title {
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--mc-muted);
}

.facet-chip {
  width: 100%;
  text-align: left;
  padding: 10px 12px;
  border: 1px solid var(--mc-line);
  border-radius: var(--mc-radius);
  background: var(--mc-soft);
  font-size: 13px;
  font-weight: 600;
  color: var(--mc-ink);
  cursor: pointer;
  transition:
    border-color 0.15s,
    background 0.15s;
}

.facet-chip:hover {
  border-color: #bdbdbd;
  background: #fff;
}

.facet-chip--on {
  background: var(--mc-ink);
  color: #fff;
  border-color: var(--mc-ink);
}

.facet-chip--on:hover {
  background: var(--mc-ink);
  border-color: var(--mc-ink);
  color: #fff;
}

.sidebar-tip {
  margin-top: 4px;
  padding: 10px 12px;
  border: 1px solid var(--mc-line);
  border-radius: var(--mc-radius);
  background: var(--mc-soft);
}

.sidebar-tip-label {
  margin: 0 0 6px;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: #a3a3a3;
}

.sidebar-tip-text {
  margin: 0;
  font-size: 12px;
  line-height: 1.55;
  color: var(--mc-muted);
}

.layout-main {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.mc-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 14px;
  padding: clamp(18px, 2vw, 22px);
  background: var(--mc-panel);
  border: 1px solid var(--mc-line);
  border-radius: var(--mc-radius);
  box-shadow: 0 1px 0 rgba(0, 0, 0, 0.04);
}

.mc-toolbar-title {
  margin: 0 0 6px;
  font-size: clamp(17px, 1.45vw, 20px);
  font-weight: 800;
  color: var(--mc-ink);
  letter-spacing: -0.02em;
}

.mc-toolbar-meta {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--mc-muted);
}

.mc-toolbar-count {
  font-weight: 800;
  color: var(--mc-ink);
}

.mc-toolbar-dot {
  margin: 0 6px;
  color: var(--mc-line);
}

.mc-toolbar-unread {
  color: #b45309;
  font-weight: 700;
}

.mc-toolbar__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.mc-btn-ghost {
  padding: 10px 18px;
  border: 1px solid var(--mc-line);
  border-radius: var(--mc-radius);
  background: var(--mc-panel);
  color: var(--mc-ink);
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.05em;
  cursor: pointer;
}

.mc-btn-ghost:hover {
  border-color: var(--mc-ink);
}

.mc-btn-primary {
  padding: 10px 18px;
  border: 1px solid var(--mc-ink);
  border-radius: var(--mc-radius);
  background: var(--mc-ink);
  color: #fff;
  font-size: 13px;
  font-weight: 800;
  cursor: pointer;
}

.mc-btn-outline {
  padding: 10px 18px;
  border: 1px solid var(--mc-line);
  border-radius: var(--mc-radius);
  background: var(--mc-panel);
  color: var(--mc-ink);
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
}

.mc-btn-outline:hover {
  border-color: var(--mc-ink);
}

.mc-skeleton-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 280px), 1fr));
  gap: 14px;
}

.mc-state {
  padding: 28px 20px;
  text-align: center;
  border-radius: var(--mc-radius);
  border: 1px solid var(--mc-line);
  background: var(--mc-panel);
  font-size: 14px;
  font-weight: 600;
}

.mc-state--error {
  border-color: #fecaca;
  background: #fef2f2;
  color: #b91c1c;
}

.mc-empty {
  padding: 48px 28px;
  text-align: center;
  border: 1px dashed var(--mc-line);
  border-radius: var(--mc-radius);
  background: var(--mc-panel);
}

.mc-empty-title {
  margin: 0 0 10px;
  font-size: clamp(18px, 1.6vw, 22px);
  font-weight: 800;
  color: var(--mc-ink);
}

.mc-empty-desc {
  margin: 0 0 22px;
  font-size: 14px;
  color: var(--mc-muted);
  line-height: 1.55;
}

/* 窄屏：两列卡片自动填满行宽（auto-fit 折叠空轨）；宽屏：改为一列通栏横排，避免右侧大块留白 */
.mc-card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 280px), 1fr));
  gap: 14px;
}

.mc-card {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 16px;
  background: var(--mc-panel);
  border: 1px solid var(--mc-line);
  border-radius: var(--mc-radius);
  transition:
    box-shadow 0.2s ease,
    border-color 0.15s ease;
}

.mc-card:hover {
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.06);
  border-color: #d4d4d4;
}

.mc-card__media {
  flex-shrink: 0;
  width: 72px;
  height: 72px;
  border-radius: var(--mc-radius);
  overflow: hidden;
  border: 1px solid var(--mc-line);
  background: var(--mc-soft);
  align-self: flex-start;
}

.mc-card__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.mc-card__thumb-ph {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 700;
  color: #a3a3a3;
}

.mc-card__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.mc-card__head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px 10px;
}

.mc-card__name {
  flex: 1;
  min-width: 0;
  margin: 0;
  font-size: clamp(15px, 1.25vw, 17px);
  font-weight: 800;
  color: var(--mc-ink);
}

.mc-unread-pill {
  flex-shrink: 0;
  padding: 3px 8px;
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: #fff;
  background: #dc2626;
  border-radius: 2px;
}

.mc-card__product-title {
  margin: 0;
  font-size: 14px;
  font-weight: 700;
  color: var(--mc-ink);
  line-height: 1.45;
  word-break: break-word;
}

.mc-card__product-extra {
  margin: 0;
  font-size: 12px;
  font-weight: 600;
  color: var(--mc-muted);
}

.mc-card__meta {
  margin: 4px 0 0;
  padding-top: 10px;
  border-top: 1px solid var(--mc-line);
}

.mc-card__meta-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 6px 0;
  font-size: 12px;
}

.mc-card__meta-row + .mc-card__meta-row {
  border-top: 1px solid var(--mc-line);
}

.mc-card__meta-row dt {
  margin: 0;
  font-weight: 600;
  color: var(--mc-muted);
}

.mc-card__meta-row dd {
  margin: 0;
  font-weight: 700;
  color: var(--mc-ink);
  text-align: right;
  max-width: 62%;
  word-break: break-word;
}

.mc-card__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.mc-card__actions .mc-btn-primary {
  flex: 1;
  min-width: 120px;
}

@media (min-width: 880px) {
  .mc-card-grid {
    display: flex;
    flex-direction: column;
    gap: 12px;
  }

  .mc-card {
    flex-direction: row;
    align-items: stretch;
    gap: 18px 22px;
    padding: 16px 18px;
  }

  .mc-card__media {
    width: 88px;
    height: 88px;
    align-self: center;
  }

  .mc-card__main {
    flex: 1;
    min-width: 0;
    padding-top: 0;
  }

  .mc-card__meta {
    margin-top: 6px;
    padding-top: 10px;
    border-top: 1px solid var(--mc-line);
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 4px 24px;
    align-items: start;
  }

  .mc-card__meta .mc-card__meta-row {
    padding: 0;
    border-top: none;
  }

  .mc-card__actions {
    flex-direction: column;
    justify-content: center;
    flex: 0 0 118px;
    margin-top: 0;
  }

  .mc-card__actions .mc-btn-primary,
  .mc-card__actions .mc-btn-outline {
    flex: unset;
    width: 100%;
    min-width: unset;
  }
}

.mc-pagination-wrap :deep(.pw-pagination) {
  justify-content: center;
  border: none !important;
  background: transparent !important;
  padding: 8px 0 4px !important;
  margin-top: 4px;
  box-shadow: none !important;
}

.mc-pagination-wrap :deep(.pw-page-btn),
.mc-pagination-wrap :deep(.pw-page-num) {
  min-width: 40px;
  height: 40px;
  border-radius: var(--mc-radius);
  border: 1px solid var(--mc-line);
  background: var(--mc-soft);
  font-size: 13px;
  font-weight: 700;
}

.mc-pagination-wrap :deep(.pw-page-num.active) {
  border-color: var(--mc-ink);
  background: var(--mc-ink);
  color: #fff;
}

@media (max-width: 980px) {
  .layout-shell {
    grid-template-columns: 1fr;
  }

  .layout-sidebar {
    position: static;
    order: 2;
  }

  .layout-main {
    order: 1;
  }

  .mc-toolbar__actions {
    width: 100%;
  }

  .mc-btn-ghost {
    flex: 1;
  }
}
</style>
