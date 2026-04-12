<script setup>
import { ref, onMounted, computed, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { api } from '../utils/request'
import { productMatchesKeyword } from '../utils/productSearch'
import {
  formatCategoryWithParent,
  productMatchesCategorySelection
} from '../utils/categoryDisplay'
import { getFlatCategoryNavCategories } from '../utils/categoryNav'
import { showAppMessage } from '../utils/appMessage'
import { restockSubscribeSuccessBody } from '../utils/apiFriendlyMessage'
import PaginationBar from '../components/PaginationBar.vue'

const router = useRouter()
const route = useRoute()
const products = ref([])
const allCategories = ref([])
const flatNavCategories = computed(() =>
  getFlatCategoryNavCategories(allCategories.value)
)
const selectedCategory = ref(null)
const keyword = ref('')
const appliedKeyword = ref('')
const loading = ref(false)
const errorMsg = ref('')
const page = ref(1)
const pageSize = ref(12)
const subscribing = ref({})

function setPageSize(n) {
  pageSize.value = Number(n || 12)
  page.value = 1
}

function queryQ() {
  const q = route.query.q
  if (Array.isArray(q)) return String(q[0] ?? '').trim()
  return String(q ?? '').trim()
}

function syncKeywordFromRoute() {
  const q = queryQ()
  keyword.value = q
  appliedKeyword.value = q
}

onMounted(async () => {
  await loadCategories()
  syncKeywordFromRoute()
  await loadProducts()
})

watch(
  () => route.query.q,
  async () => {
    syncKeywordFromRoute()
    page.value = 1
    await loadProducts()
  }
)

const selectedCategoryName = computed(() => {
  if (!selectedCategory.value) return ''
  const hit = allCategories.value.find(
    (c) => Number(c.categoryId) === Number(selectedCategory.value)
  )
  return hit ? hit.name : ''
})

function viewDetail(productId) {
  router.push(`/product/${productId}`)
}

async function loadCategories() {
  const res = await api.getCategories()
  if (res.code === 200) {
    allCategories.value = res.data
  } else {
    errorMsg.value = res.message || '分类加载失败'
  }
}

async function loadProducts() {
  loading.value = true
  errorMsg.value = ''
  const kw = appliedKeyword.value.trim()
  const hasKw = !!kw
  const res = hasKw
    ? await api.getProducts()
    : selectedCategory.value
      ? await api.getProductsByCategory(selectedCategory.value)
      : await api.getProducts()
  if (res.code === 200) {
    let list = Array.isArray(res.data) ? res.data : []
    if (hasKw) {
      list = list.filter((p) => productMatchesKeyword(p, kw))
      if (selectedCategory.value != null) {
        list = list.filter((p) =>
          productMatchesCategorySelection(p, selectedCategory.value, allCategories.value)
        )
      }
    }
    products.value = list
  } else {
    errorMsg.value = res.message || '商品加载失败'
    products.value = []
  }
  loading.value = false
  page.value = 1
}

function filterByCategory(categoryId) {
  selectedCategory.value = categoryId
  loadProducts()
}

function runSearch() {
  appliedKeyword.value = keyword.value
  const q = appliedKeyword.value.trim()
  const prevQ = queryQ()
  router.replace({ path: '/products', query: q ? { q } : {} })
  if (q === prevQ) {
    loadProducts()
  }
}

function resetSearch() {
  keyword.value = ''
  appliedKeyword.value = ''
  selectedCategory.value = null
  const hadQ = !!queryQ()
  router.replace({ path: '/products' })
  if (!hadQ) {
    loadProducts()
  }
}

function categoryDisplayName(product) {
  return formatCategoryWithParent(product, allCategories.value)
}

async function subscribeRestock(product, e) {
  if (e?.stopPropagation) e.stopPropagation()
  const pid = Number(product?.productId || 0)
  if (!pid) return
  const uid = Number(localStorage.getItem('userId') || 0)
  if (!uid) {
    showAppMessage('请先登录', '提示')
    router.push('/login')
    return
  }
  if (subscribing.value[pid]) return
  subscribing.value = { ...subscribing.value, [pid]: true }
  const res = await api.userSubscribeRestock({ userId: uid, productId: pid })
  subscribing.value = { ...subscribing.value, [pid]: false }
  if (res.code === 200) {
    showAppMessage(restockSubscribeSuccessBody(res.message), '订阅成功')
  } else {
    showAppMessage(res.message || '操作失败', '提示')
  }
}

const total = computed(() => (Array.isArray(products.value) ? products.value.length : 0))
const pagedProducts = computed(() => {
  const list = Array.isArray(products.value) ? products.value : []
  const p = Math.max(1, Number(page.value || 1))
  const ps = Math.max(1, Number(pageSize.value || 1))
  const start = (p - 1) * ps
  return list.slice(start, start + ps)
})
</script>

<template>
  <div class="pw-page product-list">
    <section class="pw-hero product-hero">
      <h1 class="pw-title">商品中心</h1>
      <p class="pw-lead">专业宠物用品目录</p>
    </section>

    <section class="pw-section category-bar">
      <div class="category-bar-search">
        <input
          v-model="keyword"
          class="pw-input search-input"
          placeholder="搜索商品名称或ID"
          @keyup.enter="runSearch"
        />
        <button type="button" class="pw-btn pw-btn-sm" @click="runSearch">搜索</button>
        <button type="button" class="pw-btn-ghost pw-btn-sm" @click="resetSearch">重置</button>
      </div>
      <div class="category-bar-filters">
        <button
          type="button"
          :class="['category-btn', { active: !selectedCategory }]"
          @click="filterByCategory(null)"
        >
          全部商品
        </button>
        <div v-if="flatNavCategories.length" class="product-category-tier">
          <div class="product-category-tier-btns">
            <button
              v-for="cat in flatNavCategories"
              :key="`nav-${cat.categoryId}`"
              type="button"
              :class="['category-btn', { active: selectedCategory === cat.categoryId }]"
              @click="filterByCategory(cat.categoryId)"
            >
              {{ cat.name }}
            </button>
          </div>
        </div>
      </div>
    </section>

    <div v-if="appliedKeyword || selectedCategoryName" class="product-active-filters">
      <span v-if="appliedKeyword" class="filter-chip">关键词：{{ appliedKeyword }}</span>
      <span v-if="selectedCategoryName" class="filter-chip">分类：{{ selectedCategoryName }}</span>
    </div>

    <div v-if="loading" class="pw-state">加载中...</div>
    <div v-else-if="errorMsg" class="pw-state pw-state--error">{{ errorMsg }}</div>
    <div v-else-if="products.length === 0" class="pw-state pw-state--empty">暂无商品</div>

    <div v-else class="products">
      <div
        v-for="product in pagedProducts"
        :key="product.productId"
        :class="['product-card', { disabled: Number(product.status ?? 1) !== 1 }]"
        @click="Number(product.status ?? 1) !== 1 ? null : viewDetail(product.productId)"
      >
        <div class="product-image">
          <img
            v-if="product.detail?.imageUrl"
            :src="product.detail.imageUrl"
            class="img"
            alt="商品图片"
          />
          <div v-else class="placeholder">暂无图片</div>
          <div v-if="Number(product.stock) < 20" class="stock-badge">库存紧张</div>
          <div v-if="Number(product.status ?? 1) !== 1" class="off-badge">已下架</div>
          <div v-else-if="Number(product.stock) <= 0" class="soldout-badge">售罄</div>
        </div>
        <div class="product-info">
          <h3>{{ product.title }}</h3>
          <p class="category">{{ categoryDisplayName(product) }}</p>
          <div class="footer">
            <span class="price">¥{{ product.price }}</span>
            <div class="footer-actions">
              <button
                type="button"
                class="pw-btn-ghost pw-btn-sm btn"
                @click.stop="viewDetail(product.productId)"
              >
                查看详情
              </button>
              <button
                v-if="Number(product.stock) <= 0 && Number(product.status ?? 1) === 1"
                type="button"
                class="pw-btn-ghost pw-btn-sm btn restock-btn"
                :disabled="subscribing[product.productId]"
                @click.stop="subscribeRestock(product, $event)"
              >
                {{ subscribing[product.productId] ? '提交中...' : '关注补货' }}
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <PaginationBar
      :page="page"
      :page-size="pageSize"
      :total="total"
      :page-size-options="[8, 12, 24, 48]"
      @update:page="page = $event"
      @update:page-size="setPageSize"
    />
  </div>
</template>

<style scoped>
.product-list {
  padding: 10px 0 18px;
}

.product-hero { margin-bottom: 14px; }

.category-bar {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 12px;
}

.category-bar-search {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  align-items: center;
}

.category-bar-filters {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.product-category-tier {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  gap: 8px 12px;
}

.product-category-tier-btns {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  flex: 1;
  min-width: 0;
}

.product-active-filters {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.filter-chip {
  font-size: 12px;
  color: #16355f;
  background: #eaf2ff;
  border: 1px solid #c6daf8;
  border-radius: 12px;
  padding: 2px 10px;
}

.search-input {
  min-width: 240px;
  height: 32px;
  font-size: 12px;
  padding: 0 10px;
}

.category-btn {
  padding: 7px 14px;
  border: 1px solid #cad4e1;
  background: #f8faff;
  border-radius: 2px;
  cursor: pointer;
  transition: all 0.2s;
  font-size: 12px;
  color: #506078;
  font-weight: 600;
}

.category-btn:hover {
  border-color: #0b1630;
  color: #0b1630;
}

.category-btn.active {
  background: #0b1630;
  color: #f3f7ff;
  border-color: #0b1630;
}

.products {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 12px;
}

.product-card {
  background: #fcfdff;
  border-radius: 2px;
  overflow: hidden;
  cursor: pointer;
  transition: all 0.3s ease;
  border: 1px solid #dae2ec;
}

.product-card.disabled {
  opacity: 0.75;
  cursor: not-allowed;
}

.product-card.disabled:hover {
  transform: none;
  box-shadow: none;
  border-color: #dae2ec;
}

.product-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 22px rgba(9, 22, 41, 0.08);
  border-color: #102440;
}

.product-image {
  height: 190px;
  background: linear-gradient(135deg, #d4dce5 0%, #b9c4d1 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
}

.img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.stock-badge {
  position: absolute;
  top: 8px;
  right: 8px;
  background: #ff4d4f;
  color: white;
  padding: 2px 8px;
  border-radius: 2px;
  font-size: 12px;
  font-weight: 800;
}

.off-badge, .soldout-badge {
  position: absolute;
  left: 8px;
  top: 8px;
  padding: 2px 8px;
  border-radius: 2px;
  font-size: 12px;
  font-weight: 800;
  color: #f4f6fb;
  background: rgba(11, 22, 48, 0.85);
}

.soldout-badge {
  background: rgba(94, 110, 132, 0.9);
}

.placeholder {
  color: #bbb;
  font-size: 14px;
}

.product-info {
  padding: 12px;
}

.product-info h3 {
  font-size: 16px;
  margin-bottom: 8px;
  font-weight: 700;
  color: #132239;
  line-height: 1.35;
  min-height: 43px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  word-break: break-all;
}

.category {
  color: #66768b;
  font-size: 12px;
  margin-bottom: 12px;
}

.footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 12px;
  border-top: 1px solid #e2e8f0;
}

.footer-actions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.price {
  font-size: 18px;
  color: #0d1a2f;
  font-weight: 700;
}

.btn {
  padding: 6px 12px;
  background: #f5f8fd;
  color: #2b3b54;
  border: 1px solid #cdd9e8;
  border-radius: 2px;
  cursor: pointer;
  transition: all 0.2s;
  font-size: 12px;
  font-weight: 600;
}

.btn:hover {
  background: #0b1630;
  color: #f3f7ff;
  border-color: #0b1630;
}

.restock-btn {
  border-color: #0b1630;
  color: #0b1630;
}

.restock-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
</style>
