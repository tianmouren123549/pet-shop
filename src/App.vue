<script setup>
import { RouterLink, RouterView } from 'vue-router'
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from './utils/request'
import { countNoticeBadgeUnread } from './utils/noticeBadgeAck'
import { showAppMessage } from './utils/appMessage'
import AppMessageModal from './components/AppMessageModal.vue'

/** 无自定义头像时顶栏使用的默认用户头像（内联 SVG） */
const DEFAULT_USER_AVATAR =
  'data:image/svg+xml,' +
  encodeURIComponent(
    '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64" width="64" height="64">' +
      '<circle cx="32" cy="32" r="32" fill="#dbe3ee"/>' +
      '<circle cx="32" cy="24" r="11" fill="#7a8aa0"/>' +
      '<path fill="#7a8aa0" d="M14 56c2-12 10-18 18-18s16 6 18 18"/>' +
      '</svg>',
  )

const route = useRoute()
const router = useRouter()
const role = ref('')
const nickname = ref('')
const adminName = ref('')
/** 用户头像 URL（空串则顶栏显示默认图） */
const userAvatarUrl = ref('')
/** 是否已在当前登录会话中拉取过头像（减少无意义重复请求） */
const userAvatarPrimed = ref(false)
const unreadCount = ref(0)
/** 联系商家 / 用户咨询：有未读时仅显示红点（不展示条数） */
const chatUnreadDot = ref(false)
/** 商家咨询未读单独高频轮询（与通知/订单 15s 区分） */
let merchantChatPollTimer = null
/** 商家：待发货（橙点，待支付不提示） */
const orderTodoShipDot = ref(false)
let unreadTimer = null

function handleNoticeUpdated() {
  loadUnreadCount()
}

function handleChatUnreadUpdated() {
  loadChatUnreadDot()
}

function handleMerchantOrderTodoUpdated() {
  loadMerchantOrderTodos()
}

/**
 * 商家顶栏「我的订单管理」：待支付与待发货分色点（无数字）。
 */
async function loadMerchantOrderTodos() {
  if (role.value !== 'MERCHANT') {
    orderTodoShipDot.value = false
    return
  }
  const mid = Number(localStorage.getItem('adminId') || 0)
  if (!mid) {
    orderTodoShipDot.value = false
    return
  }
  const res = await api.merchantOrderTodoBadges(mid)
  if (res.code !== 200 || !res.data) return
  orderTodoShipDot.value = Boolean(res.data.pendingShipment)
}

/**
 * 轮询用户未读商家回复、商家未读用户咨询（与通知红点分离）。
 */
async function loadChatUnreadDot() {
  if (role.value === 'USER') {
    const uid = Number(localStorage.getItem('userId') || 0)
    if (!uid) {
      chatUnreadDot.value = false
      return
    }
    const res = await api.userChatUnreadBadge(uid)
    if (res.code === 200 && res.data != null && typeof res.data === 'object') {
      const raw = /** @type {{ hasUnread?: unknown }} */ (res.data).hasUnread
      chatUnreadDot.value = raw === true || raw === 'true'
    } else {
      chatUnreadDot.value = false
    }
    return
  }
  if (role.value === 'MERCHANT') {
    const mid = Number(localStorage.getItem('adminId') || 0)
    if (!mid) {
      chatUnreadDot.value = false
      return
    }
    const res = await api.merchantChatUnreadBadge(mid)
    if (res.code === 200 && res.data != null && typeof res.data === 'object') {
      const raw = /** @type {{ hasUnread?: unknown }} */ (res.data).hasUnread
      chatUnreadDot.value = raw === true || raw === 'true'
    } else {
      chatUnreadDot.value = false
    }
    return
  }
  chatUnreadDot.value = false
}

const isAdminLoginRoute = computed(() => String(route.path || '') === '/admin-login')
const isAdminRoute = computed(() => {
  const p = String(route.path || '')
  return p === '/admin-login' || p.startsWith('/admin')
})

function syncAuthState() {
  role.value = localStorage.getItem('role') || ''
  nickname.value = localStorage.getItem('nickname') || ''
  adminName.value = localStorage.getItem('adminName') || ''
  userAvatarUrl.value = localStorage.getItem('userAvatarUrl') || ''
}

