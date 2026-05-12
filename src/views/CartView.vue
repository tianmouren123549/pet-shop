<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../utils/request'
import { formatYuan } from '../utils/formatYuan.js'
import { showAppMessage } from '../utils/appMessage'
import ConfirmModal from '../components/ConfirmModal.vue'
import AppImage from '../components/AppImage.vue'

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

/** 结算时收货信息（写入订单快照） */
const checkoutReceiverName = ref('')
const checkoutReceiverPhone = ref('')
const checkoutReceiverRegion = ref('')
const checkoutReceiverAddress = ref('')
/** 选用地址簿：非 manual 时为具体 addressId 字符串 */
const checkoutAddressSelect = ref('manual')
const checkoutAddressId = ref(null)
const savedAddresses = ref([])

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
    subtotal: formatYuan(g.rows.reduce((s, r) => s + Number(r.subtotal || 0), 0)),
  }))
})

/** 是否跨多个商家（需分单结算） */
const isMultiMerchant = computed(() => cartByMerchant.value.length > 1)

/** 可参与结算的店铺（已关联 merchantId） */
const bulkPayableGroups = computed(() => cartByMerchant.value.filter((g) => g.merchantId > 0))

/** 至少两家店才可「一键结算全部」 */
const canBulkCheckout = computed(() => bulkPayableGroups.value.length >= 2)

const totalAmount = computed(() => {
  return formatYuan(cartItems.value.reduce((sum, item) => sum + Number(item.subtotal), 0))
})

const totalItems = computed(() => {
  return cartItems.value.reduce((sum, item) => sum + item.quantity, 0)
})

/** 商品小计（元，数值） */
const subtotalNumeric = computed(() =>
  cartItems.value.reduce((sum, it) => sum + (Number(it.subtotal) || 0), 0)
)

/** 演示物流费：满 299 免运费，否则 ¥15 */
const shippingFeeYuan = computed(() => {
  const s = subtotalNumeric.value
  if (s <= 0) return 0
  return s >= 299 ? 0 : 15
})

/** 侧栏展示「应付」= 商品小计 + 演示物流费（与商品列表/详情页一致的整数元展示） */
const payableEstimateYuan = computed(() =>
  Math.max(0, Math.round(subtotalNumeric.value + shippingFeeYuan.value))
)

function goProduct(productId) {
  router.push(`/product/${productId}`)
}

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

async function prefetchProfileForCheckout() {
  if (!userId.value) return
  const res = await api.userGetProfile(userId.value)
  if (res.code !== 200) return
  const p = res.data || {}
  if (!String(checkoutReceiverName.value || '').trim() && p.nickname) {
    checkoutReceiverName.value = String(p.nickname)
  }
  if (!String(checkoutReceiverPhone.value || '').trim() && p.phone) {
    checkoutReceiverPhone.value = String(p.phone)
  }
}

async function loadSavedAddresses() {
  if (!userId.value) return
  const res = await api.userListAddresses(userId.value)
  if (res.code !== 200) return
  savedAddresses.value = Array.isArray(res.data) ? res.data : []
}

function applySavedAddress(addr) {
  if (!addr) {
    checkoutAddressId.value = null
    return
  }
  checkoutAddressId.value = addr.addressId
  checkoutReceiverName.value = String(addr.receiverName || '')
  checkoutReceiverPhone.value = String(addr.receiverPhone || '')
  checkoutReceiverRegion.value = String(addr.receiverRegion || '')
  checkoutReceiverAddress.value = String(addr.receiverDetail || '')
}

function pickDefaultSavedAddress() {
  const list = savedAddresses.value || []
  const def = list.find((a) => Number(a.isDefault) === 1)
  if (def) {
    checkoutAddressSelect.value = String(def.addressId)
    applySavedAddress(def)
    return
  }
  checkoutAddressSelect.value = 'manual'
  checkoutAddressId.value = null
}

