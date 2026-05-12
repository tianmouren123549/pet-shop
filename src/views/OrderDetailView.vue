<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter, RouterLink } from 'vue-router'
import { api } from '../utils/request'
import { formatYuan } from '../utils/formatYuan.js'
import { showAppMessage } from '../utils/appMessage'
import ConfirmModal from '../components/ConfirmModal.vue'
import AppImage from '../components/AppImage.vue'

const route = useRoute()
const router = useRouter()
const userId = ref(Number(localStorage.getItem('userId') || 0))
const loading = ref(false)
const errorMsg = ref('')
const order = ref(null)
const payConfirmOpen = ref(false)
const cancelConfirmOpen = ref(false)
const receiveConfirmOpen = ref(false)

function hasShippingSnapshot(o) {
  if (!o) return false
  return Boolean(
    String(o.receiverName || '').trim() ||
      String(o.receiverPhone || '').trim() ||
      String(o.receiverRegion || '').trim() ||
      String(o.receiverAddress || '').trim(),
  )
}

function statusText(s) {
  return s === 'CREATED'
    ? '待支付'
    : s === 'PAID'
      ? '待发货'
      : s === 'SHIPPED'
        ? '已发货'
        : s === 'COMPLETED'
          ? '已完成'
          : s === 'CANCELLED'
            ? '已取消'
            : s
}

const canPay = computed(() => order.value?.status === 'CREATED')
const canCancel = computed(() => ['CREATED', 'PAID'].includes(order.value?.status || ''))
const canConfirm = computed(() => order.value?.status === 'SHIPPED')
const canReviewOrder = computed(() => order.value?.status === 'COMPLETED')

function goToProductReview(productId) {
  const id = Number(productId)
  if (!id) return
  router.push({ path: `/product/${id}`, query: { review: '1' } })
}

async function loadOrder() {
  loading.value = true
  errorMsg.value = ''
  const res = await api.userGetOrder(route.params.id, userId.value)
  if (res.code === 200) {
    order.value = res.data
  } else {
    errorMsg.value = res.message || '订单加载失败'
    order.value = null
  }
  loading.value = false
}

function pay() {
  if (!order.value) return
  payConfirmOpen.value = true
}

async function submitPay() {
  if (!order.value) return
  const res = await api.userPayOrder(order.value.orderId, userId.value)
  payConfirmOpen.value = false
  if (res.code === 200) {
    await loadOrder()
  } else {
    showAppMessage(res.message || '支付失败', '提示')
  }
}

function cancel() {
  if (!order.value) return
  cancelConfirmOpen.value = true
}

async function submitCancel() {
  if (!order.value) return
  const res = await api.userCancelOrder(order.value.orderId, userId.value)
  cancelConfirmOpen.value = false
  if (res.code === 200) {
    await loadOrder()
  } else {
    showAppMessage(res.message || '取消失败', '提示')
  }
}

function confirmReceive() {
  if (!order.value) return
  receiveConfirmOpen.value = true
}

async function submitConfirmReceive() {
  if (!order.value) return
  const res = await api.userConfirmOrder(order.value.orderId, userId.value)
  receiveConfirmOpen.value = false
  if (res.code === 200) {
    await loadOrder()
  } else {
    showAppMessage(res.message || '操作失败', '提示')
  }
}

function contactMerchant() {
  if (!order.value || !Array.isArray(order.value.items) || order.value.items.length === 0) {
    showAppMessage('未找到商家信息，无法发起咨询', '提示')
    return
  }
  const merchantId = Number(order.value.items[0].merchantId || 1)
  router.push(`/support?merchantId=${merchantId}&orderId=${order.value.orderId}`)
}

const canContact = computed(
  () => order.value && Array.isArray(order.value.items) && order.value.items.length > 0,
)

onMounted(async () => {
  if (!userId.value) {
    showAppMessage('请先登录', '提示')
    router.push('/login')
    return
  }
  await loadOrder()
})
</script>

