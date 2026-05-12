<script setup>
import { ref, reactive, computed, onMounted, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../utils/request'
import { formatYuan } from '../utils/formatYuan.js'
import { showAppMessage } from '../utils/appMessage'
import { restockSubscribeSuccessBody } from '../utils/apiFriendlyMessage'
import ConfirmModal from '../components/ConfirmModal.vue'
import AppImage from '../components/AppImage.vue'

const route = useRoute()
const router = useRouter()
const product = ref(null)
const productDetail = ref(null)
const reviews = ref([])
const quantity = ref(1)
const userId = ref(Number(localStorage.getItem('userId') || 0))

/**
 * 从 localStorage 同步当前用户 ID（登录态可能在其它页变更）。
 */
function syncUserId() {
  userId.value = Number(localStorage.getItem('userId') || 0)
}

/**
 * 需要登录才能执行的操作（加购、下单、评价等）。
 * @param {string} [hint] 未登录时的提示文案
 * @returns {boolean} 已登录为 true
 */
function requireLoginForAction(hint = '请先登录后再使用该功能') {
  syncUserId()
  if (userId.value) return true
  showAppMessage(hint, '提示')
  router.push('/login')
  return false
}
const activeTab = ref('detail')
/** 同分类优先，不足补其它在售；候选内随机洗牌，避免每次固定同一批顺序 */
const relatedProducts = ref([])

/** 侧栏饲喂参考（通用区间，请以包装与兽医建议为准） */
const feedingGuideRows = [
  { w: '1 – 5 kg', g: '40 – 120 g' },
  { w: '5 – 10 kg', g: '120 – 200 g' },
  { w: '10 – 20 kg', g: '200 – 350 g' },
  { w: '20 kg 以上', g: '350 g 起' },
]
/** 用 reactive 便于模板内直接改 rating；避免 ref 嵌套属性在部分环境下赋值不触发更新 */
const newReview = reactive({
  rating: 0,
  content: '',
})

function setReviewRating(star) {
  const s = Math.floor(Number(star))
  if (!Number.isFinite(s) || s < 1 || s > 5) return
  newReview.rating = s
}
const showReviewForm = ref(false)
const pageError = ref('')
const selectedImageIdx = ref(0)

const avgRating = computed(() => {
  if (reviews.value.length === 0) return 0
  const sum = reviews.value.reduce((acc, r) => acc + r.rating, 0)
  return (sum / reviews.value.length).toFixed(1)
})

const isOffShelf = computed(() => Number(product.value?.status ?? 1) !== 1)
const isSoldOut = computed(() => Number(product.value?.stock ?? 0) <= 0)
/** 与列表「低库存」角标一致：有货但少于 20 件 */
const LOW_STOCK_THRESHOLD = 20
const isLowStock = computed(
  () =>
    !!product.value &&
    !isOffShelf.value &&
    !isSoldOut.value &&
    Number(product.value.stock) > 0 &&
    Number(product.value.stock) < LOW_STOCK_THRESHOLD
)
const canBuy = computed(() => !!product.value && !isOffShelf.value && !isSoldOut.value)
const subscribing = ref(false)
/** 到货提醒确认弹层 */
const restockConfirmOpen = ref(false)
/** soldout：售罄订阅；lowstock：低库存订阅（仍走同一接口，便于商家侧感知） */
const restockConfirmMode = ref(/** @type {'soldout' | 'lowstock'} */ ('soldout'))
/** 立即购买确认弹层 */
const buyConfirmOpen = ref(false)
/** 立即购买提交中 */
const buySubmitting = ref(false)
const buyReceiverName = ref('')
const buyReceiverPhone = ref('')
const buyReceiverRegion = ref('')
const buyReceiverAddress = ref('')
const buySavedAddresses = ref([])
/** 地址簿选择：manual | addressId */
const buyAddressSelect = ref('manual')
const buyAddressId = ref(null)

async function prefetchProfileForBuy() {
  if (!userId.value) return
  const res = await api.userGetProfile(userId.value)
  if (res.code !== 200) return
  const p = res.data || {}
  if (!String(buyReceiverName.value || '').trim() && p.nickname) {
    buyReceiverName.value = String(p.nickname)
  }
  if (!String(buyReceiverPhone.value || '').trim() && p.phone) {
    buyReceiverPhone.value = String(p.phone)
  }
}

async function loadSavedAddressesForBuy() {
  if (!userId.value) return
  const res = await api.userListAddresses(userId.value)
  if (res.code !== 200) return
  buySavedAddresses.value = Array.isArray(res.data) ? res.data : []
}

function applyBuySavedAddress(addr) {
  if (!addr) {
    buyAddressId.value = null
    return
  }
  buyAddressId.value = addr.addressId
  buyReceiverName.value = String(addr.receiverName || '')
  buyReceiverPhone.value = String(addr.receiverPhone || '')
  buyReceiverRegion.value = String(addr.receiverRegion || '')
  buyReceiverAddress.value = String(addr.receiverDetail || '')
}

function pickDefaultBuyAddress() {
  const list = buySavedAddresses.value || []
  const def = list.find((a) => Number(a.isDefault) === 1)
  if (def) {
    buyAddressSelect.value = String(def.addressId)
    applyBuySavedAddress(def)
    return
  }
  buyAddressSelect.value = 'manual'
  buyAddressId.value = null
}

function onBuyAddressPick() {
  const v = buyAddressSelect.value
  if (!v || v === 'manual') {
    buyAddressId.value = null
    return
  }
  const addr = buySavedAddresses.value.find((a) => String(a.addressId) === String(v))
  if (addr) applyBuySavedAddress(addr)
}

watch(buyConfirmOpen, async (v) => {
  if (!v) return
  await prefetchProfileForBuy()
  await loadSavedAddressesForBuy()
  pickDefaultBuyAddress()
})

/**
 * 立即购买应付金额（单价 × 数量），用于确认弹层展示。
 */
const buyPayAmount = computed(() => {
  if (!product.value) return '0'
  const unit = Number(product.value.price) || 0
  const qty = Number(quantity.value) || 0
  return formatYuan(unit * qty)
})

const GALLERY_FALLBACK_URLS = [
  'https://lh3.googleusercontent.com/aida-public/AB6AXuC-ufRqelDaZm4z8U0hXza-xbPMmKfJHQmwEZR6Qc24D0ZdcxlmvpV0bc5Bne0YjGprJzbyWcuIAK_mqv0T0oX0m9OnUaye0xlzn8FvOUoBnxoXiFSXfR98RTgrcftZG4Bb3eBle2a3OTnOwL7KfVp_XVarv7SJmFvpIgk-cungxrazZZg33BZQgCrefZDjFkRVicscqkChE4NCHvrbChNX2DmhiPLmoUGij6lIT-USnPK2W88LiieWhBTbcmahS-iai2CnD-FvuXj',
  'https://lh3.googleusercontent.com/aida-public/AB6AXuBzsSbU83L3zClnD_YHREFkGnwPyMmk1a0qGKatpt0K9tM9i7jbFFM2dNtjN6TI-DXSvMFBsQVEXnbd_eLPmz_sv-naPUzbhf2GMwnMis7X2sQQwyNUxDwSDw1_I9tTKTZgj7j6I6VFFuxlMhV0-sVj0pLMGgFrKwMNQvqwAAC9noA--UgCpMdbiuTmxR9KGr8KUQM6PVtlcCqbyER_68qiK9AQZZrxiATzFpr2Irwr7yTLsTYzepP6WputV5OQkivwOKbqoZ_5T6K7',
  'https://lh3.googleusercontent.com/aida-public/AB6AXuBIgSUdA34lOYyKi1_ABCqnCKXOz5mP-rGnajMkGx5x3qkFmgTZpsNfIsszlR0h2jyFNl4PCLDrTOV2FBcNfmD7UVWX3NOtzd6iNY1Jvp6Izu8PMLzpY3TOkj7EeO_aetFM-8Nnljp_3ZdfR7wcMRXb4HeiuRBE1Hk2KRByVj0rYalAk2WEN_37sQGrCdapntR2llLf0yUM3qkCPmf154Pw_BrespM7VQUH5Y2POEGm8vNQcpLA2JGqQoUzbOgblmQ1eOI28ZzISvu5',
  'https://lh3.googleusercontent.com/aida-public/AB6AXuAXTppCPzIImohBtq7mUIaBFdIKGRUmbcNIAmS3NpPzwnBIeKW0_my0KpnRSLVsYqTrb6Uye05xumKwc9kiPnz37Rndpz1LTqdmj5rI26kJvg3jCRU3OgaQnuXFOLAsMmbxMd12HrpLEDr-1wvWHcgqyr-YN_nk8vdFh3BX1pJhSecyO_2RmvmzouuAaWRAclvDW_y-9wWsgYRn0-7m0QDnaa-PxtACcxaP3B7bweqJO6mw5ZYny0-N4R-9s2K-QCO5v-Ltb8dfshis',
]

function relatedImageUrl(rp) {
  const u = String(rp?.detail?.imageUrl || '').trim()
  if (u) return u
  const id = Number(rp?.productId || 0)
  return GALLERY_FALLBACK_URLS[Math.abs(id) % GALLERY_FALLBACK_URLS.length]
}

const galleryImages = computed(() => {
  const main = String(productDetail.value?.imageUrl || '').trim()
  const arr = main ? [main, ...GALLERY_FALLBACK_URLS] : [...GALLERY_FALLBACK_URLS]
  return arr.slice(0, 4)
})

const currentImageUrl = computed(() => {
  const list = galleryImages.value
  if (!list.length) return ''
  const idx = Math.min(Math.max(0, Number(selectedImageIdx.value || 0)), list.length - 1)
  return list[idx]
})

/** 首屏标题下摘要（1～2 行），与原型「简述」一致 */
const heroLeadLines = computed(() => {
  const p = product.value
  if (!p) return []
  const raw = String(productDetail.value?.description || '').trim()
  if (raw) {
    const paras = raw.split(/\n+/).map((s) => s.trim()).filter(Boolean)
    if (paras.length >= 2) return [paras[0], paras[1]]
    const one = paras[0] || raw
    if (one.length <= 100) return [one]
    const cut = one.slice(0, 100).lastIndexOf('。')
    const idx = cut > 28 ? cut + 1 : 100
    const a = one.slice(0, idx).trim()
    const b = one.slice(idx).trim()
    if (!b) return [a]
    return [a, b.length > 100 ? `${b.slice(0, 100)}…` : b]
  }
  const cat = p.categoryName ? String(p.categoryName) : ''
  const brand = p.brandName ? String(p.brandName) : ''
  let line1 = '精选配方，均衡营养，适合日常饲喂。'
  if (cat && brand) line1 = `精选「${cat}」类目 · 品牌 ${brand}，配方均衡易消化。`
  else if (cat) line1 = `精选「${cat}」类目商品，配方均衡易消化。`
  else if (brand) line1 = `品牌 ${brand}，精选配方均衡营养。`
  const line2 = '购买前请核对规格与收货信息；成分与饲喂说明见下方详情。'
  return [line1, line2]
})

onMounted(async () => {
  syncUserId()
  const productId = route.params.id

  const productRes = await api.getProduct(productId)
  if (productRes.code === 200) {
    product.value = productRes.data
    productDetail.value = productRes.data.detail || {}
    syncUserId()
    if (userId.value && product.value?.productId) {
      api.postUserEvent({
        userId: userId.value,
        eventType: 'view',
        productId: product.value.productId,
      }).catch(() => {})
    }
  } else {
    pageError.value = productRes.message || '商品详情加载失败'
    return
  }

  const reviewsRes = await api.getReviews(productId)
  if (reviewsRes.code === 200) {
    reviews.value = reviewsRes.data
  }

  await loadRelatedProducts()
  await applyOpenReviewFromQuery()
})

/**
 * 数组洗牌（Fisher–Yates），用于相关推荐在候选商品里每次随机换一批展示。
 * @template T
 * @param {T[]} items
 * @returns {T[]}
 */
function shuffleArray(items) {
  const a = items.slice()
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1))
    const t = a[i]
    a[i] = a[j]
    a[j] = t
  }
  return a
}

