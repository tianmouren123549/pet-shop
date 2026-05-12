<script>
/** 供 {@code KeepAlive} 按名缓存，从详情返回时保留商品列表状态。 */
export default { name: 'HomeView' }
</script>
<script setup>
import { computed, ref, onMounted, onActivated, onDeactivated, onUnmounted } from 'vue'
import { useRouter, RouterLink } from 'vue-router'
import { api } from '../utils/request'
import { formatYuan } from '../utils/formatYuan.js'
import { formatCategoryWithParent } from '../utils/categoryDisplay'
import AppImage from '../components/AppImage.vue'
import AppSkeletonCard from '../components/AppSkeletonCard.vue'

const router = useRouter()
const products = ref([])
const allCategories = ref([])
const keyword = ref('')
const loading = ref(false)
const errorMsg = ref('')
const recommendedRemote = ref([])
const recommendationsReady = ref(true)
/** 上次成功拉取首页推荐的时间；避免 KeepAlive 从详情返回时次次重请求导致列表抖动 */
const lastRecommendationsFetchAt = ref(0)
/** 回到首页时：距上次成功拉取至少该间隔才再次请求（偏好变更则立即拉取） */
const REC_HOME_RECOMMENDATIONS_MIN_INTERVAL_MS = 60_000
const PET_PREF_BOTH = 'both'
const PET_PREF_CAT = 'cat'
const PET_PREF_DOG = 'dog'
const petPreference = ref(PET_PREF_BOTH)
const showPetPreferenceDialog = ref(false)
const HOME_SKELETON_COUNT = 8

/** 首页首屏背景轮播（多图） */
const heroSlides = [
  'https://images.unsplash.com/photo-1460925895917-afdab827c52f?auto=format&fit=crop&w=1920&q=80',
  'https://images.unsplash.com/photo-1551288049-bebda4e38f71?auto=format&fit=crop&w=1920&q=80',
  'https://images.unsplash.com/photo-1548199973-03cce0bbc87b?auto=format&fit=crop&w=1920&q=80',
  'https://images.unsplash.com/photo-1517849845537-4d257902454a?auto=format&fit=crop&w=1920&q=80',
  'https://images.unsplash.com/photo-1587300003448-592a574d7ad3?auto=format&fit=crop&w=1920&q=80',
  'https://images.unsplash.com/photo-1601758228041-f3b2795255f1?auto=format&fit=crop&w=1920&q=80',
  'https://images.unsplash.com/photo-1450778869180-41d0601e046e?auto=format&fit=crop&w=1920&q=80',
  'https://images.unsplash.com/photo-1543466835-00a7907e9de1?auto=format&fit=crop&w=1920&q=80',
]
const heroSlideIndex = ref(0)
const HERO_AUTOPLAY_MS = 6000
let heroAutoplayTimer = null

function goHeroSlide(i) {
  const n = heroSlides.length
  if (n <= 0) return
  const idx = ((Number(i) % n) + n) % n
  heroSlideIndex.value = idx
}

function startHeroAutoplay() {
  stopHeroAutoplay()
  if (heroSlides.length <= 1) return
  heroAutoplayTimer = window.setInterval(() => {
    goHeroSlide(heroSlideIndex.value + 1)
  }, HERO_AUTOPLAY_MS)
}

function stopHeroAutoplay() {
  if (heroAutoplayTimer != null) {
    window.clearInterval(heroAutoplayTimer)
    heroAutoplayTimer = null
  }
}

const heroBgStyle = computed(() => {
  const image = heroSlides[heroSlideIndex.value] || heroSlides[0]
  return {
    backgroundImage:
      `linear-gradient(105deg, rgba(6, 8, 12, 0.94) 0%, rgba(18, 22, 30, 0.88) 42%, rgba(10, 12, 18, 0.72) 100%), url('${image}')`,
  }
})

function prevHeroSlide() {
  if (heroSlides.length <= 1) return
  goHeroSlide(heroSlideIndex.value - 1)
}

function nextHeroSlide() {
  if (heroSlides.length <= 1) return
  goHeroSlide(heroSlideIndex.value + 1)
}

onMounted(async () => {
  const uid = Number(localStorage.getItem('userId') || 0)
  if (uid) {
    recommendationsReady.value = false
    await ensurePetPreference(uid)
  } else {
    petPreference.value = PET_PREF_BOTH
    showPetPreferenceDialog.value = false
  }
  await loadCategories()
  await loadProducts()
  await loadRecommendations()
  startHeroAutoplay()
})

onUnmounted(() => {
  stopHeroAutoplay()
})