<template>
  <div class="pw-page order-page order-page--apex">
    <section class="layout-shell">
      <aside class="layout-sidebar">
        <div class="sidebar-head">
          <h2 class="sidebar-title">订单详情</h2>
          <p class="sidebar-lead">查看物流、商品行与应付金额；需要售后请通过联系商家会话沟通。</p>
        </div>

        <div class="facet-block">
          <div class="facet-title">快捷入口</div>
          <button type="button" class="facet-chip" @click="router.push('/orders')">返回订单列表</button>
          <button
            type="button"
            class="facet-chip"
            :disabled="!canContact"
            :class="{ 'facet-chip--disabled': !canContact }"
            @click="contactMerchant"
          >
            联系商家
          </button>
        </div>

        <div v-if="order" class="side-summary">
          <div class="facet-title">本单概览</div>
          <dl class="side-summary-rows">
            <div class="side-summary-row">
              <dt>状态</dt>
              <dd>
                <span class="od-status-pill" :class="`od-status-pill--${order.status.toLowerCase()}`">{{
                  statusText(order.status)
                }}</span>
              </dd>
            </div>
            <div class="side-summary-row">
              <dt>应付</dt>
              <dd class="side-summary-amount">¥{{ formatYuan(order.payAmount) }}</dd>
            </div>
            <div v-if="order.itemCount != null" class="side-summary-row">
              <dt>件数</dt>
              <dd>{{ order.itemCount }} 件</dd>
            </div>
          </dl>
        </div>

        <div class="sidebar-tip">
          <p class="sidebar-tip-label">提示</p>
          <p class="sidebar-tip-text">待支付订单请在约定时间内完成支付；发货与售后进度以商家处理为准。</p>
        </div>
      </aside>

      <div class="layout-main">
        <div class="od-breadcrumb">
          <button type="button" class="od-breadcrumb-link" @click="router.push('/orders')">我的订单</button>
          <span class="od-breadcrumb-sep">/</span>
          <span class="od-breadcrumb-current">详情</span>
        </div>

        <div v-if="loading" class="od-state od-state--muted">订单加载中…</div>
        <div v-else-if="errorMsg" class="od-state od-state--error">
          <p class="od-err-title">{{ errorMsg }}</p>
          <button type="button" class="od-btn-primary od-btn-primary--sm" @click="loadOrder">重试</button>
        </div>

        <template v-else-if="order">
          <header class="od-toolbar">
            <div class="od-toolbar__left">
              <h1 class="od-toolbar-title">订单号 {{ order.orderNo }}</h1>
              <p class="od-toolbar-meta">
                <span>下单 {{ new Date(order.createdAt).toLocaleString() }}</span>
                <span class="od-toolbar-dot">·</span>
                <span>更新 {{ new Date(order.updatedAt).toLocaleString() }}</span>
              </p>
            </div>
            <div class="od-toolbar__actions">
              <button v-if="canCancel" type="button" class="od-btn-outline od-btn-outline--danger" @click="cancel">
                取消订单
              </button>
              <button v-if="canPay" type="button" class="od-btn-primary" @click="pay">立即支付</button>
              <button v-if="canConfirm" type="button" class="od-btn-primary" @click="confirmReceive">确认收货</button>
            </div>
          </header>

          <div class="od-hero-card">
            <div class="od-hero__label">应付金额</div>
            <div class="od-hero__amount">¥{{ formatYuan(order.payAmount) }}</div>
            <p class="od-hero__note">金额以支付完成时系统记录为准。</p>
          </div>

          <section v-if="hasShippingSnapshot(order)" class="od-panel">
            <h2 class="od-panel-title">收货与配送</h2>
            <dl class="od-dl">
              <div class="od-dl-row">
                <dt>收货人</dt>
                <dd>{{ order.receiverName || '—' }}</dd>
              </div>
              <div class="od-dl-row">
                <dt>联系电话</dt>
                <dd>{{ order.receiverPhone || '—' }}</dd>
              </div>
              <div class="od-dl-row">
                <dt>配送地区</dt>
                <dd>{{ order.receiverRegion || '—' }}</dd>
              </div>
              <div class="od-dl-row">
                <dt>详细地址</dt>
                <dd>{{ order.receiverAddress || '—' }}</dd>
              </div>
              <div class="od-dl-row">
                <dt>物流单号</dt>
                <dd>{{ order.logisticsNo ? String(order.logisticsNo) : '暂无物流信息' }}</dd>
              </div>
            </dl>
          </section>

          <section class="od-panel">
            <h2 class="od-panel-title">商品清单</h2>
            <div class="od-items">
              <article
                v-for="(it, lineIdx) in order.items"
                :key="`${it.productId}-${lineIdx}`"
                class="od-line"
                :class="{ 'od-line--review': canReviewOrder && Number(it.productId) > 0 }"
              >
                <div class="od-line__media">
                  <RouterLink
                    v-if="Number(it.productId) > 0"
                    :to="`/product/${it.productId}`"
                    class="od-line__img-link"
                    :title="'查看商品：' + it.title"
                  >
                    <AppImage v-if="it.imageUrl" :src="it.imageUrl" class="od-line__img" alt="" loading="lazy" decoding="async" />
                    <div v-else class="od-line__ph">暂无图</div>
                  </RouterLink>
                  <template v-else>
                    <AppImage v-if="it.imageUrl" :src="it.imageUrl" class="od-line__img" alt="" loading="lazy" decoding="async" />
                    <div v-else class="od-line__ph">暂无图</div>
                  </template>
                </div>
                <div class="od-line__body">
                  <RouterLink
                    v-if="Number(it.productId) > 0"
                    :to="`/product/${it.productId}`"
                    class="od-line__title-link"
                  >
                    <h3 class="od-line__title">{{ it.title }}</h3>
                  </RouterLink>
                  <h3 v-else class="od-line__title">{{ it.title }}</h3>
                  <div class="od-line__meta">
                    <span>单价 ¥{{ formatYuan(it.price) }}</span>
                    <span>数量 {{ it.quantity }}</span>
                  </div>
                </div>
                <div class="od-line__price">¥{{ formatYuan(it.subtotal) }}</div>
                <div v-if="canReviewOrder && Number(it.productId) > 0" class="od-line__cta">
                  <button type="button" class="od-btn-outline od-btn-outline--sm" @click="goToProductReview(it.productId)">
                    去评价
                  </button>
                </div>
              </article>
            </div>
          </section>

          <section class="od-panel od-panel--tips">
            <h2 class="od-panel-title">说明</h2>
            <ul class="od-tips-list">
              <li>订单状态：待支付 → 已支付（待商家发货）→ 已发货 → 已完成。</li>
              <li>待支付订单若下单后 <strong>1 小时</strong>内未完成支付，将自动取消。</li>
              <li>发货与完成由商家处理，您可在此查看进度与物流信息。</li>
              <li>订单「已完成」后，可通过「去评价」进入商品详情页填写评价。</li>
            </ul>
          </section>
        </template>
      </div>
    </section>

    <ConfirmModal
      :open="payConfirmOpen"
      title="确认支付"
      confirm-label="确认支付"
      @update:open="payConfirmOpen = $event"
      @confirm="submitPay"
    >
      <p>确认支付本订单？当前为平台内支付流程，不会产生银行卡或第三方支付平台的真实扣款。确定继续吗？</p>
    </ConfirmModal>

    <ConfirmModal
      :open="cancelConfirmOpen"
      title="取消订单"
      confirm-label="确定取消"
      @update:open="cancelConfirmOpen = $event"
      @confirm="submitCancel"
    >
      <p>确定要取消该订单吗？</p>
    </ConfirmModal>

    <ConfirmModal
      :open="receiveConfirmOpen"
      title="确认收货"
      confirm-label="确认收货"
      @update:open="receiveConfirmOpen = $event"
      @confirm="submitConfirmReceive"
    >
      <p>确认已收到全部货物吗？</p>
    </ConfirmModal>
  </div>