async function loadRelatedProducts() {
  if (!product.value) return
  const res = await api.getProducts()
  if (res.code !== 200) return
  const list = Array.isArray(res.data) ? res.data : []
  const pid = Number(product.value.productId)
  const cid = Number(product.value.categoryId)
  const onShelf = list.filter(
    (p) => Number(p.productId) !== pid && Number(p.status ?? 1) === 1
  )
  const same = onShelf.filter((p) => Number(p.categoryId) === cid)
  const other = onShelf.filter((p) => Number(p.categoryId) !== cid)
  const shuffledSame = shuffleArray(same)
  const shuffledOther = shuffleArray(other)
  const out = []
  for (const p of shuffledSame) {
    if (out.length >= 4) break
    out.push(p)
  }
  for (const p of shuffledOther) {
    if (out.length >= 4) break
    out.push(p)
  }
  relatedProducts.value = out
}

function viewProductDetail(productId) {
  router.push(`/product/${productId}`)
}

async function quickAddRelated(p, e) {
  if (e?.stopPropagation) e.stopPropagation()
  syncUserId()
  if (!requireLoginForAction()) return
  if (Number(p.stock ?? 0) <= 0 || Number(p.status ?? 1) !== 1) {
    showAppMessage('该商品暂不可加购', '提示')
    return
  }
  const res = await api.addToCart({
    userId: userId.value,
    productId: p.productId,
    quantity: 1,
  })
  if (res.code === 200) {
    showAppMessage('已加入购物车', '购物车')
  } else {
    showAppMessage(res.message || '加入失败', '提示')
  }
}

/**
 * 从订单「去评价」进入时 URL 带 ?review=1：登录后展开评价表单并滚到评价区，随后去掉 query。
 */
async function applyOpenReviewFromQuery() {
  const flag = route.query.review
  if (flag !== '1' && flag !== 'true') return

  syncUserId()
  if (!userId.value) {
    showAppMessage('请先登录后再评价', '提示')
    router.push('/login')
    return
  }

  showReviewForm.value = true
  Object.assign(newReview, { rating: 0, content: '' })

  activeTab.value = 'reviews'
  await nextTick()
  document.getElementById('pd-review-anchor')?.scrollIntoView({ behavior: 'smooth', block: 'start' })

  const rest = { ...route.query }
  delete rest.review
  router.replace({ path: route.path, query: Object.keys(rest).length ? rest : {} })
}

async function addToCart() {
  if (!product.value) return
  if (!requireLoginForAction()) return
  if (!canBuy.value) {
    showAppMessage(isOffShelf.value ? '商品已下架' : '商品已售罄', '提示')
    return
  }

  const res = await api.addToCart({
    userId: userId.value,
    productId: product.value.productId,
    quantity: quantity.value
  })

  if (res.code === 200) {
    if (userId.value && product.value?.productId) {
      api
        .postUserEvent({
          userId: userId.value,
          eventType: 'add_cart',
          productId: product.value.productId,
        })
        .catch(() => {})
    }
    showAppMessage('已添加到购物车', '购物车')
  } else {
    showAppMessage(res.message || '加入购物车失败', '提示')
  }
}

function buyNow() {
  if (!product.value) return
  if (!requireLoginForAction()) return
  if (!canBuy.value) {
    showAppMessage(isOffShelf.value ? '商品已下架' : '商品已售罄', '提示')
    return
  }
  buyConfirmOpen.value = true
}

/**
 * 用户确认后立即下单并跳转订单详情。
 */
async function submitBuyOrder() {
  if (!product.value || buySubmitting.value) return
  if (!requireLoginForAction()) return
  const aid = buyAddressId.value ? Number(buyAddressId.value) : 0
  const region = String(buyReceiverRegion.value || '').trim()
  const addr = String(buyReceiverAddress.value || '').trim()
  if (!(aid > 0) && (!region || !addr)) {
    showAppMessage('请选择地址簿中的收货地址，或填写配送地区与详细地址', '提示')
    return
  }
  buySubmitting.value = true
  const payload = {
    userId: userId.value,
    productId: product.value.productId,
    quantity: quantity.value,
    receiverName: String(buyReceiverName.value || '').trim(),
    receiverPhone: String(buyReceiverPhone.value || '').trim(),
    receiverRegion: region,
    receiverAddress: addr,
  }
  if (aid > 0) payload.addressId = aid
  const res = await api.userCreateOrderDirect(payload)
  buySubmitting.value = false
  if (res.code === 200) {
    buyConfirmOpen.value = false
    router.push(`/order/${res.data.orderId}`)
  } else {
    showAppMessage(res.message || '下单失败', '提示')
  }
}

