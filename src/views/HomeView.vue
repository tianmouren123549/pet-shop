<script setup>
import { computed, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../utils/request'
import { formatCategoryWithParent } from '../utils/categoryDisplay'
import { getFlatCategoryNavCategories } from '../utils/categoryNav'

const router = useRouter()
const products = ref([])
const allCategories = ref([])
const flatNavCategories = computed(() =>
  getFlatCategoryNavCategories(allCategories.value)
)
const selectedCategory = ref(null)
const keyword = ref('')
const loading = ref(false)
const errorMsg = ref('')
const localHotKeywords = ref([])
const HOT_KEYWORDS_STORAGE_KEY = 'petshop_hot_keywords_v1'
const showHotPanel = ref(false)
const hotPage = ref(0)
const HOT_PANEL_PAGE_SIZE = 6
const defaultHotKeywords = [
  '猫粮',
  '狗粮',
  '猫砂',
  '冻干',
  '宠物零食',
  '营养膏',
  '猫抓板',
  '犬用牵引绳',
  '宠物玩具',
  '猫罐头',
  '犬用磨牙棒',
  '宠物沐浴露'
]

onMounted(async () => {
  loadLocalHotKeywords()
  await loadCategories()
  await loadProducts()
})

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
  const res = selectedCategory.value
    ? await api.getProductsByCategory(selectedCategory.value)
    : await api.getProducts()

  if (res.code === 200) {
    products.value = Array.isArray(res.data) ? res.data : []
  } else {
    errorMsg.value = res.message || '商品加载失败'
    products.value = []
  }
  loading.value = false
}

function filterByCategory(categoryId) {
  selectedCategory.value = categoryId
  loadProducts()
}

function runSearch() {
  const q = String(keyword.value || '').trim()
  showHotPanel.value = false
  if (q) {
    recordSearchKeyword(q)
  }
  router.push(q ? { path: '/products', query: { q } } : { path: '/products' })
}

function resetSearch() {
  keyword.value = ''
  selectedCategory.value = null
  loadProducts()
}

function normalizeKeyword(value) {
  return String(value || '').trim().toLowerCase()
}

function loadLocalHotKeywords() {
  try {
    const raw = localStorage.getItem(HOT_KEYWORDS_STORAGE_KEY)
    const parsed = raw ? JSON.parse(raw) : {}
    const list = Object.entries(parsed)
      .map(([term, score]) => ({ term, score: Number(score || 0) }))
      .filter((item) => item.term && item.score > 0)
      .sort((a, b) => b.score - a.score)
      .slice(0, 8)
    localHotKeywords.value = list.map((item) => item.term)
  } catch {
    localHotKeywords.value = []
  }
}

function recordSearchKeyword(value) {
  const term = normalizeKeyword(value)
  if (!term) return
  try {
    const raw = localStorage.getItem(HOT_KEYWORDS_STORAGE_KEY)
    const parsed = raw ? JSON.parse(raw) : {}
    parsed[term] = Number(parsed[term] || 0) + 1
    localStorage.setItem(HOT_KEYWORDS_STORAGE_KEY, JSON.stringify(parsed))
    loadLocalHotKeywords()
  } catch {
    // ignore localStorage errors
  }
}

function quickSearch(term) {
  keyword.value = term
  const q = String(term || '').trim()
  if (q) recordSearchKeyword(q)
  router.push(q ? { path: '/products', query: { q } } : { path: '/products' })
  showHotPanel.value = false
}

const displayHotKeywords = computed(() => {
  return localHotKeywords.value.length > 0 ? localHotKeywords.value : defaultHotKeywords
})

const hotPanelTerms = computed(() => {
  const list = displayHotKeywords.value
  if (list.length <= HOT_PANEL_PAGE_SIZE) return list
  const maxPage = Math.ceil(list.length / HOT_PANEL_PAGE_SIZE)
  const page = hotPage.value % maxPage
  const start = page * HOT_PANEL_PAGE_SIZE
  return list.slice(start, start + HOT_PANEL_PAGE_SIZE)
})

