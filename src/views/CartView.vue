<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../utils/request'
import { showAppMessage } from '../utils/appMessage'
import ConfirmModal from '../components/ConfirmModal.vue'

const router = useRouter()
const cartItems = ref([])
const userId = ref(Number(localStorage.getItem('userId') || 0))
const loading = ref(false)
const errorMsg = ref('')
/** 结算确认弹层 */
const checkoutOpen = ref(false)
const checkoutSubmitting = ref(false)
/** 非空时表示仅结算该 merchantId 对应店铺的行（跨店购物车） */
const checkoutTargetMerchantId = ref(null)
/** 删除购物车项确认 */
const removeOpen = ref(false)
const removeCartId = ref(null)
const removeSubmitting = ref(false)
/** 「一键结算全部」确认弹层（跨店一次操作、连续多笔订单） */
const bulkCheckoutOpen = ref(false)
const bulkCheckoutSubmitting = ref(false)

/**
 * 按商家分组购物车行（依赖接口返回的 merchantId）。
 */
const cartByMerchant = computed(() => {
  const list = Array.isArray(cartItems.value) ? cartItems.value : []
  const map = new Map()
  for (const row of list) {
    const mid = Number(row.merchantId || 0)
    const key = mid || 0
    if (!map.has(key)) {
      map.set(key, {
        merchantId: key,
        label: String(row.merchantShopName || '').trim() || (key ? `商家 #${key}` : '店铺'),
        rows: [],
      })
    }
    map.get(key).rows.push(row)
  }
  return Array.from(map.values()).map((g) => ({
    ...g,
    itemCount: g.rows.reduce((s, r) => s + Number(r.quantity || 0), 0),
    subtotal: g.rows.reduce((s, r) => s + Number(r.subtotal || 0), 0).toFixed(2),
  }))
})

/** 是否跨多个商家（需分单结算） */
const isMultiMerchant = computed(() => cartByMerchant.value.length > 1)

/** 可参与结算的店铺（已关联 merchantId） */
const bulkPayableGroups = computed(() => cartByMerchant.value.filter((g) => g.merchantId > 0))

/** 至少两家店才可「一键结算全部」 */
const canBulkCheckout = computed(() => bulkPayableGroups.value.length >= 2)

const totalAmount = computed(() => {
  return cartItems.value.reduce((sum, item) => sum + Number(item.subtotal), 0).toFixed(2)
})

const totalItems = computed(() => {
  return cartItems.value.reduce((sum, item) => sum + item.quantity, 0)
})

const checkoutModalTitle = computed(() => '确认结算')

/**
 * 统一渲染：单店为一段商品行；多店为「浅条带 + 商品行」同一列表，无独立结算区。
 */
const cartSections = computed(() => {
  const list = cartItems.value || []
  if (!list.length) return []
  if (!isMultiMerchant.value) {
    return [{ key: 'all', showStripe: false, label: '', rows: list, itemCount: 0, subtotal: '' }]
  }
  return (cartByMerchant.value || []).map((g) => ({
    key: `m-${g.merchantId}`,
    showStripe: true,
    label: g.label,
    rows: g.rows,
    itemCount: g.itemCount,
    subtotal: g.subtotal,
  }))
})

/** 当前结算弹层展示的件数、小计、店铺名 */
const checkoutSummary = computed(() => {
  const mid = checkoutTargetMerchantId.value
  if (mid != null && mid > 0) {
    const g = cartByMerchant.value.find((x) => x.merchantId === mid)
    if (g) {
      return { itemCount: g.itemCount, subtotal: g.subtotal, label: g.label }
    }
  }
  return {
    itemCount: totalItems.value,
    subtotal: totalAmount.value,
    label: '当前购物车',
  }
})

onMounted(async () => {
  if (!userId.value) {
    showAppMessage('请先登录', '提示')
    router.push('/login')
    return
  }
  await loadCart()
})

async function loadCart() {
  loading.value = true
  errorMsg.value = ''
  const res = await api.getCart(userId.value)
  if (res.code === 200) {
    cartItems.value = res.data
  } else {
    errorMsg.value = res.message || '购物车加载失败'
    cartItems.value = []
  }
  loading.value = false
}

async function updateQuantity(cartId, quantity) {
  if (quantity < 0) return
  
  const res = await api.updateCart({ cartId, quantity })
  if (res.code === 200) {
    await loadCart()
  } else {
    showAppMessage(res.message || '更新失败', '提示')
  }
}

