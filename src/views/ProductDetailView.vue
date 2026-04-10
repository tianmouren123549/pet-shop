<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../utils/request'

const route = useRoute()
const router = useRouter()
const product = ref(null)
const productDetail = ref(null)
const reviews = ref([])
const quantity = ref(1)
const userId = ref(Number(localStorage.getItem('userId') || 0))
const activeTab = ref('detail')
const newReview = ref({
  rating: 5,
  content: ''
})
const showReviewForm = ref(false)
const pageError = ref('')

const avgRating = computed(() => {
  if (reviews.value.length === 0) return 0
  const sum = reviews.value.reduce((acc, r) => acc + r.rating, 0)
  return (sum / reviews.value.length).toFixed(1)
})

const isOffShelf = computed(() => Number(product.value?.status ?? 1) !== 1)
const isSoldOut = computed(() => Number(product.value?.stock ?? 0) <= 0)
const canBuy = computed(() => !!product.value && !isOffShelf.value && !isSoldOut.value)
const subscribing = ref(false)

onMounted(async () => {
  if (!userId.value) {
    alert('请先登录')
    router.push('/login')
    return
  }
  const productId = route.params.id

  const productRes = await api.getProduct(productId)
  if (productRes.code === 200) {
    product.value = productRes.data
    productDetail.value = productRes.data.detail || {}
  } else {
    pageError.value = productRes.message || '商品详情加载失败'
    return
  }

  const reviewsRes = await api.getReviews(productId)
  if (reviewsRes.code === 200) {
    reviews.value = reviewsRes.data
  }
})

async function addToCart() {
  if (!product.value) return
  if (!canBuy.value) {
    alert(isOffShelf.value ? '商品已下架' : '商品已售罄')
    return
  }

  const res = await api.addToCart({
    userId: userId.value,
    productId: product.value.productId,
    quantity: quantity.value
  })

  if (res.code === 200) {
    alert('已添加到购物车')
  } else {
    alert(res.message || '加入购物车失败')
  }
}

async function buyNow() {
  if (!product.value) return
  if (!canBuy.value) {
    alert(isOffShelf.value ? '商品已下架' : '商品已售罄')
    return
  }
  const ok = confirm('确认立即购买并生成订单吗？（Mock 模式）')
  if (!ok) return
  const res = await api.userCreateOrderDirect({
    userId: userId.value,
    productId: product.value.productId,
    quantity: quantity.value,
  })
  if (res.code === 200) {
    alert('下单成功')
    router.push(`/order/${res.data.orderId}`)
  } else {
    alert(res.message || '下单失败')
  }
}

function contactMerchant() {
  if (!product.value) return
  if (!userId.value) {
    alert('请先登录')
    return
  }
  const merchantId = Number(product.value.merchantId || 1)
  router.push(`/support?merchantId=${merchantId}&productId=${product.value.productId}`)
}

async function subscribeRestock() {
  if (!product.value) return
  if (!userId.value) {
    alert('请先登录')
    return
  }
  if (!isSoldOut.value) {
    alert('当前商品仍有库存，无需开启到货提醒')
    return
  }
  if (subscribing.value) return
  subscribing.value = true
  const res = await api.userSubscribeRestock({ userId: userId.value, productId: product.value.productId })
  subscribing.value = false
  if (res.code === 200) {
    alert(res.message || '已开启到货提醒')
  } else {
    alert(res.message || '操作失败')
  }
}

async function submitReview() {
  if (!newReview.value.content.trim()) {
    alert('请输入评价内容')
    return
  }

  const res = await api.addReview({
    userId: userId.value,
    productId: product.value.productId,
    rating: newReview.value.rating,
    content: newReview.value.content
  })

  if (res.code === 200) {
    alert('评价成功')
    newReview.value = { rating: 5, content: '' }
    showReviewForm.value = false

    const reviewsRes = await api.getReviews(product.value.productId)
    if (reviewsRes.code === 200) {
      reviews.value = reviewsRes.data
    }
  } else {
    alert(res.message || '评价提交失败')
  }
}

function goBack() {
  router.back()
}

function getStarRating(rating) {
  return '★'.repeat(rating) + '☆'.repeat(5 - rating)
}
</script>

