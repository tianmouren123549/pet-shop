<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../utils/request'

const route = useRoute()
const router = useRouter()
const userId = ref(Number(localStorage.getItem('userId') || 0))
const loading = ref(false)
const errorMsg = ref('')
const order = ref(null)

function statusText(s) {
  return s === 'CREATED'
    ? '待支付'
    : s === 'PAID'
      ? '已支付'
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

async function pay() {
  if (!order.value) return
  const ok = confirm('模拟支付：确认支付该订单吗？')
  if (!ok) return
  const res = await api.userPayOrder(order.value.orderId, userId.value)
  if (res.code === 200) {
    await loadOrder()
  } else {
    alert(res.message || '支付失败')
  }
}

async function cancel() {
  if (!order.value) return
  const ok = confirm('确定要取消该订单吗？')
  if (!ok) return
  const res = await api.userCancelOrder(order.value.orderId, userId.value)
  if (res.code === 200) {
    await loadOrder()
  } else {
    alert(res.message || '取消失败')
  }
}

async function confirmReceive() {
  if (!order.value) return
  const ok = confirm('确认已收到货物？')
  if (!ok) return
  const res = await api.userConfirmOrder(order.value.orderId, userId.value)
  if (res.code === 200) {
    await loadOrder()
  } else {
    alert(res.message || '操作失败')
  }
}

function contactMerchant() {
  if (!order.value || !Array.isArray(order.value.items) || order.value.items.length === 0) {
    alert('未找到商家信息')
    return
  }
  const merchantId = Number(order.value.items[0].merchantId || 1)
  router.push(`/support?merchantId=${merchantId}&orderId=${order.value.orderId}`)
}

onMounted(async () => {
  if (!userId.value) {
    alert('请先登录')
    router.push('/login')
    return
  }
  await loadOrder()
})
</script>

<template>
  <div class="pw-page order-page">
    <div class="pw-section order-breadcrumb">
      <span class="order-breadcrumb-link" @click="$router.push('/orders')">我的订单</span>
      <span class="order-breadcrumb-sep">/</span>
      <span class="order-breadcrumb-current">订单详情</span>
    </div>

    <div v-if="loading" class="pw-state">订单加载中...</div>
    <div v-else-if="errorMsg" class="pw-state pw-state--error">
      <div class="order-err-title">{{ errorMsg }}</div>
      <button type="button" class="pw-btn pw-btn-sm" @click="loadOrder">重试</button>
    </div>

    <template v-else-if="order">
      <section class="pw-hero order-hero">
        <div class="left">
          <div class="no">订单号：{{ order.orderNo }}</div>
          <div class="meta">
            <span>下单时间：{{ new Date(order.createdAt).toLocaleString() }}</span>
            <span>更新时间：{{ new Date(order.updatedAt).toLocaleString() }}</span>
          </div>
        </div>
        <div class="right">
          <div class="amount">¥{{ order.payAmount }}</div>
          <span class="status" :class="order.status.toLowerCase()">{{ statusText(order.status) }}</span>
        </div>
      </section>

      <section class="pw-section panel">
        <div class="panel-title">商品清单</div>
        <div class="items">
          <div v-for="it in order.items" :key="`${it.productId}`" class="item">
            <div class="img">
              <img v-if="it.imageUrl" :src="it.imageUrl" class="item-img" alt="商品图片" />
              <div v-else class="placeholder">暂无图片</div>
            </div>
            <div class="info">
              <div class="title">{{ it.title }}</div>
              <div class="sub">
                <span>单价：¥{{ Number(it.price).toFixed(2) }}</span>
                <span>数量：{{ it.quantity }}</span>
              </div>
            </div>
            <div class="subtotal">¥{{ Number(it.subtotal).toFixed(2) }}</div>
          </div>
        </div>
      </section>

      <section class="pw-section panel actions">
        <button type="button" class="pw-btn-ghost pw-btn-sm" @click="$router.push('/orders')">返回列表</button>
        <button type="button" class="pw-btn-ghost pw-btn-sm" @click="contactMerchant">联系商家</button>
        <div class="spacer"></div>
        <button v-if="canCancel" type="button" class="order-danger" @click="cancel">取消订单</button>
        <button v-if="canPay" type="button" class="pw-btn pw-btn-sm" @click="pay">立即支付</button>
        <button v-if="canConfirm" type="button" class="pw-btn pw-btn-sm" @click="confirmReceive">确认收货</button>
      </section>

      <section class="pw-section tips">
        <div class="tip-title">说明</div>
        <ul>
          <li>这是前端 Mock 的订单流转：CREATED → PAID → SHIPPED → COMPLETED。</li>
          <li>管理端可在“订单管理”里把已支付订单改为发货/完成。</li>
        </ul>
      </section>
    </template>
  </div>
</template>

<style scoped>
.order-page { width: 100%; margin: 0 auto; }
.order-breadcrumb { margin-bottom: 12px; padding: 10px 12px; }
.order-breadcrumb-link { cursor: pointer; font-weight: 700; color: #16355f; text-decoration: underline; text-underline-offset: 2px; }
.order-breadcrumb-link:hover { color: #0b1630; }
.order-breadcrumb-sep { margin: 0 8px; color: #b8c3d3; }
.order-breadcrumb-current { color: #506078; font-weight: 700; }

.order-hero { display: flex; justify-content: space-between; gap: 16px; align-items: flex-start; }
.no { font-weight: 900; margin-bottom: 8px; color: #131e30; }
.meta { display: flex; flex-wrap: wrap; gap: 14px; font-size: 12px; color: #7b8798; }
.right { display: flex; flex-direction: column; align-items: flex-end; gap: 8px; }
.amount { font-size: 20px; font-weight: 900; color: #0b1630; }
.status { padding: 2px 10px; border-radius: 2px; font-size: 11px; font-weight: 800; border: 1px solid #d9e1ec; background: #eef2f7; color: #304862; }
.status.created { background: #faf5f5; border-color: #e8d4d4; color: #8a1d1d; }
.status.paid { background: #e8ecf2; border-color: #c9d4e4; color: #23344f; }
.status.shipped { background: #f1f3f6; border-color: #dbe3ee; color: #23344f; }
.status.completed { background: #e8ecf2; border-color: #c9d4e4; color: #23344f; }
.status.cancelled { background: #faf5f5; border-color: #e8d4d4; color: #8a1d1d; }

.panel {
  background: #f9fbfe;
  border: 1px solid #d9e2ee;
  border-radius: 2px;
  padding: 16px;
  margin-bottom: 16px;
  color: #2a3b52;
}
.panel.error { color: #a73636; border-color: #f0c1c1; background: #fff1f1; }
.order-err-title { font-weight: 800; margin-bottom: 10px; }
.panel-title { font-weight: 900; color: #0e1d33; margin-bottom: 12px; }

.items { display: flex; flex-direction: column; gap: 12px; }
.item {
  display: grid;
  grid-template-columns: 82px 1fr 120px;
  gap: 12px;
  align-items: center;
  padding: 12px;
  border: 1px solid #e6edf6;
  background: #f4f7fb;
  border-radius: 2px;
}
.img {
  width: 82px; height: 66px;
  display: flex; align-items: center; justify-content: center;
  font-size: 12px; color: #b8c3d3;
  border: 1px solid #dde6f2; background: #fff;
}

.item-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.placeholder {
  font-size: 12px;
  color: #b8c3d3;
}
.title { font-weight: 800; color: #0e1d33; line-height: 1.35; }
.title {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  word-break: break-all;
}
.sub { margin-top: 6px; display: flex; gap: 14px; flex-wrap: wrap; font-size: 12px; color: #6c7d93; }
.subtotal { text-align: right; font-weight: 900; color: #0b1730; }

.actions { display: flex; gap: 10px; align-items: center; }
.spacer { flex: 1; }
.order-danger {
  height: 32px;
  padding: 0 12px;
  border-radius: 2px;
  cursor: pointer;
  font-weight: 800;
  font-size: 12px;
  border: 1px solid #e8d4d4;
  background: #faf5f5;
  color: #8a1d1d;
}
.order-danger:hover {
  border-color: #cfa9a9;
}
.ghost, .primary, .danger {
  height: 34px;
  padding: 0 14px;
  border-radius: 2px;
  cursor: pointer;
  font-weight: 800;
  font-size: 12px;
}
.ghost { border: 1px solid #cdd8e7; background: #f4f8fd; color: #2b3d58; }
.primary { border: 1px solid #0b1630; background: #0b1630; color: #f4f6fb; }
.danger { border: 1px solid #ff4d4f; background: #fff1f1; color: #a73636; }

.tips {
  background: #f7f9fc;
  border: 1px dashed #d9e1ec;
  border-radius: 2px;
  padding: 14px 16px;
  color: #5e6e84;
  font-size: 12px;
}
.tip-title { font-weight: 900; margin-bottom: 8px; color: #0e1d33; }
.tips ul { padding-left: 18px; line-height: 1.8; }
</style>