function switchHotBatch() {
  const list = displayHotKeywords.value
  if (list.length <= HOT_PANEL_PAGE_SIZE) return
  const maxPage = Math.ceil(list.length / HOT_PANEL_PAGE_SIZE)
  hotPage.value = (hotPage.value + 1) % maxPage
}

function viewDetail(productId) {
  router.push(`/product/${productId}`)
}

function categoryDisplayName(product) {
  return formatCategoryWithParent(product, allCategories.value)
}

const recommendedProducts = computed(() => {
  const list = Array.isArray(products.value) ? [...products.value] : []
  // 首页推荐排序：优先主粮类目与高库存，突出常购与供应充足商品
  list.sort((a, b) => {
    const foodCats = [4, 5, 6, 7, 10, 11, 12, 13, 14, 15]
    const aFood = foodCats.includes(Number(a.categoryId)) ? 1 : 0
    const bFood = foodCats.includes(Number(b.categoryId)) ? 1 : 0
    if (aFood !== bFood) return bFood - aFood
    return Number(b.stock || 0) - Number(a.stock || 0)
  })
  return list.slice(0, 4)
})

const selectedCategoryName = computed(() => {
  if (!selectedCategory.value) return ''
  const hit = allCategories.value.find(
    (c) => Number(c.categoryId) === Number(selectedCategory.value)
  )
  return hit ? hit.name : ''
})
</script>