<template>
  <div v-if="pageError" class="pw-page product-detail-page">
    <div class="pw-state pw-state--error">{{ pageError }}</div>
  </div>
  <div v-else-if="product" class="pw-page product-detail-page">
    <div class="pw-section product-breadcrumb">
      <span class="product-breadcrumb-link" @click="goBack">返回</span>
      <span class="product-breadcrumb-sep">/</span>
      <span class="product-breadcrumb-current">{{ product.categoryName }}</span>
      <span class="product-breadcrumb-sep">/</span>
      <span class="product-breadcrumb-current">商品详情</span>
    </div>

    <div class="product-main">
      <div class="product-gallery">
        <div class="main-image">
          <img v-if="productDetail.imageUrl" :src="productDetail.imageUrl" class="product-image" />
          <div v-else class="image-placeholder">暂无图片</div>
        </div>
      </div>

      <div class="product-info-panel">
        <h1 class="product-title">{{ product.title }}</h1>

        <div class="product-meta">
          <span class="meta-item">
            <span class="meta-label">分类：</span>
            <span class="meta-value category-tag">{{ product.categoryName }}</span>
          </span>
          <span v-if="product.brandName" class="meta-item">
            <span class="meta-label">品牌：</span>
            <span class="meta-value brand-tag">{{ product.brandName }}</span>
          </span>
        </div>

        <div class="price-box">
          <div class="price-row">
            <span class="price-label">价格</span>
            <span class="price-value">¥{{ product.price }}</span>
          </div>
          <div class="stock-row">
            <span class="stock-label">库存</span>
            <span class="stock-value" :class="{ 'low-stock': product.stock < 50 }">
              {{ product.stock }} 件
            </span>
          </div>
        </div>

        <div class="purchase-section">
          <div class="quantity-selector">
            <label class="selector-label">数量</label>
            <div class="quantity-control">
              <button
                class="qty-btn"
                @click="quantity = Math.max(1, quantity - 1)"
              >
                −
              </button>
              <input
                type="number"
                v-model.number="quantity"
                min="1"
                :max="product.stock"
                class="qty-input"
              />
              <button
                class="qty-btn"
                @click="quantity = Math.min(product.stock, quantity + 1)"
              >
                +
              </button>
            </div>
          </div>

          <div class="action-buttons">
            <button type="button" class="pw-btn pw-btn-sm btn-add-cart" :disabled="!canBuy" @click="addToCart">
              加入购物车
            </button>
            <button type="button" class="pw-btn pw-btn-sm btn-buy-now" :disabled="!canBuy" @click="buyNow">
              立即购买
            </button>
            <button type="button" class="pw-btn-ghost pw-btn-sm btn-contact" @click="contactMerchant">
              联系商家
            </button>
            <button
              v-if="isSoldOut"
              type="button"
              class="pw-btn-ghost pw-btn-sm btn-restock"
              :disabled="subscribing"
              @click="subscribeRestock"
            >
              {{ subscribing ? '提交中...' : '到货提醒' }}
            </button>
          </div>
        </div>
      </div>
    </div>

    <div class="product-details">
      <div class="detail-tabs">
        <div
          :class="['tab', { active: activeTab === 'detail' }]"
          @click="activeTab = 'detail'"
        >
          商品详情
        </div>
        <div
          :class="['tab', { active: activeTab === 'spec' }]"
          @click="activeTab = 'spec'"
        >
          规格参数
        </div>
      </div>
      <div class="detail-content">
        <div v-if="activeTab === 'detail'" class="detail-section">
          <div v-if="productDetail.description" class="description">
            {{ productDetail.description }}
          </div>
          <div v-else class="detail-placeholder">
            暂无商品详情
          </div>
        </div>
        <div v-else class="spec-section">
          <div v-if="productDetail.specJson" class="spec-list">
            <div
              v-for="(value, key) in productDetail.specJson"
              :key="key"
              class="spec-item"
            >
              <span class="spec-label">{{ key }}</span>
              <span class="spec-value">{{ value }}</span>
            </div>
          </div>
          <div v-else class="detail-placeholder">
            暂无规格参数
          </div>
        </div>
      </div>
    </div>

    <div class="reviews-section">
      <div class="section-header">
        <div class="header-left">
          <h2>用户评价</h2>
          <span class="review-count">共 {{ reviews.length }} 条</span>
          <span v-if="reviews.length > 0" class="avg-rating">
            平均评分: {{ avgRating }} {{ getStarRating(Math.round(avgRating)) }}
          </span>
        </div>
        <button class="btn-write-review" @click="showReviewForm = !showReviewForm">
          {{ showReviewForm ? '取消评价' : '写评价' }}
        </button>
      </div>

      <div v-if="showReviewForm" class="review-form">
        <div class="form-group">
          <label>评分</label>
          <div class="rating-selector">
            <span
              v-for="star in 5"
              :key="star"
              class="star"
              :class="{ active: star <= newReview.rating }"
              @click="newReview.rating = star"
            >
              {{ star <= newReview.rating ? '★' : '☆' }}
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
          <button class="btn-submit" @click="submitReview">提交评价</button>
          <button class="btn-cancel" @click="showReviewForm = false">取消</button>
        </div>
      </div>

      <div v-if="reviews.length === 0" class="no-reviews">
        <p>暂无评价，快来抢沙发吧</p>
      </div>

      <div v-else class="reviews-list">
        <div v-for="review in reviews" :key="review.reviewId" class="review-card">
          <div class="review-header">
            <div class="reviewer-info">
              <div class="reviewer-avatar">{{ review.userId.toString().slice(-1) }}</div>
              <span class="reviewer-name">用户{{ review.userId }}</span>
            </div>
            <div class="review-meta">
              <span class="review-rating">{{ getStarRating(review.rating) }}</span>
              <span class="review-date">{{ new Date(review.createdAt).toLocaleDateString() }}</span>
            </div>
          </div>
          <p class="review-content">{{ review.content }}</p>
          <div v-if="review.goldenRetrieverScore > 0" class="review-tag">
            金毛相关度: {{ (review.goldenRetrieverScore * 100).toFixed(0) }}%
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.product-detail-page {
  width: 100%;
  margin: 0 auto;
}

