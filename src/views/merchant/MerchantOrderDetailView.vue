<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../../utils/request'
import { formatYuan } from '../../utils/formatYuan.js'
import { showAppMessage } from '../../utils/appMessage'

const route = useRoute()
const router = useRouter()
const merchantId = ref(Number(localStorage.getItem('adminId') || 0))
const loading = ref(true)
const errorMsg = ref('')
/** @type {import('vue').Ref<Record<string, unknown> | null>} */
const detail = ref(null)

function resolveMediaUrl(raw) {
  const s = String(raw || '').trim()
  if (!s) return ''
  if (/^(https?:|data:|blob:)/i.test(s)) return s
  const base = String(import.meta.env.VITE_API_BASE || '').replace(/\/$/, '')
  if (s.startsWith('/')) return base ? `${base}${s}` : s
  return s
}

/** @param {Record<string, unknown>} line */
function lineImageUrl(line) {
  return resolveMediaUrl(line?.imageUrl)
}

function statusText(s) {
  if (s === 'CREATED') return '待支付'
  if (s === 'PAID') return '待发货'
  if (s === 'SHIPPED') return '已发货'
  if (s === 'COMPLETED') return '已完成'
  if (s === 'CANCELLED') return '已取消'
  return String(s || '—')
}

function statusDotClass(s) {
  const st = String(s || '').toUpperCase()
  if (st === 'CANCELLED') return 'dot--muted'
  if (st === 'CREATED') return 'dot--warn'
  if (st === 'PAID') return 'dot--ok'
  return 'dot--neutral'
}

function maskPhone(p) {
  const s = String(p || '').replace(/\s/g, '')
  if (!s) return '—'
  if (s.length < 7) return s
  return `${s.slice(0, 3)} **** ${s.slice(-4)}`
}

const orderIdNum = computed(() => Number(route.params.orderId))

/** 订单进度：与主状态 + 是否已支付组合 */
const progressSteps = computed(() => {
  const d = detail.value
  if (!d) return []
  const st = String(d.status || '').toUpperCase()
  const paid = Boolean(d.paidAt)
  const steps = [
    { key: 'submit', label: '提交订单' },
    { key: 'pay', label: '支付完成' },
    { key: 'ship', label: '等待发货' },
    { key: 'end', label: st === 'CANCELLED' ? '订单取消' : '确认完成' },
  ]
  /** @type {('done'|'current'|'pending')[]} */
  const state = /** @type {('done'|'current'|'pending')[]} */ (['pending', 'pending', 'pending', 'pending'])
  state[0] = 'done'
  if (st === 'CANCELLED') {
    if (paid) {
      state[1] = 'done'
      state[2] = 'pending'
    } else {
      state[1] = 'pending'
      state[2] = 'pending'
    }
    state[3] = 'done'
    return steps.map((s, i) => ({ ...s, state: state[i] }))
  }
  if (st === 'CREATED') {
    state[1] = 'current'
    return steps.map((s, i) => ({ ...s, state: state[i] }))
  }
  state[1] = 'done'
  if (st === 'PAID') {
    state[2] = 'current'
    return steps.map((s, i) => ({ ...s, state: state[i] }))
  }
  state[2] = 'done'
  if (st === 'SHIPPED') {
    state[3] = 'current'
    return steps.map((s, i) => ({ ...s, state: state[i] }))
  }
  if (st === 'COMPLETED') {
    state[3] = 'done'
    return steps.map((s, i) => ({ ...s, state: state[i] }))
  }
  state[3] = 'pending'
  return steps.map((s, i) => ({ ...s, state: state[i] }))
})

const linesTotal = computed(() => {
  const lines = Array.isArray(detail.value?.lines) ? detail.value.lines : []
  let sum = 0
  for (const line of lines) {
    sum += Number(line.subtotal || 0)
  }
  return formatYuan(sum)
})

async function load() {
  loading.value = true
  errorMsg.value = ''
  const oid = orderIdNum.value
  if (!merchantId.value || !Number.isFinite(oid) || oid <= 0) {
    errorMsg.value = '参数无效'
    detail.value = null
    loading.value = false
    return
  }
  const res = await api.merchantGetOrderDetail(merchantId.value, oid)
  if (res.code === 200) {
    detail.value = res.data || null
  } else {
    errorMsg.value = res.message || '加载失败'
    detail.value = null
  }
  loading.value = false
}