<template>
  <div class="home">
    <section class="search-hero">
      <div class="search-hero-inner">
        <div class="search-title">先搜商品，再逛分类</div>
        <div class="search-main">
          <input
            v-model="keyword"
            class="search-main-input"
            placeholder="搜索商品名称、品牌或商品ID"
            @focus="showHotPanel = true"
            @keyup.enter="runSearch"
          />
          <button class="search-main-btn" @click="runSearch">搜索商品</button>
        </div>
        <div v-if="showHotPanel" class="hot-panel">
          <div class="hot-panel-head">
            <span class="hot-panel-title">猜你想搜</span>
            <button class="hot-refresh-btn" @click="switchHotBatch">换一换</button>
          </div>
          <div class="hot-keywords-grid">
            <button
              v-for="term in hotPanelTerms"
              :key="`panel-hot-${term}`"
              class="hot-chip"
              @click="quickSearch(term)"
            >
              {{ term }}
            </button>
          </div>
        </div>
        <div class="search-sub">
          <button class="sub-link" @click="resetSearch">清空分类筛选</button>
          <span>搜索后将进入商品列表查看匹配结果，与下方首页精选互不干扰。</span>
        </div>
        <div v-if="selectedCategoryName" class="active-filters">
          <span class="filter-chip">当前浏览分类：{{ selectedCategoryName }}</span>
        </div>
      </div>
    </section>

    <section class="hero">
      <div class="hero-overlay">
        <p class="hero-tag">专业级宠物供应体系</p>
        <h2>高标准宠物电商<br />运营中枢</h2>
        <p class="hero-desc">宠物用品在线选购，支持浏览、购物车、下单与评价；店铺履约与平台服务协同，为您与爱宠保驾护航。</p>
        <div class="hero-actions">
          <button class="hero-btn primary" @click="$router.push('/products')">浏览商品目录</button>
        </div>
      </div>
    </section>

    <div class="content-section no-shadow">
      <div class="recommend-strip">
        <div class="recommend-label">推荐商品</div>
        <div v-if="recommendedProducts.length === 0" class="recommend-empty">当前分类暂无推荐</div>
        <div v-else class="products recommend-products">
          <div
            v-for="product in recommendedProducts"
            :key="`rec-top-${product.productId}`"
            :class="['product-card', { disabled: Number(product.status ?? 1) !== 1 }]"
            @click="Number(product.status ?? 1) !== 1 ? null : viewDetail(product.productId)"
          >
            <div class="product-image">
              <img v-if="product.detail?.imageUrl" :src="product.detail.imageUrl" class="img" alt="商品图片" />
              <div v-else class="placeholder">暂无图片</div>
              <div class="rec-badge">推荐</div>
              <div v-if="Number(product.status ?? 1) !== 1" class="sold-badge">已下架</div>
              <div v-else-if="Number(product.stock) <= 0" class="sold-badge">售罄</div>
            </div>
            <div class="product-info">
              <h3 class="product-title" :title="product.title">{{ product.title }}</h3>
              <p class="product-meta">
                <span class="category-tag">{{ categoryDisplayName(product) }}</span>
                <span v-if="product.brandName" class="brand-tag">{{ product.brandName }}</span>
              </p>
              <div class="product-footer">
                <span class="product-price">¥{{ product.price }}</span>
                <span class="product-stock">库存 {{ product.stock }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="content-section no-shadow">
      <div class="section-header">
        <h3>商品分类</h3>
      </div>
      <div class="categories">
        <button
          :class="['category-btn', { active: !selectedCategory }]"
          @click="filterByCategory(null)"
        >
          全部商品
        </button>
        <div v-if="flatNavCategories.length" class="category-tier">
          <div class="category-tier-btns">
            <button
              v-for="cat in flatNavCategories"
              :key="`nav-${cat.categoryId}`"
              :class="['category-btn', { active: selectedCategory === cat.categoryId }]"
              @click="filterByCategory(cat.categoryId)"
            >
              {{ cat.name }}
            </button>
          </div>
        </div>
      </div>
    </div>

    <div class="content-section">
      <div class="section-header">
        <h3>精选商品</h3>
        <span class="product-count">共 {{ products.length }} 件商品</span>
      </div>

      <div v-if="loading" class="empty">
        <p>商品加载中...</p>
      </div>

      <div v-else-if="errorMsg" class="empty">
        <p>{{ errorMsg }}</p>
      </div>

      <div v-else-if="products.length === 0" class="empty">
        <p>暂无商品</p>
      </div>

      <div v-else class="products">
        <div
          v-for="product in products"
          :key="product.productId"
          :class="['product-card', { disabled: Number(product.status ?? 1) !== 1 }]"
          @click="Number(product.status ?? 1) !== 1 ? null : viewDetail(product.productId)"
        >
          <div class="product-image">
            <img v-if="product.detail?.imageUrl" :src="product.detail.imageUrl" class="img" alt="商品图片" />
            <div v-else class="placeholder">暂无图片</div>
            <div class="product-badge" v-if="product.stock < 20">库存紧张</div>
            <div v-if="Number(product.status ?? 1) !== 1" class="sold-badge">已下架</div>
            <div v-else-if="Number(product.stock) <= 0" class="sold-badge">售罄</div>
          </div>
          <div class="product-info">
            <h3 class="product-title" :title="product.title">{{ product.title }}</h3>
            <p class="product-meta">
              <span class="category-tag">{{ categoryDisplayName(product) }}</span>
              <span v-if="product.brandName" class="brand-tag">{{ product.brandName }}</span>
            </p>
            <div class="product-footer">
              <span class="product-price">¥{{ product.price }}</span>
              <span class="product-stock">库存 {{ product.stock }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <section class="insight">
      <div class="insight-left">
        <p class="insight-tag">供应链与履约能力</p>
        <h3>面向规模化运营<br />打造稳定履约体系</h3>
        <ul>
          <li>冷链配送能力</li>
          <li>智能库存与补货协同</li>
          <li>商家协同管理</li>
        </ul>
      </div>
      <div class="insight-grid">
        <div class="insight-box"><strong>99.8%</strong><span>系统可用率</span></div>
        <div class="insight-box"><strong>24h</strong><span>标准发货时效</span></div>
        <div class="insight-box"><strong>12k</strong><span>合作门店规模</span></div>
        <div class="insight-box"><strong>ISO</strong><span>质量认证体系</span></div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.home {
  width: 100%;
  padding-bottom: 28px;
}

.search-hero {
  margin-bottom: 12px;
  border-radius: 6px;
  background: #f4f6f9;
  border: 1px solid #dbe2ea;
  padding: 16px 18px;
}

.search-hero-inner {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.search-title {
  font-size: 16px;
  font-weight: 800;
  color: #1a2740;
}

.search-main {
  display: flex;
  gap: 10px;
  align-items: center;
}

.search-main-input {
  flex: 1;
  min-width: 380px;
  height: 42px;
  border: 1px solid #c9d4e4;
  background: #fff;
  border-radius: 2px;
  font-size: 14px;
  color: #26354b;
  padding: 0 12px;
}

.search-main-btn {
  height: 42px;
  padding: 0 18px;
  border-radius: 2px;
  border: 1px solid #0b1630;
  background: #0b1630;
  color: #f4f6fb;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
}

.search-main-btn:hover {
  background: #172d4f;
}

.search-sub {
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
  font-size: 12px;
  color: #6b7b91;
}

.active-filters {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.hot-keywords {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.hot-panel {
  border: 1px solid #dbe2ea;
  border-radius: 4px;
  background: #fff;
  padding: 8px 10px;
  box-shadow: 0 6px 16px rgba(13, 26, 41, 0.06);
}

.hot-panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}

.hot-panel-title {
  font-size: 16px;
  color: #1a2940;
  font-weight: 700;
}

.hot-refresh-btn {
  border: none;
  background: transparent;
  color: #6b7b91;
  font-size: 12px;
  cursor: pointer;
  padding: 0;
}

.hot-refresh-btn:hover {
  color: #1f3d66;
}

.hot-keywords-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px 8px;
}

.hot-chip {
  border: none;
  background: #f5f8fd;
  color: #1b2e4a;
  border-radius: 2px;
  padding: 6px 8px;
  text-align: left;
  font-size: 13px;
  line-height: 1.3;
  cursor: pointer;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.hot-chip:hover {
  background: #edf3ff;
  color: #0f2545;
}

.filter-chip {
  font-size: 12px;
  color: #16355f;
  background: #eaf2ff;
  border: 1px solid #c6daf8;
  border-radius: 12px;
  padding: 2px 10px;
}

.sub-link {
  border: none;
  background: transparent;
  color: #16355f;
  font-size: 12px;
  cursor: pointer;
  text-decoration: underline;
  padding: 0;
}

.hero {
  position: relative;
  min-height: 460px;
  border-radius: 6px;
  overflow: hidden;
  margin-bottom: 14px;
  background-image: linear-gradient(100deg, rgba(4, 12, 25, 0.92) 20%, rgba(4, 44, 63, 0.62) 100%), url('https://images.unsplash.com/photo-1548199973-03cce0bbc87b?auto=format&fit=crop&w=1600&q=80');
  background-size: cover;
  background-position: center;
}

.hero-overlay {
  max-width: 620px;
  padding: 76px 48px;
  color: #ecf1f7;
}

.hero-tag {
  font-size: 10px;
  letter-spacing: 1.8px;
  color: #ffab42;
  margin-bottom: 12px;
}

.hero h2 {
  font-size: 62px;
  line-height: 0.96;
  font-weight: 800;
  margin-bottom: 16px;
}

.hero-desc {
  font-size: 14px;
  color: #c6d3e4;
  line-height: 1.7;
  max-width: 520px;
}

.hero-actions {
  margin-top: 24px;
  display: flex;
  gap: 12px;
}

.hero-btn {
  height: 36px;
  padding: 0 16px;
  border: 1px solid rgba(255, 255, 255, 0.34);
  background: rgba(255, 255, 255, 0.08);
  color: #f4f7fc;
  font-size: 11px;
  letter-spacing: 0.4px;
  border-radius: 2px;
  cursor: pointer;
  font-weight: 700;
}

.hero-btn.primary {
  background: #0b1630;
  border-color: #0b1630;
}

.content-section {
  background: #f4f6f9;
  border-radius: 6px;
  padding: 24px 12px;
  margin-bottom: 16px;
  width: 100%;
}

.no-shadow {
  padding-top: 10px;
  padding-bottom: 8px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
  padding: 0 8px 12px;
  border-bottom: 1px solid #dbe2ea;
}

.section-header h3 {
  font-size: 42px;
  color: #0f1827;
  font-weight: 700;
}

.product-count {
  color: #5f6d80;
  font-size: 12px;
  font-weight: 600;
}

.categories {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.category-tier {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  gap: 10px 14px;
}

.category-tier-btns {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  flex: 1;
  min-width: 0;
}

.recommend-strip {
  margin-bottom: 12px;
  border: 1px solid #dbe3ed;
  background: #fff;
  border-radius: 4px;
  padding: 10px;
}

.recommend-label {
  font-size: 12px;
  color: #5f6d80;
  font-weight: 700;
  margin-bottom: 8px;
}

.recommend-empty {
  font-size: 12px;
  color: #7a889c;
}

.recommend-products {
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
}

.rec-badge {
  position: absolute;
  top: 8px;
  right: 8px;
  background: #0b1630;
  color: #fff;
  padding: 2px 8px;
  border-radius: 2px;
  font-size: 12px;
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
  border-color: #1890ff;
  color: #1890ff;
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
  border: 1px solid #dbe3ee;
  border-radius: 2px;
  overflow: hidden;
  cursor: pointer;
  transition: all 0.3s ease;
}

.product-card.disabled {
  opacity: 0.75;
  cursor: not-allowed;
}

.product-card.disabled:hover {
  transform: none;
  box-shadow: none;
  border-color: #dbe3ee;
}

.product-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(13, 26, 41, 0.08);
  border-color: #0e203e;
}

.product-image {
  height: 186px;
  background: linear-gradient(135deg, #d4dbe3 0%, #b8c4d0 100%);
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

.placeholder {
  font-size: 14px;
  color: #bbb;
}

.product-badge {
  position: absolute;
  top: 8px;
  right: 8px;
  background: #ff4d4f;
  color: white;
  padding: 2px 8px;
  border-radius: 2px;
  font-size: 12px;
}

.sold-badge {
  position: absolute;
  left: 8px;
  top: 8px;
  padding: 2px 8px;
  border-radius: 2px;
  font-size: 12px;
  font-weight: 800;
  color: #f4f6fb;
  background: rgba(94, 110, 132, 0.9);
}


.product-info {
  padding: 12px;
}

.product-title {
  font-size: 16px;
  margin-bottom: 8px;
  overflow: hidden;
  color: #131e30;
  font-weight: 700;
  line-height: 1.35;
  min-height: 43px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  word-break: break-all;
}

.product-meta {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.category-tag, .brand-tag {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 2px;
  background: #f5f5f5;
  color: #666;
}

.brand-tag {
  background: #e6f7ff;
  color: #1890ff;
}

.product-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 12px;
  border-top: 1px solid #e3e8f0;
}

.product-price {
  font-size: 18px;
  color: #0a1424;
  font-weight: 700;
}

.product-stock {
  font-size: 12px;
  color: #999;
}

.empty {
  text-align: center;
  padding: 60px 20px;
  color: #6b7788;
  font-size: 13px;
}

.insight {
  margin-top: 10px;
  border-radius: 6px;
  padding: 34px;
  background: #06142a;
  color: #e8eef8;
  display: grid;
  grid-template-columns: 1.3fr 1fr;
  gap: 24px;
}

.insight-tag {
  font-size: 10px;
  letter-spacing: 1.4px;
  color: #e8a857;
  margin-bottom: 10px;
}

.insight h3 {
  font-size: 48px;
  line-height: 0.96;
  margin-bottom: 18px;
}

.insight ul {
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 10px;
  color: #c1cedd;
  font-size: 14px;
}

.insight-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
}

.insight-box {
  border: 1px solid rgba(174, 189, 210, 0.22);
  background: rgba(255, 255, 255, 0.03);
  min-height: 110px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.insight-box strong {
  font-size: 36px;
  line-height: 1;
}

.insight-box span {
  color: #8f9fb4;
  font-size: 10px;
  letter-spacing: 0.7px;
}

@media (max-width: 980px) {
  .hero h2,
  .insight h3,
  .section-header h3 {
    font-size: 34px;
  }
  .insight {
    grid-template-columns: 1fr;
  }
}
</style>