function contactMerchant() {
  if (!product.value) return
  if (!requireLoginForAction('请先登录后再联系商家')) return
  const merchantId = Number(product.value.merchantId || 1)
  router.push(`/support?merchantId=${merchantId}&productId=${product.value.productId}`)
}

function openRestockSubscribeConfirm() {
  if (!product.value) return
  if (!requireLoginForAction('请先登录后再订阅提醒')) return
  if (isOffShelf.value) {
    showAppMessage('商品已下架', '提示')
    return
  }
  if (!isSoldOut.value && !isLowStock.value) {
    showAppMessage('当前库存充足，无需订阅提醒', '提示')
    return
  }
  restockConfirmMode.value = isSoldOut.value ? 'soldout' : 'lowstock'
  restockConfirmOpen.value = true
}

const restockConfirmTitle = computed(() =>
  restockConfirmMode.value === 'lowstock' ? '确认订阅补货提醒' : '确认订阅到货提醒'
)

async function confirmSubscribeRestock() {
  if (!product.value || subscribing.value) return
  syncUserId()
  if (!userId.value) {
    showAppMessage('请先登录后再订阅提醒', '提示')
    restockConfirmOpen.value = false
    return
  }
  const mode = restockConfirmMode.value
  subscribing.value = true
  try {
    const res = await api.userSubscribeRestock({
      userId: userId.value,
      productId: product.value.productId,
    })
    restockConfirmOpen.value = false
    if (res.code === 200) {
      if (mode === 'lowstock') {
        showAppMessage(
          '订阅成功。库存紧张时我们将在消息中心提醒您关注补货与库存动态。',
          '订阅成功'
        )
      } else {
        showAppMessage(restockSubscribeSuccessBody(res.message), '订阅成功')
      }
    } else {
      showAppMessage(res.message || '操作失败', '提示')
    }
  } finally {
    subscribing.value = false
  }
}

async function submitReview() {
  if (!requireLoginForAction('请先登录后再评价')) return
  if (!Number(newReview.rating) || newReview.rating < 1) {
    showAppMessage('请先点击星星选择评分', '提示')
    return
  }
  if (!newReview.content.trim()) {
    showAppMessage('请输入评价内容', '提示')
    return
  }

  const res = await api.addReview({
    userId: userId.value,
    productId: product.value.productId,
    rating: newReview.rating,
    content: newReview.content
  })

  if (res.code === 200) {
    showAppMessage('评价成功', '提示')
    Object.assign(newReview, { rating: 0, content: '' })
    showReviewForm.value = false

    const reviewsRes = await api.getReviews(product.value.productId)
    if (reviewsRes.code === 200) {
      reviews.value = reviewsRes.data
    }
  } else {
    showAppMessage(res.message || '评价提交失败', '提示')
  }
}

function goBack() {
  router.back()
}

function getStarRating(rating) {
  return '★'.repeat(rating) + '☆'.repeat(5 - rating)
}

function formatSpecInline() {
  const j = productDetail.value?.specJson
  if (!j || typeof j !== 'object') return ''
  return Object.entries(j)
    .map(([k, v]) => `${k} ${v}`)
    .join(' · ')
}

/**
 * 展开/收起写评价表单；未登录时仅提示并跳转登录。
 */
function toggleReviewForm() {
  syncUserId()
  if (!showReviewForm.value && !userId.value) {
    requireLoginForAction('请先登录后再评价')
    return
  }
  if (!showReviewForm.value) {
    Object.assign(newReview, { rating: 0, content: '' })
  }
  showReviewForm.value = !showReviewForm.value
}
</script>

