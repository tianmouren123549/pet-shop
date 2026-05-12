<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../../utils/request'
import { showAppMessage } from '../../utils/appMessage'
import PaginationBar from '../../components/PaginationBar.vue'
import { dismissNoticeBadgeForCurrentUnread } from '../../utils/noticeBadgeAck'

const router = useRouter()
const merchantId = ref(Number(localStorage.getItem('adminId') || 0))
const list = ref([])
const loading = ref(false)
const errorMsg = ref('')
const searchQuery = ref('')
/** 已生效的筛选词（须 Enter / 点「搜索」） */
const appliedSearchQuery = ref('')
const activeCategory = ref('all')
const page = ref(1)
const pageSize = ref(10)

/** 与后端写入的商家通知 title 一致；其余归入「其他」。 */
const BUCKETS = {
  restock_reminder: { label: '补货提醒', tag: '补货提醒', dot: '#16a34a' },
  user_urge_restock: { label: '用户催补货', tag: '用户催补货', dot: '#2563eb' },
  platform_urge_ship: { label: '平台催发货', tag: '平台催发货', dot: '#d97706' },
  other: { label: '其他', tag: '其他', dot: '#64748b' },
}

/**
 * @param {Record<string, unknown>} n
 */
function noticeBucket(n) {
  const t = String(n.title || '').trim()
  if (t === '补货提醒') return 'restock_reminder'
  if (t === '用户催补货') return 'user_urge_restock'
  if (t === '平台催发货') return 'platform_urge_ship'
  return 'other'
}

/**
 * @param {string} [iso]
 */
function formatNoticeTime(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return ''
  const now = new Date()
  const pad = (x) => String(x).padStart(2, '0')
  const timeStr = `${pad(d.getHours())}:${pad(d.getMinutes())}`
  const today = now.toDateString() === d.toDateString()
  const yest = new Date(now)
  yest.setDate(yest.getDate() - 1)
  const yesterday = yest.toDateString() === d.toDateString()
  if (today) return `今天 ${timeStr}`
  if (yesterday) return `昨天 ${timeStr}`
  return `${d.getMonth() + 1}月${d.getDate()}日 ${timeStr}`
}

const searchFiltered = computed(() => {
  const arr = Array.isArray(list.value) ? list.value : []
  const q = appliedSearchQuery.value.trim().toLowerCase()
  if (!q) return arr
  return arr.filter((n) => {
    const blob = `${n.title || ''} ${n.content || ''} ${n.reason || ''}`.toLowerCase()
    return blob.includes(q)
  })
})

const categoryCounts = computed(() => {
  /** @type {Record<string, number>} */
  const c = { all: 0, restock_reminder: 0, user_urge_restock: 0, platform_urge_ship: 0, other: 0 }
  for (const n of searchFiltered.value) {
    c.all += 1
    c[noticeBucket(n)] += 1
  }
  return c
})

/** 左侧：全部 + 系统仅有的两类；仅当存在未归类标题时出现「其他」 */
const sidebarCategories = computed(() => {
  const rows = [
    { id: 'all', label: '全部', mutedDot: true },
    { id: 'restock_reminder', label: BUCKETS.restock_reminder.label, dot: BUCKETS.restock_reminder.dot },
    { id: 'user_urge_restock', label: BUCKETS.user_urge_restock.label, dot: BUCKETS.user_urge_restock.dot },
    { id: 'platform_urge_ship', label: BUCKETS.platform_urge_ship.label, dot: BUCKETS.platform_urge_ship.dot },
  ]
  if (categoryCounts.value.other > 0) {
    rows.push({ id: 'other', label: BUCKETS.other.label, dot: BUCKETS.other.dot })
  }
  return rows
})

const filtered = computed(() => {
  const arr = searchFiltered.value
  if (activeCategory.value === 'all') return arr
  return arr.filter((n) => noticeBucket(n) === activeCategory.value)
})

const unreadInFeed = computed(() => filtered.value.filter((n) => Number(n.readStatus) !== 1).length)

function runNoticeSearch() {
  appliedSearchQuery.value = String(searchQuery.value || '').trim()
}

watch([activeCategory, appliedSearchQuery], () => {
  page.value = 1
})

watch(categoryCounts, (c) => {
  if (activeCategory.value === 'other' && c.other === 0) {
    activeCategory.value = 'all'
  }
  if (activeCategory.value === 'platform_urge_ship' && c.platform_urge_ship === 0) {
    activeCategory.value = 'all'
  }
})

