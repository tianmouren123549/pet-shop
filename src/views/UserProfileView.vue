<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../utils/request'
import { showAppMessage } from '../utils/appMessage'
import { formatYuan } from '../utils/formatYuan.js'
import AppImage from '../components/AppImage.vue'

const router = useRouter()
const pageLoading = ref(true)
const saving = ref(false)
const errorMsg = ref('')
const form = ref({
  nickname: '',
  phone: '',
  email: '',
  avatarUrl: '',
})
const membershipTier = ref('NORMAL')
const plusExpiresAt = ref('')
const frequentProducts = ref([])
const addresses = ref([])
const addressFormOpen = ref(false)
const editingAddressId = ref(null)
const addressForm = ref({
  label: '',
  receiverName: '',
  receiverPhone: '',
  receiverRegion: '',
  receiverDetail: '',
  isDefault: 0,
})

const membershipLabel = computed(() => {
  if (membershipTier.value === 'PLUS') return 'PLUS 会员'
  return '普通会员'
})

function formatPlusExpiry(iso) {
  const s = String(iso || '').trim()
  if (!s) return ''
  const d = new Date(s)
  if (Number.isNaN(d.getTime())) return s
  return d.toLocaleString()
}

const ordersBrief = ref([])
/** 物流摘要展示：空串表示无；SHIP_NO 表示已发货暂无单号 */
const logisticsLine = ref('')

function userId() {
  return Number(localStorage.getItem('userId') || 0)
}

function persistHeaderAvatar(avatarUrl) {
  localStorage.setItem('userAvatarUrl', String(avatarUrl || '').trim())
  window.dispatchEvent(new CustomEvent('petshop-user-avatar-updated'))
}

async function loadProfile() {
  errorMsg.value = ''
  const uid = userId()
  if (!uid) {
    errorMsg.value = '请先登录'
    return
  }
  const res = await api.userGetProfile(uid)
  if (res.code !== 200) {
    errorMsg.value = res.message || '加载失败'
    return
  }
  form.value = {
    nickname: String(res.data?.nickname || ''),
    phone: String(res.data?.phone || ''),
    email: String(res.data?.email || ''),
    avatarUrl: String(res.data?.avatarUrl || ''),
  }
  membershipTier.value = String(res.data?.membershipTier || 'NORMAL').toUpperCase()
  plusExpiresAt.value = String(res.data?.plusExpiresAt || '')
  persistHeaderAvatar(form.value.avatarUrl)
}

async function loadFrequent() {
  const uid = userId()
  if (!uid) return
  const res = await api.userGetFrequentProducts(uid, 12)
  if (res.code !== 200) return
  frequentProducts.value = Array.isArray(res.data) ? res.data : []
}

async function loadAddresses() {
  const uid = userId()
  if (!uid) return
  const res = await api.userListAddresses(uid)
  if (res.code !== 200) return
  addresses.value = Array.isArray(res.data) ? res.data : []
}

function resetAddressForm() {
  editingAddressId.value = null
  addressForm.value = {
    label: '',
    receiverName: '',
    receiverPhone: '',
    receiverRegion: '',
    receiverDetail: '',
    isDefault: 0,
  }
}

function openAddAddress() {
  resetAddressForm()
  addressFormOpen.value = true
}

function editAddress(row) {
  editingAddressId.value = row.addressId
  addressForm.value = {
    label: row.label || '',
    receiverName: row.receiverName || '',
    receiverPhone: row.receiverPhone || '',
    receiverRegion: row.receiverRegion || '',
    receiverDetail: row.receiverDetail || '',
    isDefault: row.isDefault === 1 ? 1 : 0,
  }
  addressFormOpen.value = true
}

async function saveAddress() {
  const uid = userId()
  if (!uid) return
  const body = { ...addressForm.value }
  let res
  if (editingAddressId.value) {
    res = await api.userUpdateAddress(uid, editingAddressId.value, body)
  } else {
    res = await api.userCreateAddress(uid, body)
  }
  if (res.code !== 200) {
    showAppMessage(res.message || '保存失败', '提示')
    return
  }
  showAppMessage('已保存', '提示')
  addressFormOpen.value = false
  resetAddressForm()
  await loadAddresses()
}

async function removeAddress(addressId) {
  const uid = userId()
  if (!uid) return
  const res = await api.userDeleteAddress(uid, addressId)
  if (res.code !== 200) {
    showAppMessage(res.message || '删除失败', '提示')
    return
  }
  showAppMessage('已删除', '提示')
  await loadAddresses()
}