onActivated(async () => {
  const uid = Number(localStorage.getItem('userId') || 0)
  if (uid) {
    // 首次进入仍在 onMounted 拉推荐时，避免与 onActivated 并发重复请求
    if (!recommendationsReady.value) {
      startHeroAutoplay()
      return
    }
    const prevPref = petPreference.value
    await ensurePetPreference(uid)
    const prefChanged = normalizePetPreference(petPreference.value) !== normalizePetPreference(prevPref)
    const now = Date.now()
    const intervalElapsed =
      lastRecommendationsFetchAt.value > 0 &&
      now - lastRecommendationsFetchAt.value >= REC_HOME_RECOMMENDATIONS_MIN_INTERVAL_MS
    if (prefChanged || intervalElapsed) {
      await loadRecommendations()
    }
  }
  startHeroAutoplay()
})

onDeactivated(() => {
  stopHeroAutoplay()
})

function normalizePetPreference(value) {
  const raw = String(value || '').trim().toLowerCase()
  if (raw === PET_PREF_CAT || raw === PET_PREF_DOG || raw === PET_PREF_BOTH) return raw
  return PET_PREF_BOTH
}

async function ensurePetPreference(userId) {
  const uid = Number(userId || 0)
  if (!uid) return
  try {
    const res = await api.userGetPetPreference(uid)
    if (res.code !== 200) {
      showPetPreferenceDialog.value = true
      return
    }
    const saved = normalizePetPreference(res.data?.petPreference)
    petPreference.value = saved
    showPetPreferenceDialog.value = !res.data?.petPreference
  } catch {
    petPreference.value = PET_PREF_BOTH
    showPetPreferenceDialog.value = true
  }
}

async function savePetPreference(value) {
  const uid = Number(localStorage.getItem('userId') || 0)
  const normalized = normalizePetPreference(value)
  petPreference.value = normalized
  try {
    if (uid) {
      await api.userUpdatePetPreference(uid, { petPreference: normalized })
    }
    showPetPreferenceDialog.value = false
  } catch {
    showPetPreferenceDialog.value = false
  }
  if (uid) {
    await loadRecommendations()
  }
}

function reopenPetPreferenceDialog() {
  const uid = Number(localStorage.getItem('userId') || 0)
  if (!uid) return
  showPetPreferenceDialog.value = true
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
  const res = await api.getProducts()

  if (res.code === 200) {
    products.value = Array.isArray(res.data) ? res.data : []
  } else {
    errorMsg.value = res.message || '商品加载失败'
    products.value = []
  }
  loading.value = false
}

function runSearch() {
  const q = String(keyword.value || '').trim()
  if (q) {
    recordSearchKeyword(q)
  }
  router.push(q ? { path: '/products', query: { q } } : { path: '/products' })
}

function normalizeKeyword(value) {
  return String(value || '').trim().toLowerCase()
}

function recordSearchKeyword(value) {
  const term = normalizeKeyword(value)
  if (!term) return
  try {
    const HOT_KEYWORDS_STORAGE_KEY = 'petshop_hot_keywords_v1'
    const raw = localStorage.getItem(HOT_KEYWORDS_STORAGE_KEY)
    const parsed = raw ? JSON.parse(raw) : {}
    parsed[term] = Number(parsed[term] || 0) + 1
    localStorage.setItem(HOT_KEYWORDS_STORAGE_KEY, JSON.stringify(parsed))
  } catch {
    // ignore localStorage errors
  }
}

function viewDetail(productId) {
  router.push(`/product/${productId}`)
}

async function loadRecommendations() {
  const uid = Number(localStorage.getItem('userId') || 0)
  if (!uid) {
    recommendedRemote.value = []
    recommendationsReady.value = true
    lastRecommendationsFetchAt.value = 0
    return
  }
  try {
    const res = await api.getUserRecommendations(uid, 6)
    if (res.code !== 200 || !Array.isArray(res.data)) {
      recommendedRemote.value = []
      return
    }
    recommendedRemote.value = res.data
      .map((item) => {
        const product = item?.product || null
        if (!product || Number(product.status ?? 1) !== 1) return null
        return {
          ...product,
          recommendModelVersion: String(item?.modelVersion || ''),
          commentStrongRecommend: Boolean(item?.commentStrongRecommend),
          avgReviewRating: item?.avgReviewRating ?? null,
          reviewCount: item?.reviewCount ?? null,
        }
      })
      .filter((item) => !!item)
    lastRecommendationsFetchAt.value = Date.now()
  } catch {
    recommendedRemote.value = []
  } finally {
    recommendationsReady.value = true
  }
}

