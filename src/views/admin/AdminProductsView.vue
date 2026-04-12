<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { api } from '../../utils/request'
import { showAppMessage } from '../../utils/appMessage'
import PaginationBar from '../../components/PaginationBar.vue'
import ConfirmModal from '../../components/ConfirmModal.vue'

const products = ref([])
const loading = ref(false)
const errorMsg = ref('')
const sending = ref({})
const bulkSending = ref(false)
const restockConfirmOpen = ref(false)
/** 待确认发送补货提醒的商品行 */
const restockPending = ref(null)
const bulkConfirmOpen = ref(false)

const route = useRoute()
/** 来自 URL ?shelf=online | offline，与商家筛选叠加 */
const shelfFilter = ref('')

function syncShelfFromQuery(query) {
  const v = String(query?.shelf || '').toLowerCase()
  shelfFilter.value = v === 'online' || v === 'offline' ? v : ''
}

watch(
  () => route.query.shelf,
  () => {
    syncShelfFromQuery(route.query)
    page.value = 1
  },
)

const lowStockCount = computed(() => (products.value || []).filter((p) => Number(p.stock || 0) < 20).length)
const selectedMerchantId = ref('ALL') // 'ALL' | number-string
const keyword = ref('')
const groupByMerchant = ref(true)
const page = ref(1)
const pageSize = ref(10)
/** 按商家分组时，每个店铺表格内约可见行数（超出则在块内滚动） */
const MERCHANT_GROUP_VISIBLE_ROWS = 5
/** 按商家分组时，底部分页按「店铺」计数：每页展示的店铺数 */
const SHOPS_PER_PAGE = 2

const stats = computed(() => {
  const list = products.value || []
  const online = list.filter((p) => Number(p.status) === 1).length
  const off = list.length - online
  const lowStock = list.filter((p) => Number(p.stock) > 0 && Number(p.stock) < 20).length
  const soldOut = list.filter((p) => Number(p.stock) <= 0).length
  return { total: list.length, online, off, lowStock, soldOut }
})

const merchantOptions = computed(() => {
  const list = Array.isArray(products.value) ? products.value : []
  const map = new Map()
  for (const p of list) {
    const mid = Number(p?.merchantId || 0)
    if (!mid) continue
    const name = String(p?.merchantShopName || '').trim()
    if (!map.has(mid)) map.set(mid, name || `商家${mid}`)
  }
  return Array.from(map.entries())
    .map(([merchantId, shopName]) => ({ merchantId, shopName }))
    .sort((a, b) => a.merchantId - b.merchantId)
})

function normalize(value) {
  return String(value || '').trim().toLowerCase()
}

const filteredProducts = computed(() => {
  const list = Array.isArray(products.value) ? products.value : []
  const kw = normalize(keyword.value)
  const midSel = selectedMerchantId.value === 'ALL' ? null : Number(selectedMerchantId.value || 0)
  return list.filter((p) => {
    if (midSel && Number(p?.merchantId || 0) !== midSel) return false
    if (shelfFilter.value === 'online' && Number(p?.status) !== 1) return false
    if (shelfFilter.value === 'offline' && Number(p?.status) !== 0) return false
    if (!kw) return true
    const title = normalize(p?.title)
    const pid = String(p?.productId ?? '')
    const shopName = normalize(p?.merchantShopName)
    const mId = String(p?.merchantId ?? '')
    return title.includes(kw) || pid.includes(kw) || shopName.includes(kw) || mId.includes(kw)
  })
})

watch([selectedMerchantId, keyword, groupByMerchant], () => {
  page.value = 1
})

const merchantGroupsAll = computed(() => {
  const list = Array.isArray(filteredProducts.value) ? filteredProducts.value : []
  const map = new Map()
  for (const p of list) {
    const mid = Number(p?.merchantId || 0) || 0
    const key = mid || -1
    if (!map.has(key)) map.set(key, [])
    map.get(key).push(p)
  }
  const keys = Array.from(map.keys()).sort((a, b) => {
    if (a === -1) return 1
    if (b === -1) return -1
    return a - b
  })
  return keys.map((k) => {
    const items = map.get(k) || []
    const sample = items[0] || {}
    const merchantId = k === -1 ? 0 : Number(k)
    const shopName = String(sample?.merchantShopName || '').trim() || (merchantId ? `商家${merchantId}` : '未绑定商家')
    return { merchantId, shopName, items }
  })
})