/**
 * 从接口拉取用户头像并写入 localStorage，供顶栏展示。
 */
async function refreshUserAvatarFromApi() {
  if (role.value !== 'USER') return
  const uid = Number(localStorage.getItem('userId') || 0)
  if (!uid) return
  const res = await api.userGetProfile(uid)
  if (res.code !== 200 || !res.data) return
  const url = String(res.data.avatarUrl || '').trim()
  localStorage.setItem('userAvatarUrl', url)
  userAvatarUrl.value = url
}

function onUserAvatarImgError(e) {
  if (e?.target) e.target.src = DEFAULT_USER_AVATAR
}

function handleUserAvatarUpdated() {
  syncAuthState()
}

function resetUserAvatarSession() {
  userAvatarPrimed.value = false
  userAvatarUrl.value = ''
}

const userAvatarDisplay = computed(() => {
  const u = (userAvatarUrl.value || '').trim()
  return u || DEFAULT_USER_AVATAR
})

async function loadUnreadCount() {
  if (role.value === 'USER') {
    const uid = Number(localStorage.getItem('userId') || 0)
    if (!uid) {
      unreadCount.value = 0
      return
    }
    const res = await api.userGetNotifications(uid)
    if (res.code !== 200) {
      unreadCount.value = 0
      return
    }
    const arr = Array.isArray(res.data) ? res.data : []
    unreadCount.value = countNoticeBadgeUnread(arr, 'user', uid)
    return
  }

  if (role.value === 'MERCHANT') {
    const mid = Number(localStorage.getItem('adminId') || 0)
    if (!mid) {
      unreadCount.value = 0
      return
    }
    const res = await api.merchantGetNotifications(mid)
    if (res.code !== 200) {
      unreadCount.value = 0
      return
    }
    const arr = Array.isArray(res.data) ? res.data : []
    unreadCount.value = countNoticeBadgeUnread(arr, 'merchant', mid)
    return
  }

  unreadCount.value = 0
}

watch(
  () => route.fullPath,
  async () => {
    syncAuthState()
    await loadUnreadCount()
    await loadChatUnreadDot()
    await loadMerchantOrderTodos()
    const uid = Number(localStorage.getItem('userId') || 0)
    if (role.value === 'USER' && uid) {
      if (route.path === '/profile' || !userAvatarPrimed.value) {
        await refreshUserAvatarFromApi()
        userAvatarPrimed.value = true
      }
    } else {
      resetUserAvatarSession()
    }
  },
  { immediate: true }
)

onMounted(() => {
  unreadTimer = setInterval(() => {
    loadUnreadCount()
    loadMerchantOrderTodos()
    if (localStorage.getItem('role') !== 'MERCHANT') loadChatUnreadDot()
  }, 15000)
  merchantChatPollTimer = setInterval(() => {
    if (localStorage.getItem('role') === 'MERCHANT') loadChatUnreadDot()
  }, 5000)
  window.addEventListener('petshop-notice-updated', handleNoticeUpdated)
  window.addEventListener('petshop-chat-unread-updated', handleChatUnreadUpdated)
  window.addEventListener('petshop-merchant-order-todo-updated', handleMerchantOrderTodoUpdated)
  window.addEventListener('petshop-user-avatar-updated', handleUserAvatarUpdated)
})

onUnmounted(() => {
  if (unreadTimer) clearInterval(unreadTimer)
  unreadTimer = null
  if (merchantChatPollTimer) clearInterval(merchantChatPollTimer)
  merchantChatPollTimer = null
  window.removeEventListener('petshop-notice-updated', handleNoticeUpdated)
  window.removeEventListener('petshop-chat-unread-updated', handleChatUnreadUpdated)
  window.removeEventListener('petshop-merchant-order-todo-updated', handleMerchantOrderTodoUpdated)
  window.removeEventListener('petshop-user-avatar-updated', handleUserAvatarUpdated)
})