/** 首页推荐条：仅展示「个性化推荐」体系角标，不使用「评论强推」（后者仅在 AI 导购内手动触发）。 */
function recommendBadgeText(product) {
  const v = String(product?.recommendModelVersion || '')
  if (v.startsWith('fallback-')) return '热门补位'
  if (v) return '个性化'
  return '推荐'
}

function categoryDisplayName(product) {
  return formatCategoryWithParent(product, allCategories.value)
}

const fallbackRecommendedProducts = computed(() => {
  const list = Array.isArray(products.value) ? [...products.value] : []
  list.sort((a, b) => {
    const foodCats = [4, 5, 6, 7, 10, 11, 12, 13, 14, 15]
    const aFood = foodCats.includes(Number(a.categoryId)) ? 1 : 0
    const bFood = foodCats.includes(Number(b.categoryId)) ? 1 : 0
    if (aFood !== bFood) return bFood - aFood
    return Number(b.stock || 0) - Number(a.stock || 0)
  })
  return list.slice(0, 6)
})

function productPetType(product) {
  const title = String(product?.title || '').toLowerCase()
  const categoryName = String(product?.categoryName || '').toLowerCase()
  const categoryDisplay = String(categoryDisplayName(product) || '').toLowerCase()
  const merged = `${title} ${categoryName} ${categoryDisplay}`
  const isCat = merged.includes('猫') || merged.includes('cat')
  const isDog = merged.includes('狗') || merged.includes('犬') || merged.includes('dog')
  if (isCat && !isDog) return PET_PREF_CAT
  if (isDog && !isCat) return PET_PREF_DOG
  return PET_PREF_BOTH
}

const displayedProducts = computed(() => {
  const list = Array.isArray(products.value) ? products.value : []
  const pref = normalizePetPreference(petPreference.value)
  if (pref === PET_PREF_BOTH) return list
  return list.filter((p) => {
    const t = productPetType(p)
    return t === pref || t === PET_PREF_BOTH
  })
})

const HOME_FEATURED_LIMIT = 30
const featuredProducts = computed(() => {
  const list = Array.isArray(displayedProducts.value) ? displayedProducts.value : []
  return list.slice(0, HOME_FEATURED_LIMIT)
})

const petPreferenceLabel = computed(() => {
  const pref = normalizePetPreference(petPreference.value)
  if (pref === PET_PREF_CAT) return '猫猫优先'
  if (pref === PET_PREF_DOG) return '狗狗优先'
  return '猫狗都养'
})

const recommendationStripLoading = computed(() => {
  const uid = Number(localStorage.getItem('userId') || 0)
  return Boolean(uid) && !recommendationsReady.value
})

const recommendedProducts = computed(() => {
  const uid = Number(localStorage.getItem('userId') || 0)
  if (uid && !recommendationsReady.value) {
    return []
  }
  if (Array.isArray(recommendedRemote.value) && recommendedRemote.value.length > 0) {
    return recommendedRemote.value
  }
  return fallbackRecommendedProducts.value
})

/** 首页「严选清单」条数（高库存排序，原右侧工作台已移除，此处多展示几条） */
const HOME_CURATED_STRIP_COUNT = 12

const specialistRows = computed(() => {
  const list = [...(displayedProducts.value || [])]
  list.sort((a, b) => Number(b.stock || 0) - Number(a.stock || 0))
  return list.slice(0, HOME_CURATED_STRIP_COUNT)
})

function productDisabled(product) {
  return Number(product?.status ?? 1) !== 1
}

function isSoldOut(product) {
  return Number(product?.stock || 0) <= 0
}
</script>