function onCheckoutAddressPick() {
  const v = checkoutAddressSelect.value
  if (!v || v === 'manual') {
    checkoutAddressId.value = null
    return
  }
  const addr = savedAddresses.value.find((a) => String(a.addressId) === String(v))
  if (addr) applySavedAddress(addr)
}

watch(checkoutOpen, async (v) => {
  if (!v) return
  await prefetchProfileForCheckout()
  await loadSavedAddresses()
  pickDefaultSavedAddress()
})
watch(bulkCheckoutOpen, async (v) => {
  if (!v) return
  await prefetchProfileForCheckout()
  await loadSavedAddresses()
  pickDefaultSavedAddress()
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
  if (quantity < 1) return

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
  const aid = checkoutAddressId.value ? Number(checkoutAddressId.value) : 0
  const region = String(checkoutReceiverRegion.value || '').trim()
  const addr = String(checkoutReceiverAddress.value || '').trim()
  if (!(aid > 0) && (!region || !addr)) {
    showAppMessage('请选择地址簿或填写配送地区与详细地址', '提示')
    return
  }
  checkoutSubmitting.value = true
  const payload = {
    userId: userId.value,
    receiverName: String(checkoutReceiverName.value || '').trim(),
    receiverPhone: String(checkoutReceiverPhone.value || '').trim(),
    receiverRegion: region,
    receiverAddress: addr,
  }
  if (aid > 0) payload.addressId = aid
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
  const aid = checkoutAddressId.value ? Number(checkoutAddressId.value) : 0
  const region = String(checkoutReceiverRegion.value || '').trim()
  const addr = String(checkoutReceiverAddress.value || '').trim()
  if (!(aid > 0) && (!region || !addr)) {
    showAppMessage('请选择地址簿或填写配送地区与详细地址', '提示')
    return
  }
  bulkCheckoutSubmitting.value = true
  let nOk = 0
  const basePayload = {
    userId: userId.value,
    receiverName: String(checkoutReceiverName.value || '').trim(),
    receiverPhone: String(checkoutReceiverPhone.value || '').trim(),
    receiverRegion: region,
    receiverAddress: addr,
  }
  if (aid > 0) basePayload.addressId = aid
  for (const g of groups) {
    const res = await api.userCreateOrderFromCart({
      ...basePayload,
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
  <div class="pw-page cart-page cart-page--apex">
    <header class="cart-apex-head">
      <p class="cart-apex-kicker">Shopping Cart · 购物车</p>
      <p class="cart-apex-lead">共 {{ totalItems }} 件商品，核对数量与金额后可发起结算</p>
    </header>

    <div v-if="loading" class="cart-apex-state">购物车加载中…</div>

    <div v-else-if="errorMsg" class="cart-apex-state cart-apex-state--error">{{ errorMsg }}</div>

    <div v-else-if="cartItems.length === 0" class="cart-apex-empty">
      <p class="cart-apex-empty-title">购物车是空的</p>
      <p class="cart-apex-empty-lead">将心仪商品加入购物车，在此统一核对与结算。</p>
      <button type="button" class="cart-apex-btn cart-apex-btn--primary" @click="router.push('/products')">
        去选购
      </button>
    </div>

    <div v-else class="cart-apex-layout">
      <main class="cart-apex-main">
        <section class="cart-apex-panel">
          <div class="cart-apex-panel-head">
            <div>
              <h2 class="cart-apex-h2">购物车商品</h2>
              <p class="cart-apex-h2-en">Items in your cart</p>
            </div>
            <span class="cart-apex-count">{{ totalItems }} 件</span>
          </div>

          <div class="cart-apex-list">
            <div v-for="section in cartSections" :key="section.key" class="cart-apex-section">
              <div v-if="section.showStripe" class="cart-apex-stripe">
                <span class="cart-apex-stripe-name">{{ section.label }}</span>
                <span class="cart-apex-stripe-meta">{{ section.itemCount }} 件 · ¥{{ section.subtotal }}</span>
              </div>

              <article
                v-for="item in section.rows"
                :key="item.cartId"
                class="cart-apex-card"
              >
                <button type="button" class="cart-apex-thumb" @click="goProduct(item.productId)">
                  <AppImage
                    v-if="item.imageUrl"
                    :src="item.imageUrl"
                    class="cart-apex-thumb-img"
                    alt=""
                    loading="lazy"
                    decoding="async"
                  />
                  <span v-else class="cart-apex-thumb-ph">暂无图</span>
                </button>

                <div class="cart-apex-card-body">
                  <div class="cart-apex-card-top">
                    <div class="cart-apex-card-text">
                      <span class="cart-apex-tag">{{ item.merchantShopName || '严选' }}</span>
                      <h3 class="cart-apex-item-title">
                        <button type="button" class="cart-apex-title-btn" @click="goProduct(item.productId)">
                          {{ item.title }}
                        </button>
                      </h3>
                      <p class="cart-apex-sku">SKU · 商品 ID {{ item.productId }}</p>
                      <p class="cart-apex-desc">
                        由商家备货发货；价格以加入购物车时的标价为准，结算前请再次确认数量与地址。
                      </p>
                    </div>
                    <div class="cart-apex-price-block">
                      <span class="cart-apex-price">¥{{ formatYuan(item.price) }}</span>
                      <span class="cart-apex-line">单价</span>
                    </div>
                  </div>

                  <div class="cart-apex-card-foot">
                    <div class="cart-apex-qty" aria-label="数量">
                      <button
                        type="button"
                        class="cart-apex-qty-btn"
                        :disabled="item.quantity <= 1"
                        @click="updateQuantity(item.cartId, item.quantity - 1)"
                      >
                        −
                      </button>
                      <span class="cart-apex-qty-num">{{ String(item.quantity).padStart(2, '0') }}</span>
                      <button type="button" class="cart-apex-qty-btn" @click="updateQuantity(item.cartId, item.quantity + 1)">
                        +
                      </button>
                    </div>
                    <div class="cart-apex-actions">
                      <span class="cart-apex-line-total">小计 ¥{{ formatYuan(item.subtotal) }}</span>
                      <button type="button" class="cart-apex-link" @click="router.push('/products')">继续逛</button>
                      <span class="cart-apex-sep" aria-hidden="true">|</span>
                      <button type="button" class="cart-apex-link cart-apex-link--danger" @click="removeItem(item.cartId)">
                        移除
                      </button>
                    </div>
                  </div>
                </div>
              </article>
            </div>
          </div>
        </section>

        <section class="cart-apex-panel cart-apex-panel--soft">
          <h2 class="cart-apex-h2 cart-apex-h2--sm">更多选购</h2>
          <p class="cart-apex-muted">搭配耗材、常购品可在商城继续加入购物车。</p>
          <button type="button" class="cart-apex-btn cart-apex-btn--ghost" @click="router.push('/products')">
            浏览全部商品
          </button>
        </section>
      </main>

      <aside class="cart-apex-aside">
        <div class="cart-apex-manifest">
          <h3 class="cart-apex-h3">结算清单</h3>
          <p class="cart-apex-h3-en">Order manifest</p>

          <dl class="cart-apex-rows">
            <div class="cart-apex-row">
              <dt>商品小计</dt>
              <dd>¥{{ formatYuan(subtotalNumeric) }}</dd>
            </div>
            <div class="cart-apex-row">
              <dt>物流费</dt>
              <dd>{{ shippingFeeYuan === 0 ? '¥0（已满额）' : `¥${shippingFeeYuan}` }}</dd>
            </div>
          </dl>

          <div class="cart-apex-total-block">
            <span class="cart-apex-total-label">应付（预估）</span>
            <span class="cart-apex-total-num">¥{{ formatYuan(payableEstimateYuan) }}</span>
          </div>
          <p class="cart-apex-total-note">下单金额以确认订单时系统计算为准。</p>

          <button
            type="button"
            class="cart-apex-btn cart-apex-btn--checkout"
            :disabled="isMultiMerchant && !canBulkCheckout"
            @click="checkout"
          >
            <span>发起结算</span>
            <svg class="cart-apex-arrow" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
              <path d="M5 12h14M13 6l6 6-6 6" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
          </button>
          <p v-if="isMultiMerchant && !canBulkCheckout" class="cart-apex-hint">
            部分商品未关联店铺，暂无法结算，请联系客服处理。
          </p>
        </div>

        <div class="cart-apex-logistics">
          <div class="cart-apex-logistics-icon" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
              <path d="M1 3h15v13H1zM16 8h4l3 3v5h-7V8z" stroke-linejoin="round" />
              <circle cx="7" cy="19" r="2" />
              <circle cx="19" cy="19" r="2" />
            </svg>
          </div>
          <div>
            <p class="cart-apex-logistics-title">物流时效</p>
            <p class="cart-apex-logistics-body">
              预计送达：<strong>下单后 1–3 个工作日</strong>（以承运商与收货地为准）
            </p>
          </div>
        </div>

        <div class="cart-apex-benefit">
          <p class="cart-apex-benefit-title">服务保障</p>
          <ul>
            <li>正品保障 · 订单可追溯</li>
            <li>售后与咨询 · 工作日响应</li>
            <li>满 299 元免演示运费</li>
          </ul>
        </div>
      </aside>
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
      <div class="checkout-addr-form">
        <p class="checkout-addr-title">收货信息（将保存到订单）</p>
        <p v-if="!savedAddresses.length" class="checkout-addr-profile-hint">
          还没有保存的收货地址，可到 <button type="button" class="checkout-inline-link" @click="router.push('/profile')">个人中心</button> 新增多条常用地址。
        </p>
        <label v-if="savedAddresses.length" class="checkout-field checkout-field--block">
          <span>地址簿</span>
          <select v-model="checkoutAddressSelect" class="checkout-select" @change="onCheckoutAddressPick">
            <option value="manual">手动填写</option>
            <option v-for="a in savedAddresses" :key="a.addressId" :value="String(a.addressId)">
              {{ (a.label || '地址') + ' · ' + (a.receiverName || '') + ' ' + (a.receiverRegion || '') }}
            </option>
          </select>
        </label>
        <label class="checkout-field">
          <span>收货人</span>
          <input v-model="checkoutReceiverName" type="text" placeholder="姓名" autocomplete="name" />
        </label>
        <label class="checkout-field">
          <span>联系电话</span>
          <input v-model="checkoutReceiverPhone" type="tel" placeholder="手机号" autocomplete="tel" />
        </label>
        <label class="checkout-field">
          <span>配送地区</span>
          <input v-model="checkoutReceiverRegion" type="text" placeholder="省 / 市 / 区" />
        </label>
        <label class="checkout-field">
          <span>详细地址</span>
          <input v-model="checkoutReceiverAddress" type="text" placeholder="街道、门牌、楼层等" />
        </label>
      </div>
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
      <div class="checkout-addr-form">
        <p class="checkout-addr-title">收货信息（每笔订单相同快照）</p>
        <p v-if="!savedAddresses.length" class="checkout-addr-profile-hint">
          还没有保存的收货地址，可到 <button type="button" class="checkout-inline-link" @click="router.push('/profile')">个人中心</button> 新增多条常用地址。
        </p>
        <label v-if="savedAddresses.length" class="checkout-field checkout-field--block">
          <span>地址簿</span>
          <select v-model="checkoutAddressSelect" class="checkout-select" @change="onCheckoutAddressPick">
            <option value="manual">手动填写</option>
            <option v-for="a in savedAddresses" :key="a.addressId" :value="String(a.addressId)">
              {{ (a.label || '地址') + ' · ' + (a.receiverName || '') + ' ' + (a.receiverRegion || '') }}
            </option>
          </select>
        </label>
        <label class="checkout-field">
          <span>收货人</span>
          <input v-model="checkoutReceiverName" type="text" placeholder="姓名" autocomplete="name" />
        </label>
        <label class="checkout-field">
          <span>联系电话</span>
          <input v-model="checkoutReceiverPhone" type="tel" placeholder="手机号" autocomplete="tel" />
        </label>
        <label class="checkout-field">
          <span>配送地区</span>
          <input v-model="checkoutReceiverRegion" type="text" placeholder="省 / 市 / 区" />
        </label>
        <label class="checkout-field">
          <span>详细地址</span>
          <input v-model="checkoutReceiverAddress" type="text" placeholder="街道、门牌、楼层等" />
        </label>
      </div>
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
.cart-page--apex {
  --cx-ink: #0a0a0a;
  --cx-muted: #737373;
  --cx-line: #e5e5e5;
  --cx-panel: #ffffff;
  --cx-bg: #f5f5f5;
  --cx-soft: #fafafa;
  --cx-accent: #a35d00;
  /* 与商品列表 Apex（--apex-radius 2px）一致 */
  --cx-radius: 2px;
  font-family: 'Inter', 'Microsoft YaHei', 'PingFang SC', system-ui, sans-serif;
  /* 与全站 .pw-page 最大宽接近，减少左右留白 */
  width: 100%;
  max-width: min(1720px, 100%);
  margin: 0 auto;
  padding-bottom: clamp(28px, 3vh, 40px);
  background: transparent;
}

.cart-apex-head {
  margin-bottom: clamp(22px, 2.5vw, 30px);
}

.cart-apex-kicker {
  margin: 0 0 8px;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--cx-muted);
}

.cart-apex-lead {
  margin: 4px 0 0;
  font-size: clamp(15px, 1.25vw, 17px);
  color: var(--cx-muted);
  font-weight: 600;
}

.cart-apex-state {
  padding: 48px 20px;
  text-align: center;
  font-size: 14px;
  color: var(--cx-muted);
  background: var(--cx-panel);
  border: 1px solid var(--cx-line);
  border-radius: var(--cx-radius);
}

.cart-apex-state--error {
  color: #b91c1c;
}

.cart-apex-empty {
  padding: 56px 24px;
  text-align: center;
  background: var(--cx-panel);
  border: 1px dashed var(--cx-line);
  border-radius: var(--cx-radius);
}

.cart-apex-empty-title {
  margin: 0 0 10px;
  font-size: clamp(22px, 2vw, 26px);
  font-weight: 800;
  color: var(--cx-ink);
}

.cart-apex-empty-lead {
  margin: 0 0 22px;
  font-size: 15px;
  color: var(--cx-muted);
  line-height: 1.6;
}

.cart-apex-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(300px, 400px);
  gap: clamp(22px, 3.2vw, 36px);
  align-items: start;
}

.cart-apex-main {
  display: flex;
  flex-direction: column;
  gap: clamp(16px, 2vw, 22px);
  min-width: 0;
}

.cart-apex-panel {
  background: var(--cx-panel);
  border: 1px solid var(--cx-line);
  border-radius: var(--cx-radius);
  padding: clamp(20px, 2.2vw, 28px);
  box-shadow: 0 1px 0 rgba(0, 0, 0, 0.04);
}

.cart-apex-panel--soft {
  background: var(--cx-soft);
}

.cart-apex-panel-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: clamp(18px, 2vw, 22px);
  padding-bottom: clamp(16px, 1.8vw, 20px);
  border-bottom: 1px solid var(--cx-line);
}

.cart-apex-h2 {
  margin: 0;
  font-size: clamp(19px, 1.6vw, 22px);
  font-weight: 800;
  color: var(--cx-ink);
  letter-spacing: -0.02em;
}

.cart-apex-h2-en {
  margin: 6px 0 0;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: #a3a3a3;
}

.cart-apex-h2--sm {
  font-size: clamp(17px, 1.45vw, 20px);
  margin-bottom: 10px;
}

.cart-apex-count {
  flex-shrink: 0;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--cx-muted);
  padding: 9px 14px;
  border: 1px solid var(--cx-line);
  border-radius: 999px;
  background: var(--cx-soft);
}

.cart-apex-list {
  display: flex;
  flex-direction: column;
  gap: clamp(14px, 1.8vw, 18px);
}

.cart-apex-section {
  display: flex;
  flex-direction: column;
  gap: clamp(14px, 1.8vw, 18px);
}

.cart-apex-stripe {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 10px;
  padding: 12px 16px;
  background: var(--cx-bg);
  border: 1px solid var(--cx-line);
  border-radius: var(--cx-radius);
  font-size: 14px;
}

.cart-apex-stripe-name {
  font-weight: 800;
  color: var(--cx-ink);
}

.cart-apex-stripe-meta {
  color: var(--cx-muted);
  font-weight: 600;
}

.cart-apex-card {
  display: flex;
  gap: clamp(16px, 2.2vw, 24px);
  padding: clamp(16px, 2.2vw, 22px);
  border: 1px solid var(--cx-line);
  border-radius: var(--cx-radius);
  background: var(--cx-panel);
  transition: box-shadow 0.2s ease;
}

.cart-apex-card:hover {
  box-shadow: 0 8px 28px rgba(0, 0, 0, 0.06);
}

.cart-apex-thumb {
  flex-shrink: 0;
  width: clamp(108px, 12vw, 132px);
  height: clamp(108px, 12vw, 132px);
  padding: 0;
  border: 1px solid var(--cx-line);
  border-radius: var(--cx-radius);
  overflow: hidden;
  background: var(--cx-soft);
  cursor: pointer;
}

.cart-apex-thumb-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.cart-apex-thumb-ph {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  font-size: 12px;
  color: #a3a3a3;
}

.cart-apex-card-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.cart-apex-card-top {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}

.cart-apex-card-text {
  min-width: 0;
}

.cart-apex-tag {
  display: inline-block;
  margin-bottom: 10px;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--cx-accent);
}