function logout() {
  localStorage.removeItem('role')
  localStorage.removeItem('userId')
  localStorage.removeItem('nickname')
  localStorage.removeItem('userAvatarUrl')
  localStorage.removeItem('accessToken')
  localStorage.removeItem('adminId')
  localStorage.removeItem('adminName')
  resetUserAvatarSession()
  syncAuthState()
  window.location.href = '#/login'
}

function requireLoginThenGo(path, expectedRole) {
  syncAuthState()
  if (!role.value) {
    showAppMessage('请先登录后再使用该功能', '提示')
    return
  }
  if (expectedRole && role.value !== expectedRole) {
    showAppMessage('请使用对应身份账号登录后再试', '提示')
    return
  }
  router.push(path)
}
</script>

<template>
  <div class="app">
    <header v-if="!isAdminLoginRoute" class="header" :class="{ admin: isAdminRoute }">
      <div class="container">
        <h1 class="logo">
          {{
            role === 'ADMIN'
              ? '管理员控制台'
              : role === 'MERCHANT'
                ? '商家控制台'
                : '宠物商城'
          }}
        </h1>
        <nav class="nav">
          <template v-if="isAdminRoute">
            <RouterLink to="/admin">管理首页</RouterLink>
            <RouterLink to="/admin/products">商品管理</RouterLink>
            <RouterLink to="/admin/inventory">库存监管</RouterLink>
            <RouterLink to="/admin/orders">订单管理</RouterLink>
            <RouterLink to="/admin/accounts">账号管理</RouterLink>
          </template>
          <template v-else-if="!role">
            <RouterLink to="/">首页</RouterLink>
            <RouterLink to="/products">商品列表</RouterLink>
          </template>
          <template v-else-if="role === 'USER'">
            <RouterLink to="/">首页</RouterLink>
            <RouterLink to="/products">商品列表</RouterLink>
            <RouterLink to="/cart">购物车</RouterLink>
            <RouterLink to="/orders">我的订单</RouterLink>
            <RouterLink to="/notifications" class="nav-link-with-badge">
              通知
              <span v-if="unreadCount > 0" class="nav-badge">{{ unreadCount > 99 ? '99+' : unreadCount }}</span>
            </RouterLink>
            <RouterLink to="/merchant-contact" class="nav-link-with-badge">
              联系商家
              <span v-if="chatUnreadDot" class="nav-badge-dot" aria-hidden="true" />
            </RouterLink>
            <RouterLink to="/profile">个人中心</RouterLink>
          </template>
          <template v-else-if="role === 'ADMIN'">
            <RouterLink to="/admin">管理首页</RouterLink>
            <RouterLink to="/admin/products">商品管理</RouterLink>
            <RouterLink to="/admin/inventory">库存监管</RouterLink>
            <RouterLink to="/admin/orders">订单管理</RouterLink>
            <RouterLink to="/admin/accounts">账号管理</RouterLink>
          </template>
          <template v-else>
            <RouterLink to="/merchant">商家首页</RouterLink>
            <RouterLink to="/merchant/products">我的商品</RouterLink>
            <RouterLink to="/merchant/orders" class="nav-link-with-badge">
              我的订单管理
              <span v-if="orderTodoShipDot" class="nav-badge-dot nav-badge-dot--ship" aria-hidden="true" />
            </RouterLink>
            <RouterLink to="/merchant/notifications" class="nav-link-with-badge">
              通知
              <span v-if="unreadCount > 0" class="nav-badge">{{ unreadCount > 99 ? '99+' : unreadCount }}</span>
            </RouterLink>
            <RouterLink to="/merchant/support" class="nav-link-with-badge">
              用户咨询
              <span v-if="chatUnreadDot" class="nav-badge-dot" aria-hidden="true" />
            </RouterLink>
            <RouterLink to="/merchant/profile">商家资料</RouterLink>
          </template>

          <RouterLink v-if="!role" to="/login">登录</RouterLink>
          <span v-if="role === 'USER'" class="user-badge user-badge--with-avatar">
            <img
              class="user-avatar"
              :src="userAvatarDisplay"
              alt=""
              width="28"
              height="28"
              @error="onUserAvatarImgError"
            />
            <span class="user-badge-text">用户：{{ nickname || 'user' }}</span>
          </span>
          <span v-else-if="role" class="user-badge">
            {{
              role === 'ADMIN'
                ? `管理员：${adminName || 'admin'}`
                : `商家：${adminName || 'merchant'}`
            }}
          </span>
          <a v-if="role" href="javascript:void(0)" @click="logout">退出</a>
        </nav>
      </div>
    </header>

    <main class="main">
      <RouterView />
    </main>

    <footer class="footer">
      <div class="footer-content">
        <p>宠物商城 - 基于 BERT + XGBoost 的智能推荐系统</p>
        <p class="footer-sub">毕业设计项目 © 2026</p>
      </div>
    </footer>

    <AppMessageModal />
  </div>
