<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../../utils/request'

const router = useRouter()
const products = ref([])
const orders = ref([])
const loading = ref(false)
const errorMsg = ref('')

const onlineProducts = computed(() => products.value.filter((p) => p.status === 1).length)
const offProducts = computed(() => products.value.length - onlineProducts.value)
const lowStockProducts = computed(() => products.value.filter((p) => Number(p.stock) > 0 && Number(p.stock) < 20).length)
const soldOutProducts = computed(() => products.value.filter((p) => Number(p.stock) <= 0).length)

const paidOrders = computed(() => orders.value.filter((o) => o.status === 'PAID').length) // 待发货
const createdOrders = computed(() => orders.value.filter((o) => o.status === 'CREATED').length)
const shippedOrders = computed(() => orders.value.filter((o) => o.status === 'SHIPPED').length)
const completedOrders = computed(() => orders.value.filter((o) => o.status === 'COMPLETED').length)
const cancelledOrders = computed(() => orders.value.filter((o) => o.status === 'CANCELLED').length)

onMounted(async () => {
  loading.value = true
  errorMsg.value = ''
  const [pRes, oRes] = await Promise.all([api.adminGetProducts(), api.adminGetOrders('')])
  if (pRes.code === 200) products.value = pRes.data || []
  else errorMsg.value = pRes.message || '商品加载失败'
  if (oRes.code === 200) orders.value = oRes.data || []
  else errorMsg.value = errorMsg.value || oRes.message || '订单加载失败'
  loading.value = false
})
</script>

<template>
  <div class="admin-page">
    <div class="head">
      <h2>运营总览</h2>
      <p class="desc">集中查看库存与订单状态，快速进入运营操作入口。</p>
    </div>

    <div v-if="loading" class="panel">数据加载中...</div>
    <div v-else-if="errorMsg" class="panel error">{{ errorMsg }}</div>

    <template v-else>
      <div class="section">
        <div class="section-title">商品概览</div>
        <div class="stats">
          <div class="stat-item"><span>商品总数</span><strong>{{ products.length }}</strong></div>
          <div class="stat-item"><span>上架中</span><strong>{{ onlineProducts }}</strong></div>
          <div class="stat-item muted"><span>已下架</span><strong>{{ offProducts }}</strong></div>
          <div class="stat-item warn"><span>库存紧张</span><strong>{{ lowStockProducts }}</strong></div>
          <div class="stat-item muted"><span>售罄</span><strong>{{ soldOutProducts }}</strong></div>
        </div>
      </div>

      <div class="section">
        <div class="section-title">订单概览</div>
        <div class="stats">
          <div class="stat-item"><span>待支付</span><strong>{{ createdOrders }}</strong></div>
          <div class="stat-item dark"><span>待发货</span><strong>{{ paidOrders }}</strong></div>
          <div class="stat-item"><span>已发货</span><strong>{{ shippedOrders }}</strong></div>
          <div class="stat-item"><span>已完成</span><strong>{{ completedOrders }}</strong></div>
          <div class="stat-item muted"><span>已取消</span><strong>{{ cancelledOrders }}</strong></div>
        </div>
      </div>

      <div class="section">
        <div class="section-title">快捷入口</div>
        <div class="card-list">
          <div class="card" @click="router.push('/admin/products')">
            <h3>商品管理</h3>
            <p>上架/下架、价格与库存维护，快速处理低库存与售罄商品。</p>
          </div>
          <div class="card" @click="router.push('/admin/orders')">
            <h3>订单处理</h3>
            <p>查看订单并处理状态流转，覆盖发货/完成/取消。</p>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.admin-page { background: #f4f6f9; padding: 8px; }
.head { margin-bottom: 14px; }
h2 { font-size: 34px; color: #1a2740; margin-bottom: 8px; }
.desc { color: #68788d; font-size: 13px; }

.panel {
  background: #f9fbfe;
  border: 1px solid #dce4ef;
  border-radius: 2px;
  padding: 60px 40px;
  text-align: center;
  color: #6c7d93;
}
.panel.error { background: #fff1f1; border-color: #f0c1c1; color: #a73636; }

.section { margin: 12px 0 16px; }
.section-title { font-weight: 900; color: #0e1930; margin: 6px 0 12px; }

.stats { display: grid; grid-template-columns: repeat(5, 1fr); gap: 12px; }
.stat-item { padding: 16px; border-radius: 4px; background: #ffffff; border: 1px solid #dde5ef; min-height: 104px; display: flex; flex-direction: column; justify-content: space-between; }
.stat-item span { color: #6d7d91; font-size: 11px; letter-spacing: 0.6px; font-weight: 800; }
.stat-item strong { color: #0e1930; font-size: 40px; line-height: 1; }
.stat-item.dark { background: #08142a; border-color: #08142a; }
.stat-item.dark span, .stat-item.dark strong { color: #f2f6fc; }
.stat-item.muted { background: #f7f9fc; }
.stat-item.warn { background: #fff7e6; border-color: #ffd591; }
.card-list { display: grid; grid-template-columns: repeat(2, minmax(220px, 1fr)); gap: 14px; margin-top: 6px; }
.card { border: 1px solid #d8e1ed; border-radius: 4px; padding: 18px; cursor: pointer; background: #fff; }
.card:hover { border-color: #0c1e3d; box-shadow: 0 8px 18px rgba(14, 28, 46, 0.08); }
.card h3 { font-size: 20px; color: #13233b; margin-bottom: 6px; }
.card p { color: #657489; font-size: 13px; line-height: 1.6; }

@media (max-width: 980px) {
  .stats { grid-template-columns: repeat(2, 1fr); }
  .card-list { grid-template-columns: 1fr; }
}
</style>