.cart-apex-item-title {
  margin: 0 0 8px;
  font-size: clamp(16px, 1.5vw, 20px);
  font-weight: 700;
  line-height: 1.35;
  color: var(--cx-ink);
}

.cart-apex-title-btn {
  margin: 0;
  padding: 0;
  border: none;
  background: none;
  font: inherit;
  color: inherit;
  text-align: left;
  cursor: pointer;
}

.cart-apex-title-btn:hover {
  text-decoration: underline;
  text-underline-offset: 3px;
}

.cart-apex-sku {
  margin: 0 0 10px;
  font-size: 13px;
  font-weight: 600;
  color: var(--cx-muted);
}

.cart-apex-desc {
  margin: 0;
  font-size: clamp(13px, 1.1vw, 15px);
  line-height: 1.6;
  color: #525252;
}

.cart-apex-price-block {
  flex-shrink: 0;
  text-align: right;
}

.cart-apex-price {
  display: block;
  font-size: clamp(22px, 2vw, 26px);
  font-weight: 800;
  color: var(--cx-ink);
  letter-spacing: -0.02em;
}

.cart-apex-line {
  display: block;
  margin-top: 6px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #a3a3a3;
}

.cart-apex-card-foot {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-top: 4px;
  border-top: 1px solid var(--cx-line);
}