async function ship() {
  const oid = orderIdNum.value
  const res = await api.merchantUpdateOrderStatus(merchantId.value, oid, 'SHIPPED')
  if (res.code === 200) {
    showAppMessage('已发货', '提示')
    await load()
  } else {
    showAppMessage(res.message || '发货失败', '提示')
  }
}

function printSlip() {
  window.print()
}

function contactCustomer() {
  router.push('/merchant/support')
}

function updateStatusClick() {
  const st = String(detail.value?.status || '')
  if (st === 'PAID') {
    void ship()
    return
  }
  showAppMessage('当前状态请在用户端完成支付或确认收货后再操作', '提示')
}

onMounted(load)
</script>

<template>
  <div class="merchant-order-detail">
    <button type="button" class="back-btn" @click="router.push('/merchant/orders')">
      <svg class="back-btn__icon" viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
        <path
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          stroke-linecap="round"
          stroke-linejoin="round"
          d="M15 18l-6-6 6-6"
        />
      </svg>
      <span>返回订单列表</span>
    </button>

    <div v-if="loading" class="panel">加载中…</div>
    <div v-else-if="errorMsg" class="panel panel-err">{{ errorMsg }}</div>
    <template v-else-if="detail">
      <section class="card summary-card">
        <div class="summary-row">
          <div class="summary-cell">
            <span class="k">订单号</span>
            <span class="v mono">{{ detail.orderNo }}</span>
          </div>
          <div class="summary-cell">
            <span class="k">买家</span>
            <span class="v">{{ detail.buyerNickname || `用户 #${detail.userId ?? '—'}` }}</span>
          </div>
          <div class="summary-cell">
            <span class="k">实付</span>
            <span class="v v-strong">¥{{ formatYuan(detail.payAmount) }}</span>
          </div>
          <div class="summary-cell">
            <span class="k">状态</span>
            <span class="v status-with-dot">
              <span class="dot" :class="statusDotClass(detail.status)" />
              {{ statusText(detail.status) }}
            </span>
          </div>
        </div>
        <div class="order-time">
          <span class="k">下单时间</span>
          <span class="v">{{ detail.createdAt ? new Date(detail.createdAt).toLocaleString() : '—' }}</span>
        </div>
      </section>

      <section class="card progress-card">
        <div class="progress-track">
          <div
            v-for="(step, idx) in progressSteps"
            :key="step.key"
            class="progress-step"
            :class="[`progress-step--${step.state}`]"
          >
            <div class="step-icon-wrap">
              <span v-if="step.state === 'done'" class="step-icon step-icon--check">✓</span>
              <span v-else-if="step.state === 'current'" class="step-icon step-icon--dot">●</span>
              <span v-else class="step-icon step-icon--empty">○</span>
              <span v-if="idx < progressSteps.length - 1" class="step-line" />
            </div>
            <div class="step-label">{{ step.label }}</div>
            <div v-if="step.state === 'current'" class="step-hint">进行中</div>
          </div>
        </div>
      </section>

      <div class="two-col">
        <section class="card kv-card">
          <h3 class="card-h">客户信息</h3>
          <div class="kv-row">
            <span class="kv-k">收货人</span>
            <span class="kv-v">{{ detail.receiverName || '—' }}</span>
          </div>
          <div class="kv-row">
            <span class="kv-k">联系电话</span>
            <span class="kv-v">{{ maskPhone(detail.receiverPhone) }}</span>
          </div>
          <div class="kv-row">
            <span class="kv-k">用户级别</span>
            <span class="kv-v">{{ detail.userLevelLabel || '—' }}</span>
          </div>
        </section>
        <section class="card kv-card">
          <h3 class="card-h">配送地址</h3>
          <div class="kv-row">
            <span class="kv-k">配送地区</span>
            <span class="kv-v">{{ detail.receiverRegion || '—' }}</span>
          </div>
          <div class="kv-row">
            <span class="kv-k">详细地址</span>
            <span class="kv-v">{{ detail.receiverAddress || '—' }}</span>
          </div>
          <div class="kv-row">
            <span class="kv-k">物流单号</span>
            <span class="kv-v">{{ detail.logisticsNo ? String(detail.logisticsNo) : '暂无物流信息' }}</span>
          </div>
        </section>
      </div>

      <section class="card lines-card">
        <h3 class="card-h">本店商品明细</h3>
        <div class="table-wrap">
          <table class="lines-table">
            <thead>
              <tr>
                <th class="col-product">商品</th>
                <th class="col-num">单价</th>
                <th class="col-num">数量</th>
                <th class="col-num">小计</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="line in detail.lines || []" :key="String(line.productId)">
                <td class="col-product">
                  <div class="line-item">
                    <div class="line-item__thumb">
                      <img
                        v-if="lineImageUrl(line)"
                        class="line-item__img"
                        :src="lineImageUrl(line)"
                        :alt="String(line.title || '商品图')"
                        loading="lazy"
                        decoding="async"
                      />
                      <div v-else class="line-item__placeholder" aria-hidden="true">
                        <svg viewBox="0 0 24 24" width="28" height="28" fill="none">
                          <path
                            d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14M9 9h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
                            stroke="currentColor"
                            stroke-width="1.75"
                            stroke-linecap="round"
                            stroke-linejoin="round"
                          />
                        </svg>
                      </div>
                    </div>
                    <div class="line-item__text">
                      <div class="line-item__title">{{ line.title || '—' }}</div>
                      <div v-if="line.productId" class="line-item__id">商品 ID {{ line.productId }}</div>
                    </div>
                  </div>
                </td>
                <td class="col-num">¥{{ formatYuan(line.unitPrice) }}</td>
                <td class="col-num">{{ line.quantity }}</td>
                <td class="col-num line-item__sub">¥{{ formatYuan(line.subtotal) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="lines-foot">
          <span class="total-label">商品总计</span>
          <span class="total-val">¥{{ linesTotal }}</span>
        </div>
      </section>

      <div class="footer-actions no-print">
        <button type="button" class="btn btn-ghost" @click="printSlip">打印发货单</button>
        <button type="button" class="btn btn-ghost" @click="contactCustomer">联系客户</button>
        <button type="button" class="btn btn-primary" @click="updateStatusClick">更新订单状态</button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.merchant-order-detail {
  background: #f4f6f9;
  min-height: 100%;
  padding: 16px 20px 40px;
  box-sizing: border-box;
}

.back-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
  height: 40px;
  padding: 0 18px 0 14px;
  border-radius: 10px;
  border: 1px solid #e2e8f0;
  background: #fff;
  color: #334155;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.01em;
  cursor: pointer;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.06);
  transition:
    border-color 0.15s ease,
    box-shadow 0.15s ease,
    background 0.15s ease,
    color 0.15s ease;
}