/**
 * 打开删除确认弹层。
 * @param {number} cartId 购物车行 ID
 */
function removeItem(cartId) {
  removeCartId.value = cartId
  removeOpen.value = true
}

/**
 * 确认删除购物车中的一条记录。
 */
async function confirmRemoveCartItem() {
  const cartId = removeCartId.value
  if (cartId == null || removeSubmitting.value) return
  removeSubmitting.value = true
  const res = await api.removeFromCart(cartId)
  removeSubmitting.value = false
  if (res.code === 200) {
    removeOpen.value = false
    removeCartId.value = null
    await loadCart()
  } else {
    showAppMessage(res.message || '删除失败', '提示')
  }
}

/**
 * 打开结算确认：单店走单笔订单；多店走「一次确认、连续提交」（无分按钮）。
 */
function checkout() {
  if (!cartItems.value.length) return
  if (isMultiMerchant.value) {
    if (!canBulkCheckout.value) {
      showAppMessage('部分商品未关联店铺，暂无法结算，请联系客服', '提示')
      return
    }
    bulkCheckoutOpen.value = true
    return
  }
  checkoutTargetMerchantId.value = null
  checkoutOpen.value = true
}

/**
 * @param {boolean} v
 */
function setCheckoutOpen(v) {
  checkoutOpen.value = v
  if (!v) checkoutTargetMerchantId.value = null
}

/**
 * 用户确认后从购物车生成订单（可带 merchantId 仅结算该店）。
 */
async function submitCheckout() {
  if (!cartItems.value.length || checkoutSubmitting.value) return
  checkoutSubmitting.value = true
  const payload = { userId: userId.value }
  const mid = checkoutTargetMerchantId.value
  if (mid != null && mid > 0) {
    payload.merchantId = mid
  }
  const res = await api.userCreateOrderFromCart(payload)
  checkoutSubmitting.value = false
  if (res.code === 200) {
    checkoutOpen.value = false
    checkoutTargetMerchantId.value = null
    await loadCart()
    router.push(`/order/${res.data.orderId}`)
  } else {
    showAppMessage(res.message || '下单失败', '提示')
  }
}

/**
 * 多店购物车：一次确认后按供货顺序连续提交（界面为单次结算）。
 */
async function submitBulkCheckout() {
  const groups = bulkPayableGroups.value
  if (groups.length < 2 || bulkCheckoutSubmitting.value) return
  bulkCheckoutSubmitting.value = true
  let nOk = 0
  for (const g of groups) {
    const res = await api.userCreateOrderFromCart({
      userId: userId.value,
      merchantId: g.merchantId,
    })
    if (res.code !== 200) {
      bulkCheckoutSubmitting.value = false
      bulkCheckoutOpen.value = false
      showAppMessage(
        res.message ||
          (nOk > 0 ? `已成功 ${nOk} 笔，后续提交失败，请刷新购物车后重试` : '下单失败'),
        '提示',
      )
      await loadCart()
      return
    }
    nOk += 1
  }
  bulkCheckoutSubmitting.value = false
  bulkCheckoutOpen.value = false
  await loadCart()
  router.push({ path: '/orders', query: { from: 'bulk-checkout', n: String(nOk) } })
}
</script>