.cart-apex-qty {
  display: inline-flex;
  align-items: center;
  border: 1px solid var(--cx-line);
  border-radius: 2px;
  overflow: hidden;
  background: var(--cx-panel);
}

.cart-apex-qty-btn {
  width: 44px;
  height: 44px;
  border: none;
  background: var(--cx-soft);
  font-size: 18px;
  font-weight: 600;
  color: var(--cx-muted);
  cursor: pointer;
  transition:
    background 0.15s,
    color 0.15s;
}

.cart-apex-qty-btn:hover:not(:disabled) {
  background: #ececec;
  color: var(--cx-ink);
}

.cart-apex-qty-btn:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}

.cart-apex-qty-num {
  min-width: 44px;
  text-align: center;
  font-size: 15px;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
  color: var(--cx-ink);
}

.cart-apex-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px 12px;
  font-size: 13px;
  font-weight: 700;
}

.cart-apex-line-total {
  color: var(--cx-ink);
  margin-right: 8px;
}

.cart-apex-sep {
  color: var(--cx-line);
  user-select: none;
}

.cart-apex-link {
  padding: 0;
  border: none;
  background: none;
  font: inherit;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--cx-muted);
  cursor: pointer;
  text-decoration: underline;
  text-underline-offset: 3px;
}

.cart-apex-link:hover {
  color: var(--cx-ink);
}