<template>
  <div v-if="pageError" class="pw-page product-detail-page product-detail--apex">
    <div class="pw-state pw-state--error">{{ pageError }}</div>
  </div>
  <div v-else-if="product" class="pw-page product-detail-page product-detail--apex">
    <div class="pd-shell">
      <nav class="pd-breadcrumb product-breadcrumb" aria-label="breadcrumb">
        <span class="product-breadcrumb-link" @click="goBack">返回</span>
        <span class="product-breadcrumb-sep">/</span>
        <span class="product-breadcrumb-current">{{ product.categoryName }}</span>
        <span class="product-breadcrumb-sep">/</span>
        <span class="product-breadcrumb-current">商品详情</span>
      </nav>

      <section class="pd-hero-card">
        <div class="product-gallery">
          <div class="main-image">
            <AppImage
              v-if="currentImageUrl"
              :src="currentImageUrl"
              class="product-image"
              loading="eager"
              fetchpriority="high"
              decoding="async"
            />
            <div v-else class="image-placeholder">暂无图片</div>
          </div>
          <div class="thumb-row">
            <button
              v-for="(g, i) in galleryImages"
              :key="`thumb-${i}`"
              type="button"
              class="thumb-btn"
              :class="{ active: i === selectedImageIdx }"
              @click="selectedImageIdx = i"
            >
              <AppImage :src="g" alt="缩略图" loading="lazy" decoding="async" />
            </button>
          </div>
        </div>

        <div class="product-info-panel">
          <div class="pd-badges">
            <span class="pd-badge pd-badge--premium">严选</span>
            <span class="pd-badge pd-badge--model">型号 · ID {{ product.productId }}</span>
          </div>
          <h1 class="product-title">{{ product.title }}</h1>
          <div class="pd-hero-lead">
            <p v-for="(line, idx) in heroLeadLines" :key="`lead-${idx}`" class="pd-hero-lead-line">{{ line }}</p>
          </div>

          <div class="pd-stat-row">
            <div class="pd-stat pd-stat--price">
              <span class="pd-stat-label">价格 <span class="pd-stat-label-en">PRICE</span></span>
              <span class="pd-stat-num pd-price">¥{{ formatYuan(product.price) }}</span>
            </div>
            <div class="pd-stat">
              <span class="pd-stat-label">库存 <span class="pd-stat-label-en">STOCK</span></span>
              <span class="pd-stat-num" :class="{ 'pd-stat-num--warn': product.stock < 50 }">
                {{ product.stock }} 件
              </span>
            </div>
            <div class="pd-stat">
              <span class="pd-stat-label">评分 <span class="pd-stat-label-en">RATING</span></span>
              <span class="pd-stat-num pd-stat-rating">★ {{ avgRating }}<span class="pd-stat-suffix">/5</span></span>
            </div>
          </div>

          <div class="product-meta pd-meta-compact">
            <span class="meta-item">
              <span class="meta-label">分类</span>
              <span class="meta-value category-tag">{{ product.categoryName }}</span>
            </span>
            <span v-if="product.brandName" class="meta-item">
              <span class="meta-label">品牌</span>
              <span class="meta-value brand-tag">{{ product.brandName }}</span>
            </span>
          </div>

          <div class="pd-qty-block">
            <span class="pd-qty-label">数量 <span class="pd-qty-label-en">QUANTITY</span></span>
            <div class="quantity-control quantity-control--hero">
              <button type="button" class="qty-btn" @click="quantity = Math.max(1, quantity - 1)">−</button>
              <input
                id="pd-qty-input"
                v-model.number="quantity"
                type="number"
                min="1"
                :max="product.stock"
                class="qty-input"
              />
              <button type="button" class="qty-btn" @click="quantity = Math.min(product.stock, quantity + 1)">
                +
              </button>
            </div>
          </div>

          <div class="pd-primary-actions">
            <button type="button" class="btn-pd btn-pd--black btn-pd--xl" :disabled="!canBuy" @click="addToCart">
              <svg class="btn-pd-icon-lg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
                <circle cx="9" cy="21" r="1" />
                <circle cx="20" cy="21" r="1" />
                <path d="M1 1h4l2.68 13.39a2 2 0 0 0 2 1.61h9.72a2 2 0 0 0 2-1.61L23 6H6" />
              </svg>
              <span class="btn-pd-stack">
                <span class="btn-pd-cn">加入购物车</span>
                <span class="btn-pd-en">Add to Cart</span>
              </span>
            </button>
            <button type="button" class="btn-pd btn-pd--black btn-pd--xl" :disabled="!canBuy" @click="buyNow">
              <span class="btn-pd-stack btn-pd-stack--centered">
                <span class="btn-pd-cn">立即购买</span>
                <span class="btn-pd-en">Buy Now</span>
              </span>
            </button>
          </div>

          <button type="button" class="btn-pd btn-pd--outline btn-pd--xl btn-pd--contact" @click="contactMerchant">
            <svg class="btn-pd-icon-lg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
              <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
            </svg>
            <span class="btn-pd-stack">
              <span class="btn-pd-cn">联系商家</span>
              <span class="btn-pd-en">Contact Merchant</span>
            </span>
          </button>

          <button
            v-if="isSoldOut || isLowStock"
            type="button"
            class="btn-pd btn-pd--outline btn-pd--restock"
            :disabled="subscribing"
            @click="openRestockSubscribeConfirm"
          >
            到货提醒
          </button>

          <div class="pd-trust-strip">
            <div class="pd-trust-item">
              <span class="pd-trust-k">ISO 9001</span>
              <span class="pd-trust-v">质量体系</span>
            </div>
            <div class="pd-trust-item">
              <span class="pd-trust-k">安全物流</span>
              <span class="pd-trust-v">全程可追踪</span>
            </div>
          </div>
        </div>
      </section>

      <section class="pd-tabs-card">
        <div class="pd-tabs" role="tablist">
          <button
            type="button"
            role="tab"
            :class="['pd-tab', { 'pd-tab--on': activeTab === 'detail' }]"
            @click="activeTab = 'detail'"
          >
            详情
          </button>
          <button
            type="button"
            role="tab"
            :class="['pd-tab', { 'pd-tab--on': activeTab === 'spec' }]"
            @click="activeTab = 'spec'"
          >
            规格
          </button>
          <button
            type="button"
            role="tab"
            :class="['pd-tab', { 'pd-tab--on': activeTab === 'reviews' }]"
            @click="activeTab = 'reviews'"
          >
            评价
            <span class="pd-tab-count">{{ reviews.length }}</span>
          </button>
          <button
            type="button"
            role="tab"
            :class="['pd-tab', { 'pd-tab--on': activeTab === 'authenticity' }]"
            @click="activeTab = 'authenticity'"
          >
            正品溯源
          </button>
        </div>

        <div class="pd-tab-panels">
          <div class="pd-tab-main">
            <div v-show="activeTab === 'detail'" class="pd-panel-block">
              <h3 class="pd-block-title">商品说明</h3>
              <p v-if="productDetail.description" class="pd-prose">{{ productDetail.description }}</p>
              <p v-else class="pd-prose pd-prose--muted">暂无详情文案，可联系商家了解。</p>
              <div class="pd-feature-cards">
                <div class="pd-feature-card">
                  <strong>营养配比</strong>
                  <p>均衡蛋白与脂肪，适配日常饲喂场景。</p>
                </div>
                <div class="pd-feature-card">
                  <strong>消化友好</strong>
                  <p>颗粒与配方以易吸收为设计目标。</p>
                </div>
              </div>
              <h3 class="pd-block-title pd-block-title--sp">成分参考</h3>
              <div class="pd-nutri-grid">
                <div class="pd-nutri-cell"><span class="pd-nutri-pct">26%</span><span class="pd-nutri-name">粗蛋白</span></div>
                <div class="pd-nutri-cell"><span class="pd-nutri-pct">15%</span><span class="pd-nutri-name">粗脂肪</span></div>
                <div class="pd-nutri-cell"><span class="pd-nutri-pct">4%</span><span class="pd-nutri-name">粗纤维</span></div>
                <div class="pd-nutri-cell"><span class="pd-nutri-pct">10%</span><span class="pd-nutri-name">水分</span></div>
              </div>
              <p v-if="productDetail.specJson" class="pd-ingredient-note">
                规格项：{{ formatSpecInline() }}
              </p>
            </div>

            <div v-show="activeTab === 'spec'" class="pd-panel-block">
              <h3 class="pd-block-title">规格参数</h3>
              <div v-if="productDetail.specJson" class="spec-list pd-spec-list">
                <div v-for="(value, key) in productDetail.specJson" :key="key" class="spec-item">
                  <span class="spec-label">{{ key }}</span>
                  <span class="spec-value">{{ value }}</span>
                </div>
              </div>
              <div v-else class="detail-placeholder">暂无规格参数</div>
            </div>

            <div id="pd-review-anchor" v-show="activeTab === 'reviews'" class="pd-panel-block pd-reviews-panel">
              <div class="section-header pd-review-head">
                <div class="header-left">
                  <h3 class="pd-block-title pd-block-title--inline">用户评价</h3>
                  <span class="review-count">共 {{ reviews.length }} 条</span>
                  <span v-if="reviews.length > 0" class="avg-rating">
                    均分 {{ avgRating }}
                    <span class="avg-rating-stars" aria-hidden="true">{{
                      getStarRating(Math.round(Number(avgRating)))
                    }}</span>
                  </span>
                </div>
                <button type="button" class="btn-write-review" @click="toggleReviewForm">
                  {{ showReviewForm ? '取消评价' : '写评价' }}
                </button>
              </div>

              <p v-if="!showReviewForm" class="pd-review-hint">
                标题旁琥珀色星号为历史均分示意；要对商品打分与写评语，请先点击右上角「写评价」。
              </p>

              <div v-if="showReviewForm" class="review-form">
                <div class="form-group">
                  <label>评分</label>
                  <div class="rating-selector" role="radiogroup" aria-label="评分">
                    <span
                      v-for="n in 5"
                      :key="n"
                      class="rating-star"
                      role="button"
                      tabindex="0"
                      :class="{ 'rating-star--on': n <= newReview.rating }"
                      :aria-label="`${n} 星`"
                      :aria-pressed="n <= newReview.rating"
                      @click.prevent.stop="setReviewRating(n)"
                      @keydown.enter.prevent="setReviewRating(n)"
                      @keydown.space.prevent="setReviewRating(n)"
                    >
                      {{ n <= newReview.rating ? '★' : '☆' }}
                    </span>
                  </div>
                </div>
                <div class="form-group">
                  <label>评价内容</label>
                  <textarea
                    v-model="newReview.content"
                    placeholder="请输入您的评价..."
                    rows="4"
                  ></textarea>
                </div>
                <div class="form-actions">
                  <button type="button" class="btn-submit" @click="submitReview">提交评价</button>
                  <button type="button" class="btn-cancel" @click="showReviewForm = false">取消</button>
                </div>
              </div>

              <div v-if="reviews.length === 0" class="no-reviews">
                <p>暂无评价，快来抢沙发吧</p>
              </div>
              <div v-else class="reviews-list">
                <div v-for="review in reviews" :key="review.reviewId" class="review-card">
                  <div class="review-header">
                    <div class="reviewer-info">
                      <div class="reviewer-avatar">
                        {{ (review.userNickname || String(review.userId)).toString().slice(-1) }}
                      </div>
                      <span class="reviewer-name">{{ review.userNickname || `用户${review.userId}` }}</span>
                    </div>
                    <div class="review-meta">
                      <span class="review-rating">{{ getStarRating(review.rating) }}</span>
                      <span class="review-date">{{ new Date(review.createdAt).toLocaleDateString() }}</span>
                    </div>
                  </div>
                  <p class="review-content">{{ review.content }}</p>
                </div>
              </div>
            </div>

            <div v-show="activeTab === 'authenticity'" class="pd-panel-block pd-authenticity">
              <h3 class="pd-block-title">正品溯源</h3>
              <p class="pd-prose">
                本店在售商品均经平台入驻审核；您可通过订单与客服渠道核验批次与来源。若对真伪有疑问，请联系商家或平台客服处理。
              </p>
              <ul class="pd-auth-list">
                <li>商家资质与商品信息由平台备案</li>
                <li>支持订单维度的售后与咨询</li>
                <li>疑似假货可发起平台介入</li>
              </ul>
            </div>
          </div>

          <aside v-show="activeTab === 'detail' || activeTab === 'spec'" class="pd-tab-aside">
            <div class="pd-aside-dark">
              <h4 class="pd-aside-title">物流保障</h4>
              <ul class="pd-aside-list">
                <li>合作物流，时效稳定</li>
                <li>易损品加固包装（视类目）</li>
                <li>发货后可在订单中查看轨迹</li>
              </ul>
            </div>
            <div class="pd-aside-feed">
              <h4 class="pd-aside-feed-title">饲喂参考</h4>
              <p class="pd-aside-feed-hint">以下为通用参考，请按宠物体重与医嘱调整。</p>
              <table class="pd-feed-table">
                <thead>
                  <tr>
                    <th>体重</th>
                    <th>日粮参考</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(row, idx) in feedingGuideRows" :key="`feed-${idx}`">
                    <td>{{ row.w }}</td>
                    <td>{{ row.g }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </aside>
        </div>
      </section>

      <section v-if="relatedProducts.length" class="pd-related">
        <div class="pd-related-head">
          <h2 class="pd-related-title">相关推荐</h2>
          <button type="button" class="pd-related-more" @click="router.push('/products')">查看全部</button>
        </div>
        <div class="pd-related-grid">
          <article
            v-for="rp in relatedProducts"
            :key="`rel-${rp.productId}`"
            class="pd-related-card"
            @click="viewProductDetail(rp.productId)"
          >
            <div class="pd-related-img">
              <AppImage
                :src="relatedImageUrl(rp)"
                class="pd-related-img-el"
                alt=""
                loading="lazy"
                decoding="async"
              />
            </div>
            <p class="pd-related-cat">{{ rp.categoryName || '商品' }}</p>
            <h3 class="pd-related-name">{{ rp.title }}</h3>
            <div class="pd-related-foot">
              <span class="pd-related-price">¥{{ formatYuan(rp.price) }}</span>
              <button
                type="button"
                class="pd-related-cta"
                :disabled="Number(rp.stock) <= 0 || Number(rp.status ?? 1) !== 1"
                @click="quickAddRelated(rp, $event)"
              >
                加入购物车
              </button>
            </div>
          </article>
        </div>
      </section>
    </div>

    <ConfirmModal
      :open="buyConfirmOpen"
      title="确认下单"
      confirm-label="确认购买"
      :loading="buySubmitting"
      @update:open="buyConfirmOpen = $event"
      @confirm="submitBuyOrder"
    >
      <p>请核对本次购买信息：</p>
      <p v-if="product"><strong>商品：</strong>{{ product.title }}</p>
      <p v-if="product"><strong>数量：</strong>{{ quantity }} 件</p>
      <p v-if="product"><strong>应付金额：</strong>¥{{ buyPayAmount }}</p>
      <div class="buy-addr-form">
        <p class="buy-addr-title">收货信息（将保存到订单）</p>
        <label v-if="buySavedAddresses.length" class="buy-field buy-field--block">
          <span>地址簿</span>
          <select v-model="buyAddressSelect" class="buy-select" @change="onBuyAddressPick">
            <option value="manual">手动填写</option>
            <option v-for="a in buySavedAddresses" :key="a.addressId" :value="String(a.addressId)">
              {{ (a.label || '地址') + ' · ' + (a.receiverName || '') + ' ' + (a.receiverRegion || '') }}
            </option>
          </select>
        </label>
        <p v-else class="buy-addr-hint">暂无保存地址，可在<strong>个人中心 → 收货地址</strong>新增；或直接填写下方收件信息。</p>
        <label class="buy-field">
          <span>收货人</span>
          <input v-model="buyReceiverName" type="text" placeholder="姓名" autocomplete="name" />
        </label>
        <label class="buy-field">
          <span>联系电话</span>
          <input v-model="buyReceiverPhone" type="tel" placeholder="手机号" autocomplete="tel" />
        </label>
        <label class="buy-field">
          <span>配送地区</span>
          <input v-model="buyReceiverRegion" type="text" placeholder="省 / 市 / 区" />
        </label>
        <label class="buy-field">
          <span>详细地址</span>
          <input v-model="buyReceiverAddress" type="text" placeholder="街道、门牌、楼层等" />
        </label>
      </div>
    </ConfirmModal>

    <ConfirmModal
      :open="restockConfirmOpen"
      :title="restockConfirmTitle"
      confirm-label="确定订阅"
      cancel-label="取消"
      :loading="subscribing"
      @update:open="restockConfirmOpen = $event"
      @confirm="confirmSubscribeRestock"
    >
      <p v-if="restockConfirmMode === 'soldout'" class="pd-restock-confirm">
        补货后我们将在消息中心通知您。确定要订阅「{{ product.title }}」的到货提醒吗？
      </p>
      <p v-else class="pd-restock-confirm">
        当前仅剩 {{ product.stock }} 件，库存较紧张。订阅后我们将在消息中心提醒您关注补货与库存变化。确定订阅「{{
          product.title }}」吗？
      </p>
    </ConfirmModal>
  </div>
</template>

<style scoped>
.product-detail--apex {
  --pd-ink: #0a0a0a;
  --pd-muted: #737373;
  --pd-line: #e8e8e8;
  --pd-panel: #ffffff;
  --pd-bg: #f5f5f5;
  --pd-bg-soft: #fafafa;
  --pd-radius: 2px;
  --pd-card-radius: 12px;
  --pd-accent: #a35d00;
  --pd-teal: #0f766e;
  font-family:
    'Inter',
    'Microsoft YaHei',
    'PingFang SC',
    system-ui,
    sans-serif;
}

/* 与 .pw-page 叠加：取消全局 1860 / 本页 1200 收窄，横向铺满 main */
.product-detail-page.pw-page {
  max-width: none;
  width: 100%;
  margin-left: 0;
  margin-right: 0;
  padding-left: clamp(12px, 3vw, 36px);
  padding-right: clamp(12px, 3vw, 36px);
  padding-bottom: clamp(32px, 4vh, 48px);
  box-sizing: border-box;
  /* 内容偏少时也让灰底铺满视口，避免中间一条「窄岛」 */
  min-height: calc(100dvh - 100px);
}

.product-detail-page {
  width: 100%;
  max-width: none;
  margin: 0;
  background: var(--pd-bg);
}

.pd-shell {
  padding: 12px 0 0;
  width: 100%;
  max-width: none;
}

.buy-addr-form {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px dashed var(--pd-line);
  text-align: left;
}

.buy-addr-title {
  margin: 0 0 10px;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--pd-muted);
}