async function makeDefaultAddress(addressId) {
  const uid = userId()
  if (!uid) return
  const res = await api.userSetDefaultAddress(uid, addressId)
  if (res.code !== 200) {
    showAppMessage(res.message || '操作失败', '提示')
    return
  }
  await loadAddresses()
}

function cancelAddressForm() {
  addressFormOpen.value = false
  resetAddressForm()
}

async function loadOrdersBrief() {
  const uid = userId()
  if (!uid) return
  const res = await api.userGetOrders(uid)
  if (res.code !== 200) return
  ordersBrief.value = Array.isArray(res.data) ? res.data : []
  logisticsLine.value = ''
  const shipped = ordersBrief.value
    .filter((o) => o.status === 'SHIPPED')
    .sort((a, b) => new Date(b.updatedAt || 0).getTime() - new Date(a.updatedAt || 0).getTime())
  if (!shipped.length) return
  const det = await api.userGetOrder(shipped[0].orderId, uid)
  if (det.code !== 200) return
  const no = String(det.data?.logisticsNo || '').trim()
  logisticsLine.value = no ? `运单号 ${no}` : 'SHIP_NO'
}

function onAvatarChange(e) {
  const file = e.target.files?.[0]
  if (!file) return
  const reader = new FileReader()
  reader.onload = () => {
    form.value.avatarUrl = String(reader.result || '')
  }
  reader.readAsDataURL(file)
}

async function saveProfile() {
  const uid = userId()
  if (!uid) {
    errorMsg.value = '请先登录'
    return
  }
  saving.value = true
  errorMsg.value = ''
  const res = await api.userUpdateProfile(uid, form.value)
  saving.value = false
  if (res.code !== 200) {
    errorMsg.value = res.message || '保存失败'
    return
  }
  localStorage.setItem('nickname', form.value.nickname)
  persistHeaderAvatar(form.value.avatarUrl)
  showAppMessage('保存成功', '提示')
}

function goOrdersTab(tabKey) {
  router.push({ path: '/orders', query: { tab: tabKey } })
}

function goAfterSale() {
  router.push('/merchant-contact')
}

