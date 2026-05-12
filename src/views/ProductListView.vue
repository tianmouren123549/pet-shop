<script>
/** 与 App.vue 中 {@code KeepAlive} 的 include 一致，从详情返回时保留筛选与列表 */
export default { name: 'ProductListView' }
</script>
<script setup>
import { ref, onMounted, computed, watch, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { api } from '../utils/request'
import { formatYuan } from '../utils/formatYuan.js'
import { productMatchesKeyword } from '../utils/productSearch'
import {
  formatCategoryWithParent,
  productMatchesCategorySelection
} from '../utils/categoryDisplay'
import { getFlatCategoryNavCategories } from '../utils/categoryNav'
import { showAppMessage } from '../utils/appMessage'
import PaginationBar from '../components/PaginationBar.vue'
import AppImage from '../components/AppImage.vue'
import AppSkeletonCard from '../components/AppSkeletonCard.vue'

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
/** 每页固定商品数，不提供每页条数切换 */
const PAGE_SIZE = 10
const categoryExpanded = ref(false)
const CATEGORY_VISIBLE_COUNT = 12
const sortBy = ref('featured')
const LIST_SKELETON_COUNT = 10
/** 价格区间上限「不设上限」时用该值与最后一档按钮对应 */
const PRICE_BRACKET_OPEN = Number.POSITIVE_INFINITY
/** 价格区间：[min, max]，max 为 PRICE_BRACKET_OPEN 表示不设上限 */
const selectedPriceBracket = ref(/** @type {null | [number, number]} */ (null))
const viewMode = ref('grid')

function setPriceBracket(bracket) {
  selectedPriceBracket.value = bracket
}

function queryQ() {
  const q = route.query.q
  if (Array.isArray(q)) return String(q[0] ?? '').trim()
  return String(q ?? '').trim()
}

function queryCategory() {
  const c = route.query.category
  if (Array.isArray(c)) return String(c[0] ?? '').trim()
  return String(c ?? '').trim()
}

function syncKeywordFromRoute() {
  const q = queryQ()
  keyword.value = q
  appliedKeyword.value = q
}

function syncCategoryFromRoute() {
  const raw = queryCategory()
  if (!raw) {
    selectedCategory.value = null
    return
  }
  const id = Number(raw)
  selectedCategory.value = Number.isFinite(id) && id > 0 ? id : null
}

onMounted(async () => {
  await loadCategories()
  syncKeywordFromRoute()
  syncCategoryFromRoute()
  await loadProducts()
})

/**
 * 仅在「商品中心」路由下同步 query（keep-alive 缓存时离开该页全局 route 会变为其它页，不能误读 query）。
 */
watch(
  () => (route.name === 'products' ? `${queryQ()}\t${queryCategory()}` : null),
  async () => {
    if (route.name !== 'products') return
    syncKeywordFromRoute()
    syncCategoryFromRoute()
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

const visibleNavCategories = computed(() => {
  const list = Array.isArray(flatNavCategories.value) ? flatNavCategories.value : []
  if (categoryExpanded.value) return list
  return list.slice(0, CATEGORY_VISIBLE_COUNT)
})

const hasMoreCategories = computed(() => {
  const list = Array.isArray(flatNavCategories.value) ? flatNavCategories.value : []
  return list.length > CATEGORY_VISIBLE_COUNT
})

function viewDetail(productId) {
  router.push(`/product/${productId}`)
}

/** 翻页后滚回页面顶部，便于继续看列表与筛选区 */
function onProductsPageChange(nextPage) {
  page.value = Math.max(1, Math.floor(Number(nextPage || 1)))
  nextTick(() => {
    window.scrollTo({ top: 0, behavior: 'smooth' })
  })
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
  const res = await api.getProducts()
  if (res.code === 200) {
    let list = Array.isArray(res.data) ? res.data : []
    if (hasKw) {
      list = list.filter((p) => productMatchesKeyword(p, kw))
    }
    if (selectedCategory.value != null) {
      list = list.filter((p) =>
        productMatchesCategorySelection(p, selectedCategory.value, allCategories.value)
      )
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
  const q = appliedKeyword.value.trim()
  const query = {}
  if (q) query.q = q
  if (categoryId != null) query.category = String(categoryId)
  router.replace({ path: '/products', query })
}

function runSearch() {
  appliedKeyword.value = keyword.value
  const q = appliedKeyword.value.trim()
  const query = {}
  if (q) query.q = q
  if (selectedCategory.value != null) query.category = String(selectedCategory.value)
  router.replace({ path: '/products', query })
}

function resetSearch() {
  keyword.value = ''
  appliedKeyword.value = ''
  selectedCategory.value = null
  categoryExpanded.value = false
  selectedPriceBracket.value = null
  router.replace({ path: '/products' })
}

function categoryDisplayName(product) {
  return formatCategoryWithParent(product, allCategories.value)
}

const showcaseImages = [
  'https://lh3.googleusercontent.com/aida-public/AB6AXuAHR39RYr7dSh7ybGkp5mzrBCyB3GrHSs2qzfkf5nxiKHmR0GIeccud5P1ucdVgJwQscUe9asfqoaet_vBTLP6F6BEZ-6N3M9uULo-etNMjTh5qXmzU0doyefeNuPmWZ6n9z_k2uF3K5fishNH7TLbdUAOZ9lM8XfRsK_tjN9NuwMrdtRNGZxCA6pgMgvgzkcF2dkBGue3FKTTmFQQDXjDkejdIWLSII5NY7IyW4zz0cwz_xptHaBHA8IQ-4yu8Z-EW0KOfXw6WIv2l',
  'https://lh3.googleusercontent.com/aida-public/AB6AXuAL0eqfp3wcECnKwl-g5qWJH8WA6CPdiTsNrIyzBOnaxJjbIwr81rIvZS4bk4aZdWiPNb-XKl0NCMVpEEyw2V6PYhKq10MmsC2tFcVT9Ta171IFdl_2CLmo861yQVnAFEJ6HyEQzlEjJaV1sC83Dt0Xk3avtNQX9q9Ew6_vIithgJPT7sRlLusDBSEDaeY-g2eBkIOZ4Gtmfjplz_G_lrkb7ftlPU2jyLFtV7_-EwcjUpdUvrTF0e96H8ceSOrY8O908VYaQWkpeNla',
  'https://lh3.googleusercontent.com/aida-public/AB6AXuD10Pd5ktOqHGwiXLyR_AHlbjW8Fbv0nf7GVb9lCPT-zkQcofiNpRhpn9rA_Il3OIUgRMO6QUnZxWWj-SStop28Hwp6FkrKWGOpCpQgwI7NApKaQVzMiYBocGBThVuIVYaU_m0L2wqTDjOVOdqQ6UlFWFhOTHtpX0PI762aDSZHzBbNGhLJqW4uYYLHDFh0EWo2r-8DbI9hZ4La8pAiyQ4orlSPcui8Ng2C8jVkWTjh-6SlWn5oVVb_2qlW59sn33jrs_K4YyeJreXV',
  'https://lh3.googleusercontent.com/aida-public/AB6AXuBtMD9QMWNzmTD64j_bg9tUYZyf-OnM83tcngUMNUY-cefy55jW4byLMhGAB5P1dnxMQkGfIqm3bxknc6ELdU1Zqf16BZUUotjx9NQCeIWu3IpV3G0bZ2fQeU7xp4vzoczoQJ1DLf0fjXQASAQmLh6IdidRL0wbIZJQFQZBOKScNs_9HBdlBIgeWstDyV3LUvm-VN_x97OpL4mhvgG7epD8QXMTajRPUCbGRpAh_xhBXCz0yVkMWjGtAniuqKaMnwSWoLJngZ27TvS-',
]

function fallbackImageUrl(productId) {
  const idx = Math.abs(Number(productId || 0)) % showcaseImages.length
  return showcaseImages[idx]
}

const priceFilteredProducts = computed(() => {
  const list = Array.isArray(products.value) ? products.value : []
  const bracket = selectedPriceBracket.value
  if (!bracket) return list
  const [min, max] = bracket
  return list.filter((p) => {
    const price = Number(p.price || 0)
    if (price < min) return false
    if (max !== PRICE_BRACKET_OPEN && price > max) return false
    return true
  })
})

/** 列表仅展示在售且有库存，避免售罄卡与有货卡在同一行被 grid 拉伸高度不一致 */
const sellableProducts = computed(() =>
  priceFilteredProducts.value.filter(
    (p) => Number(p.status ?? 1) === 1 && Number(p.stock ?? 0) > 0
  )
)

const total = computed(() => sellableProducts.value.length)
const sortedProducts = computed(() => {
  const list = Array.isArray(sellableProducts.value) ? [...sellableProducts.value] : []
  if (sortBy.value === 'price_asc') {
    list.sort((a, b) => Number(a.price || 0) - Number(b.price || 0))
  } else if (sortBy.value === 'price_desc') {
    list.sort((a, b) => Number(b.price || 0) - Number(a.price || 0))
  } else if (sortBy.value === 'stock_desc') {
    list.sort((a, b) => Number(b.stock || 0) - Number(a.stock || 0))
  } else if (sortBy.value === 'newest') {
    list.sort((a, b) => Number(b.productId || 0) - Number(a.productId || 0))
  }
  return list
})

const pagedProducts = computed(() => {
  const list = Array.isArray(sortedProducts.value) ? sortedProducts.value : []
  const p = Math.max(1, Number(page.value || 1))
  const start = (p - 1) * PAGE_SIZE
  return list.slice(start, start + PAGE_SIZE)
})

const pageRangeText = computed(() => {
  const t = Number(total.value || 0)
  if (!t) return '0 / 0'
  const p = Math.max(1, Number(page.value || 1))
  const start = (p - 1) * PAGE_SIZE + 1
  const end = Math.min(t, start + PAGE_SIZE - 1)
  return `${start}-${end} / ${t}`
})

watch(selectedPriceBracket, () => {
  page.value = 1
})

function productBadgeTag(product, idx) {
  if (Number(product.status ?? 1) !== 1) return ''
  if (Number(product.stock || 0) <= 0) return ''
  const id = Number(product.productId || 0)
  const stock = Number(product.stock || 0)
  if (stock < 15) return '库存精选'
  if (id % 7 === 0) return '新品上架'
  if (id % 5 === 0) return '热销推荐'
  if (idx % 6 === 2) return '严选'
  return ''
}

function productRatingStars(product) {
  const id = Number(product?.productId || 1)
  const base = 4.6 + (id % 5) * 0.08
  return Math.min(5, Math.round(base * 10) / 10)
}

</script>

<template>
  <div class="pw-page product-list product-list--apex">
    <section class="layout-shell">
      <aside class="layout-sidebar">
        <div class="sidebar-head">
          <h2 class="sidebar-title">高级筛选</h2>
          <p class="sidebar-lead">按类目与条件缩小范围</p>
        </div>

        <div class="facet-block">
          <div class="facet-title">分类</div>
          <button
            type="button"
            :class="['facet-chip', { 'facet-chip--on': !selectedCategory }]"
            @click="filterByCategory(null)"
          >
            全部商品
          </button>
          <button
            v-for="cat in visibleNavCategories"
            :key="`side-nav-${cat.categoryId}`"
            type="button"
            :class="['facet-chip', { 'facet-chip--on': selectedCategory === cat.categoryId }]"
            @click="filterByCategory(cat.categoryId)"
          >
            {{ cat.name }}
          </button>
          <button
            v-if="hasMoreCategories"
            type="button"
            class="facet-more"
            @click="categoryExpanded = !categoryExpanded"
          >
            {{ categoryExpanded ? '收起' : '更多分类' }}
          </button>
        </div>

        <div class="facet-block">
          <div class="facet-title">价格区间</div>
          <div class="price-brackets">
            <button
              type="button"
              :class="['bracket-btn', { 'bracket-btn--on': selectedPriceBracket === null }]"
              @click="setPriceBracket(null)"
            >
              不限
            </button>
            <button
              type="button"
              :class="[
                'bracket-btn',
                { 'bracket-btn--on': selectedPriceBracket && selectedPriceBracket[0] === 0 && selectedPriceBracket[1] === 99 },
              ]"
              @click="setPriceBracket([0, 99])"
            >
              ¥0 – 99
            </button>
            <button
              type="button"
              :class="[
                'bracket-btn',
                {
                  'bracket-btn--on':
                    selectedPriceBracket &&
                    selectedPriceBracket[0] === 100 &&
                    selectedPriceBracket[1] === 299,
                },
              ]"
              @click="setPriceBracket([100, 299])"
            >
              ¥100 – 299
            </button>
            <button
              type="button"
              :class="[
                'bracket-btn',
                {
                  'bracket-btn--on':
                    selectedPriceBracket &&
                    selectedPriceBracket[0] === 300 &&
                    selectedPriceBracket[1] === 599,
                },
              ]"
              @click="setPriceBracket([300, 599])"
            >
              ¥300 – 599
            </button>
            <button
              type="button"
              :class="[
                'bracket-btn',
                {
                  'bracket-btn--on':
                    selectedPriceBracket &&
                    selectedPriceBracket[0] === 600 &&
                    selectedPriceBracket[1] === PRICE_BRACKET_OPEN,
                },
              ]"
              @click="setPriceBracket([600, PRICE_BRACKET_OPEN])"
            >
              ¥600 以上
            </button>
          </div>
        </div>

        <div class="facet-block facet-block--muted">
          <div class="facet-title">养护阶段</div>
          <p class="facet-hint">演示项，不参与筛选</p>
          <label class="brand-check brand-check--disabled">
            <input type="checkbox" disabled />
            <span>幼年期</span>
          </label>
          <label class="brand-check brand-check--disabled">
            <input type="checkbox" disabled />
            <span>成年期</span>
          </label>
        </div>
      </aside>

      <div class="layout-main">
        <header class="main-toolbar">
          <div class="main-toolbar__left">
            <h1 class="inventory-title">精选库存</h1>
            <p class="inventory-meta">
              <span class="inventory-count">{{ total }}</span>
              件商品
              <span class="inventory-dot">·</span>
              <span>{{ pageRangeText }}</span>
            </p>
          </div>
          <div class="main-toolbar__search">
            <input
              v-model="keyword"
              class="toolbar-search-input"
              type="search"
              placeholder="搜索商品名称、品牌或商品 ID"
              enterkeyhint="search"
              @keyup.enter="runSearch"
            />
            <button type="button" class="toolbar-search-btn" @click="runSearch">搜索</button>
            <button type="button" class="toolbar-reset" @click="resetSearch">重置</button>
          </div>
          <div class="main-toolbar__right">
            <div class="view-toggle" role="group" aria-label="视图">
              <button
                type="button"
                :class="['view-toggle__btn', { 'view-toggle__btn--on': viewMode === 'grid' }]"
                @click="viewMode = 'grid'"
              >
                标准
              </button>
              <button
                type="button"
                :class="['view-toggle__btn', { 'view-toggle__btn--on': viewMode === 'dense' }]"
                @click="viewMode = 'dense'"
              >
                紧凑
              </button>
            </div>
            <select v-model="sortBy" class="sort-select">
              <option value="featured">推荐排序</option>
              <option value="newest">最新上架</option>
              <option value="price_asc">价格从低到高</option>
              <option value="price_desc">价格从高到低</option>
              <option value="stock_desc">库存优先</option>
            </select>
          </div>
        </header>

        <div v-if="appliedKeyword || selectedCategoryName || selectedPriceBracket" class="product-active-filters">
          <span v-if="appliedKeyword" class="filter-chip">关键词：{{ appliedKeyword }}</span>
          <span v-if="selectedCategoryName" class="filter-chip">分类：{{ selectedCategoryName }}</span>
          <span v-if="selectedPriceBracket" class="filter-chip">
            价格：
            <template v-if="selectedPriceBracket[1] === PRICE_BRACKET_OPEN">¥{{ selectedPriceBracket[0] }} 以上</template>
            <template v-else>¥{{ selectedPriceBracket[0] }} – {{ selectedPriceBracket[1] }}</template>
          </span>
        </div>

        <div v-if="loading" class="products products-skeleton">
          <AppSkeletonCard :count="LIST_SKELETON_COUNT" />
        </div>
        <div v-else-if="errorMsg" class="list-state list-state--error">{{ errorMsg }}</div>
        <div v-else-if="products.length === 0" class="list-empty">暂无商品，试试调整筛选或关键词</div>
        <div v-else-if="total === 0" class="list-empty">暂无可售商品（可能已全部售罄或下架），请调整筛选条件</div>

        <div v-else :class="['products', viewMode === 'grid' ? 'products--grid' : 'products--dense']">
          <article
            v-for="(product, idx) in pagedProducts"
            :key="`${product.productId}-${idx}`"
            class="product-card"
            @click="viewDetail(product.productId)"
          >
            <div class="product-image">
              <AppImage
                :src="product.detail?.imageUrl || fallbackImageUrl(product.productId)"
                class="img"
                alt=""
                loading="lazy"
                decoding="async"
              />
              <div v-if="Number(product.stock) > 0 && Number(product.stock) < 20" class="stock-badge">低库存</div>
              <span v-if="productBadgeTag(product, idx)" class="card-tag">{{ productBadgeTag(product, idx) }}</span>
            </div>
            <div class="product-info">
              <div class="product-info__top">
                <span class="brand-row">{{ product.brandName || '宠物商城' }}</span>
                <span class="rating-row">
                  <span class="rating-stars">★★★★★</span>
                  <span class="rating-num">{{ productRatingStars(product) }}</span>
                </span>
              </div>
              <h3 class="product-title" :title="product.title">{{ product.title }}</h3>
              <p class="product-desc">{{ categoryDisplayName(product) }}</p>
              <div class="apex-card-footer">
                <span class="price">¥{{ formatYuan(product.price) }}</span>
                <div class="apex-card-footer-actions">
                  <button type="button" class="cta-btn" @click.stop="viewDetail(product.productId)">加入购物车</button>
                </div>
              </div>
            </div>
          </article>
        </div>

        <PaginationBar
          :page="page"
          :page-size="PAGE_SIZE"
          :total="total"
          :show-range-meta="false"
          @update:page="onProductsPageChange"
        />
      </div>
    </section>
  </div>
</template>

<style scoped>
.product-list--apex {
  --apex-ink: #0a0a0a;
  --apex-muted: #737373;
  --apex-line: #e5e5e5;
  --apex-panel: #ffffff;
  --apex-bg: #f5f5f5;
  --apex-accent: #a35d00;
  --apex-radius: 2px;
  /* 1:1 主图，纵向更舒展 */
  --apex-thumb-ratio: 1 / 1;
  font-family:
    'Inter',
    'Microsoft YaHei',
    'PingFang SC',
    system-ui,
    sans-serif;
  padding: 0;
  background: transparent;
  border-radius: 0;
}

.product-list--apex.pw-page {
  padding-bottom: 0;
}

.layout-shell {
  display: grid;
  grid-template-columns: 272px minmax(0, 1fr);
  gap: 20px;
  align-items: start;
}

.layout-sidebar {
  background: var(--apex-panel);
  border: 1px solid var(--apex-line);
  border-radius: var(--apex-radius);
  padding: 22px 18px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  box-shadow: 0 1px 0 rgba(0, 0, 0, 0.04);
}

.sidebar-head {
  margin-bottom: 12px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--apex-line);
}