</template>

<style scoped>
.order-page--apex {
  --od-ink: #0a0a0a;
  --od-muted: #737373;
  --od-line: #e5e5e5;
  --od-panel: #ffffff;
  --od-soft: #fafafa;
  --od-radius: 2px;
  font-family: 'Inter', 'Microsoft YaHei', 'PingFang SC', system-ui, sans-serif;
}

.order-page--apex.pw-page {
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
  background: var(--od-panel);
  border: 1px solid var(--od-line);
  border-radius: var(--od-radius);
  padding: 16px 14px;
  box-shadow: 0 1px 0 rgba(0, 0, 0, 0.04);
}

.sidebar-head {
  margin-bottom: 8px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--od-line);
}

.sidebar-title {
  margin: 0 0 6px;
  font-size: clamp(17px, 1.35vw, 20px);
  font-weight: 800;
  letter-spacing: -0.02em;
  color: var(--od-ink);
}

.sidebar-lead {
  margin: 0;
  font-size: 12px;
  line-height: 1.55;
  color: var(--od-muted);
}

.facet-block {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px 0 10px;
  border-top: 1px solid var(--od-line);
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
  color: var(--od-muted);
}

.facet-chip {
  width: 100%;
  text-align: left;
  padding: 10px 12px;
  border: 1px solid var(--od-line);
  border-radius: var(--od-radius);
  background: var(--od-soft);
  font-size: 13px;
  font-weight: 600;
  color: var(--od-ink);
  cursor: pointer;
  transition:
    border-color 0.15s,
    background 0.15s;
}

