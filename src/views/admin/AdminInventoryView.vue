<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../../utils/request'
import { showAppMessage } from '../../utils/appMessage'
import PaginationBar from '../../components/PaginationBar.vue'
import ConfirmModal from '../../components/ConfirmModal.vue'

const route = useRoute()
const router = useRouter()

const products = ref([])
const loading = ref(false)
const errorMsg = ref('')
const sending = ref({})
const restockConfirmOpen = ref(false)
const restockPending = ref(null)

const tab = ref('low')
const page = ref(1)
const pageSize = ref(10)

const tabCounts = computed(() => {
  const list = products.value || []
  const low = list.filter((p) => {
    const s = Number(p.stock || 0)
    return s > 0 && s < 20
  }).length
  const sold = list.filter((p) => Number(p.stock || 0) <= 0).length
  return { low, sold, all: low + sold }
})

const filteredList = computed(() => {
  const list = products.value || []
  if (tab.value === 'low') {
    return list.filter((p) => {
      const s = Number(p.stock || 0)
      return s > 0 && s < 20
    })
  }
  if (tab.value === 'sold') {
    return list.filter((p) => Number(p.stock || 0) <= 0)
  }
  return list.filter((p) => Number(p.stock || 0) < 20)
})

const total = computed(() => filteredList.value.length)
const pagedList = computed(() => {
  const list = filteredList.value
  const p = Math.max(1, Number(page.value || 1))
  const ps = Math.max(1, Number(pageSize.value || 1))
  const start = (p - 1) * ps
  return list.slice(start, start + ps)
})

function applyTabFromRoute() {
  const t = String(route.query.tab || 'low').toLowerCase()
  tab.value = ['low', 'sold', 'all'].includes(t) ? t : 'low'
}

function setTab(next) {
  tab.value = next
  page.value = 1
  router.replace({ path: route.path, query: { tab: next } })
}

watch(
  () => route.query.tab,
  () => {
    applyTabFromRoute()
    page.value = 1
  },
)

watch(tab, () => {
  page.value = 1
})

function setPageSize(n) {
  pageSize.value = Number(n || 10)
  page.value = 1
}

async function loadProducts() {
  loading.value = true
  errorMsg.value = ''
  const res = await api.adminGetProducts()
  if (res.code === 200) {
    products.value = res.data || []
  } else {
    errorMsg.value = res.message || '加载失败'
  }
  loading.value = false
}

async function toggleStatus(item) {
  const next = item.status === 1 ? 0 : 1
  const res = await api.adminUpdateProduct(item.productId, { status: next })
  if (res.code === 200) {
    await loadProducts()
  } else {
    showAppMessage(res.message || '操作失败', '提示')
  }
}

function notifyRestock(item) {
  const pid = item.productId
  if (!pid || sending.value[pid]) return
  restockPending.value = item
  restockConfirmOpen.value = true
}

async function confirmNotifyRestock() {
  const item = restockPending.value
  if (!item?.productId) {
    restockConfirmOpen.value = false
    return
  }
  const pid = item.productId
  if (sending.value[pid]) return
  const stockNum = Number(item.stock || 0)
  const reason = stockNum <= 0 ? '售罄' : stockNum < 20 ? '库存紧张' : '常规提醒'
  sending.value = { ...(sending.value || {}), [pid]: true }
  const res = await api.adminNotifyRestock(pid, { reason })
  sending.value = { ...(sending.value || {}), [pid]: false }
  restockConfirmOpen.value = false
  restockPending.value = null
  if (res.code === 200) {
    showAppMessage('已发送补货提醒，商家可在通知中心查看', '发送成功')
  } else {
    showAppMessage(res.message || '发送失败', '提示')
  }
}

onMounted(async () => {
  applyTabFromRoute()
  await loadProducts()
})
</script>