const total = computed(() => (Array.isArray(filtered.value) ? filtered.value.length : 0))
const pagedNotices = computed(() => {
  const arr = Array.isArray(filtered.value) ? filtered.value : []
  const p = Math.max(1, Number(page.value || 1))
  const ps = Math.max(1, Number(pageSize.value || 1))
  const start = (p - 1) * ps
  return arr.slice(start, start + ps)
})

/**
 * @param {string} bucketId
 */
function catMeta(bucketId) {
  return BUCKETS[bucketId] || BUCKETS.other
}

async function load() {
  if (!merchantId.value) return
  loading.value = true
  errorMsg.value = ''
  const res = await api.merchantGetNotifications(merchantId.value)
  if (res.code === 200) {
    list.value = res.data || []
    dismissNoticeBadgeForCurrentUnread('merchant', merchantId.value, list.value)
  } else {
    errorMsg.value = res.message || '加载失败'
  }
  loading.value = false
}

async function markRead(item) {
  if (!item?.noticeId) return
  const res = await api.merchantMarkNotificationRead(merchantId.value, item.noticeId)
  if (res.code === 200) {
    await load()
    window.dispatchEvent(new Event('petshop-notice-updated'))
  } else {
    showAppMessage(res.message || '操作失败', '提示')
  }
}

async function clearAllUnread() {
  const unread = filtered.value.filter((n) => Number(n.readStatus) !== 1)
  if (!unread.length) return
  try {
    const results = await Promise.all(
      unread.map((n) => api.merchantMarkNotificationRead(merchantId.value, n.noticeId)),
    )
    if (results.some((r) => r.code !== 200)) {
      showAppMessage('部分通知未能标记为已读，请稍后重试', '提示')
    }
    await load()
    window.dispatchEvent(new Event('petshop-notice-updated'))
  } catch {
    showAppMessage('操作失败', '提示')
  }
}

function goEdit(item) {
  const pid = Number(item?.productId || 0)
  if (!pid) return
  router.push(`/merchant/product/${pid}/edit`)
}

onMounted(async () => {
  if (!merchantId.value) {
    showAppMessage('请先登录', '提示')
    router.push('/admin-login')
    return
  }
  await load()
})
</script>

<template>
  <div class="mn-page">
    <div v-if="loading" class="mn-state">加载中…</div>
    <div v-else-if="errorMsg" class="mn-state mn-state--err">{{ errorMsg }}</div>
    <div v-else-if="list.length === 0" class="mn-state mn-state--empty">暂无通知</div>

    <div v-else class="mn-shell">
      <div class="mn-toolbar" role="toolbar" aria-label="通知筛选与操作">
        <div class="mn-cats" role="tablist" aria-label="通知分类">
          <button
            v-for="c in sidebarCategories"
            :key="c.id"
            type="button"
            role="tab"
            :aria-selected="activeCategory === c.id"
            class="mn-cat"
            :class="{ 'mn-cat--active': activeCategory === c.id }"
            @click="activeCategory = c.id"
          >
            <span v-if="c.mutedDot" class="mn-cat-dot mn-cat-dot--muted" />
            <span v-else class="mn-cat-dot" :style="{ background: c.dot }" />
            <span class="mn-cat-label">{{ c.label }}</span>
            <span class="mn-cat-badge">{{ categoryCounts[c.id] ?? 0 }}</span>
          </button>
        </div>
        <div class="mn-toolbar-end">
          <div class="mn-search" role="search">
            <svg class="mn-search-ic" viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
              <path
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                d="M11 19a8 8 0 100-16 8 8 0 000 16zm10 2l-4.35-4.35"
              />
            </svg>
            <input
              v-model="searchQuery"
              class="mn-search-input"
              type="search"
              placeholder="搜索通知…"
              autocomplete="off"
              enterkeyhint="search"
              @keyup.enter="runNoticeSearch"
            />
          </div>
          <button type="button" class="mn-search-submit" @click="runNoticeSearch">搜索</button>
          <button type="button" class="mn-refresh" @click="load">刷新</button>
          <button type="button" class="mn-clear" :disabled="unreadInFeed === 0" @click="clearAllUnread">
            全部已读
          </button>
        </div>
      </div>

      <main class="mn-feed">
        <div v-if="filtered.length === 0" class="mn-empty-feed">该分类下暂无通知</div>

        <ul v-else class="mn-list" aria-live="polite">
          <li
            v-for="n in pagedNotices"
            :key="n.noticeId"
            class="mn-card"
            :class="{ 'mn-card--unread': Number(n.readStatus) !== 1 }"
            :style="{ '--mn-accent': catMeta(noticeBucket(n)).dot }"
          >
            <div class="mn-card-meta">
              <span class="mn-meta-dot" :style="{ background: catMeta(noticeBucket(n)).dot }" />
              <span class="mn-meta-cat">{{ catMeta(noticeBucket(n)).tag }}</span>
              <span class="mn-meta-time">{{ formatNoticeTime(n.createdAt) }}</span>
            </div>
            <h3 class="mn-card-title">{{ n.title || '通知' }}</h3>
            <p class="mn-card-body">{{ n.content }}</p>

            <div class="mn-card-foot">
              <div class="mn-card-links">
                <button
                  v-if="n.productId"
                  type="button"
                  class="mn-link"
                  @click="goEdit(n)"
                >
                  去补货
                </button>
                <span v-if="n.productId" class="mn-muted">商品 ID {{ n.productId }}</span>
              </div>
              <button
                v-if="Number(n.readStatus) !== 1"
                type="button"
                class="mn-mark"
                @click="markRead(n)"
              >
                标记已读
              </button>
              <span v-else class="mn-read-pill">已读</span>
            </div>
          </li>
        </ul>

        <PaginationBar
          class="mn-pager"
          :page="page"
          :page-size="pageSize"
          :total="total"
          compact
          @update:page="page = $event"
        />
      </main>
    </div>
  </div>