.cart-apex-link--danger {
  color: #b45309;
}

.cart-apex-link--danger:hover {
  color: #7c2d12;
}

.cart-apex-muted {
  margin: 0 0 16px;
  font-size: clamp(14px, 1.15vw, 16px);
  line-height: 1.6;
  color: #525252;
}

.cart-apex-aside {
  display: flex;
  flex-direction: column;
  gap: clamp(14px, 2vw, 18px);
  position: sticky;
  top: 88px;
}

.cart-apex-manifest {
  background: var(--cx-panel);
  border: 1px solid var(--cx-line);
  border-radius: var(--cx-radius);
  padding: clamp(22px, 2.2vw, 28px) clamp(20px, 2vw, 26px);
  box-shadow: 0 1px 0 rgba(0, 0, 0, 0.04);
}

.cart-apex-h3 {
  margin: 0;
  font-size: clamp(16px, 1.35vw, 18px);
  font-weight: 800;
  color: var(--cx-ink);
  letter-spacing: 0.04em;
}

.cart-apex-h3-en {
  margin: 6px 0 18px;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: #a3a3a3;
}

.cart-apex-rows {
  margin: 0;
}

.cart-apex-row {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: 12px;
  padding: 12px 0;
  border-bottom: 1px solid var(--cx-line);
  font-size: 14px;
}

