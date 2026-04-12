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
/** 默认展示全部通知；勾选后仅看未读 */
const onlyUnread = ref(false)
const page = ref(1)
const pageSize = ref(8)

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

function setPageSize(n) {
  pageSize.value = Number(n || 8)
  page.value = 1
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
  <div class="pw-page">
    <section class="pw-hero">
      <h1 class="pw-title">通知中心</h1>
      <p class="pw-lead">接收平台补货提醒、用户催补货等消息。</p>
    </section>

    <section class="pw-section">
      <div class="pw-toolbar pw-toolbar--tight">
        <label class="pw-label">
          <input type="checkbox" v-model="onlyUnread" />
          <span>仅看未读</span>
        </label>
        <button type="button" class="pw-btn pw-btn-sm" @click="load">刷新</button>
      </div>

      <div v-if="loading" class="pw-state">加载中...</div>
      <div v-else-if="errorMsg" class="pw-state pw-state--error">{{ errorMsg }}</div>
      <div v-else-if="filtered.length === 0" class="pw-state pw-state--empty">暂无通知</div>

      <div v-else class="pw-list">
        <div
          v-for="n in pagedNotices"
          :key="n.noticeId"
          class="pw-item"
          :class="{ 'pw-item--unread': Number(n.readStatus) !== 1 }"
        >
          <div class="pw-item-main">
            <div class="pw-item-title-row">
              <div class="pw-item-title">{{ n.title }}</div>
              <div class="pw-item-time">{{ n.createdAt ? new Date(n.createdAt).toLocaleString() : '' }}</div>
            </div>
            <div class="pw-item-body">{{ n.content }}</div>
            <div v-if="n.productId" class="pw-item-meta">
              <span class="pw-link" role="button" tabindex="0" @click.stop="goEdit(n)">去补货</span>
              <span class="pw-muted">商品ID：{{ n.productId }}</span>
            </div>
          </div>
          <div class="pw-item-actions">
            <button v-if="Number(n.readStatus) !== 1" type="button" class="pw-btn-ghost pw-btn-sm" @click="markRead(n)">
              标记已读
            </button>
            <span v-else class="pw-read-tag">已读</span>
          </div>
        </div>
      </div>

      <PaginationBar
        :page="page"
        :page-size="pageSize"
        :total="total"
        :page-size-options="[6, 8, 12, 20]"
        compact
        @update:page="page = $event"
        @update:page-size="setPageSize"
      />
    </section>
  </div>
</template>

<style scoped>
.pw-item-actions {
  flex-shrink: 0;
  display: flex;
  align-items: flex-start;
}
</style>