function scrollToAddresses() {
  document.getElementById('profile-addresses')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function scrollToAccount() {
  document.getElementById('profile-account')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

async function refreshAll() {
  pageLoading.value = true
  errorMsg.value = ''
  await loadProfile()
  ordersBrief.value = []
  logisticsLine.value = ''
  frequentProducts.value = []
  addresses.value = []
  if (userId() && !errorMsg.value) {
    await loadOrdersBrief()
    await loadFrequent()
    await loadAddresses()
  }
  pageLoading.value = false
}

onMounted(refreshAll)
</script>

<template>
  <div class="pw-page profile-page profile-page--apex">
    <div class="pf-canvas">
      <div v-if="pageLoading" class="pf-state pf-state--muted">加载中…</div>

      <template v-else-if="errorMsg">
        <div class="pf-state pf-state--error">
          <p class="pf-state-msg">{{ errorMsg }}</p>
          <button
            v-if="errorMsg === '请先登录'"
            type="button"
            class="pf-btn-primary pf-btn-primary--sm"
            @click="router.push('/login')"
          >
            去登录
          </button>
          <button v-else type="button" class="pf-btn-primary pf-btn-primary--sm" @click="refreshAll">重试</button>
        </div>
      </template>

      <section v-else class="layout-shell">
        <aside class="layout-sidebar">
          <div class="sidebar-head">
            <h2 class="sidebar-title">个人中心</h2>
            <p class="sidebar-lead">头像与会员信息；右侧为订单与资料，宽屏下一目了然。</p>
          </div>

          <div class="pf-side-profile">
            <label class="pf-avatar-hit">
              <img
                v-if="form.avatarUrl"
                :src="form.avatarUrl"
                class="pf-avatar-lg"
                alt=""
                loading="lazy"
                decoding="async"
              />
              <div v-else class="pf-avatar-lg pf-avatar-lg--empty">头像</div>
              <input type="file" accept="image/*" class="pf-file-input" @change="onAvatarChange" />
            </label>
            <p class="pf-side-display-name">{{ form.nickname || '未设置昵称' }}</p>
            <span class="pf-hero-badge" :class="{ 'pf-hero-badge--plus': membershipTier === 'PLUS' }">{{
              membershipLabel
            }}</span>
            <p v-if="membershipTier === 'PLUS' && plusExpiresAt" class="pf-plus-expiry">
              PLUS 有效期至 {{ formatPlusExpiry(plusExpiresAt) }}
            </p>

            <div class="pf-side-meta">
              <button type="button" class="pf-meta-link" @click="router.push('/notifications')">
                <span class="pf-meta-ico" aria-hidden="true">
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M18 8a6 6 0 10-12 0c0 7-3 9-3 9h18s-3-2-3-9" />
                    <path d="M13.73 21a2 2 0 01-3.46 0" />
                  </svg>
                </span>
                消息通知
              </button>
              <button type="button" class="pf-meta-link" @click="scrollToAddresses">
                <span class="pf-meta-ico" aria-hidden="true">
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0118 0z" />
                    <circle cx="12" cy="10" r="3" />
                  </svg>
                </span>
                收货地址
                <span v-if="addresses.length" class="pf-meta-count">{{ addresses.length }}</span>
              </button>
            </div>

            <div class="pf-side-tools">
              <button type="button" class="pf-tool-btn" @click="refreshAll">刷新</button>
              <button type="button" class="pf-tool-btn" @click="scrollToAccount">资料</button>
            </div>
          </div>

          <button type="button" class="pf-promo-strip pf-promo-strip--sidebar" @click="router.push('/notifications')">
            <span class="pf-promo-ico" aria-hidden="true">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M12 2l3 7h7l-5.5 4 2 7L12 17l-6.5 4 2-7L2 9h7l3-7z" />
              </svg>
            </span>
            <span class="pf-promo-text">会员与通知 · 活动与提醒</span>
            <span class="pf-promo-chev" aria-hidden="true">›</span>
          </button>

          <div class="sidebar-tip">
            <p class="sidebar-tip-label">提示</p>
            <p class="sidebar-tip-text">点击头像可更换图片，修改资料请在右侧「账户与资料」中保存。</p>
          </div>
        </aside>

        <div class="layout-main">
          <header class="pf-toolbar">
            <div class="pf-toolbar__left">
              <h1 class="pf-toolbar-title">个人概览</h1>
              <p class="pf-toolbar-meta">订单入口与资料编辑；与「我的订单」页筛选联动。</p>
            </div>
            <div class="pf-toolbar__actions">
              <button type="button" class="pf-tb-ghost" @click="router.push('/orders')">全部订单</button>
              <button type="button" class="pf-tb-ghost" @click="refreshAll">刷新</button>
            </div>
          </header>

          <div class="pf-main-flow">
            <section class="pf-card" aria-labelledby="pf-orders-title">
              <button type="button" class="pf-card-head pf-card-head--action" @click="router.push('/orders')">
                <span id="pf-orders-title" class="pf-card-title">我的订单</span>
                <span class="pf-card-chev" aria-hidden="true">›</span>
              </button>

              <div class="pf-order-status">
                <button type="button" class="pf-os" @click="goOrdersTab('CREATED')">
                  <span class="pf-os-ico" aria-hidden="true">
                    <svg width="30" height="30" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6">
                      <rect x="3" y="6" width="18" height="14" rx="2" />
                      <path d="M3 10h18" />
                      <path d="M7 14h4" />
                    </svg>
                  </span>
                  <span class="pf-os-label">待付款</span>
                </button>
                <button type="button" class="pf-os" @click="goOrdersTab('SHIPPED')">
                  <span class="pf-os-ico" aria-hidden="true">
                    <svg width="30" height="30" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6">
                      <path d="M4 17h14V9H4v8z" />
                      <path d="M4 9V7a1 1 0 011-1h3l2-2h4l2 2h3a1 1 0 011 1v2" />
                      <path d="M4 13h14" />
                    </svg>
                  </span>
                  <span class="pf-os-label">待收货</span>
                </button>
                <button type="button" class="pf-os" @click="goOrdersTab('COMPLETED')">
                  <span class="pf-os-ico" aria-hidden="true">
                    <svg width="30" height="30" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6">
                      <path d="M7 10h6M7 14h10" />
                      <rect x="4" y="4" width="16" height="16" rx="2" />
                    </svg>
                  </span>
                  <span class="pf-os-label">待评价</span>
                </button>
                <button type="button" class="pf-os" @click="goAfterSale">
                  <span class="pf-os-ico" aria-hidden="true">
                    <svg width="30" height="30" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6">
                      <circle cx="12" cy="12" r="9" />
                      <path d="M9 10h6M9 14h3" />
                    </svg>
                  </span>
                  <span class="pf-os-label">退款/售后</span>
                </button>
              </div>

              <div class="pf-logistics">
                <p v-if="logisticsLine && logisticsLine !== 'SHIP_NO'" class="pf-logistics-line">{{ logisticsLine }}</p>
                <p v-else-if="logisticsLine === 'SHIP_NO'" class="pf-logistics-line">最近订单已发货，物流单号暂未同步</p>
                <p v-else class="pf-logistics-line pf-logistics-line--muted">当前暂无物流信息</p>
                <button type="button" class="pf-logistics-link" @click="router.push('/orders')">去查看全部订单 &gt;</button>
              </div>
            </section>

            <div class="pf-split">
              <section class="pf-card" aria-labelledby="pf-freq-title">
                <div id="pf-freq-title" class="pf-card-head pf-card-head--static">
                  <span class="pf-card-title">常购清单</span>
                </div>
                <div v-if="!frequentProducts.length" class="pf-empty-block">
                  <p class="pf-empty-text">确认收货后的商品会出现在这里</p>
                  <button type="button" class="pf-btn-outline" @click="router.push('/products')">去逛逛</button>
                </div>
                <ul v-else class="pf-freq-list">
                  <li v-for="item in frequentProducts" :key="item.productId" class="pf-freq-row">
                    <AppImage :src="item.imageUrl" class="pf-freq-img" alt="" />
                    <div class="pf-freq-main">
                      <button type="button" class="pf-freq-title" @click="router.push(`/product/${item.productId}`)">
                        {{ item.title }}
                      </button>
                      <p class="pf-freq-meta">购买 {{ item.buyCount }} 件 · {{ formatYuan(item.price) }}</p>
                    </div>
                  </li>
                </ul>
              </section>

              <section id="profile-account" class="pf-card pf-card--account" tabindex="-1">
                <div class="pf-card-head pf-card-head--static">
                  <span class="pf-card-title">账户与资料</span>
                </div>
                <p class="pf-account-lead">修改昵称、邮箱与手机；更换头像后请点击保存。</p>

                <div class="profile-form">
                  <div class="profile-field">
                    <label class="profile-label" for="pf-nickname">昵称</label>
                    <div class="profile-field-control">
                      <input id="pf-nickname" v-model="form.nickname" class="pf-input" type="text" maxlength="30" />
                    </div>
                  </div>
                  <div class="profile-field">
                    <label class="profile-label" for="pf-email">登录邮箱</label>
                    <div class="profile-field-control">
                      <input
                        id="pf-email"
                        v-model="form.email"
                        class="pf-input"
                        type="email"
                        maxlength="80"
                        required
                        autocomplete="email"
                      />
                    </div>
                  </div>
                  <div class="profile-field">
                    <label class="profile-label" for="pf-phone">手机号（可选）</label>
                    <div class="profile-field-control">
                      <input id="pf-phone" v-model="form.phone" class="pf-input" type="text" maxlength="20" />
                    </div>
                  </div>
                </div>

                <div class="profile-actions">
                  <div class="profile-actions-spacer" aria-hidden="true" />
                  <button type="button" class="pf-btn-primary" :disabled="saving" @click="saveProfile">
                    {{ saving ? '保存中…' : '保存资料' }}
                  </button>
                </div>
              </section>
            </div>

            <section id="profile-addresses" class="pf-card pf-card--below-split">
              <div class="pf-card-head pf-card-head--split">
                <span class="pf-card-title">收货地址 <span v-if="addresses.length" class="pf-title-count">共 {{ addresses.length }} 条</span></span>
                <button type="button" class="pf-tool-btn" @click="openAddAddress">新增地址</button>
              </div>
              <p class="pf-account-lead">最多保存多条收货地址，下单时可选地址簿或临时填写；默认地址会在结算时优先选中。</p>
              <ul v-if="addresses.length" class="pf-addr-grid">
                <li v-for="a in addresses" :key="a.addressId" class="pf-addr-card">
                  <div class="pf-addr-card-head">
                    <span v-if="a.label" class="pf-addr-label">{{ a.label }}</span>
                    <span v-if="a.isDefault === 1" class="pf-addr-def">默认</span>
                  </div>
                  <p class="pf-addr-line2">{{ a.receiverName }} {{ a.receiverPhone }}</p>
                  <p class="pf-addr-line3">{{ a.receiverRegion }} {{ a.receiverDetail }}</p>
                  <div class="pf-addr-actions pf-addr-actions--card">
                    <button v-if="a.isDefault !== 1" type="button" class="pf-link-btn" @click="makeDefaultAddress(a.addressId)">
                      设为默认
                    </button>
                    <button type="button" class="pf-link-btn" @click="editAddress(a)">编辑</button>
                    <button type="button" class="pf-link-btn pf-link-btn--danger" @click="removeAddress(a.addressId)">
                      删除
                    </button>
                  </div>
                </li>
              </ul>
              <div v-else class="pf-empty-block pf-empty-block--tight">
                <p class="pf-empty-text">暂无收货地址</p>
                <button type="button" class="pf-btn-primary pf-btn-primary--small" @click="openAddAddress">新增收货地址</button>
              </div>
              <div v-if="addressFormOpen" class="pf-addr-form">
                <input v-model="addressForm.label" class="pf-input" placeholder="标签（家 / 公司等，可选）" />
                <input v-model="addressForm.receiverName" class="pf-input" placeholder="收货人" />
                <input v-model="addressForm.receiverPhone" class="pf-input" placeholder="手机号" />
                <input v-model="addressForm.receiverRegion" class="pf-input" placeholder="省 / 市 / 区" />
                <input v-model="addressForm.receiverDetail" class="pf-input" placeholder="街道、门牌、楼层等" />
                <label class="pf-check">
                  <input v-model="addressForm.isDefault" type="checkbox" :true-value="1" :false-value="0" />
                  设为默认地址
                </label>
                <div class="pf-addr-form-actions">
                  <button type="button" class="pf-btn-outline" @click="cancelAddressForm">取消</button>
                  <button type="button" class="pf-btn-primary" @click="saveAddress">保存</button>
                </div>
              </div>
            </section>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.profile-page--apex {
  --pf-ink: #0a0a0a;
  --pf-muted: #737373;
  --pf-line: #e5e5e5;
  --pf-panel: #ffffff;
  --pf-soft: #fafafa;
  --pf-canvas: #f5f5f5;
  --pf-radius: 2px;
  font-family: 'Inter', 'Microsoft YaHei', 'PingFang SC', system-ui, sans-serif;
}

.profile-page--apex.pw-page {
  padding-bottom: clamp(26px, 3.2vh, 40px);
}

.pf-canvas {
  background: var(--pf-canvas);
  min-height: min(74vh, 940px);
  margin: 0;
  padding: clamp(12px, 2.7vw, 20px) 0 32px;
}

.layout-shell {
  display: grid;
  grid-template-columns: minmax(236px, 304px) minmax(0, 1fr);
  gap: clamp(18px, 2.4vw, 28px);
  align-items: start;
  width: 100%;
  max-width: min(1260px, 100%);
  margin: 0 auto;
}

.layout-sidebar {
  position: sticky;
  top: 72px;
  background: var(--pf-panel);
  border: 1px solid var(--pf-line);
  border-radius: var(--pf-radius);
  padding: 18px 16px;
  box-shadow: 0 1px 0 rgba(0, 0, 0, 0.04);
}

.sidebar-head {
  margin-bottom: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--pf-line);
}

.sidebar-title {
  margin: 0 0 6px;
  font-size: clamp(18px, 1.48vw, 22px);
  font-weight: 800;
  letter-spacing: -0.02em;
  color: var(--pf-ink);
}

.sidebar-lead {
  margin: 0;
  font-size: 13px;
  line-height: 1.55;
  color: var(--pf-muted);
}

.pf-side-profile {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  gap: 10px;
  padding: 8px 0 14px;
  border-bottom: 1px solid var(--pf-line);
}

.pf-avatar-hit {
  position: relative;
  cursor: pointer;
  border-radius: 50%;
}

.pf-avatar-hit:focus-within {
  outline: 2px solid var(--pf-ink);
  outline-offset: 2px;
}

.pf-avatar-lg {
  width: 88px;
  height: 88px;
  border-radius: 50%;
  object-fit: cover;
  border: 1px solid var(--pf-line);
  display: block;
  background: var(--pf-soft);
}

.pf-avatar-lg--empty {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #a3a3a3;
  font-size: 12px;
  font-weight: 700;
}

.pf-file-input {
  position: absolute;
  width: 0;
  height: 0;
  opacity: 0;
}

.pf-side-display-name {
  margin: 0;
  font-size: 17px;
  font-weight: 800;
  color: var(--pf-ink);
  line-height: 1.35;
  word-break: break-all;
}

.pf-hero-badge {
  padding: 4px 11px;
  border: 1px solid var(--pf-line);
  border-radius: var(--pf-radius);
  background: var(--pf-soft);
  font-size: 13px;
  font-weight: 700;
  color: #525252;
}

.pf-hero-badge--plus {
  border-color: rgba(180, 134, 72, 0.55);
  color: #6b4f2a;
  background: linear-gradient(180deg, #fdf8ef 0%, #f3eadc 100%);
}

.pf-plus-expiry {
  margin: 6px 0 0;
  font-size: 12px;
  font-weight: 600;
  color: var(--pf-muted);
  line-height: 1.35;
}

.pf-side-meta {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}

.pf-meta-link {
  display: inline-flex;
  align-items: center;
  justify-content: flex-start;
  gap: 7px;
  padding: 9px 11px;
  width: 100%;
  box-sizing: border-box;
  border: 1px solid var(--pf-line);
  border-radius: var(--pf-radius);
  background: var(--pf-soft);
  font-size: 13px;
  font-weight: 700;
  color: var(--pf-ink);
  cursor: pointer;
}

.pf-meta-link:hover {
  border-color: var(--pf-ink);
}

.pf-meta-ico {
  display: flex;
  color: var(--pf-muted);
}

.pf-meta-count {
  margin-left: auto;
  min-width: 22px;
  height: 22px;
  padding: 0 7px;
  border-radius: 999px;
  background: var(--pf-ink);
  color: #fff;
  font-size: 11px;
  font-weight: 800;
  line-height: 22px;
  text-align: center;
}

.pf-side-tools {
  display: flex;
  gap: 8px;
  width: 100%;
  margin-top: 4px;
}

.pf-tool-btn {
  flex: 1;
  padding: 9px 11px;
  border: 1px solid var(--pf-line);
  border-radius: var(--pf-radius);
  background: var(--pf-panel);
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.06em;
  color: var(--pf-ink);
  cursor: pointer;
}

.pf-tool-btn:hover {
  border-color: var(--pf-ink);
}

.pf-promo-strip {
  display: flex;
  align-items: center;
  gap: 11px;
  width: 100%;
  margin-top: 12px;
  padding: 11px 13px;
  border: 1px solid #ebe4d8;
  border-radius: var(--pf-radius);
  background: linear-gradient(180deg, #faf8f4 0%, #f3f0ea 100%);
  font-size: 13px;
  font-weight: 700;
  color: #3f3f3f;
  cursor: pointer;
  text-align: left;
}

.pf-promo-strip:hover {
  border-color: #cfc7b8;
}

.pf-promo-strip--sidebar {
  margin-top: 14px;
}

.pf-promo-ico {
  flex-shrink: 0;
  display: flex;
  color: #6b5c3e;
}

.pf-promo-text {
  flex: 1;
  min-width: 0;
  line-height: 1.4;
}

.pf-promo-chev {
  flex-shrink: 0;
  font-size: 16px;
  font-weight: 300;
  color: #a3a3a3;
}

.sidebar-tip {
  margin-top: 14px;
  padding: 11px 13px;
  border: 1px solid var(--pf-line);
  border-radius: var(--pf-radius);
  background: var(--pf-soft);
}

.sidebar-tip-label {
  margin: 0 0 6px;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: #a3a3a3;
}

.sidebar-tip-text {
  margin: 0;
  font-size: 13px;
  line-height: 1.55;
  color: var(--pf-muted);
}

.layout-main {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.pf-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  padding: clamp(18px, 1.95vw, 22px) clamp(20px, 2.1vw, 26px);
  background: var(--pf-panel);
  border: 1px solid var(--pf-line);
  border-radius: var(--pf-radius);
  box-shadow: 0 1px 0 rgba(0, 0, 0, 0.04);
}

.pf-toolbar-title {
  margin: 0 0 6px;
  font-size: clamp(19px, 1.55vw, 24px);
  font-weight: 800;
  color: var(--pf-ink);
  letter-spacing: -0.02em;
}

.pf-toolbar-meta {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--pf-muted);
}

.pf-toolbar__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.pf-tb-ghost {
  padding: 11px 20px;
  border: 1px solid var(--pf-line);
  border-radius: var(--pf-radius);
  background: var(--pf-panel);
  color: var(--pf-ink);
  font-size: 13px;
  font-weight: 800;
  letter-spacing: 0.05em;
  cursor: pointer;
}

.pf-tb-ghost:hover {
  border-color: var(--pf-ink);
}

.pf-main-flow {
  display: flex;
  flex-direction: column;
  gap: 16px;
  width: 100%;
}

.pf-card {
  background: var(--pf-panel);
  border: 1px solid var(--pf-line);
  border-radius: var(--pf-radius);
  box-shadow: 0 1px 0 rgba(0, 0, 0, 0.03);
  overflow: hidden;
}

.pf-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 15px 18px 11px;
  border: none;
  border-bottom: 1px solid var(--pf-line);
  background: var(--pf-panel);
  cursor: default;
}

.pf-card-head--action {
  cursor: pointer;
  text-align: left;
}

.pf-card-head--action:hover {
  background: var(--pf-soft);
}

.pf-card-title {
  font-size: 17px;
  font-weight: 800;
  color: var(--pf-ink);
}

.pf-card-chev {
  font-size: 20px;
  font-weight: 300;
  color: #a3a3a3;
  line-height: 1;
}

.pf-order-status {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 20px clamp(14px, 3.2vw, 36px) 24px;
  gap: 10px;
}

@media (min-width: 900px) {
  .pf-order-status {
    padding: 24px 30px 28px;
    gap: 14px;
  }
}

.pf-os {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 11px;
  padding: 7px 5px;
  border: none;
  background: none;
  cursor: pointer;
  color: var(--pf-ink);
}

.pf-os:hover .pf-os-label {
  text-decoration: underline;
  text-underline-offset: 3px;
}

.pf-os-ico {
  display: flex;
  color: #404040;
}

.pf-os-label {
  font-size: 13px;
  font-weight: 700;
  color: #404040;
  text-align: center;
  line-height: 1.25;
}

@media (min-width: 900px) {
  .pf-os-label {
    font-size: 14px;
  }
}

.pf-logistics {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  justify-content: space-between;
  gap: 11px 18px;
  padding: 15px 18px 18px;
  background: var(--pf-soft);
  border-top: 1px solid var(--pf-line);
}

.pf-logistics-line {
  margin: 0;
  flex: 1;
  min-width: 216px;
  font-size: 13px;
  font-weight: 600;
  color: #525252;
  line-height: 1.45;
}

.pf-logistics-line--muted {
  color: #a3a3a3;
}

.pf-logistics-link {
  flex-shrink: 0;
  padding: 0;
  border: none;
  background: none;
  font-size: 14px;
  font-weight: 700;
  color: var(--pf-ink);
  cursor: pointer;
  text-decoration: underline;
  text-underline-offset: 3px;
}

.pf-split {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.2fr);
  gap: 17px;
  align-items: start;
}