.cart-apex-row dt {
  margin: 0;
  font-weight: 600;
  color: var(--cx-muted);
}

.cart-apex-row dd {
  margin: 0;
  font-weight: 700;
  color: var(--cx-ink);
}

.cart-apex-total-block {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-top: 4px;
  padding-top: 18px;
  border-top: 1px solid var(--cx-line);
  gap: 12px;
}

.cart-apex-total-label {
  font-size: 13px;
  font-weight: 800;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--cx-muted);
}

.cart-apex-total-num {
  font-size: clamp(32px, 4.5vw, 44px);
  font-weight: 800;
  letter-spacing: -0.03em;
  color: var(--cx-ink);
}

.cart-apex-total-note {
  margin: 0 0 16px;
  padding: 12px 14px;
  font-size: 13px;
  line-height: 1.55;
  color: #737373;
  background: var(--cx-soft);
  border: 1px solid var(--cx-line);
  border-radius: var(--cx-radius);
}

.cart-apex-btn {
  border-radius: 2px;
  font-size: 13px;
  font-weight: 800;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  cursor: pointer;
  transition:
    background 0.15s,
    border-color 0.15s,
    opacity 0.15s;
}

.cart-apex-btn--primary {
  padding: 12px 24px;
  border: 1px solid var(--cx-ink);
  background: var(--cx-ink);
  color: #fff;
}