.sidebar-title {
  margin: 0 0 6px;
  font-size: 13px;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--apex-ink);
}

.sidebar-lead {
  margin: 0;
  font-size: 12px;
  color: var(--apex-muted);
  line-height: 1.45;
}

.facet-block {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 16px 0;
  border-top: 1px solid var(--apex-line);
}

.facet-block:first-of-type {
  border-top: none;
  padding-top: 8px;
}

.facet-block--muted .facet-hint {
  margin: -4px 0 4px;
  font-size: 11px;
  color: #a3a3a3;
}

.facet-title {
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--apex-muted);
}

.facet-chip {
  width: 100%;
  text-align: left;
  padding: 10px 12px;
  border: 1px solid var(--apex-line);
  border-radius: var(--apex-radius);
  background: #fafafa;
  font-size: 13px;
  font-weight: 600;
  color: var(--apex-ink);
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
  background: var(--apex-ink);
  color: #fff;
  border-color: var(--apex-ink);
}

/* 覆盖 .facet-chip:hover，避免选中项悬停变白底导致白字看不见 */
.facet-chip--on:hover {
  background: var(--apex-ink);
  border-color: var(--apex-ink);
  color: #fff;
}

.facet-more {
  align-self: flex-start;
  margin-top: 4px;
  padding: 6px 0;
  border: none;
  background: transparent;
  font-size: 12px;
  font-weight: 700;
  color: var(--apex-ink);
  text-decoration: underline;
  text-underline-offset: 3px;
  cursor: pointer;
}

