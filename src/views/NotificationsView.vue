<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../utils/request'
import { showAppMessage } from '../utils/appMessage'
import PaginationBar from '../components/PaginationBar.vue'
import { dismissNoticeBadgeForCurrentUnread } from '../utils/noticeBadgeAck'
import AppSkeletonCard from '../components/AppSkeletonCard.vue'

const router = useRouter()
const userId = ref(Number(localStorage.getItem('userId') || 0))
const list = ref([])
const loading = ref(false)
const errorMsg = ref('')
/** false：全部通知；true：仅未读 */
const onlyUnread = ref(false)
const page = ref(1)
const pageSize = ref(8)
const NOTICE_SKELETON_COUNT = 6

const filtered = computed(() => {
  const arr = Array.isArray(list.value) ? list.value : []
  if (!onlyUnread.value) return arr
  return arr.filter((n) => Number(n.readStatus) !== 1)
})

watch(onlyUnread, () => {
  page.value = 1
})

const total = computed(() => (Array.isArray(filtered.value) ? filtered.value.length : 0))
const pagedNotices = computed(() => {
  const arr = Array.isArray(filtered.value) ? filtered.value : []
  const p = Math.max(1, Number(page.value || 1))
  const ps = Math.max(1, Number(pageSize.value || 1))
  const start = (p - 1) * ps
  return arr.slice(start, start + ps)
})

watch([total, pageSize], () => {
  const tp = Math.max(1, Math.ceil(Number(total.value || 0) / Math.max(1, Number(pageSize.value || 1))))
  if (page.value > tp) page.value = tp
})

async function load() {
  if (!userId.value) return
  loading.value = true
  errorMsg.value = ''
  const res = await api.userGetNotifications(userId.value)
  if (res.code === 200) {
    list.value = res.data || []
    dismissNoticeBadgeForCurrentUnread('user', userId.value, list.value)
  } else {
    errorMsg.value = res.message || '加载失败'
  }
  loading.value = false
}

async function markRead(item) {
  if (!item?.noticeId) return
  const res = await api.userMarkNotificationRead(userId.value, item.noticeId)
  if (res.code === 200) {
    await load()
    window.dispatchEvent(new Event('petshop-notice-updated'))
  } else {
    showAppMessage(res.message || '操作失败', '提示')
  }
}

function goProduct(item) {
  const pid = Number(item?.productId || 0)
  if (!pid) return
  router.push(`/product/${pid}`)
}

onMounted(async () => {
  if (!userId.value) {
    showAppMessage('请先登录', '提示')
    router.push('/login')
    return
  }
  await load()
})
</script>

<template>
  <div class="pw-page notice-page notice-page--apex">
    <section class="layout-shell">
      <aside class="layout-sidebar">
        <div class="sidebar-head">
          <h2 class="sidebar-title">通知中心</h2>
          <p class="sidebar-lead">按阅读状态筛选消息</p>
        </div>

        <div class="facet-block">
          <div class="facet-title">筛选</div>
          <button
            type="button"
            :class="['facet-chip', { 'facet-chip--on': !onlyUnread }]"
            @click="onlyUnread = false"
          >
            全部通知
          </button>
          <button
            type="button"
            :class="['facet-chip', { 'facet-chip--on': onlyUnread }]"
            @click="onlyUnread = true"
          >
            仅未读
          </button>
        </div>
      </aside>

      <div class="layout-main">
        <header class="notice-toolbar">
          <div class="notice-toolbar__left">
            <h1 class="notice-toolbar-title">消息列表</h1>
            <p class="notice-toolbar-meta">
              <span class="notice-toolbar-count">{{ total }}</span>
              条
              <span class="notice-toolbar-dot">·</span>
              <span>{{ onlyUnread ? '仅展示未读' : '含已读与未读' }}</span>
            </p>
          </div>
          <button type="button" class="notice-btn-ghost" @click="load">刷新</button>
        </header>

        <div v-if="loading" class="notice-skeleton-list">
          <AppSkeletonCard :count="NOTICE_SKELETON_COUNT" />
        </div>
        <div v-else-if="errorMsg" class="notice-state notice-state--error">{{ errorMsg }}</div>
        <div v-else-if="filtered.length === 0" class="notice-empty">
          <p class="notice-empty-title">暂无通知</p>
          <p class="notice-empty-desc">订单进度、到货提醒与售后消息将出现在此处。</p>
        </div>

        <div v-else class="notice-list">
          <article
            v-for="n in pagedNotices"
            :key="n.noticeId"
            class="notice-card"
            :class="{ 'notice-card--unread': Number(n.readStatus) !== 1 }"
          >
            <div class="notice-card__main">
              <div class="notice-card__head">
                <h3 class="notice-card__title">{{ n.title }}</h3>
                <time class="notice-card__time">{{ n.createdAt ? new Date(n.createdAt).toLocaleString() : '' }}</time>
              </div>
              <p class="notice-card__body">{{ n.content }}</p>
              <div v-if="n.productId" class="notice-card__foot">
                <button type="button" class="notice-link" @click="goProduct(n)">查看商品</button>
                <span class="notice-muted">商品 ID：{{ n.productId }}</span>
              </div>
            </div>
            <div class="notice-card__actions">
              <button v-if="Number(n.readStatus) !== 1" type="button" class="notice-btn-outline" @click="markRead(n)">
                标记已读
              </button>
              <span v-else class="notice-read-pill">已读</span>
            </div>
          </article>
        </div>

        <PaginationBar
          class="notice-pagination"
          :page="page"
          :page-size="pageSize"
          :total="total"
          compact
          @update:page="page = $event"
        />
      </div>
    </section>
  </div>