<template>
  <div class="home home--premium">
    <section
      class="hero-premium"
      :style="heroBgStyle"
      @mouseenter="stopHeroAutoplay"
      @mouseleave="startHeroAutoplay"
    >
      <div class="hero-premium__inner">
        <p class="hero-premium__eyebrow">专业严选 · 履约与养护</p>
        <p class="hero-premium__headline">为每一只宠物，坚持专业标准。</p>
        <p class="hero-premium__lead">
          从主粮到日用护理，以数据化推荐与稳定供应链，让每一次下单都更安心、更省心。
        </p>
        <div class="hero-premium__cta-row">
          <button type="button" class="btn-solid" @click="router.push('/products')">浏览全部商品</button>
          <button type="button" class="btn-ghost" @click="router.push('/ai')">AI 导购</button>
          <button type="button" class="btn-ghost" @click="router.push('/support')">售后与咨询</button>
        </div>
        <div class="hero-premium__search" role="search">
          <input
            v-model="keyword"
            class="hero-premium__search-input"
            type="search"
            placeholder="搜索商品名称、品牌或商品 ID"
            enterkeyhint="search"
            @keyup.enter="runSearch"
          />
          <button type="button" class="btn-search" @click="runSearch">搜索</button>
        </div>
      </div>
      <div v-if="heroSlides.length > 1" class="hero-carousel" aria-label="首屏背景轮播">
        <button type="button" class="hero-carousel__arrow" aria-label="上一张" @click="prevHeroSlide">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <path
              d="M14 6l-6 6 6 6"
              stroke="currentColor"
              stroke-width="2.2"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
          </svg>
        </button>
        <div class="hero-carousel__dots" role="tablist">
          <button
            v-for="(_, i) in heroSlides"
            :key="`hero-dot-${i}`"
            type="button"
            class="hero-carousel__dot"
            :class="{ 'hero-carousel__dot--active': i === heroSlideIndex }"
            :aria-label="`第 ${i + 1} 张`"
            :aria-current="i === heroSlideIndex ? 'true' : undefined"
            @click="goHeroSlide(i)"
          />
        </div>
        <button type="button" class="hero-carousel__arrow" aria-label="下一张" @click="nextHeroSlide">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <path
              d="M10 6l6 6-6 6"
              stroke="currentColor"
              stroke-width="2.2"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
          </svg>
        </button>
      </div>
    </section>

    <section class="section-premium section-premium--rec">
      <div class="section-premium__inner">
        <header class="section-premium__head">
          <div>
            <p class="eyebrow eyebrow--dark">为您推荐</p>
            <h2 class="section-premium__title">推荐商品</h2>
          </div>
        </header>

        <div v-if="recommendationStripLoading" class="state-line">正在生成个性化推荐…</div>
        <div v-else-if="recommendedProducts.length === 0" class="state-line">当前暂无推荐，试试浏览更多商品。</div>
        <div v-else class="products-grid products-grid--rec">
          <article
            v-for="product in recommendedProducts"
            :key="`rec-${product.productId}`"
            class="tile-card"
            :class="{ 'is-disabled': productDisabled(product) }"
            @click="productDisabled(product) ? null : viewDetail(product.productId)"
          >
            <div class="tile-card__media">
              <AppImage
                v-if="product.detail?.imageUrl"
                :src="product.detail.imageUrl"
                class="tile-card__img"
                alt=""
                loading="lazy"
                decoding="async"
              />
              <div v-else class="tile-card__ph">暂无图片</div>
              <span
                v-if="!productDisabled(product) && !isSoldOut(product)"
                class="tile-badge tile-badge--rec"
              >
                {{ recommendBadgeText(product) }}
              </span>
              <span v-if="productDisabled(product)" class="tile-badge tile-badge--muted">已下架</span>
              <span v-else-if="isSoldOut(product)" class="tile-badge tile-badge--muted">售罄</span>
            </div>
            <div class="tile-card__body">
              <h3 class="tile-card__title">{{ product.title }}</h3>
              <p v-if="product.brandName" class="tile-card__meta">
                <span>{{ product.brandName }}</span>
              </p>
              <div class="tile-card__foot">
                <span class="tile-card__price">¥{{ formatYuan(product.price) }}</span>
                <span class="tile-card__stock">库存 {{ product.stock }}</span>
              </div>
            </div>
          </article>
        </div>
      </div>
    </section>

    <section class="section-premium section-premium--split">
      <div class="section-premium__inner curated-block">
        <p class="eyebrow eyebrow--dark">高库存热卖</p>
        <h2 class="section-premium__title section-premium__title--sm">严选清单</h2>
        <ul class="demand-list">
          <li
            v-for="p in specialistRows"
            :key="`spec-${p.productId}`"
            class="demand-row"
            :class="{ 'is-disabled': productDisabled(p) }"
            @click="productDisabled(p) ? null : viewDetail(p.productId)"
          >
            <div class="demand-thumb">
              <AppImage
                v-if="p.detail?.imageUrl"
                :src="p.detail.imageUrl"
                class="demand-thumb__img"
                alt=""
                loading="lazy"
                decoding="async"
              />
              <div v-else class="demand-thumb__ph" />
            </div>
            <div class="demand-body">
              <p class="demand-title">{{ p.title }}</p>
              <p class="demand-desc">库存 {{ p.stock }}</p>
            </div>
            <span class="demand-price">¥{{ formatYuan(p.price) }}</span>
          </li>
        </ul>
        <div class="trust-float trust-float--inline">
          <p class="trust-float__line">深受养宠家庭与商家伙伴信赖</p>
          <p class="trust-float__stars" aria-hidden="true">★★★★★</p>
          <p class="trust-float__sub">用户评价来自真实订单与社区分享</p>
        </div>
      </div>
    </section>

    <section class="logistics-cta" :style="heroBgStyle">
      <div class="logistics-cta__veil" />
      <div class="logistics-cta__inner">
        <p class="eyebrow eyebrow--on-dark">企业服务</p>
        <h2 class="logistics-cta__title">稳定履约 · 批量采购与售后协同</h2>
        <p class="logistics-cta__lead">需要发票、对账或大宗咨询，可通过客服入口发起沟通。</p>
        <RouterLink class="btn-accent" to="/support">联系客服与合作咨询</RouterLink>
      </div>
    </section>

    <section class="section-premium section-premium--catalog">
      <div class="section-premium__inner">
        <header class="section-premium__head">
          <div>
            <p class="eyebrow eyebrow--dark">商品目录</p>
            <h2 class="section-premium__title">精选上架</h2>
          </div>
          <div class="catalog-tools">
            <button type="button" class="pref-chip" @click="reopenPetPreferenceDialog">偏好：{{ petPreferenceLabel }}</button>
            <span class="catalog-meta">
              共 {{ displayedProducts.length }} 件，展示前 {{ Math.min(displayedProducts.length, HOME_FEATURED_LIMIT) }} 件
            </span>
          </div>
        </header>

        <div v-if="loading" class="products-grid products-grid--skeleton">
          <AppSkeletonCard :count="HOME_SKELETON_COUNT" />
        </div>
        <div v-else-if="errorMsg" class="empty-card">{{ errorMsg }}</div>
        <div v-else-if="featuredProducts.length === 0" class="empty-card">暂无商品</div>
        <div v-else class="products-grid">
          <article
            v-for="product in featuredProducts"
            :key="product.productId"
            class="tile-card"
            :class="{ 'is-disabled': productDisabled(product) }"
            @click="productDisabled(product) ? null : viewDetail(product.productId)"
          >
            <div class="tile-card__media">
              <AppImage
                v-if="product.detail?.imageUrl"
                :src="product.detail.imageUrl"
                class="tile-card__img"
                alt=""
                loading="lazy"
                decoding="async"
              />
              <div v-else class="tile-card__ph">暂无图片</div>
              <span v-if="Number(product.stock) < 20 && !productDisabled(product) && !isSoldOut(product)" class="tile-badge">库存紧张</span>
              <span v-if="productDisabled(product)" class="tile-badge tile-badge--muted">已下架</span>
              <span v-else-if="isSoldOut(product)" class="tile-badge tile-badge--muted">售罄</span>
            </div>
            <div class="tile-card__body">
              <h3 class="tile-card__title">{{ product.title }}</h3>
              <p v-if="product.brandName" class="tile-card__meta">
                <span>{{ product.brandName }}</span>
              </p>
              <div class="tile-card__foot">
                <span class="tile-card__price">¥{{ formatYuan(product.price) }}</span>
                <span class="tile-card__stock">库存 {{ product.stock }}</span>
              </div>
            </div>
          </article>
        </div>
      </div>
    </section>

    <div v-if="showPetPreferenceDialog" class="pet-pref-mask" @click.self="showPetPreferenceDialog = false">
      <div class="pet-pref-dialog">
        <div class="pet-pref-title-row">
          <span class="pet-pref-icon">🐾</span>
          <h4>欢迎来到宠物商城</h4>
        </div>
        <p>为了让首页更贴合你，先告诉我们你主要养哪类宠物：</p>
        <p class="pet-pref-tip">你的选择会影响首页推荐结果，可随时在「偏好」中修改。</p>
        <div class="pet-pref-actions">
          <button type="button" class="pet-pref-btn" @click="savePetPreference('cat')">我养猫</button>
          <button type="button" class="pet-pref-btn" @click="savePetPreference('dog')">我养狗</button>
          <button type="button" class="pet-pref-btn both" @click="savePetPreference('both')">猫狗都养</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.home--premium {
  --ink: #0a0a0a;
  --muted: #5c5c5c;
  --line: #e8e8e8;
  --surface: #ffffff;
  --surface-2: #f5f5f5;
  --accent: #a35d00;
  --accent-hover: #8a4f00;
  --radius: 2px;
  /* 抵消 App.vue 中 .main 对用户端的 12px 基准，避免正文字体过小 */
  font-size: 15px;
  line-height: 1.55;
  font-family:
    'Inter',
    'Microsoft YaHei',
    'PingFang SC',
    system-ui,
    -apple-system,
    sans-serif;
  color: var(--ink);
  padding-bottom: 8px;
}