.price-brackets {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.bracket-btn {
  padding: 8px 12px;
  border: 1px solid var(--apex-line);
  border-radius: var(--apex-radius);
  background: #fff;
  font-size: 12px;
  font-weight: 600;
  color: var(--apex-ink);
  cursor: pointer;
  transition:
    border-color 0.15s,
    background 0.15s;
}

.bracket-btn:hover {
  border-color: #a3a3a3;
}

.bracket-btn--on {
  background: var(--apex-ink);
  color: #fff;
  border-color: var(--apex-ink);
}

.brand-check {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  font-weight: 600;
  color: #404040;
  cursor: pointer;
}

.brand-check--disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.brand-check input {
  accent-color: var(--apex-ink);
}

.layout-main {
  min-width: 0;
  /* 右侧商品列浅灰底，仅单卡为白，工具条扁平贴在灰底上 */
  background: var(--apex-bg);
}

.main-toolbar {
  background: transparent;
  border: none;
  border-bottom: 1px solid rgba(0, 0, 0, 0.06);
  border-radius: 0;
  padding: 16px 0 18px;
  margin-bottom: 12px;
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(0, 1.4fr) minmax(0, 1fr);
  gap: 16px 20px;
  align-items: center;
}

.inventory-title {
  margin: 0 0 6px;
  font-size: clamp(20px, 2vw, 26px);
  font-weight: 800;
  letter-spacing: -0.02em;
  color: var(--apex-ink);
}

.inventory-meta {
  margin: 0;
  font-size: 13px;
  color: var(--apex-muted);
  font-weight: 600;
}

.inventory-count {
  font-size: 18px;
  font-weight: 800;
  color: var(--apex-ink);
  margin-right: 4px;
}

.inventory-dot {
  margin: 0 6px;
  opacity: 0.5;
}

.main-toolbar__search {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: stretch;
}

.toolbar-search-input {
  flex: 1;
  min-width: 180px;
  height: 44px;
  border: 1px solid var(--apex-line);
  border-radius: var(--apex-radius);
  padding: 0 14px;
  font-size: 14px;
}

.toolbar-search-btn {
  height: 44px;
  padding: 0 20px;
  border: none;
  border-radius: var(--apex-radius);
  background: var(--apex-ink);
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
}

.toolbar-reset {
  height: 44px;
  padding: 0 14px;
  border: 1px solid var(--apex-line);
  border-radius: var(--apex-radius);
  background: #fafafa;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}

.main-toolbar__right {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  justify-content: flex-end;
}

.view-toggle {
  display: inline-flex;
  border: 1px solid var(--apex-line);
  border-radius: var(--apex-radius);
  overflow: hidden;
}

.view-toggle__btn {
  padding: 8px 14px;
  border: none;
  background: #fff;
  font-size: 12px;
  font-weight: 700;
  color: var(--apex-muted);
  cursor: pointer;
}

.view-toggle__btn--on {
  background: var(--apex-ink);
  color: #fff;
}

.sort-select {
  height: 40px;
  padding: 0 10px;
  border: 1px solid var(--apex-line);
  border-radius: var(--apex-radius);
  font-size: 12px;
  font-weight: 600;
  background: #fff;
  color: var(--apex-ink);
}

.product-active-filters {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 14px;
}

.filter-chip {
  font-size: 12px;
  font-weight: 600;
  color: var(--apex-ink);
  background: #fff;
  border: 1px solid var(--apex-line);
  border-radius: var(--apex-radius);
  padding: 6px 12px;
}

.list-state {
  padding: 24px;
  border-radius: var(--apex-radius);
  border: 1px solid var(--apex-line);
  background: #fff;
}

.list-state--error {
  color: #991b1b;
  background: #fef2f2;
  border-color: #fecaca;
}

.list-empty {
  text-align: center;
  padding: 48px 20px;
  color: var(--apex-muted);
  font-size: 14px;
  border: 1px dashed var(--apex-line);
  border-radius: var(--apex-radius);
  background: #fafafa;
}

.products {
  display: grid;
  gap: 14px 16px;
  /* 按内容高度排卡，避免同一行里「被拉伸」的假性变高 */
  align-items: start;
  background: transparent;
}

.products-skeleton {
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 220px), 1fr));
}