</template>

<style scoped>
.mn-page {
  min-height: 100%;
  padding: 8px 4px 32px;
  max-width: 1200px;
  margin: 0 auto;
  box-sizing: border-box;
}

.mn-search {
  flex: 1 1 200px;
  display: flex;
  align-items: center;
  gap: 10px;
  height: 42px;
  padding: 0 14px;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  background: #fff;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.05);
  max-width: min(320px, 100%);
  min-width: 0;
}

.mn-search-ic {
  flex-shrink: 0;
  color: #94a3b8;
}

.mn-search-input {
  flex: 1;
  min-width: 0;
  border: none;
  background: transparent;
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
  outline: none;
}

.mn-search-input::placeholder {
  color: #94a3b8;
  font-weight: 500;
}

.mn-search-submit {
  flex-shrink: 0;
  height: 42px;
  padding: 0 18px;
  border-radius: 12px;
  border: 1px solid #0f172a;
  background: #0f172a;
  font-size: 13px;
  font-weight: 800;
  color: #fff;
  cursor: pointer;
}

.mn-refresh {
  flex-shrink: 0;
  height: 42px;
  padding: 0 18px;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  background: #fff;
  font-size: 13px;
  font-weight: 700;
  color: #334155;
  cursor: pointer;
}

.mn-refresh:hover {
  border-color: #cbd5e1;
  background: #f8fafc;
}

.mn-state {
  padding: 48px 24px;
  text-align: center;
  font-size: 14px;
  font-weight: 600;
  color: #64748b;
  border-radius: 16px;
  border: 1px solid #e2e8f0;
  background: #fff;
}

.mn-state--err {
  color: #b91c1c;
  background: #fef2f2;
  border-color: #fecaca;
}

.mn-state--empty {
  background: #f8fafc;
}

.mn-shell {
  display: flex;
  flex-direction: column;
  gap: 14px;
  align-items: stretch;
}

.mn-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px 16px;
  padding: 12px 14px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  box-shadow: 0 4px 18px rgba(15, 23, 42, 0.06);
}

.mn-cats {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  padding: 0;
  margin: 0;
  border: none;
  background: transparent;
  box-shadow: none;
}

.mn-toolbar-end {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  flex: 1 1 auto;
  min-width: 0;
}

.mn-cat {
  width: auto;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 44px;
  padding: 12px 16px;
  margin: 0;
  border: 1px solid #e2e8f0;
  border-radius: 999px;
  background: #fff;
  cursor: pointer;
  font-size: 14px;
  font-weight: 700;
  color: #475569;
  text-align: left;
  transition:
    background 0.15s ease,
    color 0.15s ease,
    border-color 0.15s ease;
}