.hero-premium {
  position: relative;
  min-height: min(72vh, 640px);
  border-radius: var(--radius);
  overflow: hidden;
  margin-bottom: 20px;
  background-size: cover;
  background-position: center;
  transition: background-image 0.55s ease-in-out;
  padding-bottom: 88px;
}

.hero-premium__inner {
  position: relative;
  z-index: 2;
  max-width: 760px;
  padding: clamp(48px, 8vw, 92px) clamp(20px, 4vw, 48px) 28px;
  color: #f5f5f5;
}

.hero-premium__eyebrow {
  font-size: 12px;
  letter-spacing: 0.2em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.78);
  margin: 0 0 18px;
  font-weight: 600;
}

.hero-premium__headline {
  margin: 0 0 18px;
  font-size: clamp(30px, 4.6vw, 52px);
  font-weight: 800;
  line-height: 1.08;
  letter-spacing: -0.02em;
}

.hero-premium__lead {
  margin: 0 0 30px;
  font-size: clamp(16px, 1.35vw, 18px);
  line-height: 1.75;
  color: rgba(245, 245, 245, 0.9);
  max-width: 600px;
}

.hero-premium__cta-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 28px;
}

.btn-solid {
  height: 48px;
  padding: 0 28px;
  border-radius: var(--radius);
  border: 1px solid #0a0a0a;
  background: #0a0a0a;
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  cursor: pointer;
}