<template>
  <div class="pw-page cart-page">
    <section class="pw-hero cart-hero">
      <h1 class="pw-title">我的购物车</h1>
      <p class="pw-lead">共 {{ totalItems }} 件商品</p>
    </section>

    <div v-if="loading" class="pw-state">购物车加载中...</div>

    <div v-else-if="errorMsg" class="pw-state pw-state--error">{{ errorMsg }}</div>

    <div v-else-if="cartItems.length === 0" class="pw-state pw-state--empty">
      <div class="cart-empty-title">购物车是空的</div>
      <div class="cart-empty-lead">快去选购心仪的商品吧</div>
      <button type="button" class="pw-btn pw-btn-sm" @click="$router.push('/')">去逛逛</button>
    </div>

    <div v-else class="cart-content">
      <section class="pw-section cart-table">
        <div class="table-header">
          <div class="col-product">商品信息</div>
          <div class="col-price">单价</div>
          <div class="col-quantity">数量</div>
          <div class="col-subtotal">小计</div>
          <div class="col-action">操作</div>
        </div>

        <div class="cart-items">
          <div v-for="section in cartSections" :key="section.key" class="cart-section-wrap">
            <div v-if="section.showStripe" class="cart-shop-stripe">
              <span class="cart-shop-name">{{ section.label }}</span>
              <span class="cart-shop-meta">{{ section.itemCount }} 件 · ¥{{ section.subtotal }}</span>
            </div>
            <div v-for="item in section.rows" :key="item.cartId" class="cart-item">
              <div class="col-product">
                <div class="item-image">
                  <img v-if="item.imageUrl" :src="item.imageUrl" class="item-img" alt="商品图片" />
                  <div v-else class="item-placeholder">暂无图片</div>
                </div>
                <div class="item-details">
                  <h3 class="item-title">{{ item.title }}</h3>
                </div>
              </div>
              <div class="col-price">
                <span class="price">¥{{ item.price }}</span>
              </div>
              <div class="col-quantity">
                <div class="quantity-control">
                  <button
                    class="qty-btn"
                    @click="updateQuantity(item.cartId, item.quantity - 1)"
                    :disabled="item.quantity <= 0"
                  >
                    −
                  </button>
                  <input type="text" class="qty-input" :value="item.quantity" readonly />
                  <button class="qty-btn" @click="updateQuantity(item.cartId, item.quantity + 1)">+</button>
                </div>
              </div>
              <div class="col-subtotal">
                <span class="subtotal">¥{{ item.subtotal }}</span>
              </div>
              <div class="col-action">
                <button type="button" class="remove-btn" @click="removeItem(item.cartId)">删除</button>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section class="pw-section cart-summary">
        <div class="summary-content">
          <div class="summary-row">
            <span class="summary-label">商品总数：</span>
            <span class="summary-value">{{ totalItems }} 件</span>
          </div>
          <div class="summary-row total-row">
            <span class="summary-label">应付总额：</span>
            <span class="total-amount">¥{{ totalAmount }}</span>
          </div>
          <button
            type="button"
            class="pw-btn checkout-btn"
            :disabled="isMultiMerchant && !canBulkCheckout"
            @click="checkout"
          >
            立即结算
          </button>
          <p v-if="isMultiMerchant && !canBulkCheckout" class="cart-multi-hint">
            部分商品未关联店铺，暂无法结算，请联系客服处理。
          </p>
        </div>
      </section>
    </div>

    <ConfirmModal
      :open="checkoutOpen"
      :title="checkoutModalTitle"
      confirm-label="确认下单"
      :loading="checkoutSubmitting"
      @update:open="setCheckoutOpen"
      @confirm="submitCheckout"
    >
      <p>
        共 <strong>{{ checkoutSummary.itemCount }}</strong> 件商品，应付 <strong>¥{{ checkoutSummary.subtotal }}</strong>。确认生成订单？
      </p>
    </ConfirmModal>

    <ConfirmModal
      :open="bulkCheckoutOpen"
      title="确认结算"
      confirm-label="确认下单"
      :loading="bulkCheckoutSubmitting"
      @update:open="bulkCheckoutOpen = $event"
      @confirm="submitBulkCheckout"
    >
      <p>
        共 <strong>{{ totalItems }}</strong> 件商品，应付合计 <strong>¥{{ totalAmount }}</strong>。确认后将为您生成订单并进入「我的订单」。
      </p>
      <p class="cart-bulk-modal-note">订单生成后即可在列表中查看与支付。</p>
    </ConfirmModal>

    <ConfirmModal
      :open="removeOpen"
      title="删除商品"
      confirm-label="删除"
      :loading="removeSubmitting"
      @update:open="removeOpen = $event"
      @confirm="confirmRemoveCartItem"
    >
      <p>确定从购物车中移除该商品吗？此操作可稍后重新加入购物车。</p>
    </ConfirmModal>
  </div>
</template>

<style scoped>
.cart-page {
  width: 100%;
  margin: 0 auto;
}

.cart-hero {
  margin-bottom: 14px;
}

.cart-empty-title {
  font-size: 14px;
  font-weight: 800;
  color: #131e30;
  margin-bottom: 6px;
}

.cart-empty-lead {
  font-size: 12px;
  color: #6b7b91;
  margin-bottom: 14px;
  text-align: center;
}

.cart-content {
  display: grid;
  grid-template-columns: 1fr 320px;
  gap: 16px;
}

.cart-table {
  background: #f9fbfe;
  border-radius: 2px;
  overflow: hidden;
  border: 1px solid #dce4ef;
}