.mn-cat:hover {
  background: #f8fafc;
  border-color: #cbd5e1;
  color: #0f172a;
}

.mn-cat--active {
  background: #0f172a;
  border-color: #0f172a;
  color: #f8fafc;
  box-shadow: none;
}

.mn-cat-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
}

.mn-cat-dot--muted {
  background: #cbd5e1;
}

.mn-cat-label {
  flex: 1;
  min-width: 0;
}

.mn-cat-badge {
  font-size: 12px;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
  color: #64748b;
  background: #fff;
  border: 1px solid #e2e8f0;
  padding: 2px 8px;
  border-radius: 999px;
}

.mn-cat--active .mn-cat-badge {
  background: rgba(255, 255, 255, 0.12);
  border-color: rgba(255, 255, 255, 0.28);
  color: #f8fafc;
}

.mn-cat--active .mn-cat-dot--muted {
  background: rgba(255, 255, 255, 0.45);
}

.mn-feed {
  border-radius: 16px;
  border: 1px solid #e2e8f0;
  background: #fff;
  box-shadow: 0 4px 24px rgba(15, 23, 42, 0.06);
  padding: 18px 22px 16px;
  min-width: 0;
}

.mn-clear {
  height: 42px;
  padding: 0 18px;
  border-radius: 10px;
  border: 1px solid #e2e8f0;
  background: #fff;
  font-size: 13px;
  font-weight: 700;
  color: #475569;
  cursor: pointer;
}

.mn-clear:hover:not(:disabled) {
  border-color: #cbd5e1;
  background: #f8fafc;
}

.mn-clear:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.mn-empty-feed {
  padding: 36px 16px;
  text-align: center;
  font-size: 14px;
  font-weight: 600;
  color: #94a3b8;
}

.mn-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.mn-card {
  padding: 16px 18px;
  border-radius: 14px;
  border: 1px solid #e8ecf1;
  border-left: 4px solid var(--mn-accent, #cbd5e1);
  background: #fff;
  box-shadow: 0 2px 10px rgba(15, 23, 42, 0.05);
}

.mn-card--unread {
  background: linear-gradient(180deg, #f8fafc 0%, #ffffff 52%);
  box-shadow: 0 3px 14px rgba(15, 23, 42, 0.07);
}

.mn-card-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px 12px;
  margin-bottom: 10px;
}

.mn-meta-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.mn-meta-cat {
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.02em;
  color: #475569;
}

.mn-meta-time {
  margin-left: auto;
  font-size: 12px;
  font-weight: 600;
  color: #94a3b8;
}

.mn-card-title {
  margin: 0 0 8px;
  font-size: 16px;
  font-weight: 800;
  color: #0f172a;
  line-height: 1.35;
}

.mn-card-body {
  margin: 0 0 14px;
  font-size: 14px;
  font-weight: 500;
  color: #475569;
  line-height: 1.55;
}

.mn-card-foot {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.mn-card-links {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.mn-link {
  border: none;
  background: none;
  padding: 0;
  font-size: 13px;
  font-weight: 800;
  color: #2563eb;
  cursor: pointer;
  text-decoration: underline;
  text-underline-offset: 3px;
}

.mn-link:hover {
  color: #1d4ed8;
}

.mn-muted {
  font-size: 12px;
  font-weight: 600;
  color: #94a3b8;
}

.mn-mark {
  height: 34px;
  padding: 0 14px;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
  background: #fff;
  font-size: 12px;
  font-weight: 700;
  color: #475569;
  cursor: pointer;
}

.mn-mark:hover {
  border-color: #cbd5e1;
  background: #f8fafc;
}

.mn-read-pill {
  font-size: 12px;
  font-weight: 700;
  color: #94a3b8;
}

.mn-pager {
  margin-top: 8px;
}

@media (max-width: 840px) {
  .mn-toolbar {
    flex-direction: column;
    align-items: stretch;
  }

  .mn-toolbar-end {
    justify-content: stretch;
    width: 100%;
  }

  .mn-search {
    flex: 1 1 auto;
    max-width: none;
  }

  .mn-cat {
    flex: 1 1 auto;
    min-width: calc(50% - 8px);
    justify-content: center;
  }

  .mn-meta-time {
    margin-left: 0;
    width: 100%;
  }
}
</style>