@media (max-width: 820px) {
  .pf-split {
    grid-template-columns: 1fr;
  }
}

.pf-card--below-split {
  margin-top: 17px;
}

.pf-card-head--split {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.pf-freq-list {
  list-style: none;
  margin: 0;
  padding: 0 12px 14px;
}

.pf-freq-row {
  display: flex;
  gap: 12px;
  padding: 12px 6px;
  border-bottom: 1px solid var(--pf-line);
}

.pf-freq-row:last-child {
  border-bottom: none;
}

.pf-freq-img {
  width: 56px;
  height: 56px;
  border-radius: 10px;
  object-fit: cover;
  flex-shrink: 0;
  border: 1px solid var(--pf-line);
}

.pf-freq-main {
  min-width: 0;
  flex: 1;
}

.pf-freq-title {
  display: block;
  padding: 0;
  border: none;
  background: none;
  font-size: 14px;
  font-weight: 700;
  color: var(--pf-ink);
  text-align: left;
  cursor: pointer;
  line-height: 1.35;
}

.pf-freq-title:hover {
  text-decoration: underline;
}

.pf-freq-meta {
  margin: 4px 0 0;
  font-size: 12px;
  font-weight: 600;
  color: var(--pf-muted);
}

.pf-empty-block--tight {
  padding: 18px 18px 22px;
}

.pf-title-count {
  margin-left: 8px;
  font-size: 12px;
  font-weight: 700;
  color: var(--pf-muted);
}

.pf-addr-grid {
  list-style: none;
  margin: 0;
  padding: 0 18px 16px;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 14px;
}

.pf-addr-card {
  border: 1px solid var(--pf-line);
  border-radius: 12px;
  padding: 14px 14px 12px;
  background: var(--pf-panel);
  box-shadow: 0 2px 12px rgba(15, 23, 42, 0.04);
}

.pf-addr-card-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}