.back-btn:hover {
  border-color: #cbd5e1;
  background: #f8fafc;
  color: #0f172a;
}

.back-btn:focus-visible {
  outline: none;
  border-color: #93c5fd;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.22);
}

.back-btn:active {
  transform: translateY(1px);
}

.back-btn__icon {
  flex-shrink: 0;
  color: #64748b;
}

.panel {
  padding: 20px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  font-size: 14px;
  color: #64748b;
}

.panel-err {
  color: #b91c1c;
  background: #fef2f2;
  border-color: #fecaca;
}

.card {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 18px 20px;
  margin-bottom: 16px;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
}

.summary-card .summary-row {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 14px 20px;
}

@media (min-width: 800px) {
  .summary-card .summary-row {
    grid-template-columns: repeat(4, 1fr);
  }
}

.summary-cell .k,
.order-time .k {
  display: block;
  font-size: 11px;
  font-weight: 700;
  color: #94a3b8;
  letter-spacing: 0.04em;
  margin-bottom: 4px;
}

.summary-cell .v,
.order-time .v {
  font-size: 14px;
  color: #334155;
  word-break: break-all;
}

.v-strong {
  font-weight: 800;
  color: #0f172a;
}

.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 13px;
}

.order-time {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid #f1f5f9;
}

.status-with-dot {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}
.dot--muted {
  background: #94a3b8;
}
.dot--warn {
  background: #f59e0b;
}
.dot--ok {
  background: #22c55e;
}
.dot--neutral {
  background: #3b82f6;
}

.progress-card {
  padding-top: 22px;
  padding-bottom: 22px;
}

.progress-track {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 4px;
}

.progress-step {
  flex: 1;
  text-align: center;
  position: relative;
  min-width: 0;
}