</template>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html, body {
  width: 100%;
  min-height: 100vh;
  background-color: #f1f3f6;
  opacity: 1;
}

#app {
  width: 100%;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.app {
  width: 100%;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.header {
  background: #f7f8fa;
  border-bottom: 1px solid #dfe3ea;
  box-shadow: 0 4px 14px rgba(13, 25, 42, 0.06);
  position: sticky;
  top: 0;
  z-index: 1000;
}

.header.admin {
  background: #08142a;
  border-bottom-color: rgba(255,255,255,0.08);
  box-shadow: 0 10px 30px rgba(8, 20, 42, 0.22);
}

.container {
  width: 100%;
  max-width: 1460px;
  margin: 0 auto;
  padding: 0 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  height: 58px;
}

.logo {
  font-size: 20px;
  color: #0d1626;
  font-weight: 800;
  letter-spacing: 0.2px;
  cursor: default;
}

.header.admin .logo {
  color: #e7eef9;
}

.nav {
  display: flex;
  gap: 6px;
  align-items: center;
}

.nav a {
  text-decoration: none;
  color: #4f5e74;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0.2px;
  padding: 7px 12px;
  border-radius: 3px;
  transition: all 0.2s;
}

.header.admin .nav a {
  color: #b8c7dc;
}

.nav a:hover {
  background-color: #e8edf4;
  color: #1d2d46;
}

.header.admin .nav a:hover {
  background: rgba(255,255,255,0.08);
  color: #e7eef9;
}

.nav a.router-link-active {
  background-color: #0b1630;
  color: #f4f6fb;
}

.header.admin .nav a.router-link-active {
  background: rgba(255,255,255,0.14);
  color: #f4f6fb;
}

.nav-link-with-badge {
  position: relative;
  display: inline-flex;
  align-items: center;
}

.nav-badge {
  margin-left: 6px;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 999px;
  background: #ff4d4f;
  color: #fff;
  font-size: 11px;
  line-height: 18px;
  text-align: center;
  font-weight: 800;
}

.nav-badge-dot {
  margin-left: 6px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #ff4d4f;
  flex-shrink: 0;
  box-shadow: 0 0 0 1px #fff;
}

.nav-badge-dot--ship {
  background: #fa8c16;
}

.user-badge {
  color: #58667a;
  font-size: 12px;
  padding: 7px 10px;
  background: #eef2f7;
  border: 1px solid #d9e0ea;
  border-radius: 3px;
}

.user-badge--with-avatar {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.user-avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  object-fit: cover;
  flex-shrink: 0;
  border: 1px solid #d0d8e4;
  background: #fff;
}

.user-badge-text {
  line-height: 1.2;
}

.header.admin .user-badge {
  color: #e7eef9;
  background: rgba(255,255,255,0.08);
  border-color: rgba(255,255,255,0.14);
}

.main {
  flex: 1;
  width: 100%;
  max-width: 1460px;
  margin: 16px auto;
  padding: 0 20px;
}

.footer {
  background-color: #edf1f6;
  border-top: 1px solid #dfe5ee;
  color: #6a7688;
  padding: 24px 0;
  margin-top: 40px;
}

.footer-content {
  width: 100%;
  max-width: 1460px;
  margin: 0 auto;
  padding: 0 20px;
  text-align: center;
}

.footer-content p {
  margin: 5px 0;
  font-size: 12px;
}

.footer-sub {
  color: #8893a3;
  font-size: 11px;
}

body {
  font-family: 'Microsoft YaHei', 'PingFang SC', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
}
</style>