.btn-ghost {
  height: 48px;
  padding: 0 28px;
  border-radius: var(--radius);
  border: 1px solid rgba(255, 255, 255, 0.55);
  background: transparent;
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.04em;
  cursor: pointer;
}

.btn-ghost:hover {
  background: rgba(255, 255, 255, 0.08);
}

.hero-premium__search {
  display: flex;
  align-items: stretch;
  gap: 0;
  max-width: 560px;
  background: #fff;
  border-radius: var(--radius);
  border: 1px solid #e0e0e0;
  overflow: hidden;
}

.hero-premium__search-input {
  flex: 1;
  min-width: 0;
  border: none;
  font-size: 17px;
  color: #111;
  outline: none;
  padding: 16px 14px 16px 18px;
  background: transparent;
}

.hero-premium__search-input::placeholder {
  color: #888;
  font-size: 16px;
}

.btn-search {
  flex-shrink: 0;
  padding: 0 26px;
  border: none;
  background: #0a0a0a;
  color: #fff;
  font-size: 15px;
  font-weight: 700;
  cursor: pointer;
  min-width: 88px;
}

.hero-carousel {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 4;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 20px;
  padding: 16px 20px 20px;
  background: linear-gradient(180deg, transparent 0%, rgba(0, 0, 0, 0.55) 100%);
}

.hero-carousel__arrow {
  width: 48px;
  height: 48px;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  border: 1px solid rgba(255, 255, 255, 0.45);
  background: rgba(0, 0, 0, 0.45);
  color: #fff;
  cursor: pointer;
  transition:
    background 0.2s,
    border-color 0.2s,
    transform 0.15s;
}

.hero-carousel__arrow:hover {
  background: rgba(255, 255, 255, 0.18);
  border-color: rgba(255, 255, 255, 0.75);
  transform: scale(1.04);
}

.hero-carousel__arrow:active {
  transform: scale(0.96);
}

.hero-carousel__dots {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 8px;
  max-width: min(420px, 70vw);
}

.hero-carousel__dot {
  width: 9px;
  height: 9px;
  padding: 0;
  border-radius: 50%;
  border: 1px solid rgba(255, 255, 255, 0.55);
  background: rgba(255, 255, 255, 0.25);
  cursor: pointer;
  transition:
    background 0.2s,
    transform 0.15s;
}

.hero-carousel__dot:hover {
  background: rgba(255, 255, 255, 0.55);
}

.hero-carousel__dot--active {
  background: #fff;
  border-color: #fff;
  transform: scale(1.15);
}

.section-premium {
  margin-bottom: 28px;
}

.section-premium__inner {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  padding: clamp(20px, 3vw, 36px);
}

.section-premium__head {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 16px;
  margin-bottom: 24px;
  flex-wrap: wrap;
}

.eyebrow {
  margin: 0 0 8px;
  font-size: 10px;
  letter-spacing: 0.2em;
  text-transform: uppercase;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.85);
}

.eyebrow--dark {
  color: var(--muted);
  /* 与区块大标题拉开层级，但可读性明显好于 10px */
  font-size: 12px;
  letter-spacing: 0.12em;
}

.eyebrow--on-dark {
  color: rgba(255, 255, 255, 0.75);
}

.section-premium__title {
  margin: 0;
  font-size: clamp(20px, 2.1vw, 26px);
  font-weight: 800;
  letter-spacing: -0.02em;
  line-height: 1.25;
}

.section-premium__title--sm {
  font-size: clamp(18px, 2vw, 22px);
}