.buy-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 10px;
  font-size: 13px;
  color: var(--pd-muted);
}

.buy-field--block {
  margin-bottom: 12px;
}

.buy-field span {
  font-weight: 700;
  color: var(--pd-ink);
}

.buy-field input,
.buy-select {
  height: 40px;
  padding: 0 12px;
  border: 1px solid var(--pd-line);
  border-radius: var(--pd-radius);
  font-size: 14px;
  color: var(--pd-ink);
  background: #fff;
  box-sizing: border-box;
}

.buy-select {
  cursor: pointer;
}

.buy-field input:focus,
.buy-select:focus {
  outline: none;
  border-color: #737373;
}

.buy-addr-hint {
  margin: 0 0 12px;
  font-size: 12px;
  color: var(--pd-muted);
  line-height: 1.55;
}

.buy-addr-hint strong {
  color: var(--pd-ink);
}

.pd-breadcrumb.product-breadcrumb,
.product-breadcrumb {
  margin-bottom: 16px;
  padding: 12px 16px;
  background: var(--pd-panel);
  border: 1px solid var(--pd-line);
  border-radius: var(--pd-card-radius);
}

.product-breadcrumb-link {
  cursor: pointer;
  font-weight: 700;
  color: var(--pd-ink);
  text-decoration: underline;
  text-underline-offset: 3px;
}

.product-breadcrumb-sep {
  margin: 0 8px;
  color: var(--pd-line);
}

.product-breadcrumb-current {
  color: var(--pd-muted);
  font-weight: 600;
}

.pd-hero-card {
  display: grid;
  grid-template-columns: minmax(260px, 46%) minmax(0, 1fr);
  gap: clamp(24px, 3vw, 48px) clamp(28px, 4vw, 56px);
  /* 行高随左侧图库；右侧列拉高后与左侧总高度对齐，避免大图+缩略图下大片留白 */
  align-items: stretch;
  background: var(--pd-panel);
  padding: clamp(22px, 2.8vw, 40px) clamp(22px, 3.2vw, 44px);
  border-radius: var(--pd-card-radius);
  margin-bottom: 20px;
  border: 1px solid var(--pd-line);
  box-shadow: 0 4px 28px rgba(15, 23, 42, 0.06);
}