const total = computed(() => (Array.isArray(filteredProducts.value) ? filteredProducts.value.length : 0))
const pagedFilteredProducts = computed(() => {
  const list = Array.isArray(filteredProducts.value) ? filteredProducts.value : []
  const p = Math.max(1, Number(page.value || 1))
  const ps = Math.max(1, Number(pageSize.value || 1))
  const start = (p - 1) * ps
  return list.slice(start, start + ps)
})

/** 全部商家 + 分组：底部分页按「店铺」；其它情况按「商品」 */
const useShopLevelPagination = computed(
  () => groupByMerchant.value && selectedMerchantId.value === 'ALL',
)

watch([merchantGroupsAll, groupByMerchant, selectedMerchantId], () => {
  if (!groupByMerchant.value || selectedMerchantId.value !== 'ALL') return
  const totalShops = merchantGroupsAll.value.length
  const tp = Math.max(1, Math.ceil(totalShops / SHOPS_PER_PAGE))
  if (page.value > tp) page.value = tp
})

watch([total, pageSize, groupByMerchant, selectedMerchantId], () => {
  if (useShopLevelPagination.value) return
  const tp = Math.max(1, Math.ceil(Number(total.value || 0) / Math.max(1, Number(pageSize.value || 1))))
  if (page.value > tp) page.value = tp
})

function setPageSize(n) {
  pageSize.value = Number(n || 10)
  page.value = 1
}

const groupedProducts = computed(() => {
  if (!groupByMerchant.value) return []
  const all = merchantGroupsAll.value
  if (selectedMerchantId.value !== 'ALL') {
    const g = all[0]
    if (!g) return []
    return [
      {
        merchantId: g.merchantId,
        shopName: g.shopName,
        items: pagedFilteredProducts.value,
        itemTotal: g.items.length,
      },
    ]
  }
  const p = Math.max(1, Number(page.value || 1))
  const start = (p - 1) * SHOPS_PER_PAGE
  return all.slice(start, start + SHOPS_PER_PAGE).map((row) => ({ ...row, itemTotal: row.items.length }))
})

const groupPaginationTotal = computed(() => merchantGroupsAll.value.length)