</template>

<style scoped>
.notice-page--apex {
  --nx-ink: #0a0a0a;
  --nx-muted: #737373;
  --nx-line: #e5e5e5;
  --nx-panel: #ffffff;
  --nx-soft: #fafafa;
  --nx-radius: 2px;
  font-family: 'Inter', 'Microsoft YaHei', 'PingFang SC', system-ui, sans-serif;
  padding: 0;
  background: transparent;
}

.notice-page--apex.pw-page {
  padding-bottom: clamp(24px, 3vh, 36px);
}

.layout-shell {
  display: grid;
  grid-template-columns: 272px minmax(0, 1fr);
  gap: 20px;
  align-items: start;
}

.layout-sidebar {
  background: var(--nx-panel);
  border: 1px solid var(--nx-line);
  border-radius: var(--nx-radius);
  padding: 22px 18px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  box-shadow: 0 1px 0 rgba(0, 0, 0, 0.04);
}

.sidebar-head {
  margin-bottom: 12px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--nx-line);
}

.sidebar-title {
  margin: 0 0 6px;
  font-size: clamp(17px, 1.4vw, 20px);
  font-weight: 800;
  letter-spacing: -0.02em;
  color: var(--nx-ink);
}

.sidebar-lead {
  margin: 0;
  font-size: 12px;
  color: var(--nx-muted);
  line-height: 1.45;
}

.facet-block {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 16px 0 8px;
  border-top: 1px solid var(--nx-line);
}

.facet-block:first-of-type {
  border-top: none;
  padding-top: 8px;
}

.facet-title {
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--nx-muted);
}

.facet-chip {
  width: 100%;
  text-align: left;
  padding: 10px 12px;
  border: 1px solid var(--nx-line);
  border-radius: var(--nx-radius);
  background: var(--nx-soft);
  font-size: 13px;
  font-weight: 600;
  color: var(--nx-ink);
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
  background: var(--nx-ink);
  color: #fff;
  border-color: var(--nx-ink);
}

.facet-chip--on:hover {
  background: var(--nx-ink);
  border-color: var(--nx-ink);
  color: #fff;
}

.layout-main {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.notice-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
  padding: clamp(18px, 2vw, 22px) clamp(18px, 2vw, 22px);
  background: var(--nx-panel);
  border: 1px solid var(--nx-line);
  border-radius: var(--nx-radius);
  box-shadow: 0 1px 0 rgba(0, 0, 0, 0.04);
}

.notice-toolbar-title {
  margin: 0 0 6px;
  font-size: clamp(17px, 1.45vw, 20px);
  font-weight: 800;
  color: var(--nx-ink);
  letter-spacing: -0.02em;
}

.notice-toolbar-meta {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--nx-muted);
}

.notice-toolbar-count {
  font-weight: 800;
  color: var(--nx-ink);
}

.notice-toolbar-dot {
  margin: 0 6px;
  color: var(--nx-line);
}

.notice-btn-ghost {
  padding: 10px 18px;
  border: 1px solid var(--nx-line);
  border-radius: var(--nx-radius);
  background: var(--nx-panel);
  color: var(--nx-ink);
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.06em;
  cursor: pointer;
}