.facet-chip:hover:not(:disabled) {
  border-color: #bdbdbd;
  background: #fff;
}

.facet-chip:disabled,
.facet-chip--disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.side-summary {
  padding: 12px 0 10px;
  border-top: 1px solid var(--od-line);
}

.side-summary-rows {
  margin: 10px 0 0;
}

.side-summary-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px solid var(--od-line);
  font-size: 13px;
}

.side-summary-row:last-child {
  border-bottom: none;
}

.side-summary-row dt {
  margin: 0;
  font-weight: 600;
  color: var(--od-muted);
}

.side-summary-row dd {
  margin: 0;
  font-weight: 700;
  color: var(--od-ink);
  text-align: right;
}

.side-summary-amount {
  font-size: 16px;
  font-weight: 800;
  letter-spacing: -0.02em;
}

.od-status-pill {
  display: inline-block;
  padding: 4px 10px;
  font-size: 11px;
  font-weight: 800;
  border: 1px solid var(--od-line);
  border-radius: var(--od-radius);
  background: var(--od-soft);
  color: #404040;
}

.od-status-pill--created {
  background: #fffbeb;
  border-color: #fde68a;
  color: #92400e;
}

.od-status-pill--paid {
  background: #eff6ff;
  border-color: #bfdbfe;
  color: #1d4ed8;
}

.od-status-pill--shipped {
  background: #f0fdf4;
  border-color: #bbf7d0;
  color: #166534;
}

.od-status-pill--completed {
  background: #faf5ff;
  border-color: #e9d5ff;
  color: #6b21a8;
}

.od-status-pill--cancelled {
  background: #fef2f2;
  border-color: #fecaca;
  color: #b91c1c;
}

.sidebar-tip {
  margin-top: 8px;
  padding: 10px 12px;
  border: 1px solid var(--od-line);
  border-radius: var(--od-radius);
  background: var(--od-soft);
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
  color: var(--od-muted);
}

.layout-main {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.od-breadcrumb {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
}

.od-breadcrumb-link {
  padding: 0;
  border: none;
  background: none;
  font: inherit;
  font-weight: 700;
  color: var(--od-ink);
  text-decoration: underline;
  text-underline-offset: 3px;
  cursor: pointer;
}

.od-breadcrumb-link:hover {
  color: #525252;
}

.od-breadcrumb-sep {
  color: #a3a3a3;
}

.od-breadcrumb-current {
  color: var(--od-muted);
  font-weight: 600;
}

.od-state {
  padding: 28px 20px;
  text-align: center;
  border-radius: var(--od-radius);
  border: 1px solid var(--od-line);
  background: var(--od-panel);
}

.od-state--muted {
  color: var(--od-muted);
  font-weight: 600;
}

.od-state--error {
  border-color: #fecaca;
  background: #fef2f2;
}

.od-err-title {
  margin: 0 0 14px;
  color: #b91c1c;
  font-weight: 700;
}

.od-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 14px;
  padding: clamp(18px, 2vw, 22px);
  background: var(--od-panel);
  border: 1px solid var(--od-line);
  border-radius: var(--od-radius);
  box-shadow: 0 1px 0 rgba(0, 0, 0, 0.04);
}

.od-toolbar-title {
  margin: 0 0 6px;
  font-size: clamp(17px, 1.45vw, 20px);
  font-weight: 800;
  color: var(--od-ink);
  letter-spacing: -0.02em;
}

.od-toolbar-meta {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--od-muted);
}

.od-toolbar-dot {
  margin: 0 6px;
  color: var(--od-line);
}

.od-toolbar__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.od-hero-card {
  padding: clamp(20px, 2.5vw, 28px);
  background: var(--od-panel);
  border: 1px solid var(--od-line);
  border-radius: var(--od-radius);
}

.od-hero__label {
  margin: 0 0 6px;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--od-muted);
}

.od-hero__amount {
  font-size: clamp(28px, 4vw, 40px);
  font-weight: 800;
  letter-spacing: -0.03em;
  color: var(--od-ink);
  line-height: 1.1;
}