.cart-apex-btn--ghost {
  padding: 10px 18px;
  border: 1px solid var(--cx-line);
  background: var(--cx-panel);
  color: var(--cx-ink);
}

.cart-apex-btn--ghost:hover {
  border-color: var(--cx-ink);
}

.cart-apex-btn--checkout {
  width: 100%;
  box-sizing: border-box;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  min-height: 56px;
  padding: 16px 22px;
  border: 1px solid var(--cx-ink);
  background: var(--cx-ink);
  color: #fff;
  font-size: 15px;
  font-weight: 800;
  letter-spacing: 0.04em;
  line-height: 1.25;
  text-transform: none;
}

.cart-apex-btn--checkout:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.cart-apex-arrow {
  width: 22px;
  height: 22px;
  flex-shrink: 0;
}

.cart-apex-hint {
  margin: 12px 0 0;
  font-size: 12px;
  line-height: 1.55;
  color: #b45309;
}

.cart-apex-logistics {
  display: flex;
  gap: 16px;
  padding: clamp(16px, 2vw, 20px) clamp(18px, 2vw, 22px);
  background: var(--cx-bg);
  border: 1px solid var(--cx-line);
  border-radius: var(--cx-radius);
}

.cart-apex-logistics-icon {
  flex-shrink: 0;
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--cx-radius);
  background: var(--cx-panel);
  border: 1px solid var(--cx-line);
  color: var(--cx-accent);
}