.product-gallery {
  position: sticky;
  top: 80px;
  height: fit-content;
  align-self: start;
}

.main-image {
  position: relative;
  width: 100%;
  aspect-ratio: 1 / 1;
  max-height: min(720px, 58vh, 52vw);
  margin: 0 auto;
  background: var(--pd-bg-soft);
  border-radius: 10px;
  overflow: hidden;
  border: 1px solid var(--pd-line);
}

.main-image :deep(img) {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: center;
  display: block;
}

.thumb-row {
  margin-top: 12px;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
}

.thumb-btn {
  border: 1px solid var(--pd-line);
  border-radius: 8px;
  background: var(--pd-panel);
  overflow: hidden;
  cursor: pointer;
  padding: 0;
  aspect-ratio: 1 / 1;
}

.thumb-btn :deep(img) {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.thumb-btn.active {
  border-color: var(--pd-ink);
  box-shadow: 0 0 0 1px var(--pd-ink);
}

.image-placeholder {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  color: var(--pd-muted);
}

.product-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.product-info-panel {
  display: flex;
  flex-direction: column;
  gap: clamp(22px, 2.8vh, 32px);
  min-width: 0;
  width: 100%;
  min-height: 100%;
  height: 100%;
}

.pd-badges {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}

.pd-badge {
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  padding: 4px 10px;
  border-radius: 999px;
}

.pd-badge--fill {
  background: var(--pd-ink);
  color: #fff;
}

.pd-badge--premium {
  background: linear-gradient(135deg, #fff7ed 0%, #ffedd5 100%);
  color: #c2410c;
  border: 1px solid rgba(194, 65, 12, 0.22);
}

.pd-badge--model {
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.02em;
  text-transform: none;
  padding: 5px 12px;
  border-radius: 8px;
  border: 1px solid var(--pd-line);
  color: var(--pd-muted);
  background: var(--pd-bg-soft);
}

.pd-badge--line {
  border: 1px solid var(--pd-line);
  color: var(--pd-muted);
  background: var(--pd-panel);
}

.pd-hero-lead {
  margin: -6px 0 0;
}

.pd-hero-lead-line {
  margin: 0 0 8px;
  font-size: 11px;
  line-height: 1.65;
  color: var(--pd-muted);
}

.pd-hero-lead-line:last-child {
  margin-bottom: 0;
}

.pd-stat-row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0;
  padding: clamp(22px, 2.8vh, 32px) 0;
  border-top: 1px solid var(--pd-line);
  border-bottom: 1px solid var(--pd-line);
}

.pd-stat {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
  padding: 0 clamp(10px, 2vw, 22px);
}

.pd-stat:first-child {
  padding-left: 0;
}

.pd-stat:last-child {
  padding-right: 0;
}

.pd-stat:not(:first-child) {
  border-left: 1px solid var(--pd-line);
}

.pd-stat-label {
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--pd-muted);
}

.pd-stat-label-en {
  font-size: 9px;
  font-weight: 700;
  letter-spacing: 0.12em;
  color: #a3a3a3;
  margin-left: 6px;
}

.pd-stat-num {
  font-size: 12px;
  font-weight: 700;
  color: var(--pd-ink);
}

.pd-stat-num--warn {
  color: var(--pd-accent);
}

.pd-price {
  font-size: clamp(24px, 4vw, 32px);
  font-weight: 800;
  letter-spacing: -0.03em;
  line-height: 1;
}

.pd-stat-rating {
  color: var(--pd-accent);
}

.pd-stat-suffix {
  font-size: 10px;
  font-weight: 600;
  color: var(--pd-muted);
  margin-left: 2px;
}

.pd-meta-compact.product-meta {
  padding: 0;
  border-bottom: none;
  gap: 10px 16px;
}

.pd-qty-block {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 12px;
}

.pd-qty-label {
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--pd-muted);
}

.pd-qty-label-en {
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.14em;
  color: #a3a3a3;
  margin-left: 8px;
}

.quantity-control--hero {
  width: 100%;
  min-height: 48px;
  border-radius: 12px;
}

.quantity-control--hero .qty-btn {
  width: 48px;
  height: 48px;
  font-size: 16px;
}

.quantity-control--hero .qty-input {
  flex: 1;
  min-width: 0;
  height: 48px;
  font-size: 12px;
  font-weight: 700;
}

.pd-primary-actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}

.btn-pd {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 48px;
  padding: 0 18px;
  border-radius: var(--pd-radius);
  font-size: 10px;
  font-weight: 700;
  cursor: pointer;
  border: 1px solid transparent;
  transition:
    background 0.15s,
    border-color 0.15s,
    opacity 0.15s;
}

.btn-pd--xl {
  min-height: 56px;
  padding: 10px 16px;
  border-radius: var(--pd-radius);
}

.btn-pd--black.btn-pd--xl {
  gap: 12px;
  justify-content: center;
}

.btn-pd-stack {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  text-align: left;
  line-height: 1.15;
}

.btn-pd-stack--centered {
  align-items: center;
  text-align: center;
  width: 100%;
}

.btn-pd-cn {
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.02em;
}

.btn-pd-en {
  font-size: 9px;
  font-weight: 700;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  opacity: 0.92;
}

.btn-pd-icon-lg {
  width: 22px;
  height: 22px;
  flex-shrink: 0;
}

.btn-pd--contact {
  flex-direction: row;
  gap: 14px;
}

.btn-pd--contact .btn-pd-stack {
  align-items: flex-start;
}

.btn-pd:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.btn-pd--black {
  background: var(--pd-ink);
  color: #fff;
  border-color: var(--pd-ink);
}

.btn-pd--accent {
  background: var(--pd-accent);
  color: #fff;
  border-color: var(--pd-accent);
}

.btn-pd--accent:hover:not(:disabled) {
  filter: brightness(1.06);
}

.btn-pd--outline {
  width: 100%;
  background: var(--pd-panel);
  color: var(--pd-ink);
  border-color: #d4d4d4;
}

.btn-pd--outline:hover:not(:disabled) {
  border-color: var(--pd-ink);
  background: var(--pd-bg-soft);
}

.btn-pd--restock {
  margin-top: -4px;
}

.btn-pd-icon {
  width: 18px;
  height: 18px;
  flex-shrink: 0;
}

.btn-pd--outline.btn-pd--xl.btn-pd--contact .btn-pd-cn {
  font-size: 11px;
}

.pd-trust-strip {
  display: flex;
  flex-wrap: wrap;
  gap: 16px 28px;
  margin-top: auto;
  padding-top: clamp(16px, 2.5vh, 28px);
  border-top: 1px solid var(--pd-line);
}

.pd-trust-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.pd-trust-k {
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.04em;
  color: var(--pd-ink);
}

.pd-trust-v {
  font-size: 10px;
  color: var(--pd-muted);
}

.product-title {
  font-size: clamp(20px, 2.7vw, 28px);
  color: var(--pd-ink);
  font-weight: 800;
  letter-spacing: -0.02em;
  line-height: 1.3;
  margin: 0;
}

.product-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 20px;
}

.product-meta:not(.pd-meta-compact) {
  padding: 12px 0;
  border-bottom: 1px solid var(--pd-line);
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.meta-label {
  color: var(--pd-muted);
  font-size: 10px;
}

.category-tag,
.brand-tag {
  padding: 3px 8px;
  border-radius: var(--pd-radius);
  font-size: 10px;
  font-weight: 600;
  border: 1px solid var(--pd-line);
}

.category-tag {
  background: var(--pd-bg-soft);
  color: var(--pd-muted);
}

.brand-tag {
  background: var(--pd-panel);
  color: var(--pd-ink);
}

.quantity-control {
  display: flex;
  align-items: center;
  border: 1px solid var(--pd-line);
  border-radius: var(--pd-radius);
  overflow: hidden;
  background: var(--pd-panel);
}

.qty-btn {
  width: 36px;
  height: 36px;
  border: none;
  background: var(--pd-panel);
  cursor: pointer;
  font-size: 18px;
  color: var(--pd-muted);
  transition:
    background 0.15s,
    color 0.15s;
}

.qty-btn:hover {
  background: var(--pd-bg);
  color: var(--pd-ink);
}

.qty-input {
  width: 52px;
  height: 36px;
  border: none;
  border-left: 1px solid var(--pd-line);
  border-right: 1px solid var(--pd-line);
  text-align: center;
  font-size: 14px;
  font-weight: 600;
  color: var(--pd-ink);
}

.pd-tabs-card {
  background: var(--pd-panel);
  border-radius: var(--pd-card-radius);
  padding: 0 0 24px;
  margin-bottom: 24px;
  border: 1px solid var(--pd-line);
  box-shadow: 0 4px 24px rgba(15, 23, 42, 0.04);
  overflow: hidden;
}

.pd-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 0;
  padding: 0 clamp(16px, 2vw, 32px);
  border-bottom: 1px solid var(--pd-line);
  background: var(--pd-bg-soft);
}