async function loadProducts() {
  loading.value = true
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

function merchantText(item) {
  const name = String(item?.merchantShopName || '').trim()
  const mid = Number(item?.merchantId || 0)
  return name || (mid ? `商家${mid}` : '-')
}

function notifyRestock(item) {
  const pid = item.productId
  if (!pid) return
  if (sending.value[pid]) return
  restockPending.value = item
  restockConfirmOpen.value = true
}

/**
 * 确认后向商家发送单条补货提醒。
 */
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

function notifyLowStockBatch() {
  if (bulkSending.value) return
  if (!lowStockCount.value) {
    showAppMessage('当前没有低库存/售罄商品（库存 < 20）', '提示')
    return
  }
  bulkConfirmOpen.value = true
}

/**
 * 确认后批量发送低库存补货提醒。
 */
async function confirmBulkNotify() {
  if (bulkSending.value) return
  const targets = (products.value || []).filter((p) => Number(p.stock || 0) < 20)
  if (!targets.length) {
    bulkConfirmOpen.value = false
    return
  }
  bulkConfirmOpen.value = false
  bulkSending.value = true
  let okCount = 0
  for (const item of targets) {
    const pid = item.productId
    if (!pid) continue
    sending.value = { ...(sending.value || {}), [pid]: true }
    const stockNum = Number(item.stock || 0)
    const reason = stockNum <= 0 ? '售罄' : '库存紧张'
    const res = await api.adminNotifyRestock(pid, { reason })
    sending.value = { ...(sending.value || {}), [pid]: false }
    if (res.code === 200) okCount += 1
  }
  bulkSending.value = false
  showAppMessage(`已发送 ${okCount}/${targets.length} 条补货提醒`, '批量提醒')
}

onMounted(async () => {
  syncShelfFromQuery(route.query)
  await loadProducts()
})
</script>

<template>
  <div class="admin-page">
    <h2>商品管理（平台监管）</h2>
    <p class="desc">运营人员可浏览全站商品，必要时执行下架或恢复上架；商品标价与库存以各店铺维护为准。</p>
    <p v-if="shelfFilter" class="shelf-banner">
      当前仅显示<strong>{{ shelfFilter === 'online' ? '上架中' : '已下架' }}</strong>商品。
      <RouterLink class="shelf-banner-link" to="/admin/products">查看全部商品</RouterLink>
    </p>

    <div class="stats">
      <div class="stat"><span>商品总数</span><strong>{{ stats.total }}</strong></div>
      <div class="stat"><span>上架中</span><strong>{{ stats.online }}</strong></div>
      <div class="stat muted"><span>已下架</span><strong>{{ stats.off }}</strong></div>
      <div class="stat warn"><span>库存紧张</span><strong>{{ stats.lowStock }}</strong></div>
      <div class="stat muted"><span>售罄</span><strong>{{ stats.soldOut }}</strong></div>
    </div>

    <div class="toolbar">
      <button class="tool-btn warn" :disabled="bulkSending" @click="notifyLowStockBatch">
        {{ bulkSending ? '发送中...' : '一键提醒低库存' }}
      </button>
      <button class="tool-btn" @click="loadProducts">刷新</button>
    </div>

    <div class="filters">
      <div class="filter-item">
        <label>归属商家</label>
        <select v-model="selectedMerchantId" class="filter-select">
          <option value="ALL">全部商家</option>
          <option v-for="m in merchantOptions" :key="m.merchantId" :value="String(m.merchantId)">
            {{ m.shopName }}（ID: {{ m.merchantId }}）
          </option>
        </select>
      </div>
      <div class="filter-item">
        <label>关键词</label>
        <input
          v-model="keyword"
          class="filter-input"
          type="text"
          placeholder="商品名 / 商品ID / 商家ID / 店铺名"
        />
      </div>
      <label class="filter-check">
        <input v-model="groupByMerchant" type="checkbox" />
        按商家分组展示
      </label>
      <div class="filter-right">
        <span class="filter-hint">
          当前 {{ filteredProducts.length }} 件
          <template v-if="groupByMerchant && selectedMerchantId === 'ALL'">
            · 每页 {{ SHOPS_PER_PAGE }} 家店铺 · 各店铺表格内独立滚动（约 {{ MERCHANT_GROUP_VISIBLE_ROWS }} 行高）
          </template>
        </span>
      </div>
    </div>

    <div v-if="loading">加载中...</div>
    <div v-else-if="errorMsg">{{ errorMsg }}</div>
    <div v-else-if="products.length === 0">暂无商品</div>
    <template v-else>
      <template v-if="groupByMerchant">
        <div v-if="groupedProducts.length === 0" class="empty-panel">暂无匹配商品</div>
        <div v-else class="groups">
          <div v-for="g in groupedProducts" :key="g.merchantId || 'none'" class="group">
            <div class="group-head">
              <div class="group-title">
                <strong class="group-name">{{ g.shopName }}</strong>
                <span class="group-meta">（ID: {{ g.merchantId || '—' }}）</span>
                <span class="group-count">{{ g.itemTotal ?? g.items.length }} 件</span>
              </div>
            </div>
            <div
              :class="selectedMerchantId === 'ALL' ? 'group-table-scroll' : 'group-table-wrap'"
              :style="
                selectedMerchantId === 'ALL'
                  ? { '--merchant-visible-rows': MERCHANT_GROUP_VISIBLE_ROWS }
                  : undefined
              "
            >
              <table class="table">
                <thead>
                  <tr>
                    <th>ID</th>
                    <th>名称</th>
                    <th>价格</th>
                    <th>库存</th>
                    <th>状态</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="item in g.items" :key="item.productId">
                    <td>{{ item.productId }}</td>
                    <td>{{ item.title }}</td>
                    <td>¥{{ Number(item.price).toFixed(2) }}</td>
                    <td>
                      <div class="stock-cell">
                        <span class="stock-num">{{ item.stock }}</span>
                        <span v-if="Number(item.stock) <= 0" class="stock-tag soldout">售罄</span>
                        <span v-else-if="Number(item.stock) < 20" class="stock-tag low">库存紧张</span>
                      </div>
                    </td>
                    <td><span :class="['status', item.status === 1 ? 'ok' : 'off']">{{ item.status === 1 ? '上架中' : '已下架' }}</span></td>
                    <td>
                      <div class="btn-group">
                        <button class="action-btn primary" @click="toggleStatus(item)">
                          {{ item.status === 1 ? '下架' : '上架' }}
                        </button>
                        <button
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
            </div>
          </div>
        </div>
      </template>

      <table v-else class="table">
      <thead>
        <tr>
          <th>ID</th>
          <th>名称</th>
          <th>所属商家</th>
          <th>价格</th>
          <th>库存</th>
          <th>状态</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="item in pagedFilteredProducts" :key="item.productId">
          <td>{{ item.productId }}</td>
          <td>{{ item.title }}</td>
          <td class="muted">
            <span class="merchant-name">{{ merchantText(item) }}</span>
            <span class="merchant-id">（ID: {{ Number(item.merchantId || 0) || '—' }}）</span>
          </td>
          <td>¥{{ Number(item.price).toFixed(2) }}</td>
          <td>
            <div class="stock-cell">
              <span class="stock-num">{{ item.stock }}</span>
              <span v-if="Number(item.stock) <= 0" class="stock-tag soldout">售罄</span>
              <span v-else-if="Number(item.stock) < 20" class="stock-tag low">库存紧张</span>
            </div>
          </td>
          <td><span :class="['status', item.status === 1 ? 'ok' : 'off']">{{ item.status === 1 ? '上架中' : '已下架' }}</span></td>
          <td>
            <div class="btn-group">
              <button class="action-btn primary" @click="toggleStatus(item)">
                {{ item.status === 1 ? '下架' : '上架' }}
              </button>
              <button
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
        v-if="useShopLevelPagination"
        :page="page"
        :page-size="SHOPS_PER_PAGE"
        :total="groupPaginationTotal"
        :page-size-options="[]"
        @update:page="page = $event"
        @update:page-size="() => {}"
      />
      <PaginationBar
        v-else
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

    <ConfirmModal
      :open="bulkConfirmOpen"
      title="一键提醒低库存"
      confirm-label="确定发送"
      @update:open="bulkConfirmOpen = $event"
      @confirm="confirmBulkNotify"
    >
      <p>将向各商品对应商家发送补货提醒，共 {{ lowStockCount }} 条。确定继续吗？</p>
    </ConfirmModal>
  </div>
</template>

<style scoped>
.admin-page { background: #f4f6f9; padding: 8px; }
h2 { font-size: 34px; color: #1a2740; }
.desc { color: #68788d; font-size: 13px; margin: 8px 0 14px; }

.shelf-banner {
  margin: 0 0 12px;
  padding: 10px 12px;
  border-radius: 4px;
  border: 1px solid #c9d4e4;
  background: #f0f4fa;
  color: #304862;
  font-size: 13px;
  font-weight: 700;
}
.shelf-banner-link {
  margin-left: 10px;
  color: #0b1630;
  font-weight: 900;
}

.stats {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 12px;
  margin: 10px 0 14px;
}
.stat {
  padding: 14px;
  border-radius: 4px;
  background: #ffffff;
  border: 1px solid #dde5ef;
  min-height: 86px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}
.stat span { color: #6d7d91; font-size: 11px; letter-spacing: 0.6px; font-weight: 800; }
.stat strong { color: #0e1930; font-size: 34px; line-height: 1; }
.stat.muted { background: #f7f9fc; }
.stat.warn { background: #fff7e6; border-color: #ffd591; }

.toolbar { display: flex; gap: 8px; flex-wrap: wrap; margin: 0 0 12px; }
.tool-btn { height: 32px; padding: 0 12px; border: 1px solid #c9d4e4; background: #fff; color: #23344f; border-radius: 2px; cursor: pointer; font-size: 12px; font-weight: 800; }
.tool-btn.warn { border-color: #ffd591; background: #fff7e6; color: #ad6800; }
.tool-btn:disabled { opacity: 0.6; cursor: not-allowed; }

.filters {
  display: grid;
  grid-template-columns: 220px 1fr auto;
  gap: 12px;
  align-items: end;
  margin: 0 0 12px;
  background: #f9fbfe;
  border: 1px solid #dbe3ed;
  border-radius: 4px;
  padding: 12px;
}
.filter-item { display: flex; flex-direction: column; gap: 6px; min-width: 0; }
.filter-item label { font-size: 11px; font-weight: 800; color: #6d7d91; letter-spacing: 0.5px; }
.filter-select, .filter-input {
  height: 32px;
  border: 1px solid #c9d4e4;
  border-radius: 2px;
  background: #fff;
  color: #23344f;
  font-size: 12px;
  padding: 0 10px;
  outline: none;
}
.filter-input { width: 100%; }
.filter-select:focus, .filter-input:focus { border-color: #0b1630; }
.filter-check { display: inline-flex; gap: 8px; align-items: center; font-size: 12px; color: #506078; font-weight: 700; user-select: none; }
.filter-check input { width: 14px; height: 14px; accent-color: #0b1630; }
.filter-right { display: flex; justify-content: flex-end; }
.filter-hint { font-size: 12px; color: #6c7d93; font-weight: 700; }

.groups { display: flex; flex-direction: column; gap: 12px; }
.group { border: 1px solid #dbe3ed; border-radius: 4px; overflow: hidden; background: #fff; }
.group-head {
  padding: 12px 12px;
  background: #f1f4f8;
  border-bottom: 1px solid #ecf0f5;
}
.group-title { display: flex; align-items: baseline; gap: 10px; flex-wrap: wrap; }
.group-name { color: #0e1930; font-size: 13px; font-weight: 900; }
.group-meta { color: #6c7d93; font-size: 12px; font-weight: 700; }
.group-count {
  margin-left: auto;
  color: #23344f;
  font-size: 12px;
  font-weight: 900;
  padding: 2px 8px;
  border-radius: 999px;
  border: 1px solid #dbe3ee;
  background: #fcfdff;
}

.group-table-scroll {
  --merchant-row-h: 44px;
  --merchant-thead-h: 40px;
  max-height: calc(var(--merchant-thead-h) + var(--merchant-visible-rows, 5) * var(--merchant-row-h));
  overflow-y: auto;
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
  border-top: 1px solid #ecf0f5;
}
.group-table-scroll .table { border-top: none; border-left: none; border-right: none; border-bottom: none; }
.group-table-scroll .table thead th {
  position: sticky;
  top: 0;
  z-index: 1;
  box-shadow: 0 1px 0 #ecf0f5;
}

.group-table-wrap {
  border-top: 1px solid #ecf0f5;
}
.group-table-wrap .table { border-top: none; border-left: none; border-right: none; border-bottom: none; }

.table { width: 100%; border-collapse: collapse; background: #fff; border: 1px solid #dbe3ed; }
.table th, .table td { border-bottom: 1px solid #ecf0f5; padding: 12px 10px; font-size: 13px; color: #26354b; }
.table th { background: #f1f4f8; text-align: left; font-size: 11px; color: #5f6d80; letter-spacing: 0.6px; }
.status { display: inline-block; padding: 2px 8px; border-radius: 2px; font-size: 11px; font-weight: 700; }
.status.ok { background: #d9f4df; color: #166b2d; }
.status.off { background: #ffe2e2; color: #8a1d1d; }
.action-btn { height: 30px; padding: 0 10px; border: 1px solid #c9d4e4; background: #f8fbff; color: #23344f; border-radius: 2px; cursor: pointer; font-size: 12px; font-weight: 600; }
.action-btn.primary { border-color: #0b1630; background: #0b1630; color: #f4f6fb; }
.action-btn.warn { border-color: #ffd591; background: #fff7e6; color: #ad6800; }
.action-btn:disabled { opacity: 0.6; cursor: not-allowed; }
.btn-group { display: flex; gap: 8px; flex-wrap: wrap; }

.stock-cell { display: flex; align-items: center; gap: 8px; }
.stock-num { font-weight: 800; color: #0e1930; }
.muted { color: #6d7d91; }
.merchant-name { font-size: 12px; color: #23344f; font-weight: 800; }
.merchant-id { font-size: 12px; color: #7b8798; font-weight: 700; }
.stock-tag {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 2px;
  font-size: 11px;
  font-weight: 800;
  border: 1px solid #d9e1ec;
  background: #f7f9fc;
  color: #5e6e84;
}
.stock-tag.low { border-color: #ffd591; background: #fff7e6; color: #ad6800; }
.stock-tag.soldout { border-color: #c9d4e4; background: #eef2f7; color: #304862; }

.empty-panel {
  background: #f9fbfe;
  border: 1px solid #dbe3ed;
  border-radius: 4px;
  padding: 50px 20px;
  text-align: center;
  color: #6c7d93;
  font-weight: 700;
}

@media (max-width: 980px) {
  .stats { grid-template-columns: repeat(2, 1fr); }
  .filters { grid-template-columns: 1fr; }
  .filter-right { justify-content: flex-start; }
  .group-count { margin-left: 0; }
}
</style>