.step-icon-wrap {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 8px;
  height: 28px;
}

.step-line {
  position: absolute;
  left: calc(50% + 16px);
  right: calc(-50% + 16px);
  height: 2px;
  background: #e2e8f0;
  top: 50%;
  transform: translateY(-50%);
  z-index: 0;
}

.progress-step--done + .progress-step .step-line,
.progress-step--done .step-line {
  background: #86efac;
}

.step-icon {
  position: relative;
  z-index: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  font-size: 14px;
  line-height: 1;
  background: #fff;
  border: 2px solid #e2e8f0;
  color: #94a3b8;
}

.step-icon--check {
  background: #ecfdf5;
  border-color: #22c55e;
  color: #15803d;
  font-weight: 800;
}

.step-icon--dot {
  border-color: #0f172a;
  color: #0f172a;
  font-size: 10px;
}

.step-icon--empty {
  background: #fff;
}

.step-label {
  font-size: 12px;
  font-weight: 600;
  color: #475569;
  line-height: 1.3;
}

.step-hint {
  font-size: 11px;
  color: #0f172a;
  margin-top: 2px;
}

.two-col {
  display: grid;
  grid-template-columns: 1fr;
  gap: 16px;
}

@media (min-width: 880px) {
  .two-col {
    grid-template-columns: 1fr 1fr;
  }
}

.card-h {
  margin: 0 0 14px;
  font-size: 15px;
  font-weight: 800;
  color: #0f172a;
}

.kv-row {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: 16px;
  padding: 10px 0;
  border-bottom: 1px solid #f1f5f9;
  font-size: 13px;
}

.kv-row:last-child {
  border-bottom: none;
}

.kv-k {
  color: #64748b;
  flex-shrink: 0;
}

.kv-v {
  text-align: right;
  color: #0f172a;
  font-weight: 600;
  word-break: break-word;
}

.lines-card .table-wrap {
  overflow-x: auto;
  margin: 0 -4px;
}

.lines-table {
  width: 100%;
  min-width: 420px;
  border-collapse: collapse;
  font-size: 13px;
}

.lines-table th,
.lines-table td {
  padding: 14px 12px;
  border-bottom: 1px solid #f1f5f9;
  text-align: left;
  vertical-align: middle;
}

.lines-table th {
  font-size: 11px;
  font-weight: 700;
  color: #64748b;
  letter-spacing: 0.04em;
  background: linear-gradient(180deg, #f8fafc 0%, #f1f5f9 100%);
}

.col-product {
  min-width: 200px;
}

.col-num {
  text-align: right;
  white-space: nowrap;
  width: 96px;
}

.line-item {
  display: flex;
  align-items: center;
  gap: 14px;
  min-height: 76px;
}

.line-item__thumb {
  flex-shrink: 0;
  width: 72px;
  height: 72px;
  border-radius: 12px;
  overflow: hidden;
  background: linear-gradient(145deg, #f1f5f9 0%, #e2e8f0 100%);
  border: 1px solid #e2e8f0;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.05);
}

.line-item__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.line-item__placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #94a3b8;
}

.line-item__text {
  min-width: 0;
  flex: 1;
}

.line-item__title {
  font-weight: 700;
  color: #0f172a;
  line-height: 1.45;
  font-size: 14px;
}

.line-item__id {
  margin-top: 6px;
  font-size: 12px;
  font-weight: 600;
  color: #64748b;
  font-variant-numeric: tabular-nums;
}

.line-item__sub {
  font-weight: 800;
  color: #0f172a;
}

.lines-foot {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 12px;
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid #f1f5f9;
}

.total-label {
  font-size: 13px;
  color: #64748b;
  font-weight: 600;
}

.total-val {
  font-size: 16px;
  font-weight: 800;
  color: #0f172a;
}

.footer-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 8px;
}

.btn {
  min-height: 40px;
  padding: 0 18px;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  border: 1px solid transparent;
}

.btn-ghost {
  background: #fff;
  border-color: #cbd5e1;
  color: #334155;
}

.btn-ghost:hover {
  background: #f8fafc;
}

.btn-primary {
  background: #0f172a;
  border-color: #0f172a;
  color: #fff;
}

@media print {
  .no-print,
  .back-btn,
  .footer-actions {
    display: none !important;
  }
  .merchant-order-detail {
    background: #fff;
  }
}
</style>