.products--grid {
  /* min(100%,220px) 避免最后一列在窄缝中被挤到 0 宽导致文案裁切异常 */
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 220px), 1fr));
}

.products--dense {
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 180px), 1fr));
}

.product-card {
  background: var(--apex-panel);
  border: 1px solid var(--apex-line);
  border-radius: var(--apex-radius);
  overflow: hidden;
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  cursor: pointer;
  transition:
    box-shadow 0.2s,
    border-color 0.2s;
}

.product-card:hover {
  box-shadow: 0 16px 40px rgba(0, 0, 0, 0.08);
  border-color: #d4d4d4;
}

.product-image {
  position: relative;
  width: 100%;
  aspect-ratio: var(--apex-thumb-ratio);
  overflow: hidden;
  background: #f5f5f5;
  flex-shrink: 0;
}

/*
 * AppImage 未传 aspectRatio 时为单层 <img>，固有宽高会撑破网格。
 * 绝对定位 + cover：任意比例素材在固定比例框内居中裁剪，卡片对齐。
 */
.product-image :deep(img) {
  position: absolute;
  left: 0;
  top: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: center;
  display: block;
}

.product-image :deep(> div) {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  overflow: hidden;
}

.product-image :deep(> div > img) {
  position: absolute;
  left: 0;
  top: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: center;
  display: block;
}