.pf-addr-actions--card {
  flex-direction: row;
  flex-wrap: wrap;
  justify-content: flex-start;
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px dashed var(--pf-line);
}

.pf-btn-primary--small {
  margin-top: 12px;
  padding: 8px 18px;
  font-size: 13px;
}

.pf-addr-label {
  font-size: 13px;
  font-weight: 800;
  color: var(--pf-ink);
}

.pf-addr-def {
  font-size: 11px;
  font-weight: 800;
  padding: 2px 7px;
  border-radius: 999px;
  border: 1px solid rgba(180, 134, 72, 0.45);
  color: #6b4f2a;
  background: #fdf8ef;
}

.pf-addr-line2,
.pf-addr-line3 {
  margin: 4px 0 0;
  font-size: 13px;
  font-weight: 600;
  color: #525252;
  line-height: 1.45;
}

.pf-addr-actions {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
  align-items: flex-end;
}

.pf-link-btn {
  padding: 0;
  border: none;
  background: none;
  font-size: 12px;
  font-weight: 700;
  color: var(--pf-ink);
  cursor: pointer;
  text-decoration: underline;
  text-underline-offset: 3px;
}

.pf-link-btn--danger {
  color: #b91c1c;
}

.pf-addr-form {
  display: grid;
  grid-template-columns: 1fr;
  gap: 10px;
  padding: 12px 18px 18px;
  border-top: 1px solid var(--pf-line);
}