.product-breadcrumb {
  margin-bottom: 12px;
  padding: 10px 12px;
}

.product-breadcrumb-link {
  cursor: pointer;
  font-weight: 700;
  color: #16355f;
  text-decoration: underline;
  text-underline-offset: 2px;
}

.product-breadcrumb-link:hover {
  color: #0b1630;
}

.product-breadcrumb-sep {
  margin: 0 8px;
  color: #b8c3d3;
}

.product-breadcrumb-current {
  color: #506078;
  font-weight: 700;
}

.product-main {
  display: grid;
  grid-template-columns: 450px 1fr;
  gap: 24px;
  background: #f9fbfe;
  padding: 24px;
  border-radius: 2px;
  margin-bottom: 16px;
  border: 1px solid #d9e2ee;
}

.product-gallery {
  position: sticky;
  top: 80px;
  height: fit-content;
}

.main-image {
  width: 100%;
  height: 450px;
  background: linear-gradient(135deg, #d8e0e8 0%, #bcc8d5 100%);
  border-radius: 2px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid #e8e8e8;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
}

.image-placeholder {
  font-size: 14px;
  color: #bbb;
}

.product-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  border-radius: 2px;
}

.product-info-panel {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.product-title {
  font-size: 28px;
  color: #0e1d33;
  font-weight: 700;
  line-height: 1.4;
}

.product-meta {
  display: flex;
  gap: 16px;
  padding: 12px 0;
  border-bottom: 1px solid #e8e8e8;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.meta-label {
  color: #999;
  font-size: 14px;
}

.category-tag, .brand-tag {
  padding: 2px 8px;
  border-radius: 2px;
  font-size: 12px;
}

.category-tag {
  background: #f5f5f5;
  color: #666;
}

.brand-tag {
  background: #e6f7ff;
  color: #1890ff;
}

.price-box {
  background: #edf2f8;
  padding: 20px;
  border-radius: 2px;
  border: 1px solid #d9e2ee;
}

.price-row {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 12px;
}

.price-label {
  font-size: 14px;
  color: #999;
}

.price-value {
  font-size: 34px;
  color: #0b1730;
  font-weight: 700;
}

.stock-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.stock-label {
  font-size: 14px;
  color: #999;
}

.stock-value {
  font-size: 14px;
  color: #52c41a;
  font-weight: 500;
}

.stock-value.low-stock {
  color: #ff4d4f;
}

.purchase-section {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 20px;
  background: #edf2f8;
  border-radius: 2px;
  border: 1px solid #d9e2ee;
}

.quantity-selector {
  display: flex;
  align-items: center;
  gap: 16px;
}

.selector-label {
  font-size: 14px;
  color: #666;
}

.quantity-control {
  display: flex;
  align-items: center;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  overflow: hidden;
  background: white;
}

.qty-btn {
  width: 36px;
  height: 36px;
  border: none;
  background: white;
  cursor: pointer;
  font-size: 18px;
  color: #666;
  transition: all 0.2s;
}

.qty-btn:hover {
  background: #f5f5f5;
  color: #1890ff;
}

.qty-input {
  width: 60px;
  height: 36px;
  border: none;
  border-left: 1px solid #d9d9d9;
  border-right: 1px solid #d9d9d9;
  text-align: center;
  font-size: 14px;
}

.action-buttons {
  display: flex;
  gap: 12px;
}

.btn-add-cart,
.btn-buy-now,
.btn-contact,
.btn-restock {
  flex: 1;
}

.product-details {
  background: #f9fbfe;
  border-radius: 2px;
  padding: 24px;
  margin-bottom: 16px;
  border: 1px solid #d9e2ee;
}

.detail-tabs {
  display: flex;
  gap: 24px;
  border-bottom: 1px solid #e8e8e8;
  margin-bottom: 20px;
}

.tab {
  padding: 12px 0;
  cursor: pointer;
  font-size: 14px;
  color: #666;
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
  transition: all 0.2s;
}

.tab.active {
  color: #1890ff;
  border-bottom-color: #1890ff;
}

.detail-content {
  padding: 16px 0;
}

.detail-section, .spec-section {
  min-height: 200px;
}

.description {
  line-height: 1.8;
  color: #666;
  font-size: 14px;
  white-space: pre-wrap;
}

.spec-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.spec-item {
  display: flex;
  padding: 12px;
  background: #fafafa;
  border-radius: 4px;
  border: 1px solid #f0f0f0;
}

.spec-label {
  width: 120px;
  color: #999;
  font-size: 14px;
  flex-shrink: 0;
}

.spec-value {
  color: #333;
  font-size: 14px;
  flex: 1;
}

.detail-placeholder {
  color: #999;
  text-align: center;
  padding: 40px;
  font-size: 14px;
}

.review-form {
  background: #fafafa;
  padding: 24px;
  border-radius: 8px;
  margin-bottom: 24px;
  border: 1px solid #f0f0f0;
}

.form-group {
  margin-bottom: 20px;
}

.form-group label {
  display: block;
  margin-bottom: 8px;
  font-size: 14px;
  color: #666;
  font-weight: 500;
}

.rating-selector {
  display: flex;
  gap: 8px;
}

.rating-selector .star {
  font-size: 28px;
  cursor: pointer;
  color: #d9d9d9;
  transition: color 0.2s;
}

.rating-selector .star.active {
  color: #faad14;
}

.rating-selector .star:hover {
  color: #faad14;
}

.form-group textarea {
  width: 100%;
  padding: 12px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  font-size: 14px;
  font-family: inherit;
  resize: vertical;
  transition: border-color 0.2s;
}

.form-group textarea:focus {
  outline: none;
  border-color: #1890ff;
  box-shadow: 0 0 0 2px rgba(24,144,255,0.1);
}

.form-actions {
  display: flex;
  gap: 12px;
}

.btn-submit, .btn-cancel {
  padding: 8px 24px;
  border: none;
  border-radius: 4px;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-submit {
  background: #1890ff;
  color: white;
}

.btn-submit:hover {
  background: #40a9ff;
}

.btn-cancel {
  background: white;
  color: #666;
  border: 1px solid #d9d9d9;
}

.btn-cancel:hover {
  border-color: #1890ff;
  color: #1890ff;
}

.reviews-section {
  background: #f9fbfe;
  border-radius: 2px;
  padding: 24px;
  border: 1px solid #d9e2ee;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 2px solid #f0f0f0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.section-header h2 {
  font-size: 20px;
  color: #333;
  font-weight: 600;
}

.review-count {
  color: #999;
  font-size: 14px;
}

.avg-rating {
  color: #faad14;
  font-size: 14px;
  font-weight: 500;
}

.btn-write-review {
  padding: 8px 20px;
  background: #1890ff;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
  transition: background 0.2s;
}

.btn-write-review:hover {
  background: #40a9ff;
}

.no-reviews {
  text-align: center;
  padding: 60px;
  color: #999;
  font-size: 14px;
}

.reviews-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.review-card {
  padding: 20px;
  background: #fafafa;
  border-radius: 8px;
  border: 1px solid #f0f0f0;
  transition: all 0.2s;
}

.review-card:hover {
  box-shadow: 0 4px 12px rgba(0,0,0,0.08);
  border-color: #e0e0e0;
}

.review-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.reviewer-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.reviewer-avatar {
  width: 36px;
  height: 36px;
  background: #1890ff;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  color: white;
}

.reviewer-name {
  font-size: 14px;
  color: #333;
}

.review-meta {
  display: flex;
  align-items: center;
  gap: 12px;
}

.review-rating {
  color: #faad14;
  font-size: 14px;
}

.review-date {
  color: #999;
  font-size: 12px;
}

.review-content {
  line-height: 1.6;
  color: #666;
  font-size: 14px;
  margin-bottom: 12px;
}

.review-tag {
  display: inline-block;
  padding: 4px 8px;
  background: #f6ffed;
  color: #52c41a;
  border-radius: 2px;
  font-size: 12px;
  border: 1px solid #b7eb8f;
}
</style>