.table-header {
  display: grid;
  grid-template-columns: 2fr 1fr 1.2fr 1fr 0.8fr;
  padding: 16px 20px;
  background: #f1f4f8;
  font-weight: 500;
  color: #666;
  font-size: 14px;
  border-bottom: 1px solid #dce4ef;
}

.cart-items {
  display: flex;
  flex-direction: column;
  max-height: 600px;
  overflow-y: auto;
}

.cart-section-wrap {
  display: contents;
}

.cart-item {
  display: grid;
  grid-template-columns: 2fr 1fr 1.2fr 1fr 0.8fr;
  padding: 20px;
  border-bottom: 1px solid #f0f0f0;
  align-items: center;
  transition: background 0.2s;
}

.cart-item:hover {
  background: #f4f7fb;
}

.col-product {
  display: flex;
  align-items: center;
  gap: 12px;
}

.item-image {
  width: 70px;
  height: 70px;
  background: #fafafa;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: #bbb;
  flex-shrink: 0;
  border: 1px solid #e8e8e8;
}

.item-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  border-radius: 4px;
}

.item-placeholder {
  font-size: 12px;
  color: #bbb;
}

.item-details {
  flex: 1;
}

.item-title {
  font-size: 14px;
  color: #333;
  font-weight: 400;
  line-height: 1.5;
}

.col-price .price {
  font-size: 16px;
  color: #333;
}

.quantity-control {
  display: flex;
  align-items: center;
  gap: 0;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  overflow: hidden;
  width: fit-content;
}

.qty-btn {
  width: 32px;
  height: 32px;
  border: none;
  background: white;
  cursor: pointer;
  font-size: 16px;
  color: #666;
  transition: all 0.2s;
}

.qty-btn:hover:not(:disabled) {
  background: #f5f5f5;
  color: #1890ff;
}

.qty-btn:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}

.qty-input {
  width: 50px;
  height: 32px;
  border: none;
  border-left: 1px solid #d9d9d9;
  border-right: 1px solid #d9d9d9;
  text-align: center;
  font-size: 14px;
}

.col-subtotal .subtotal {
  font-size: 18px;
  color: #ff4d4f;
  font-weight: 500;
}

.remove-btn {
  padding: 6px 12px;
  background: white;
  color: #ff4d4f;
  border: 1px solid #ff4d4f;
  border-radius: 4px;
  cursor: pointer;
  font-size: 12px;
  transition: all 0.2s;
}

.remove-btn:hover {
  background: #ff4d4f;
  color: white;
}

.cart-summary {
  background: #f9fbfe;
  border-radius: 2px;
  padding: 24px;
  border: 1px solid #dce4ef;
  height: fit-content;
  position: sticky;
  top: 80px;
}

.summary-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.summary-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 0;
  font-size: 14px;
}

.summary-label {
  color: #666;
}

.summary-value {
  color: #333;
}

.total-row {
  border-top: 1px solid #e8e8e8;
  padding-top: 16px;
  margin-top: 8px;
}

.total-row .summary-label {
  font-size: 16px;
  color: #333;
  font-weight: 500;
}

.total-amount {
  font-size: 28px;
  color: #ff4d4f;
  font-weight: 500;
}

.checkout-btn {
  width: 100%;
  padding: 12px;
  background: #0b1630;
  color: #f3f7ff;
  border: none;
  border-radius: 4px;
  font-size: 16px;
  cursor: pointer;
  transition: all 0.2s;
  margin-top: 8px;
}

.checkout-btn:hover:not(:disabled) {
  background: #162b4d;
}

.checkout-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.cart-shop-stripe {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  padding: 10px 20px;
  background: #f4f7fb;
  border-top: 1px solid #e8ecf2;
  border-bottom: 1px solid #e8ecf2;
  font-size: 12px;
}

.cart-items > .cart-shop-stripe:first-child {
  border-top: none;
}

.cart-shop-name {
  font-weight: 800;
  color: #2a3b52;
}

.cart-shop-meta {
  color: #6b7b91;
  font-weight: 600;
}

.cart-multi-hint {
  margin: 0;
  padding: 12px 0 0;
  font-size: 13px;
  line-height: 1.6;
  color: #506078;
  text-align: left;
}

.cart-bulk-modal-note {
  font-size: 13px;
  color: #506078;
  margin: 0;
}
</style>