.pf-check {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--pf-muted);
}

.pf-addr-form-actions {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
}

.pf-card--account {
  padding: 0 0 18px;
}

@media (min-width: 821px) {
  .pf-card--account {
    position: sticky;
    top: 72px;
  }
}

.pf-empty-block {
  padding: 30px 18px 26px;
  text-align: center;
}

.pf-empty-text {
  margin: 0 0 15px;
  font-size: 15px;
  font-weight: 600;
  color: #a3a3a3;
}

.pf-btn-outline {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 43px;
  padding: 0 24px;
  border: 1px solid var(--pf-line);
  border-radius: var(--pf-radius);
  background: var(--pf-panel);
  color: var(--pf-ink);
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
}

.pf-btn-outline:hover {
  border-color: var(--pf-ink);
}

.pf-account-lead {
  margin: 0;
  padding: 11px 18px 0;
  font-size: 13px;
  font-weight: 600;
  line-height: 1.5;
  color: var(--pf-muted);
}

.profile-form {
  display: flex;
  flex-direction: column;
  padding: 13px 18px 0;
  border-top: 1px solid var(--pf-line);
  margin-top: 12px;
}

.profile-field {
  display: grid;
  grid-template-columns: minmax(86px, 128px) minmax(0, 1fr);
  column-gap: 18px;
  align-items: center;
  padding: 13px 0;
  border-bottom: 1px solid var(--pf-line);
}