.pd-tab {
  position: relative;
  padding: 19px 24px;
  margin: 0;
  border: none;
  background: transparent;
  font-size: clamp(14px, 1.15vw, 16px);
  font-weight: 600;
  color: var(--pd-muted);
  cursor: pointer;
  transition: color 0.15s;
}

.pd-tab:hover {
  color: var(--pd-ink);
}

.pd-tab--on {
  color: var(--pd-ink);
  font-weight: 800;
}

.pd-tab--on::after {
  content: '';
  position: absolute;
  left: 18px;
  right: 18px;
  bottom: 0;
  height: 3px;
  background: var(--pd-ink);
  border-radius: 2px 2px 0 0;
}

.pd-tab-count {
  margin-left: 8px;
  font-size: clamp(12px, 1vw, 14px);
  font-weight: 700;
  color: var(--pd-muted);
}

.pd-tab-panels {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(260px, 26%);
  gap: clamp(20px, 3vw, 40px);
  padding: clamp(26px, 2.5vw, 32px) clamp(16px, 2.4vw, 36px) 0;
  align-items: start;
  font-size: 14px;
}

.pd-tab-main {
  min-width: 0;
}

.pd-panel-block {
  animation: pd-fade 0.2s ease;
}

@keyframes pd-fade {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

.pd-block-title {
  margin: 0 0 14px;
  font-size: 13px;
  font-weight: 800;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: #525252;
}

.pd-block-title--sp {
  margin-top: 28px;
}

.pd-block-title--inline {
  margin: 0;
  letter-spacing: normal;
  text-transform: none;
  font-size: clamp(18px, 1.9vw, 22px);
  color: var(--pd-ink);
}

.pd-prose {
  margin: 0 0 22px;
  line-height: 1.75;
  color: #3f3f46;
  font-size: clamp(14px, 1.25vw, 15px);
  white-space: pre-wrap;
}

.pd-prose--muted {
  font-style: italic;
  color: #71717a;
  font-size: clamp(14px, 1.25vw, 15px);
}

.pd-restock-confirm {
  margin: 0;
  line-height: 1.65;
  color: var(--pd-muted);
  font-size: 13px;
}

.pd-feature-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 14px;
  margin-bottom: 8px;
}

.pd-feature-card {
  padding: 18px 20px;
  border-radius: 10px;
  background: var(--pd-bg-soft);
  border: 1px solid var(--pd-line);
}

.pd-feature-card strong {
  display: block;
  margin-bottom: 10px;
  font-size: 13px;
  font-weight: 800;
  color: var(--pd-ink);
}

.pd-feature-card p {
  margin: 0;
  font-size: 13px;
  line-height: 1.65;
  color: #525252;
}

.pd-nutri-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 16px;
}

.pd-nutri-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 18px 10px;
  border-radius: 10px;
  border: 1px solid var(--pd-line);
  background: var(--pd-panel);
}

.pd-nutri-pct {
  font-size: clamp(20px, 2.4vw, 26px);
  font-weight: 800;
  color: var(--pd-teal);
}

.pd-nutri-name {
  font-size: 11px;
  font-weight: 700;
  color: #404040;
  margin-top: 6px;
}

.pd-ingredient-note {
  font-size: 13px;
  line-height: 1.65;
  color: #525252;
  margin: 0;
}

.pd-auth-list {
  margin: 12px 0 0;
  padding-left: 22px;
  color: #525252;
  font-size: 13px;
  line-height: 1.75;
}

/*
 * 正品溯源：全局 App.vue 里 :where(.main … p, li) 会把正文压成 11px；
 * 用 .pd-authenticity 提高特异性，保证标题 / 段落 / 列表可读。
 */
.pd-authenticity .pd-block-title {
  font-size: clamp(17px, 1.55vw, 21px);
  letter-spacing: 0.06em;
  color: var(--pd-ink);
  margin-bottom: 18px;
}

.pd-authenticity .pd-prose {
  font-size: clamp(15px, 1.35vw, 18px);
  line-height: 1.75;
  color: #3f3f46;
  margin-bottom: 20px;
}

.pd-authenticity .pd-auth-list {
  margin-top: 4px;
  padding-left: 24px;
  font-size: clamp(15px, 1.35vw, 18px);
  line-height: 1.75;
  color: #404040;
}

.pd-authenticity .pd-auth-list > li {
  font-size: clamp(15px, 1.35vw, 18px);
  line-height: 1.7;
  margin-bottom: 12px;
}

.pd-authenticity .pd-auth-list > li:last-child {
  margin-bottom: 0;
}

.pd-aside-dark {
  background: linear-gradient(160deg, #1a1a1a 0%, #2d2d2d 100%);
  color: #f5f5f5;
  border-radius: 10px;
  padding: 20px 22px;
  margin-bottom: 16px;
}

.pd-aside-title {
  margin: 0 0 14px;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.95);
}

.pd-aside-list {
  margin: 0;
  padding-left: 20px;
  font-size: 13px;
  line-height: 1.7;
  color: rgba(255, 255, 255, 0.9);
}

.pd-aside-feed {
  border: 1px solid var(--pd-line);
  border-radius: 10px;
  padding: 18px 20px;
  background: var(--pd-panel);
}

.pd-aside-feed-title {
  margin: 0 0 10px;
  font-size: 15px;
  font-weight: 800;
  color: var(--pd-ink);
}

.pd-aside-feed-hint {
  margin: 0 0 14px;
  font-size: 12px;
  color: #525252;
  line-height: 1.55;
}

.pd-feed-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.pd-feed-table th,
.pd-feed-table td {
  padding: 12px 14px;
  text-align: left;
  border-bottom: 1px solid var(--pd-line);
}

.pd-feed-table td {
  color: var(--pd-ink);
  font-weight: 600;
}

.pd-feed-table th {
  font-weight: 700;
  color: #525252;
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

.pd-feed-table tbody tr:last-child td {
  border-bottom: none;
}

.pd-related {
  background: var(--pd-panel);
  border-radius: var(--pd-card-radius);
  border: 1px solid var(--pd-line);
  padding: clamp(20px, 2.5vw, 28px) clamp(16px, 2.4vw, 36px) clamp(24px, 3vh, 36px);
  box-shadow: 0 4px 24px rgba(15, 23, 42, 0.04);
}

.pd-related-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
}

.pd-related-title {
  margin: 0;
  font-size: 14px;
  font-weight: 800;
  color: var(--pd-ink);
}

.pd-related-more {
  border: none;
  background: transparent;
  font-size: 11px;
  font-weight: 700;
  color: var(--pd-accent);
  cursor: pointer;
  text-decoration: underline;
  text-underline-offset: 3px;
}

.pd-related-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: clamp(14px, 2vw, 24px);
}

.pd-related-card {
  display: flex;
  flex-direction: column;
  border: 1px solid var(--pd-line);
  border-radius: 10px;
  padding: 12px;
  cursor: pointer;
  transition:
    box-shadow 0.2s,
    border-color 0.2s;
  background: var(--pd-panel);
}

.pd-related-card:hover {
  border-color: #d4d4d4;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.06);
}

.pd-related-img {
  position: relative;
  width: 100%;
  aspect-ratio: 1 / 1;
  border-radius: 8px;
  overflow: hidden;
  background: var(--pd-bg-soft);
  margin-bottom: 10px;
}