.state-line {
  padding: 24px 12px;
  text-align: center;
  color: var(--muted);
  font-size: 14px;
}

.products-grid--rec {
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 16px;
}

@media (min-width: 1100px) {
  .products-grid--rec {
    grid-template-columns: repeat(6, minmax(0, 1fr));
  }
}

.tile-badge--rec {
  left: 10px;
  right: auto;
  background: rgba(10, 10, 10, 0.88);
  color: #fff;
  font-weight: 700;
  max-width: calc(100% - 20px);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 与同区块其它卡片一致：不在内部再套一层窄 max-width，避免左右大块留白 */
.curated-block {
  width: 100%;
  max-width: none;
  margin: 0;
}

.demand-list {
  list-style: none;
  margin: 16px 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

@media (min-width: 960px) {
  .demand-list {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 14px;
  }
}

@media (min-width: 1280px) {
  .demand-list {
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 14px;
  }
}

.demand-body {
  min-width: 0;
}

.demand-row {
  display: grid;
  grid-template-columns: 56px 1fr auto;
  gap: 14px;
  align-items: center;
  padding: 12px 14px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  cursor: pointer;
  transition:
    border-color 0.15s,
    box-shadow 0.15s;
}

.demand-row:hover:not(.is-disabled) {
  border-color: #bbb;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.06);
}

.demand-row.is-disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.demand-thumb {
  width: 56px;
  height: 56px;
  border-radius: var(--radius);
  overflow: hidden;
  background: var(--surface-2);
  border: 1px solid var(--line);
}

.demand-thumb__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.demand-thumb__ph {
  width: 100%;
  height: 100%;
}

.demand-title {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
  color: var(--ink);
  line-height: 1.35;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.demand-desc {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--muted);
}

.demand-price {
  font-size: 17px;
  font-weight: 800;
}

.trust-float {
  max-width: 360px;
  background: #fff;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  padding: 14px 16px;
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.06);
}

.trust-float--inline {
  position: static;
  margin-top: 22px;
  max-width: none;
  width: 100%;
  box-sizing: border-box;
}

.trust-float__line {
  margin: 0 0 6px;
  font-size: 12px;
  font-weight: 700;
  color: var(--ink);
}

.trust-float__stars {
  margin: 0 0 6px;
  font-size: 13px;
  letter-spacing: 2px;
  color: var(--accent);
}

.trust-float__sub {
  margin: 0;
  font-size: 10px;
  color: var(--muted);
}

.logistics-cta {
  position: relative;
  border-radius: var(--radius);
  overflow: hidden;
  margin-bottom: 32px;
  min-height: 280px;
  background-size: cover;
  background-position: center;
}

.logistics-cta__veil {
  position: absolute;
  inset: 0;
  background: linear-gradient(100deg, rgba(0, 0, 0, 0.82) 0%, rgba(0, 0, 0, 0.55) 100%);
}

.logistics-cta__inner {
  position: relative;
  z-index: 1;
  padding: clamp(40px, 6vw, 72px) clamp(20px, 4vw, 48px);
  max-width: 560px;
  color: #f5f5f5;
}

.logistics-cta__title {
  margin: 0 0 12px;
  font-size: clamp(22px, 2.8vw, 30px);
  font-weight: 800;
  letter-spacing: -0.02em;
}

.logistics-cta__lead {
  margin: 0 0 24px;
  font-size: 14px;
  line-height: 1.65;
  color: rgba(245, 245, 245, 0.85);
}

.btn-accent {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 48px;
  padding: 0 28px;
  border-radius: var(--radius);
  background: var(--accent);
  color: #fff;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  text-decoration: none;
  border: 1px solid var(--accent);
}

.btn-accent:hover {
  background: var(--accent-hover);
  border-color: var(--accent-hover);
}

.catalog-tools {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
}

.pref-chip {
  min-height: 38px;
  padding: 8px 16px;
  border-radius: var(--radius);
  border: 1px solid var(--line);
  background: var(--surface-2);
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
}

.pref-chip:hover {
  border-color: #bbb;
}

.catalog-meta {
  font-size: 12px;
  color: var(--muted);
  font-weight: 600;
}

.products-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 14px;
}

.products-grid--skeleton {
  min-height: 200px;
}

.tile-card {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  overflow: hidden;
  cursor: pointer;
  transition:
    box-shadow 0.2s,
    border-color 0.2s;
  display: flex;
  flex-direction: column;
}

.tile-card:hover:not(.is-disabled) {
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.07);
  border-color: #ccc;
}

.tile-card.is-disabled {
  opacity: 0.72;
  cursor: not-allowed;
}