.card-tag {
  position: absolute;
  left: 6px;
  top: 6px;
  padding: 4px 8px;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: #fff;
  background: rgba(10, 10, 10, 0.88);
  border-radius: var(--apex-radius);
}

.stock-badge {
  position: absolute;
  top: 6px;
  right: 6px;
  padding: 3px 8px;
  font-size: 12px;
  font-weight: 800;
  color: #fff;
  background: var(--apex-accent);
  border-radius: var(--apex-radius);
}

.product-info {
  padding: 8px 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1;
  min-width: 0;
  min-height: 0;
}

.product-info__top {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: 6px;
}

.brand-row {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: none;
  color: var(--apex-muted);
}

.rating-row {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}

.rating-stars {
  font-size: 13px;
  letter-spacing: 0.5px;
  color: var(--apex-accent);
}

.rating-num {
  font-size: 15px;
  font-weight: 800;
  color: var(--apex-ink);
}

.product-title {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  line-height: 1.38;
  color: var(--apex-ink);
  word-break: break-word;
  overflow-wrap: anywhere;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
  text-overflow: ellipsis;
  min-width: 0;
}

.product-desc {
  margin: 0;
  font-size: 12px;
  line-height: 1.35;
  color: var(--apex-muted);
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
  flex: 0 0 auto;
}