.notice-btn-ghost:hover {
  border-color: var(--nx-ink);
}

.notice-skeleton-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 280px), 1fr));
  gap: 12px;
}

.notice-state {
  padding: 28px 20px;
  text-align: center;
  border: 1px solid #fecaca;
  border-radius: var(--nx-radius);
  background: #fef2f2;
  color: #b91c1c;
  font-size: 14px;
  font-weight: 600;
}

.notice-empty {
  padding: 48px 24px;
  text-align: center;
  border: 1px dashed var(--nx-line);
  border-radius: var(--nx-radius);
  background: var(--nx-panel);
}

.notice-empty-title {
  margin: 0 0 10px;
  font-size: clamp(18px, 1.6vw, 22px);
  font-weight: 800;
  color: var(--nx-ink);
}

.notice-empty-desc {
  margin: 0;
  font-size: 14px;
  color: var(--nx-muted);
  line-height: 1.55;
}

.notice-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.notice-card {
  display: flex;
  gap: 16px;
  justify-content: space-between;
  align-items: flex-start;
  padding: clamp(18px, 2vw, 22px);
  background: var(--nx-panel);
  border: 1px solid var(--nx-line);
  border-radius: var(--nx-radius);
  transition: box-shadow 0.2s ease;
}

.notice-card:hover {
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.06);
}

.notice-card--unread {
  border-left: 3px solid var(--nx-ink);
  padding-left: calc(clamp(18px, 2vw, 22px) - 2px);
  background: var(--nx-soft);
}

.notice-card__main {
  min-width: 0;
  flex: 1;
}

.notice-card__head {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px 16px;
  margin-bottom: 10px;
}

.notice-card__title {
  margin: 0;
  font-size: clamp(15px, 1.25vw, 17px);
  font-weight: 800;
  color: var(--nx-ink);
}

.notice-card__time {
  font-size: 12px;
  font-weight: 600;
  color: #a3a3a3;
  white-space: nowrap;
}

.notice-card__body {
  margin: 0;
  font-size: 14px;
  line-height: 1.6;
  color: #525252;
}

.notice-card__foot {
  margin-top: 12px;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
}

.notice-link {
  padding: 0;
  border: none;
  background: none;
  font-size: 13px;
  font-weight: 700;
  color: var(--nx-ink);
  text-decoration: underline;
  text-underline-offset: 3px;
  cursor: pointer;
}

.notice-link:hover {
  color: #525252;
}

.notice-muted {
  font-size: 12px;
  font-weight: 600;
  color: #a3a3a3;
}

.notice-card__actions {
  flex-shrink: 0;
  padding-top: 2px;
}

.notice-btn-outline {
  padding: 8px 14px;
  border: 1px solid var(--nx-line);
  border-radius: var(--nx-radius);
  background: var(--nx-panel);
  font-size: 12px;
  font-weight: 700;
  color: var(--nx-ink);
  cursor: pointer;
}

.notice-btn-outline:hover {
  border-color: var(--nx-ink);
}

.notice-read-pill {
  display: inline-block;
  padding: 8px 12px;
  font-size: 12px;
  font-weight: 700;
  color: var(--nx-muted);
  border: 1px solid var(--nx-line);
  border-radius: var(--nx-radius);
  background: var(--nx-panel);
}

.notice-pagination :deep(.pw-pagination) {
  justify-content: center;
  border: none !important;
  background: transparent !important;
  border-radius: 0 !important;
  padding: 8px 0 4px !important;
  margin-top: 4px;
  box-shadow: none !important;
}

.notice-pagination :deep(.pw-pagination--no-meta .pw-pagination-right) {
  justify-content: center;
}

.notice-pagination :deep(.pw-page-btn),
.notice-pagination :deep(.pw-page-num) {
  min-width: 40px;
  height: 40px;
  border-radius: var(--nx-radius);
  border: 1px solid var(--nx-line);
  background: var(--nx-soft);
  font-size: 13px;
  font-weight: 700;
}

.notice-pagination :deep(.pw-page-num.active) {
  border-color: var(--nx-ink);
  background: var(--nx-ink);
  color: #fff;
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

  .notice-card {
    flex-direction: column;
  }

  .notice-card__actions {
    align-self: stretch;
  }

  .notice-btn-outline {
    width: 100%;
  }
}
</style>