.pd-related-img-el {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.pd-related-img :deep(img) {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.pd-related-cat {
  margin: 0 0 6px;
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--pd-muted);
}

.pd-related-name {
  margin: 0 0 10px;
  font-size: 11px;
  font-weight: 600;
  line-height: 1.35;
  color: var(--pd-ink);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 2.7em;
  flex: 1;
}

/* 价格在上、全宽购买按钮在下（对齐极简电商卡片） */
.pd-related-foot {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 10px;
  margin-top: auto;
}

.pd-related-price {
  font-size: 15px;
  font-weight: 800;
  color: var(--pd-ink);
  letter-spacing: -0.02em;
  align-self: flex-start;
}

.pd-related-cta {
  width: 100%;
  min-height: 38px;
  padding: 0 12px;
  border-radius: 0;
  border: none;
  background: var(--pd-ink);
  color: #fff;
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  cursor: pointer;
  flex-shrink: 0;
}

.pd-related-cta:disabled {
  background: #a3a3a3;
  color: #f5f5f5;
  cursor: not-allowed;
}

/* 用户评价：整体放大；见 App.vue 对 .pd-reviews-panel 内 p 的 font-size: inherit 解除全局 11px */
.pd-reviews-panel {
  padding: clamp(8px, 1vw, 12px) 0 4px;
}

.pd-reviews-panel .pd-block-title--inline {
  font-size: clamp(22px, 2.2vw, 28px);
}

.pd-review-hint {
  margin: 0 0 22px;
  padding: 14px 16px;
  border-radius: var(--pd-radius);
  background: var(--pd-bg-soft);
  border: 1px solid var(--pd-line);
  font-size: clamp(14px, 1.15vw, 16px);
  line-height: 1.6;
  color: #525252;
}

.pd-reviews-panel p.review-content {
  font-size: clamp(15px, 1.3vw, 18px);
  line-height: 1.7;
}

.section-header.pd-review-head {
  margin-bottom: 16px;
  padding-bottom: 0;
  border-bottom: none;
}

.pd-spec-list.spec-list {
  margin-top: 0;
}

.spec-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.spec-item {
  display: flex;
  padding: 12px 14px;
  background: var(--pd-bg);
  border-radius: var(--pd-radius);
  border: 1px solid var(--pd-line);
}

.spec-label {
  width: 120px;
  color: #525252;
  font-size: 12px;
  flex-shrink: 0;
  font-weight: 700;
}

.spec-value {
  color: var(--pd-ink);
  font-size: 14px;
  font-weight: 600;
  flex: 1;
}

.detail-placeholder {
  color: #525252;
  text-align: center;
  padding: 40px 18px;
  font-size: 14px;
  border: 1px dashed var(--pd-line);
  border-radius: var(--pd-radius);
  background: var(--pd-bg);
}

.review-form {
  position: relative;
  z-index: 2;
  pointer-events: auto;
  background: var(--pd-bg);
  padding: 22px 24px;
  border-radius: var(--pd-radius);
  margin-bottom: 24px;
  border: 1px solid var(--pd-line);
}

.form-group {
  margin-bottom: 16px;
}

.form-group label {
  display: block;
  margin-bottom: 10px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: #525252;
}

.rating-selector {
  position: relative;
  z-index: 1;
  display: flex;
  flex-wrap: wrap;
  gap: 4px 8px;
  align-items: center;
}

/* span[role=button]：避开 App.vue 对全局 button 的 min-height/font-size，保证可点区域与对比度 */
.rating-selector .rating-star {
  margin: 0;
  padding: 0;
  border: none;
  background: transparent;
  font-family: inherit;
  font-size: clamp(26px, 2.6vw, 32px);
  line-height: 1;
  cursor: pointer;
  color: #737373;
  transition: color 0.15s ease;
  min-width: 44px;
  min-height: 44px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  user-select: none;
  touch-action: manipulation;
  -webkit-tap-highlight-color: transparent;
}

.rating-selector .rating-star.rating-star--on {
  color: var(--pd-accent);
}

.rating-selector .rating-star:not(.rating-star--on):hover {
  color: #525252;
}

.rating-selector .rating-star.rating-star--on:hover {
  color: var(--pd-accent);
}

.rating-selector .rating-star:focus {
  outline: none;
}

.rating-selector .rating-star:focus-visible {
  outline: 2px solid var(--pd-accent);
  outline-offset: 3px;
  border-radius: 2px;
}

.form-group textarea {
  width: 100%;
  padding: 14px 16px;
  border: 1px solid var(--pd-line);
  border-radius: var(--pd-radius);
  font-size: clamp(14px, 1.2vw, 16px);
  font-family: inherit;
  resize: vertical;
  color: var(--pd-ink);
  background: var(--pd-panel);
  line-height: 1.6;
}

.form-group textarea:focus {
  outline: none;
  border-color: #737373;
}

.form-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.btn-submit,
.btn-cancel {
  padding: 10px 22px;
  border-radius: var(--pd-radius);
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  transition:
    background 0.15s,
    border-color 0.15s;
}

.btn-submit {
  background: var(--pd-ink);
  color: #fff;
  border: 1px solid var(--pd-ink);
}

.btn-cancel {
  background: var(--pd-panel);
  color: var(--pd-ink);
  border: 1px solid var(--pd-line);
}

.btn-cancel:hover {
  border-color: var(--pd-ink);
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 20px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--pd-line);
}

.header-left {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}

.section-header h2,
.section-header h3 {
  font-size: clamp(17px, 1.7vw, 20px);
  color: var(--pd-ink);
  font-weight: 800;
  letter-spacing: -0.02em;
}

.review-count {
  color: #525252;
  font-size: clamp(14px, 1.15vw, 16px);
  font-weight: 600;
}

.avg-rating {
  color: var(--pd-ink);
  font-size: clamp(14px, 1.15vw, 16px);
  font-weight: 600;
  letter-spacing: 0.04em;
}

/* 均分旁星号：琥珀色实心星，与正文区分；选中高亮时仍保持金色系 */
.avg-rating-stars {
  margin-left: 8px;
  color: var(--pd-accent);
  font-weight: 800;
  letter-spacing: 0.12em;
  text-shadow: 0 0.5px 0 rgba(163, 93, 0, 0.35);
}

.avg-rating-stars::selection {
  background: rgba(163, 93, 0, 0.22);
  color: #7a4200;
}

.btn-write-review {
  padding: 10px 18px;
  background: var(--pd-ink);
  color: #fff;
  border: 1px solid var(--pd-ink);
  border-radius: var(--pd-radius);
  cursor: pointer;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.no-reviews {
  text-align: center;
  padding: 56px 20px;
  color: #525252;
  font-size: clamp(15px, 1.25vw, 17px);
  border: 1px dashed var(--pd-line);
  border-radius: var(--pd-radius);
  background: var(--pd-bg);
}

.reviews-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.review-card {
  padding: 20px 22px;
  background: var(--pd-panel);
  border-radius: var(--pd-radius);
  border: 1px solid var(--pd-line);
  transition: box-shadow 0.2s;
}

.review-card:hover {
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.06);
}

.review-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
  gap: 12px;
}

.reviewer-info {
  display: flex;
  align-items: center;
  gap: 10px;
}

.reviewer-avatar {
  width: 44px;
  height: 44px;
  background: var(--pd-ink);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 700;
  color: #fff;
}

.reviewer-name {
  font-size: clamp(15px, 1.25vw, 17px);
  font-weight: 600;
  color: var(--pd-ink);
}

.review-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.review-rating {
  color: var(--pd-accent);
  font-size: clamp(16px, 1.5vw, 20px);
  letter-spacing: 0.06em;
}

.review-date {
  color: #525252;
  font-size: 13px;
}

.review-content {
  line-height: 1.65;
  color: #3f3f46;
  margin: 0;
}

@media (max-width: 900px) {
  .pd-hero-card {
    grid-template-columns: 1fr;
  }

  .product-gallery {
    position: static;
  }

  .main-image {
    max-height: min(440px, 90vw);
  }

  .pd-stat-row {
    grid-template-columns: 1fr;
    gap: 0;
    padding: 8px 0 16px;
  }

  .pd-stat {
    padding: 14px 0 !important;
    border-left: none !important;
  }

  .pd-stat:not(:last-child) {
    border-bottom: 1px solid var(--pd-line);
  }

  .pd-tab-panels {
    grid-template-columns: 1fr;
  }

  .pd-nutri-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .pd-related-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 520px) {
  .pd-primary-actions {
    grid-template-columns: 1fr;
  }

  .pd-related-grid {
    grid-template-columns: 1fr;
  }
}
</style>