.apex-card-footer {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 6px;
  /* 名称/标签与价格之间略留空隙 */
  margin-top: 12px;
}

.price {
  font-size: 18px;
  font-weight: 800;
  color: var(--apex-ink);
  letter-spacing: -0.02em;
  align-self: flex-start;
}

.apex-card-footer-actions {
  display: flex;
  flex-direction: column;
  flex-wrap: nowrap;
  gap: 6px;
  width: 100%;
  justify-content: stretch;
}

.cta-btn {
  width: 100%;
  padding: 10px 12px;
  border-radius: 0;
  border: 1px solid var(--apex-ink);
  background: var(--apex-ink);
  color: #fff;
  font-size: 13px;
  font-weight: 800;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  cursor: pointer;
}

.cta-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

@media (max-width: 1100px) {
  .main-toolbar {
    grid-template-columns: 1fr;
  }

  .main-toolbar__right {
    justify-content: flex-start;
  }
}

@media (max-width: 980px) {
  .layout-shell {
    grid-template-columns: 1fr;
  }

  .layout-sidebar {
    order: 2;
  }

  .layout-main {
    order: 1;
  }
}

:deep(.pw-pagination) {
  justify-content: center;
  border: none !important;
  background: transparent !important;
  border-radius: 0 !important;
  padding: 18px 0 4px !important;
  margin-top: 4px;
  box-shadow: none !important;
}

:deep(.pw-pagination--no-meta .pw-pagination-right) {
  justify-content: center;
}

:deep(.pw-pagination-meta) {
  font-size: 13px;
  color: #525252;
}

:deep(.pw-page-btn),
:deep(.pw-page-num) {
  min-width: 40px;
  height: 40px;
  border-radius: var(--apex-radius);
  border: 1px solid var(--apex-line);
  background: #fafafa;
  font-size: 13px;
  font-weight: 700;
}

:deep(.pw-page-num.active) {
  border-color: var(--apex-ink);
  background: var(--apex-ink);
  color: #fff;
}

:deep(.pw-pagination-size) {
  height: 40px;
  border-radius: var(--apex-radius);
  border-color: var(--apex-line);
}
</style>