.od-hero__note {
  margin: 12px 0 0;
  font-size: 12px;
  font-weight: 600;
  color: var(--od-muted);
}

.od-panel {
  padding: clamp(18px, 2vw, 22px);
  background: var(--od-panel);
  border: 1px solid var(--od-line);
  border-radius: var(--od-radius);
}

.od-panel-title {
  margin: 0 0 14px;
  font-size: 13px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #a3a3a3;
}

.od-dl {
  margin: 0;
}

.od-dl-row {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 10px 0;
  border-bottom: 1px solid var(--od-line);
  font-size: 14px;
}

.od-dl-row:last-child {
  border-bottom: none;
}

.od-dl-row dt {
  margin: 0;
  flex-shrink: 0;
  font-weight: 600;
  color: var(--od-muted);
}

.od-dl-row dd {
  margin: 0;
  text-align: right;
  font-weight: 700;
  color: var(--od-ink);
  word-break: break-word;
}

.od-items {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.od-line {
  display: grid;
  grid-template-columns: 72px 1fr auto;
  gap: 14px 16px;
  align-items: center;
  padding: 14px;
  border: 1px solid var(--od-line);
  border-radius: var(--od-radius);
  background: var(--od-soft);
}

.od-line--review {
  grid-template-columns: 72px 1fr auto 88px;
}

.od-line__media {
  width: 72px;
  height: 72px;
  border-radius: var(--od-radius);
  overflow: hidden;
  border: 1px solid var(--od-line);
  background: var(--od-panel);
}

.od-line__img-link {
  display: block;
  width: 100%;
  height: 100%;
  text-decoration: none;
}

.od-line__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.od-line__ph {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 700;
  color: #a3a3a3;
}

.od-line__body {
  min-width: 0;
}

.od-line__title-link {
  text-decoration: none;
  color: inherit;
}

.od-line__title {
  margin: 0;
  font-size: 15px;
  font-weight: 800;
  color: var(--od-ink);
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.od-line__title-link:hover .od-line__title {
  text-decoration: underline;
  text-underline-offset: 3px;
}

.od-line__meta {
  margin-top: 6px;
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  font-size: 12px;
  font-weight: 600;
  color: var(--od-muted);
}

.od-line__price {
  font-size: 17px;
  font-weight: 800;
  color: var(--od-ink);
  text-align: right;
}

.od-line__cta {
  display: flex;
  justify-content: flex-end;
}

.od-panel--tips {
  background: var(--od-soft);
}

.od-tips-list {
  margin: 0;
  padding-left: 18px;
  font-size: 13px;
  line-height: 1.65;
  color: #525252;
}

.od-btn-primary {
  padding: 10px 20px;
  border: 1px solid var(--od-ink);
  border-radius: var(--od-radius);
  background: var(--od-ink);
  color: #fff;
  font-size: 13px;
  font-weight: 800;
  cursor: pointer;
}

.od-btn-primary:hover {
  background: #262626;
  border-color: #262626;
}

.od-btn-primary--sm {
  padding: 8px 16px;
  font-size: 12px;
}

.od-btn-outline {
  padding: 10px 18px;
  border: 1px solid var(--od-line);
  border-radius: var(--od-radius);
  background: var(--od-panel);
  color: var(--od-ink);
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
}

.od-btn-outline:hover {
  border-color: var(--od-ink);
}

.od-btn-outline--sm {
  padding: 8px 12px;
  font-size: 12px;
}

.od-btn-outline--danger {
  border-color: #fecaca;
  background: #fff;
  color: #b91c1c;
}

.od-btn-outline--danger:hover {
  border-color: #f87171;
  background: #fef2f2;
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

  .od-line {
    grid-template-columns: 64px 1fr;
    grid-template-rows: auto auto;
  }

  .od-line__media {
    grid-row: span 2;
    width: 64px;
    height: 64px;
  }

  .od-line__price {
    grid-column: 2;
    text-align: left;
  }

  .od-line--review {
    grid-template-columns: 64px 1fr;
  }

  .od-line__cta {
    grid-column: 1 / -1;
    justify-content: stretch;
  }

  .od-line__cta .od-btn-outline {
    width: 100%;
  }

  .od-toolbar__actions {
    width: 100%;
  }

  .od-toolbar__actions .od-btn-primary,
  .od-toolbar__actions .od-btn-outline {
    flex: 1;
    min-width: 0;
  }
}
</style>