.profile-field:last-of-type {
  border-bottom: none;
}

.profile-label {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--pf-muted);
  text-align: right;
}

.profile-label::after {
  content: '：';
}

.profile-field-control {
  min-width: 0;
}

.pf-input {
  width: 100%;
  max-width: 556px;
  height: 44px;
  padding: 0 11px;
  font-size: 15px;
  font-weight: 600;
  color: var(--pf-ink);
  border: 1px solid var(--pf-line);
  border-radius: var(--pf-radius);
  background: var(--pf-panel);
  box-sizing: border-box;
}

.pf-input:focus {
  outline: none;
  border-color: var(--pf-ink);
}

.profile-actions {
  display: grid;
  grid-template-columns: minmax(86px, 128px) minmax(0, 1fr);
  column-gap: 18px;
  padding: 17px 18px 0;
  margin-top: 8px;
  border-top: 1px solid var(--pf-line);
}

.profile-actions-spacer {
  min-height: 1px;
}

.pf-btn-primary {
  padding: 12px 24px;
  border: 1px solid var(--pf-ink);
  border-radius: var(--pf-radius);
  background: var(--pf-ink);
  color: #fff;
  font-size: 15px;
  font-weight: 800;
  cursor: pointer;
  justify-self: start;
}

.pf-btn-primary:hover:not(:disabled) {
  background: #262626;
  border-color: #262626;
}