.cart-apex-logistics-icon svg {
  width: 24px;
  height: 24px;
}

.cart-apex-logistics-title {
  margin: 0 0 6px;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--cx-muted);
}

.cart-apex-logistics-body {
  margin: 0;
  font-size: 14px;
  line-height: 1.55;
  color: #404040;
}

.cart-apex-logistics-body strong {
  color: var(--cx-accent);
  font-weight: 800;
}

.cart-apex-benefit {
  padding: clamp(16px, 2vw, 20px) clamp(18px, 2vw, 22px);
  background: var(--cx-panel);
  border: 1px solid var(--cx-line);
  border-radius: var(--cx-radius);
}

.cart-apex-benefit-title {
  margin: 0 0 10px;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--cx-muted);
}

.cart-apex-benefit ul {
  margin: 0;
  padding-left: 20px;
  font-size: 13px;
  line-height: 1.65;
  color: #525252;
}

.cart-bulk-modal-note {
  font-size: 14px;
  color: #506078;
  margin: 0;
}

.checkout-addr-form {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px dashed #d9e1ec;
  text-align: left;
}

.checkout-addr-title {
  margin: 0 0 10px;
  font-size: 13px;
  font-weight: 800;
  color: #10213a;
}

.checkout-addr-profile-hint {
  margin: 0 0 12px;
  font-size: 12px;
  line-height: 1.5;
  color: #506078;
}

.checkout-inline-link {
  padding: 0;
  border: none;
  background: none;
  font: inherit;
  font-weight: 800;
  color: #10213a;
  text-decoration: underline;
  cursor: pointer;
}

.checkout-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 10px;
  font-size: 13px;
  color: #506078;
}

.checkout-field span {
  font-weight: 700;
}

.checkout-field input {
  height: 36px;
  padding: 0 10px;
  border: 1px solid #cdd8e7;
  border-radius: 8px;
  font-size: 14px;
  color: #131e30;
  background: #fff;
}

.checkout-field input:focus {
  outline: 2px solid #222;
  outline-offset: 1px;
}

.checkout-field--block {
  width: 100%;
}

.checkout-select {
  height: 36px;
  padding: 0 10px;
  border: 1px solid #cdd8e7;
  border-radius: 8px;
  font-size: 14px;
  color: #131e30;
  background: #fff;
}

.checkout-select:focus {
  outline: 2px solid #222;
  outline-offset: 1px;
}

@media (max-width: 980px) {
  .cart-apex-layout {
    grid-template-columns: 1fr;
  }

  .cart-apex-aside {
    position: static;
  }

  .cart-apex-card-top {
    flex-direction: column;
  }

  .cart-apex-price-block {
    text-align: left;
  }
}
</style>