.tile-card__media {
  position: relative;
  width: 100%;
  aspect-ratio: 4 / 3;
  background: var(--surface-2);
  overflow: hidden;
}

.tile-card__img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: center;
  display: block;
}

.tile-card__ph {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  color: #999;
}

.tile-badge {
  position: absolute;
  top: 10px;
  right: 10px;
  font-size: 11px;
  font-weight: 700;
  padding: 5px 9px;
  border-radius: var(--radius);
  background: var(--ink);
  color: #fff;
}

.tile-badge--muted {
  background: #666;
}

.tile-card__body {
  padding: 12px 14px 14px;
  flex: 1;
  display: flex;
  flex-direction: column;
}

.tile-card__title {
  margin: 0 0 8px;
  font-size: 15px;
  font-weight: 700;
  line-height: 1.4;
  color: var(--ink);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.tile-card__meta {
  margin: 0 0 10px;
  font-size: 12px;
  color: var(--muted);
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.tile-card__foot {
  margin-top: auto;
  padding-top: 10px;
  border-top: 1px solid var(--line);
  display: flex;
  justify-content: space-between;
  align-items: baseline;
}

.tile-card__price {
  font-size: 18px;
  font-weight: 800;
}

.tile-card__stock {
  font-size: 12px;
  color: var(--muted);
  font-weight: 600;
}

.empty-card {
  text-align: center;
  padding: 48px 20px;
  color: var(--muted);
  font-size: 14px;
  border: 1px dashed var(--line);
  border-radius: var(--radius);
}

.pet-pref-mask {
  position: fixed;
  inset: 0;
  background: rgba(10, 10, 10, 0.55);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1200;
}

.pet-pref-dialog {
  width: min(480px, calc(100vw - 32px));
  background: #fff;
  border-radius: var(--radius);
  border: 1px solid var(--line);
  padding: 22px 24px;
  box-shadow: 0 24px 64px rgba(0, 0, 0, 0.18);
}

.pet-pref-dialog h4 {
  margin: 0;
  font-size: 18px;
  font-weight: 800;
}

.pet-pref-dialog p {
  margin: 0 0 10px;
  color: var(--muted);
  font-size: 13px;
}

.pet-pref-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.pet-pref-icon {
  width: 36px;
  height: 36px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius);
  background: var(--surface-2);
  font-size: 18px;
}

.pet-pref-tip {
  font-size: 12px;
  margin-bottom: 16px;
}

.pet-pref-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.pet-pref-btn {
  border: 1px solid var(--line);
  background: #fafafa;
  color: var(--ink);
  border-radius: var(--radius);
  padding: 10px 16px;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
}

.pet-pref-btn:hover {
  border-color: #999;
}

.pet-pref-btn.both {
  border-color: var(--ink);
  background: var(--ink);
  color: #fff;
}

@media (max-width: 640px) {
  .hero-premium {
    padding-bottom: 100px;
  }

  .hero-premium__search {
    flex-wrap: wrap;
  }

  .hero-premium__search-input {
    min-width: 0;
    width: 100%;
    padding: 14px 16px;
    font-size: 16px;
  }

  .hero-premium__search-input::placeholder {
    font-size: 15px;
  }

  .btn-search {
    width: 100%;
    padding: 14px;
    min-width: unset;
  }

  .hero-carousel {
    flex-wrap: wrap;
    gap: 12px;
    padding-bottom: 16px;
  }

  .hero-carousel__arrow {
    width: 44px;
    height: 44px;
  }

  .hero-carousel__dots {
    order: -1;
    width: 100%;
    max-width: none;
  }
}
</style>

<style>
/* 权重高于 App.vue `.main … h2/h3`，避免首页区块标题被全局 clamp 压扁 */
.main:not(.main-admin):not(.main-merchant):not(.main-auth) .home--premium h2.section-premium__title {
  font-size: clamp(20px, 2.1vw, 26px);
  font-weight: 800;
  letter-spacing: -0.02em;
  line-height: 1.25;
}

.main:not(.main-admin):not(.main-merchant):not(.main-auth) .home--premium h2.section-premium__title--sm {
  font-size: clamp(18px, 1.8vw, 22px);
  font-weight: 800;
  letter-spacing: -0.02em;
  line-height: 1.25;
}

.main:not(.main-admin):not(.main-merchant):not(.main-auth) .home--premium h2.logistics-cta__title {
  font-size: clamp(22px, 2.8vw, 30px);
  font-weight: 800;
  letter-spacing: -0.02em;
}

.main:not(.main-admin):not(.main-merchant):not(.main-auth) .home--premium h3.tile-card__title {
  font-size: 15px;
  font-weight: 700;
  line-height: 1.4;
}
</style>