.pf-btn-primary:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.pf-btn-primary--sm {
  padding: 8px 16px;
  font-size: 12px;
}

.pf-state {
  padding: 28px 20px;
  text-align: center;
  border-radius: var(--pf-radius);
  border: 1px solid var(--pf-line);
  background: var(--pf-panel);
  max-width: 420px;
  margin: 0 auto;
}

.pf-state--muted {
  color: var(--pf-muted);
  font-weight: 600;
}

.pf-state--error {
  border-color: #fecaca;
  background: #fef2f2;
}

.pf-state-msg {
  margin: 0 0 14px;
  color: #b91c1c;
  font-weight: 700;
}

@media (max-width: 640px) {
  .profile-field {
    grid-template-columns: 1fr;
    row-gap: 6px;
  }

  .profile-label {
    text-align: left;
  }

  .pf-input {
    max-width: none;
  }

  .profile-actions {
    grid-template-columns: 1fr;
  }

  .profile-actions-spacer {
    display: none;
  }

  .pf-btn-primary {
    width: 100%;
    justify-self: stretch;
  }
}

@media (max-width: 979px) {
  .layout-shell {
    grid-template-columns: 1fr;
    max-width: 600px;
  }

  .layout-sidebar {
    position: static;
    order: 1;
  }

  .layout-main {
    order: 2;
  }

  .pf-toolbar__actions {
    width: 100%;
  }

  .pf-tb-ghost {
    flex: 1;
  }
}
</style>