<template>
  <div class="admin-page">
    <h2>库存监管</h2>
    <p class="desc">集中查看库存紧张与售罄商品，可发送补货提醒或调整上架状态；商品标价与库存由各店铺自行维护。</p>

    <div class="tabs">
      <button type="button" class="tab" :class="{ active: tab === 'low' }" @click="setTab('low')">
        库存紧张（1–19）<span class="tab-num">{{ tabCounts.low }}</span>
      </button>
      <button type="button" class="tab" :class="{ active: tab === 'sold' }" @click="setTab('sold')">
        售罄<span class="tab-num">{{ tabCounts.sold }}</span>
      </button>
      <button type="button" class="tab" :class="{ active: tab === 'all' }" @click="setTab('all')">
        全部异常<span class="tab-num">{{ tabCounts.all }}</span>
      </button>
    </div>

    <div class="toolbar">
      <button type="button" class="tool-btn" @click="loadProducts">刷新</button>
      <RouterLink class="tool-btn link" to="/admin/products">前往商品管理</RouterLink>
    </div>

    <div v-if="loading">加载中...</div>
    <div v-else-if="errorMsg">{{ errorMsg }}</div>
    <div v-else-if="filteredList.length === 0" class="empty-panel">当前分类下暂无商品</div>
    <template v-else>
      <table class="table">
        <thead>
          <tr>
            <th>ID</th>
            <th>名称</th>
            <th>商家</th>
            <th>库存</th>
            <th>上架</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in pagedList" :key="item.productId">
            <td>{{ item.productId }}</td>
            <td>{{ item.title }}</td>
            <td class="muted">
              <span class="shop">{{ String(item.merchantShopName || '').trim() || '—' }}</span>
              <span class="mid">（ID: {{ Number(item.merchantId || 0) || '—' }}）</span>
            </td>
            <td>
              <span class="stock">{{ item.stock }}</span>
              <span v-if="Number(item.stock) <= 0" class="tag soldout">售罄</span>
              <span v-else-if="Number(item.stock) < 20" class="tag low">紧张</span>
            </td>
            <td><span :class="['pill', item.status === 1 ? 'on' : 'off']">{{ item.status === 1 ? '上架中' : '已下架' }}</span></td>
            <td>
              <div class="btn-group">
                <button type="button" class="action-btn primary" @click="toggleStatus(item)">
                  {{ item.status === 1 ? '下架' : '上架' }}
                </button>
                <button
                  type="button"
                  class="action-btn"
                  :class="{ warn: Number(item.stock) < 20 }"
                  :disabled="sending[item.productId]"
                  @click="notifyRestock(item)"
                >
                  {{ sending[item.productId] ? '发送中...' : '补货提醒' }}
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>

      <PaginationBar
        :page="page"
        :page-size="pageSize"
        :total="total"
        :page-size-options="[8, 10, 20, 50]"
        @update:page="page = $event"
        @update:page-size="setPageSize"
      />
    </template>

    <ConfirmModal
      :open="restockConfirmOpen"
      title="发送补货提醒"
      confirm-label="确定发送"
      @update:open="restockConfirmOpen = $event"
      @confirm="confirmNotifyRestock"
    >
      <template v-if="restockPending">
        <p>将向商家发送一条补货提醒通知。</p>
        <p><strong>商品：</strong>{{ restockPending.title }}</p>
        <p>
          <strong>原因：</strong>
          {{
            Number(restockPending.stock || 0) <= 0
              ? '售罄'
              : Number(restockPending.stock || 0) < 20
                ? '库存紧张'
                : '常规提醒'
          }}
        </p>
        <p><strong>当前库存：</strong>{{ restockPending.stock }}</p>
      </template>
    </ConfirmModal>
  </div>
</template>

<style scoped>
.admin-page { background: #f4f6f9; padding: 8px; }
h2 { font-size: 34px; color: #1a2740; margin-bottom: 8px; }
.desc { color: #68788d; font-size: 13px; margin: 0 0 14px; }

.tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}
.tab {
  height: 36px;
  padding: 0 14px;
  border: 1px solid #c9d4e4;
  border-radius: 2px;
  background: #fff;
  color: #23344f;
  font-size: 12px;
  font-weight: 800;
  cursor: pointer;
}
.tab.active {
  border-color: #0b1630;
  background: #0b1630;
  color: #f4f6fb;
}
.tab-num {
  margin-left: 6px;
  opacity: 0.85;
  font-weight: 900;
}

.toolbar { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 12px; }
.tool-btn {
  height: 32px;
  padding: 0 12px;
  border: 1px solid #c9d4e4;
  background: #fff;
  color: #23344f;
  border-radius: 2px;
  cursor: pointer;
  font-size: 12px;
  font-weight: 800;
  text-decoration: none;
  display: inline-flex;
  align-items: center;
}
.tool-btn.link:hover { border-color: #0b1630; }

.table { width: 100%; border-collapse: collapse; background: #fff; border: 1px solid #dbe3ed; }
.table th, .table td { border-bottom: 1px solid #ecf0f5; padding: 12px 10px; font-size: 13px; color: #26354b; }
.table th { background: #f1f4f8; text-align: left; font-size: 11px; color: #5f6d80; letter-spacing: 0.6px; }

.muted { color: #6d7d91; }
.shop { font-weight: 800; color: #23344f; }
.mid { font-size: 12px; color: #7b8798; }
.stock { font-weight: 800; color: #0e1930; }
.tag {
  display: inline-block;
  margin-left: 8px;
  padding: 2px 8px;
  border-radius: 2px;
  font-size: 11px;
  font-weight: 800;
}
.tag.low { border: 1px solid #ffd591; background: #fff7e6; color: #ad6800; }
.tag.soldout { border: 1px solid #c9d4e4; background: #eef2f7; color: #304862; }

.pill { display: inline-block; padding: 2px 8px; border-radius: 2px; font-size: 11px; font-weight: 700; }
.pill.on { background: #d9f4df; color: #166b2d; }
.pill.off { background: #ffe2e2; color: #8a1d1d; }

.btn-group { display: flex; gap: 8px; flex-wrap: wrap; }
.action-btn { height: 30px; padding: 0 10px; border: 1px solid #c9d4e4; background: #f8fbff; color: #23344f; border-radius: 2px; cursor: pointer; font-size: 12px; font-weight: 600; }
.action-btn.primary { border-color: #0b1630; background: #0b1630; color: #f4f6fb; }
.action-btn.warn { border-color: #ffd591; background: #fff7e6; color: #ad6800; }
.action-btn:disabled { opacity: 0.6; cursor: not-allowed; }

.empty-panel {
  background: #f9fbfe;
  border: 1px solid #dbe3ed;
  border-radius: 4px;
  padding: 40px 20px;
  text-align: center;
  color: #6c7d93;
  font-weight: 700;
}
</style>
