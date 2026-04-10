<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../utils/request'
import PaginationBar from '../components/PaginationBar.vue'

const router = useRouter()
const userId = ref(Number(localStorage.getItem('userId') || 0))
const loading = ref(false)
const errorMsg = ref('')
const orders = ref([])
const merchantCards = ref([])
const page = ref(1)
const pageSize = ref(9)

function uniqBy(list, keyFn) {
  const seen = new Set()
  const out = []
  for (const x of list || []) {
    const k = keyFn(x)
    if (seen.has(k)) continue
    seen.add(k)
    out.push(x)
  }
  return out
}

async function load() {
  loading.value = true
  errorMsg.value = ''
  merchantCards.value = []
  page.value = 1

  const res = await api.userGetOrders(userId.value)
  if (res.code !== 200) {
    loading.value = false
    errorMsg.value = res.message || '加载失败'
    return
  }
  orders.value = res.data || []

  const details = []
  for (const o of orders.value.slice(0, 20)) {
    const d = await api.userGetOrder(o.orderId, userId.value)
    if (d.code === 200 && d.data) details.push(d.data)
  }

  const cards = []
  for (const od of details) {
    const items = Array.isArray(od.items) ? od.items : []
    const mids = uniqBy(
      items.map((it) => Number(it.merchantId || 0)).filter((x) => x > 0),
      (x) => x
    )
    for (const mid of mids) {
      cards.push({
        merchantId: mid,
        merchantName: `商家${mid}`,
        lastOrderId: od.orderId,
        lastOrderNo: od.orderNo,
        lastTime: od.updatedAt || od.createdAt,
      })
    }
  }

  merchantCards.value = uniqBy(cards, (x) => Number(x.merchantId)).sort(
    (a, b) => new Date(b.lastTime || 0).getTime() - new Date(a.lastTime || 0).getTime()
  )

  loading.value = false
}

function goChat(card) {
  if (!card?.merchantId) return
  const q = [`merchantId=${encodeURIComponent(card.merchantId)}`]
  if (card.lastOrderId) q.push(`orderId=${encodeURIComponent(card.lastOrderId)}`)
  router.push(`/support?${q.join('&')}`)
}

const hasData = computed(() => (merchantCards.value || []).length > 0)
const total = computed(() => (Array.isArray(merchantCards.value) ? merchantCards.value.length : 0))
const pagedCards = computed(() => {
  const list = Array.isArray(merchantCards.value) ? merchantCards.value : []
  const p = Math.max(1, Number(page.value || 1))
  const ps = Math.max(1, Number(pageSize.value || 1))
  const start = (p - 1) * ps
  return list.slice(start, start + ps)
})

function setPageSize(n) {
  pageSize.value = Number(n || 9)
  page.value = 1
}

onMounted(async () => {
  if (!userId.value) {
    alert('请先登录')
    router.push('/login')
    return
  }
  await load()
})
</script>

<template>
  <div class="pw-page">
    <section class="pw-hero">
      <h1 class="pw-title">联系商家</h1>
      <p class="pw-lead">从已购订单中选择商家，进入会话继续沟通售后与商品问题。</p>
    </section>

    <section class="pw-section">
      <div class="pw-toolbar pw-toolbar--tight">
        <button type="button" class="pw-btn pw-btn-sm" @click="load">刷新</button>
        <button type="button" class="pw-btn-ghost pw-btn-sm" @click="router.push('/orders')">我的订单</button>
      </div>

      <div v-if="loading" class="pw-state">加载中...</div>
      <div v-else-if="errorMsg" class="pw-state pw-state--error">{{ errorMsg }}</div>
      <div v-else-if="!hasData" class="pw-state pw-state--empty">暂无可联系的商家（暂无带商家的订单）。</div>

      <div v-else class="pw-grid">
        <div v-for="m in pagedCards" :key="m.merchantId" class="pw-card">
          <div class="pw-card-title">{{ m.merchantName }}</div>
          <div class="pw-card-meta">
            <span>最近订单：{{ m.lastOrderNo || '-' }}</span>
            <span>更新时间：{{ m.lastTime ? new Date(m.lastTime).toLocaleString() : '-' }}</span>
          </div>
          <div class="pw-card-actions">
            <button type="button" class="pw-btn pw-btn-sm" @click="goChat(m)">进入对话</button>
          </div>
        </div>
      </div>

      <PaginationBar
        :page="page"
        :page-size="pageSize"
        :total="total"
        :page-size-options="[6, 9, 12]"
        @update:page="page = $event"
        @update:page-size="setPageSize"
      />
    </section>
  </div>
</template>
