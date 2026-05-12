<script setup>
import { RouterLink, RouterView } from 'vue-router'
import { KeepAlive, computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from './utils/request'
import { clearAuthLocalStorage } from './utils/authStorage.js'
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
/** 对端会话未读：用户↔商家 / 商家←用户咨询 */
const chatUnreadPeerDot = ref(false)
/** 平台管理员会话未读 */
const chatUnreadPlatformDot = ref(false)
/** 商家咨询未读单独高频轮询（与通知/订单 15s 区分） */
let merchantChatPollTimer = null
/** 管理端平台客服未读红点：单独较高频轮询（对方发信后尽快点亮侧栏） */
let adminSupportBadgeTimer = null
/** 管理端：用户/商家咨询平台未读（平台收件箱） */
const adminSupportUnreadDot = ref(false)
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
async function loadAdminSupportUnread() {
  if (role.value !== 'ADMIN') {
    adminSupportUnreadDot.value = false
    return
  }
  const res = await api.adminSupportUnreadBadge()
  if (res.code === 200 && res.data != null && typeof res.data === 'object') {
    const n = /** @type {{ unread?: unknown }} */ (res.data).unread
    const num = typeof n === 'number' ? n : Number(n)
    adminSupportUnreadDot.value = Number.isFinite(num) && num > 0
  } else {
    adminSupportUnreadDot.value = false
  }
}

function applyChatUnreadBadgePayload(data) {
  const o = /** @type {Record<string, unknown>} */ (data)
  const peerRaw = o.hasUnreadPeer
  const platRaw = o.hasUnreadPlatform
  const legacyRaw = o.hasUnread
  const peer =
    peerRaw === true || peerRaw === 'true' || peerRaw === 1 || peerRaw === '1'
  const platform =
    platRaw === true || platRaw === 'true' || platRaw === 1 || platRaw === '1'
  if (peerRaw == null && platRaw == null) {
    const legacy = legacyRaw === true || legacyRaw === 'true' || legacyRaw === 1 || legacyRaw === '1'
    chatUnreadPeerDot.value = legacy
    chatUnreadPlatformDot.value = legacy
    return
  }
  chatUnreadPeerDot.value = peer
  chatUnreadPlatformDot.value = platform
}

async function loadChatUnreadDot() {
  if (role.value === 'USER') {
    const uid = Number(localStorage.getItem('userId') || 0)
    if (!uid) {
      chatUnreadPeerDot.value = false
      chatUnreadPlatformDot.value = false
      return
    }
    const res = await api.userChatUnreadBadge(uid)
    if (res.code === 200 && res.data != null && typeof res.data === 'object') {
      applyChatUnreadBadgePayload(res.data)
    } else {
      chatUnreadPeerDot.value = false
      chatUnreadPlatformDot.value = false
    }
    return
  }
  if (role.value === 'MERCHANT') {
    const mid = Number(localStorage.getItem('adminId') || 0)
    if (!mid) {
      chatUnreadPeerDot.value = false
      chatUnreadPlatformDot.value = false
      return
    }
    const res = await api.merchantChatUnreadBadge(mid)
    if (res.code === 200 && res.data != null && typeof res.data === 'object') {
      applyChatUnreadBadgePayload(res.data)
    } else {
      chatUnreadPeerDot.value = false
      chatUnreadPlatformDot.value = false
    }
    return
  }
  chatUnreadPeerDot.value = false
  chatUnreadPlatformDot.value = false
}

const isAdminLoginRoute = computed(() => String(route.path || '') === '/admin-login')
const isAdminRoute = computed(() => {
  const p = String(route.path || '')
  return p === '/admin' || p.startsWith('/admin/')
})
const isMerchantRoute = computed(() => {
  const p = String(route.path || '')
  return p.startsWith('/merchant')
})
/**
 * 商家一体化壳层仅用于真实商家路由；须与 {@link isAdminRoute} 互斥。
 * 否则在 /admin 下若 localStorage 仍为 MERCHANT，会叠出左下角「宠物商城」侧栏。
 */
const isMerchantShellRoute = computed(() => {
  const p = String(route.path || '')
  return p === '/merchant' || p.startsWith('/merchant/')
})
/** 用户咨询工作台：主内容区横向铺满（保留与侧栏壳层并存时的兜底） */
const isMerchantSupportRoute = computed(() => String(route.path || '') === '/merchant/support')
/** AI 导购页：主区全宽并参与 flex 撑满顶栏与页脚之间高度 */
const isUserAiRoute = computed(() => String(route.path || '') === '/ai')
/** 登录壳页：不参与用户端全局缩小字号，避免覆盖登录页本地排版 */
const isAuthShellRoute = computed(() => {
  const p = String(route.path || '')
  return p === '/login' || p === '/admin-login'
})
/** 已登录商家：左侧导航 + 顶栏 + 主区（登录壳页仍用全站顶栏） */
const showMerchantShell = computed(
  () => role.value === 'MERCHANT' && !isAuthShellRoute.value && isMerchantShellRoute.value,
)

const MERCHANT_SHELL_TITLES = {
  'merchant-dashboard': '商家首页',
  'merchant-products': '我的商品',
  'merchant-orders': '订单管理',
  'merchant-order-detail': '订单详情',
  'merchant-support': '用户咨询',
  'merchant-notifications': '通知中心',
  'merchant-contact-admin': '联系平台',
  'merchant-profile': '商家资料',
  'merchant-product-create': '上架新商品',
  'merchant-product-edit': '编辑商品',
}

const merchantShellPageTitle = computed(() => {
  const key = String(route.name || '')
  return MERCHANT_SHELL_TITLES[key] || '商家控制台'
})

/** 管理端顶栏圆形头像缩写（对齐控制台模板） */
const adminHubInitials = computed(() => {
  const raw = String(adminName.value || 'admin').trim()
  if (!raw) return 'AP'
  const parts = raw.split(/[\s_\-]+/).filter(Boolean)
  if (parts.length >= 2) {
    const a = parts[0][0] || ''
    const b = parts[1][0] || ''
    const pair = (a + b).toUpperCase()
    if (pair.length >= 2) return pair.slice(0, 2)
  }
  return raw.slice(0, 2).toUpperCase()
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
    await loadAdminSupportUnread()
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
  adminSupportBadgeTimer = setInterval(() => {
    if (localStorage.getItem('role') === 'ADMIN') loadAdminSupportUnread()
  }, 5000)
  merchantChatPollTimer = setInterval(() => {
    if (localStorage.getItem('role') === 'MERCHANT') loadChatUnreadDot()
  }, 5000)
  window.addEventListener('petshop-notice-updated', handleNoticeUpdated)
  window.addEventListener('petshop-chat-unread-updated', handleChatUnreadUpdated)
  window.addEventListener('petshop-admin-support-unread-updated', loadAdminSupportUnread)
  window.addEventListener('petshop-merchant-order-todo-updated', handleMerchantOrderTodoUpdated)
  window.addEventListener('petshop-user-avatar-updated', handleUserAvatarUpdated)
  window.addEventListener('petshop-auth-cleared', handleAuthCleared)
  window.addEventListener('petshop-auth-updated', handleAuthUpdated)
  void validateStoredUserSession()
})

onUnmounted(() => {
  if (unreadTimer) clearInterval(unreadTimer)
  unreadTimer = null
  if (adminSupportBadgeTimer) clearInterval(adminSupportBadgeTimer)
  adminSupportBadgeTimer = null
  if (merchantChatPollTimer) clearInterval(merchantChatPollTimer)
  merchantChatPollTimer = null
  window.removeEventListener('petshop-notice-updated', handleNoticeUpdated)
  window.removeEventListener('petshop-chat-unread-updated', handleChatUnreadUpdated)
  window.removeEventListener('petshop-admin-support-unread-updated', loadAdminSupportUnread)
  window.removeEventListener('petshop-merchant-order-todo-updated', handleMerchantOrderTodoUpdated)
  window.removeEventListener('petshop-user-avatar-updated', handleUserAvatarUpdated)
  window.removeEventListener('petshop-auth-cleared', handleAuthCleared)
  window.removeEventListener('petshop-auth-updated', handleAuthUpdated)
})

function logout() {
  clearAuthLocalStorage()
  resetUserAvatarSession()
  syncAuthState()
  window.location.href = '#/login'
}

/** JWT 失效或未授权：与 request 层清理保持一致，并离开需登录的页面 */
function handleAuthCleared() {
  syncAuthState()
  resetUserAvatarSession()
  const p = String(route.path || '')
  if (p === '/login' || p === '/admin-login') return
  if (p.startsWith('/admin')) router.replace('/admin-login')
  else router.replace('/login')
}

/** 登录成功写入 localStorage 后，立即刷新顶栏（避免仍显示上一账号昵称/头像） */
async function handleAuthUpdated() {
  syncAuthState()
  const r = localStorage.getItem('role') || ''
  const uid = Number(localStorage.getItem('userId') || 0)
  if (r === 'USER' && uid) {
    userAvatarPrimed.value = false
    try {
      await refreshUserAvatarFromApi()
    } finally {
      userAvatarPrimed.value = true
    }
  } else {
    resetUserAvatarSession()
  }
  void loadUnreadCount()
  void loadChatUnreadDot()
  void loadAdminSupportUnread()
  void loadMerchantOrderTodos()
}

/**
 * 冷启动：若仍带有 USER 的 token，拉一次资料写回 nickname/avatar，与后端一致；401 则走统一登出。
 */
async function validateStoredUserSession() {
  const token = localStorage.getItem('accessToken')
  const roleLs = localStorage.getItem('role')
  if (!token || roleLs !== 'USER') return
  const uid = Number(localStorage.getItem('userId') || 0)
  if (!uid) return
  const res = await api.userGetProfile(uid)
  if (res.code === 200 && res.data) {
    const nick = String(res.data.nickname || '').trim()
    localStorage.setItem('nickname', nick)
    localStorage.setItem('userAvatarUrl', String(res.data.avatarUrl || '').trim())
    syncAuthState()
    return
  }
  if (res.code === 401 || res.code === 403) {
    handleAuthCleared()
  }
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
  <div
    class="app"
    :class="{ 'app-layout-admin': isAdminRoute, 'app--merchant-shell': showMerchantShell }"
  >
    <aside v-if="isAdminRoute" class="admin-sidebar">
      <div class="admin-sidebar-brand">
        <span class="admin-sidebar-logo">管理中枢</span>
      </div>
      <nav class="admin-nav" aria-label="管理后台导航">
        <RouterLink class="admin-nav-link" to="/admin">运营总览</RouterLink>
        <RouterLink class="admin-nav-link" to="/admin/products">商品管理</RouterLink>
        <RouterLink class="admin-nav-link" to="/admin/inventory">库存监控</RouterLink>
        <RouterLink class="admin-nav-link" to="/admin/orders">订单管理</RouterLink>
        <RouterLink class="admin-nav-link" to="/admin/accounts">账号管理</RouterLink>
        <RouterLink class="admin-nav-link" to="/admin/audit-logs">审计日志</RouterLink>
        <RouterLink class="admin-nav-link admin-nav-link--badge" to="/admin/support">
          平台客服
          <span v-if="adminSupportUnreadDot" class="admin-nav-dot" aria-hidden="true" />
        </RouterLink>
      </nav>
    </aside>

    <div v-if="isAdminRoute" class="admin-shell-right">
      <header class="admin-topbar">
        <div class="admin-topbar-right">
          <div class="admin-topbar-icons">
            <button type="button" class="admin-topbar-icon-btn" title="通知（演示占位）" aria-label="通知">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                <path
                  d="M12 22a2.5 2.5 0 01-2.45-2h4.9A2.5 2.5 0 0112 22z"
                  fill="currentColor"
                />
                <path
                  d="M18 16H6l-.5-.6C7 13.7 7 11.9 7 10a5 5 0 019.9-.9L17 9c0 1.9 0 3.7 1.5 6.4l-.5.6z"
                  stroke="currentColor"
                  stroke-width="1.7"
                  stroke-linejoin="round"
                />
              </svg>
            </button>
            <button type="button" class="admin-topbar-icon-btn" title="帮助（演示占位）" aria-label="帮助">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                <circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="1.7" />
                <path
                  d="M10 10a2 2 0 114 0c0 2-3 1.8-3 4"
                  stroke="currentColor"
                  stroke-width="1.7"
                  stroke-linecap="round"
                />
                <circle cx="12" cy="17.5" r="1.2" fill="currentColor" />
              </svg>
            </button>
          </div>
          <div class="admin-topbar-divider" aria-hidden="true" />
          <div class="admin-topbar-profile">
            <div class="admin-topbar-avatar">{{ adminHubInitials }}</div>
            <div class="admin-topbar-user">
              <span class="admin-topbar-user-label">平台管理员</span>
              <strong class="admin-topbar-user-name">{{ adminName || 'admin' }}</strong>
            </div>
            <span class="admin-topbar-chevron" aria-hidden="true">
              <svg width="10" height="10" viewBox="0 0 24 24" fill="none">
                <path d="M6 9l6 6 6-6" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
              </svg>
            </span>
          </div>
          <a class="admin-topbar-logout" href="javascript:void(0)" @click="logout">退出</a>
        </div>
      </header>

      <main class="main main-admin">
        <RouterView v-slot="{ Component }">
          <KeepAlive :include="['HomeView', 'ProductListView']">
            <component
              :is="Component"
              :key="
                route.name === 'home' || route.name === 'products'
                  ? String(route.name)
                  : route.fullPath
              "
            />
          </KeepAlive>
        </RouterView>
      </main>
    </div>

    <aside v-if="showMerchantShell" class="merchant-aside" aria-label="商家功能导航">
      <div class="merchant-aside-brand">商家控制台</div>
      <nav class="merchant-aside-nav">
        <RouterLink class="merchant-nav-link" to="/merchant">商家首页</RouterLink>
        <RouterLink class="merchant-nav-link" to="/merchant/products">我的商品</RouterLink>
        <RouterLink class="merchant-nav-link merchant-nav-link--badge" to="/merchant/orders">
          订单管理
          <span v-if="orderTodoShipDot" class="nav-badge-dot nav-badge-dot--ship" aria-hidden="true" />
        </RouterLink>
        <RouterLink class="merchant-nav-link merchant-nav-link--badge" to="/merchant/notifications">
          通知中心
          <span v-if="unreadCount > 0" class="nav-badge">{{ unreadCount > 99 ? '99+' : unreadCount }}</span>
        </RouterLink>
        <RouterLink class="merchant-nav-link merchant-nav-link--badge" to="/merchant/support">
          用户咨询
          <span v-if="chatUnreadPeerDot" class="nav-badge-dot" aria-hidden="true" />
        </RouterLink>
        <RouterLink class="merchant-nav-link merchant-nav-link--badge" to="/merchant/contact-admin">
          联系平台
          <span v-if="chatUnreadPlatformDot" class="nav-badge-dot" aria-hidden="true" />
        </RouterLink>
        <RouterLink class="merchant-nav-link" to="/merchant/profile">商家资料</RouterLink>
      </nav>
      <p class="merchant-aside-note">商家工作台</p>
    </aside>

    <header v-if="showMerchantShell" class="merchant-topbar">
      <h2 class="merchant-topbar-title">{{ merchantShellPageTitle }}</h2>
      <div class="merchant-topbar-right">
        <span class="merchant-topbar-user">商家：{{ adminName || 'merchant' }}</span>
        <a class="merchant-topbar-logout" href="javascript:void(0)" @click="logout">退出</a>
      </div>
    </header>

    <!-- 管理端自有顶栏；商家端自有 merchant 壳层。此处不再渲染全站顶栏，避免与 /admin、/merchant 叠层 -->
    <header v-else-if="!isAdminLoginRoute && !isAdminRoute && role !== 'MERCHANT'" class="header">
      <div class="container">
        <RouterLink
          :to="role === 'ADMIN' ? '/admin' : '/'"
          class="logo logo--with-mark"
          :aria-label="role === 'ADMIN' ? '管理员控制台，返回管理首页' : '宠物商城首页'"
        >
          <img
            class="logo-mark"
            src="/pet-shop-mark.png"
            alt=""
            width="40"
            height="40"
            loading="eager"
            decoding="async"
          />
          <span class="logo-text">{{
            role === 'ADMIN' ? '管理员控制台' : '宠物商城'
          }}</span>
        </RouterLink>
        <nav class="nav">
          <template v-if="!role">
            <RouterLink to="/">首页</RouterLink>
            <RouterLink to="/products">商品列表</RouterLink>
            <RouterLink to="/ai">AI 导购</RouterLink>
          </template>
          <template v-else-if="role === 'USER'">
            <RouterLink to="/">首页</RouterLink>
            <RouterLink to="/products">商品列表</RouterLink>
            <RouterLink to="/ai">AI 导购</RouterLink>
            <RouterLink to="/cart">购物车</RouterLink>
            <RouterLink to="/orders">我的订单</RouterLink>
            <RouterLink to="/notifications" class="nav-link-with-badge">
              通知中心
              <span v-if="unreadCount > 0" class="nav-badge">{{ unreadCount > 99 ? '99+' : unreadCount }}</span>
            </RouterLink>
            <RouterLink to="/merchant-contact" class="nav-link-with-badge">
              联系商家
              <span v-if="chatUnreadPeerDot" class="nav-badge-dot" aria-hidden="true" />
            </RouterLink>
            <RouterLink to="/contact-admin" class="nav-link-with-badge">
              联系平台
              <span v-if="chatUnreadPlatformDot" class="nav-badge-dot" aria-hidden="true" />
            </RouterLink>
            <RouterLink to="/profile">个人中心</RouterLink>
          </template>
          <template v-else-if="role === 'ADMIN'">
            <RouterLink to="/admin">管理首页</RouterLink>
            <RouterLink to="/admin/products">商品管理</RouterLink>
            <RouterLink to="/admin/inventory">库存监控</RouterLink>
            <RouterLink to="/admin/orders">订单管理</RouterLink>
            <RouterLink to="/admin/accounts">账号管理</RouterLink>
            <RouterLink to="/admin/audit-logs">审计日志</RouterLink>
            <RouterLink to="/admin/support" class="nav-link-with-badge">
              平台客服
              <span v-if="adminSupportUnreadDot" class="nav-badge-dot" aria-hidden="true" />
            </RouterLink>
          </template>
          <template v-else>
            <RouterLink to="/merchant">商家首页</RouterLink>
            <RouterLink to="/merchant/products">我的商品</RouterLink>
            <RouterLink to="/merchant/orders" class="nav-link-with-badge">
              我的订单管理
              <span v-if="orderTodoShipDot" class="nav-badge-dot nav-badge-dot--ship" aria-hidden="true" />
            </RouterLink>
            <RouterLink to="/merchant/notifications" class="nav-link-with-badge">
              通知中心
              <span v-if="unreadCount > 0" class="nav-badge">{{ unreadCount > 99 ? '99+' : unreadCount }}</span>
            </RouterLink>
            <RouterLink to="/merchant/support" class="nav-link-with-badge">
              用户咨询
              <span v-if="chatUnreadPeerDot" class="nav-badge-dot" aria-hidden="true" />
            </RouterLink>
            <RouterLink to="/merchant/contact-admin" class="nav-link-with-badge">
              联系平台
              <span v-if="chatUnreadPlatformDot" class="nav-badge-dot" aria-hidden="true" />
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
              loading="lazy"
              decoding="async"
              @error="onUserAvatarImgError"
            />
            <span class="user-badge-text">用户：{{ nickname || 'user' }}</span>
          </span>
          <span v-else-if="role" class="user-badge">
            {{
              role === 'ADMIN'
                ? `管理员：${adminName || 'admin'}`
                : role === 'MERCHANT'
                  ? `商家：${adminName || 'merchant'}`
                  : `${adminName || ''}`
            }}
          </span>
          <a v-if="role" href="javascript:void(0)" @click="logout">退出</a>
        </nav>
      </div>
    </header>

    <main
      v-if="!isAdminRoute"
      class="main"
      :class="{
        'main-merchant': isMerchantRoute,
        'main-auth': isAuthShellRoute,
        'main--merchant-wide': showMerchantShell || isMerchantSupportRoute,
        'main--ai-full': isUserAiRoute,
      }"
    >
      <RouterView v-slot="{ Component }">
        <KeepAlive :include="['HomeView', 'ProductListView']">
          <component
            :is="Component"
            :key="
              route.name === 'home' || route.name === 'products'
                ? String(route.name)
                : route.fullPath
            "
          />
        </KeepAlive>
      </RouterView>
    </main>

    <footer v-if="!isAdminRoute && !isMerchantRoute && !isUserAiRoute" class="site-footer">
      <div class="footer-content">
        <p>宠物用品在线商城</p>
        <p class="footer-sub">© 2026 保留所有权利</p>
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
  /* 与商品列表 layout-main（--apex-bg #f5f5f5）同色系，避免底部与页脚出现冷灰/蓝灰跳变 */
  background: #f5f5f5;
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

.app-layout-admin {
  display: grid;
  grid-template-columns: minmax(220px, 252px) minmax(0, 1fr);
  grid-template-rows: 1fr;
  min-height: 100vh;
  max-width: 100%;
  /* 与侧栏同色：仅用背景区分左栏，不用线框 */
  background: #f3f4f6;
}

.admin-sidebar {
  grid-column: 1;
  grid-row: 1;
  display: flex;
  flex-direction: column;
  gap: 0;
  padding: 22px 14px 20px;
  background: #f3f4f6;
  border-right: none;
  box-shadow: none;
}

.admin-sidebar-brand {
  padding: 0 8px 12px;
  border-bottom: none;
  margin-bottom: 10px;
}

/* 右侧：顶栏浅灰白条 + 下方纯白主区，仅靠色差区分两块（无粗边框） */
.admin-shell-right {
  grid-column: 2;
  grid-row: 1;
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  min-width: 0;
  background: #ffffff;
}

.admin-sidebar-logo {
  display: block;
  font-size: 19px;
  font-weight: 800;
  letter-spacing: 0.06em;
  color: #0a1220;
  line-height: 1.25;
}

.admin-nav {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1;
  min-height: 0;
}

.admin-nav-link {
  display: block;
  padding: 11px 12px;
  border-radius: 10px;
  text-decoration: none;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.02em;
  color: #2c3d55;
  border: 1px solid transparent;
  border-right: 3px solid transparent;
  transition:
    background 0.15s ease,
    border-color 0.15s ease,
    color 0.15s ease;
}

.admin-nav-link:hover {
  background: rgba(0, 0, 0, 0.045);
  color: #111827;
}

.admin-nav-link.router-link-active:hover {
  background: #d1d5db;
}

.admin-nav-link.router-link-active {
  background: #e5e7eb;
  border: 1px solid transparent;
  border-right: 3px solid #f97316;
  color: #111827;
  box-shadow: none;
}

.admin-nav-link--badge {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.admin-nav-dot {
  flex-shrink: 0;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #ff4d4f;
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.85);
}

.admin-topbar {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 6px 20px 6px 18px;
  /* 与下方 #fff 主区形成柔和色块分界（仅靠色差，不用描边） */
  background: #f8fafc;
  border-bottom: none;
  box-shadow: none;
}

.admin-topbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
  margin-left: auto;
}

.admin-topbar-icons {
  display: flex;
  align-items: center;
  gap: 2px;
}

.admin-topbar-icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  padding: 0;
  border: none;
  border-radius: 9px;
  background: transparent;
  color: #64748b;
  cursor: default;
  transition:
    background 0.15s ease,
    color 0.15s ease;
}

.admin-topbar-icon-btn:hover {
  background: rgba(255, 255, 255, 0.72);
  color: #0f1a2e;
}

.admin-topbar-divider {
  width: 1px;
  height: 22px;
  background: rgba(100, 116, 139, 0.22);
  margin: 0 2px;
}

.admin-topbar-profile {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 2px 4px;
  border-radius: 10px;
  transition: background 0.15s ease;
}

.admin-topbar-profile:hover {
  background: rgba(255, 255, 255, 0.72);
}

.admin-topbar-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: linear-gradient(145deg, #0b1630 0%, #1e3a5f 100%);
  color: #f4f7fc;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.02em;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  border: 2px solid rgba(255, 255, 255, 0.95);
  box-shadow: 0 1px 4px rgba(11, 22, 48, 0.12);
}

.admin-topbar-user {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  min-width: 0;
}

.admin-topbar-user-label {
  font-size: 8px;
  font-weight: 800;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: #64748b;
}

.admin-topbar-user-name {
  font-size: 13px;
  font-weight: 800;
  color: #0f1a2e;
  max-width: 140px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  line-height: 1.15;
}

.admin-topbar-chevron {
  display: flex;
  color: #94a3b8;
  margin-right: 2px;
}

.admin-topbar-logout {
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.03em;
  text-transform: uppercase;
  color: #475569;
  text-decoration: none;
  padding: 6px 12px;
  border-radius: 8px;
  border: 1px solid rgba(148, 163, 184, 0.45);
  background: #ffffff;
  margin-left: 2px;
}

.admin-topbar-logout:hover {
  border-color: #0b1630;
  color: #0b1630;
  background: #fff;
}

.admin-shell-right > .main.main-admin {
  flex: 1;
  min-height: 0;
  overflow: auto;
  margin: 0;
  max-width: none;
  width: 100%;
  /* 顶栏已留白，此处不再叠一层上内边距（否则会像「标题上方空一大块」） */
  padding: 0 24px 32px 22px;
  background: #ffffff;
}

@media (max-width: 900px) {
  .app-layout-admin {
    grid-template-columns: 1fr;
    grid-template-rows: auto 1fr;
  }

  .admin-sidebar {
    grid-column: 1;
    grid-row: 1;
    flex-direction: column;
    flex-wrap: nowrap;
    align-items: stretch;
    gap: 0;
    padding: 14px 12px 12px;
  }

  .admin-sidebar-brand {
    margin-bottom: 8px;
    padding-bottom: 8px;
    flex: 0 0 auto;
    width: 100%;
    border-bottom: 1px solid rgba(0, 0, 0, 0.06);
  }

  .admin-nav {
    flex-direction: row;
    flex-wrap: wrap;
    flex: 0 1 auto;
    width: 100%;
    min-width: 0;
    gap: 6px;
    justify-content: flex-start;
  }

  .admin-nav-link {
    padding: 8px 10px;
    font-size: 12px;
    flex-shrink: 0;
    white-space: nowrap;
  }

  .admin-shell-right {
    grid-column: 1;
    grid-row: 2;
    min-height: 0;
  }

  .admin-topbar {
    flex-wrap: wrap;
    gap: 8px;
    padding: 6px 12px;
  }

  .admin-topbar-right {
    order: 1;
    width: 100%;
    justify-content: flex-end;
    flex-wrap: wrap;
    gap: 8px;
  }

  .admin-shell-right > .main.main-admin {
    padding: 0 14px 24px;
  }
}

.header {
  background: linear-gradient(180deg, rgba(251, 253, 255, 0.92) 0%, rgba(243, 247, 252, 0.88) 100%);
  border-bottom: 1px solid #d7deea;
  box-shadow: 0 12px 24px rgba(13, 25, 42, 0.08);
  backdrop-filter: blur(10px);
  position: sticky;
  top: 0;
  z-index: 1000;
}

.container {
  width: 100%;
  max-width: 1880px;
  margin: 0 auto;
  padding: 8px 12px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  row-gap: 10px;
  column-gap: 12px;
  min-height: 64px;
  height: auto;
  box-sizing: border-box;
}

.logo {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
  margin: 0;
  font-size: 22px;
  color: #0d1626;
  font-weight: 800;
  letter-spacing: 0.2px;
  line-height: 1.2;
  text-decoration: none;
}

.logo--with-mark {
  cursor: pointer;
}

.logo--with-mark:hover .logo-text {
  color: #0a1628;
}

.logo--with-mark:focus-visible {
  outline: 2px solid #2563eb;
  outline-offset: 4px;
  border-radius: 12px;
}

.logo-mark {
  width: 40px;
  height: 40px;
  object-fit: cover;
  border-radius: 50%;
  flex-shrink: 0;
  display: block;
  border: 1px solid rgba(15, 23, 42, 0.12);
  background: #fff;
}

.logo-text {
  font-size: 22px;
  font-weight: 800;
  letter-spacing: 0.2px;
  color: #0d1626;
}

.nav {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
  justify-content: flex-end;
  flex: 1 1 auto;
  min-width: 0;
}

.nav a {
  text-decoration: none;
  color: #4f5e74;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0.2px;
  padding: 7px 10px;
  border-radius: 10px;
  transition: all 0.2s;
  flex-shrink: 0;
  white-space: nowrap;
}

.nav a:hover {
  background-color: #e8edf4;
  color: #1d2d46;
  transform: translateY(-1px);
}

.nav a.router-link-active {
  background: linear-gradient(135deg, #101a2c 0%, #0a111d 100%);
  color: #f4f6fb;
  box-shadow: 0 8px 18px rgba(8, 14, 24, 0.22);
}

.nav-link-with-badge {
  position: relative;
  display: inline-flex;
  align-items: center;
  flex-shrink: 0;
  white-space: nowrap;
}

.nav-badge {
  margin-left: 6px;
  min-width: 22px;
  height: 22px;
  padding: 0 7px;
  border-radius: 999px;
  background: #ff4d4f;
  color: #fff;
  font-size: 13px;
  line-height: 22px;
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
  font-size: 11px;
  padding: 6px 8px;
  background: #eef2f7;
  border: 1px solid #d9e0ea;
  border-radius: 8px;
  flex-shrink: 0;
  white-space: nowrap;
  max-width: 100%;
}

.user-badge--with-avatar {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.user-avatar {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  object-fit: cover;
  flex-shrink: 0;
  border: 1px solid #d0d8e4;
  background: #fff;
}

.user-badge-text {
  line-height: 1.2;
}

.main {
  flex: 1;
  width: 100%;
  max-width: 1880px;
  margin: 18px auto;
  padding: 0 14px;
}

/* 用户端主区铺满 flex 剩余高度时与页脚同色，避免透明露出旧渐变底 */
.main:not(.main-admin) {
  background-color: #f5f5f5;
}

/* 商家侧栏壳层 / 用户咨询：主区横向铺满 */
.main.main--merchant-wide {
  max-width: none;
  width: 100%;
  margin-left: 0;
  margin-right: 0;
  padding-left: 16px;
  padding-right: 16px;
}

/* 用户端 AI 导购：去掉主区 max-width，让页面根 flex 吃满剩余视口（无页脚时尽量贴底） */
.main.main--ai-full:not(.main-merchant) {
  max-width: none;
  width: 100%;
  margin-top: 0;
  margin-bottom: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  flex: 1 1 0%;
  min-height: 0;
}

.main.main--ai-full:not(.main-merchant) > * {
  flex: 1 1 0%;
  min-height: 0;
  width: 100%;
}

/* ---------- 商家一体化壳：左栏 + 顶栏 + 主区（网格） ---------- */
.app.app--merchant-shell {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  grid-template-rows: auto 1fr;
  grid-template-areas:
    'merc-aside merc-top'
    'merc-aside merc-main';
  min-height: 100vh;
  width: 100%;
}

.app.app--merchant-shell > .merchant-aside {
  grid-area: merc-aside;
}

.app.app--merchant-shell > .merchant-topbar {
  grid-area: merc-top;
}

.app.app--merchant-shell > .main.main-merchant {
  grid-area: merc-main;
  min-height: 0;
  overflow: auto;
  margin-top: 0;
  margin-bottom: 0;
  flex: unset;
}

.merchant-aside {
  display: flex;
  flex-direction: column;
  min-height: 0;
  padding: 18px 12px 16px;
  background: #eceff2;
  border-right: 1px solid #e5e7eb;
  box-sizing: border-box;
}

.merchant-aside-brand {
  font-size: 15px;
  font-weight: 800;
  letter-spacing: 0.04em;
  color: #0f172a;
  padding: 0 10px 14px;
  margin-bottom: 8px;
  border-bottom: 1px solid #dde1e6;
}

.merchant-aside-nav {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1;
  min-height: 0;
}

.merchant-nav-link {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 0;
  text-decoration: none;
  font-size: 13px;
  font-weight: 700;
  color: #334155;
  border: 1px solid transparent;
  border-right: 3px solid transparent;
  transition:
    background 0.15s ease,
    color 0.15s ease;
}

.merchant-nav-link:hover {
  background: rgba(255, 255, 255, 0.55);
  color: #0f172a;
}

.merchant-nav-link.router-link-active {
  background: #ffffff;
  color: #0f172a;
  border-right-color: #0f172a;
}

.merchant-nav-link--badge .nav-badge-dot,
.merchant-nav-link--badge .nav-badge {
  flex-shrink: 0;
}

.merchant-aside-note {
  margin: 14px 8px 0;
  font-size: 11px;
  font-weight: 600;
  color: #94a3b8;
  line-height: 1.45;
}

.merchant-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 20px;
  background: #ffffff;
  border-bottom: 1px solid #e5e7eb;
  box-sizing: border-box;
}

.merchant-topbar-title {
  margin: 0;
  font-size: 17px;
  font-weight: 800;
  color: #0f172a;
  letter-spacing: -0.02em;
}

.merchant-topbar-right {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-shrink: 0;
}

.merchant-topbar-user {
  font-size: 13px;
  font-weight: 600;
  color: #64748b;
}

.merchant-topbar-logout {
  font-size: 13px;
  font-weight: 700;
  color: #0f172a;
  text-decoration: none;
  padding: 6px 12px;
  border: 1px solid #e5e7eb;
  border-radius: 0;
  background: #fafafa;
}

.merchant-topbar-logout:hover {
  background: #f1f5f9;
}

@media (max-width: 900px) {
  .app.app--merchant-shell {
    grid-template-columns: 1fr;
    grid-template-rows: auto auto 1fr;
    grid-template-areas:
      'merc-aside'
      'merc-top'
      'merc-main';
  }

  .merchant-aside {
    flex-direction: row;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px;
    padding: 12px;
    border-right: none;
    border-bottom: 1px solid #e5e7eb;
  }

  .merchant-aside-brand {
    width: 100%;
    margin-bottom: 0;
    padding-bottom: 8px;
    border-bottom: 1px solid #dde1e6;
  }

  .merchant-aside-nav {
    flex-direction: row;
    flex-wrap: wrap;
    flex: 1 1 auto;
  }

  .merchant-nav-link {
    flex: 0 1 auto;
    border-right: none;
    white-space: nowrap;
    flex-shrink: 0;
  }

  .merchant-nav-link.router-link-active {
    border-right-color: transparent;
    border-bottom: 2px solid #0f172a;
  }

  .merchant-aside-note {
    display: none;
  }
}

.site-footer {
  background: #f5f5f5;
  border-top: 1px solid rgba(0, 0, 0, 0.06);
  color: #737373;
  padding: 20px 0 28px;
  margin-top: 32px;
}

.footer-content {
  width: 100%;
  max-width: 1880px;
  margin: 0 auto;
  padding: 0 14px;
  text-align: center;
}

.footer-content p {
  margin: 5px 0;
  font-size: 12px;
}

.footer-sub {
  color: #a3a3a3;
  font-size: 11px;
}

body {
  font-family: 'Microsoft YaHei', 'PingFang SC', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
}

/* 用户端统一字号（不使用强制 !important） */
.main:not(.main-admin):not(.main-merchant):not(.main-auth) {
  font-size: 12px;
}

/*
 * 以下用 :where(...) 将特异性降为 0，否则 .main … :is(button)、:is(p) 等会压过各页 scoped 样式
 *（例如商品详情 Tab、正文放大完全不生效）。
 */
:where(.main:not(.main-admin):not(.main-merchant):not(.main-auth) h1) {
  font-size: clamp(20px, 1.45vw, 25px);
}

:where(.main:not(.main-admin):not(.main-merchant):not(.main-auth) h2) {
  font-size: clamp(17px, 1.3vw, 22px);
}

:where(.main:not(.main-admin):not(.main-merchant):not(.main-auth) h3) {
  font-size: clamp(15px, 1.1vw, 18px);
}

:where(
  .main:not(.main-admin):not(.main-merchant):not(.main-auth) :is(p, li, td, th, label, .text, .desc, .meta)
) {
  font-size: 11px;
}

/* 提高权重，覆盖用户端页面里的小字号写死 */
:where(
  .main:not(.main-admin):not(.main-merchant):not(.main-auth)
    .pw-page
    :is(.status, .hint, .meta, .small, .subtitle, .section-subtitle, .badge, .chip, .tag, .pill)
) {
  font-size: 10px;
}

:where(
  .main:not(.main-admin):not(.main-merchant):not(.main-auth)
    :is(button, .btn, .action-btn, .tool-btn, .tab-btn, .category-btn, .search-main-btn, .hero-btn)
) {
  min-height: 34px;
  font-size: 10px;
}

:where(.main:not(.main-admin):not(.main-merchant):not(.main-auth) :is(input, select, textarea)) {
  min-height: 32px;
  font-size: 11px;
}

/*
 * 首页 HomeView（.home--premium）：解除「全站缩小字号」对 p/button/input 的覆盖，
 * 否则卡片标题、分类按钮等会被压成 11px/10px，与本地样式冲突。
 */
.main:not(.main-admin):not(.main-merchant):not(.main-auth)
  .home--premium
  :is(p, li, td, th, label, .text, .desc, .meta) {
  font-size: inherit;
}
.main:not(.main-admin):not(.main-merchant):not(.main-auth)
  .home--premium
  :is(button, .btn, .action-btn, .tool-btn, .tab-btn, .category-btn, .search-main-btn, .hero-btn) {
  font-size: inherit;
  min-height: unset;
}
.main:not(.main-admin):not(.main-merchant):not(.main-auth) .home--premium :is(input, select, textarea) {
  font-size: inherit;
  min-height: unset;
}

/*
 * 商品列表（ProductListView）：解除全站 :where(button) 的 min-height: 34px，
 * 避免与放大后的 .cta-btn 字号、内边距打架；字号仍由页面 scoped 的 .cta-btn 控制。
 */
.main:not(.main-admin):not(.main-merchant):not(.main-auth) .product-list--apex .product-card button.cta-btn {
  min-height: unset;
}

/*
 * 商品详情评价区：解除全站对 p 的默认压制（评价正文可读性）。星级为 span[role=button]，不走全局 button 规则。
 */
.main:not(.main-admin):not(.main-merchant):not(.main-auth)
  .product-detail--apex.pw-page
  .pd-reviews-panel
  p.review-content {
  font-size: inherit;
}

/* 购物车 Apex 版：解除全站对 li / p 的 11px，由页面 scoped 控制 */
.main:not(.main-admin):not(.main-merchant):not(.main-auth)
  .cart-page--apex.pw-page
  :is(.cart-apex-benefit li, .cart-apex-logistics-body, .cart-apex-desc, .cart-apex-sku) {
  font-size: inherit;
}

.main:not(.main-admin):not(.main-merchant):not(.main-auth)
  .cart-page--apex.pw-page
  :is(.cart-apex-qty-btn, .cart-apex-btn--primary, .cart-apex-btn--ghost, .cart-apex-title-btn) {
  min-height: unset;
}

/* 结算主按钮：保留页面内 min-height / 字号，不被全局 button 规则压扁 */
.main:not(.main-admin):not(.main-merchant):not(.main-auth) .cart-page--apex.pw-page .cart-apex-btn--checkout {
  min-height: 56px;
  font-size: 15px;
}

/* 我的订单 Apex：解除全站对 p / button 的压制，与购物车页一致 */
.main:not(.main-admin):not(.main-merchant):not(.main-auth)
  .orders-page--apex.pw-page
  :is(
    .orders-apex-kicker,
    .orders-apex-lead,
    .orders-bulk-banner,
    .orders-bulk-banner-text,
    .orders-apex-state-text,
    .orders-empty-title,
    .orders-empty-desc,
    .orders-card-hint,
    .orders-summary-note
  ) {
  font-size: inherit;
}

.main:not(.main-admin):not(.main-merchant):not(.main-auth)
  .orders-page--apex.pw-page
  :is(.orders-tab, .orders-btn-ghost, .orders-btn-primary, .orders-bulk-dismiss) {
  min-height: unset;
  font-size: inherit;
}

.main:not(.main-admin):not(.main-merchant):not(.main-auth) .orders-page--apex.pw-page .orders-btn-checkout {
  min-height: 56px;
  font-size: 15px;
}

/* 通知中心：解除全站对 p / button 的压制 */
.main:not(.main-admin):not(.main-merchant):not(.main-auth)
  .notice-page--apex.pw-page
  :is(.sidebar-lead, .notice-toolbar-meta, .notice-empty-desc, .notice-card__body, .notice-muted) {
  font-size: inherit;
}

.main:not(.main-admin):not(.main-merchant):not(.main-auth)
  .notice-page--apex.pw-page
  :is(.facet-chip, .notice-btn-ghost, .notice-btn-outline, .notice-link) {
  min-height: unset;
  font-size: inherit;
}

/* 联系商家列表：解除全站字号 / 按钮压制 */
.main:not(.main-admin):not(.main-merchant):not(.main-auth)
  .mc-page--apex.pw-page
  :is(
    .sidebar-lead,
    .sidebar-tip-text,
    .mc-toolbar-meta,
    .mc-empty-desc,
    .mc-card__meta-row dt,
    .mc-card__meta-row dd,
    .mc-card__product-extra,
    .mc-card__product-title,
    .mc-unread-pill
  ) {
  font-size: inherit;
}

.main:not(.main-admin):not(.main-merchant):not(.main-auth)
  .mc-page--apex.pw-page
  :is(.facet-chip, .mc-btn-ghost, .mc-btn-primary, .mc-btn-outline) {
  min-height: unset;
  font-size: inherit;
}

/* 订单详情 Apex：解除全站字号 / 按钮压制 */
.main:not(.main-admin):not(.main-merchant):not(.main-auth)
  .order-page--apex.pw-page
  :is(
    .sidebar-lead,
    .sidebar-tip-text,
    .od-toolbar-meta,
    .od-breadcrumb,
    .od-hero__note,
    .od-dl-row,
    .od-line__meta,
    .od-tips-list,
    .od-state
  ) {
  font-size: inherit;
}

.main:not(.main-admin):not(.main-merchant):not(.main-auth)
  .order-page--apex.pw-page
  :is(.facet-chip, .od-btn-primary, .od-btn-outline, .od-breadcrumb-link) {
  min-height: unset;
  font-size: inherit;
}

/* 联系平台（用户）：与联系商家同系 Apex 双栏 */
.main:not(.main-admin):not(.main-merchant):not(.main-auth)
  .ca-page--apex.pw-page
  :is(
    .sidebar-lead,
    .sidebar-tip-text,
    .ca-toolbar-meta,
    .ca-scope-text,
    .ca-side-session-sub,
    .ca-state,
    .pw-chat-head-lead
  ) {
  font-size: inherit;
}

.main:not(.main-admin):not(.main-merchant):not(.main-auth)
  .ca-page--apex.pw-page
  :is(.facet-chip, .ca-btn-ghost, .ca-btn-primary, .ca-quick-btn) {
  min-height: unset;
  font-size: inherit;
}

/* 个人中心 Apex */
.main:not(.main-admin):not(.main-merchant):not(.main-auth)
  .profile-page--apex.pw-page
  :is(
    .sidebar-lead,
    .sidebar-tip-text,
    .pf-toolbar-meta,
    .pf-promo-text,
    .pf-logistics-line,
    .pf-account-lead,
    .pf-empty-text,
    .pf-state,
    .pf-os-label,
    .profile-label
  ) {
  font-size: inherit;
}

.main:not(.main-admin):not(.main-merchant):not(.main-auth)
  .profile-page--apex.pw-page
  :is(
    .pf-meta-link,
    .pf-tool-btn,
    .pf-tb-ghost,
    .pf-os,
    .pf-promo-strip,
    .pf-logistics-link,
    .pf-btn-primary,
    .pf-btn-outline,
    .pf-input
  ) {
  min-height: unset;
  font-size: inherit;
}

/* 咨询会话页 */
.main:not(.main-admin):not(.main-merchant):not(.main-auth)
  .chat-page--apex.pw-page
  :is(.chat-shell-lead, .chat-left-desc, .active-conversation-sub, .pw-chat-head-lead) {
  font-size: inherit;
}

.main:not(.main-admin):not(.main-merchant):not(.main-auth)
  .chat-page--apex.pw-page
  :is(.chat-back-btn, .topic-item, .quick-btn) {
  min-height: unset;
  font-size: inherit;
}

/* 管理端：右侧已是白板，页面容器不再套一层描边大卡片，由内层 KPI/表格卡片承担层次 */
.main.main-admin .admin-page {
  background: transparent;
  border: none;
  border-radius: 0;
  padding: 12px 28px 28px;
  box-shadow: none;
}

/* 商品管理：收紧页头与 KPI 上方留白，贴近 Apex Paws 密度 */
.main.main-admin .admin-page.prod-mod {
  padding: 12px 24px 24px;
}

/* 商家端：外层不再套渐变泡泡卡片，扁平分割见 assets/merchant-flat.css */
.main.main-merchant :is(.admin-page, .dashboard, .merchant-page) {
  background: transparent;
  border: none;
  border-radius: 0;
  padding: 0;
  box-shadow: none;
}

.main.main-admin :is(h1, h2),
.main.main-merchant :is(h1, h2) {
  color: #0f1a2e;
  letter-spacing: 0.02em;
}

.main.main-admin :is(.desc, .subtitle, .section-subtitle) {
  color: #5a6b82;
  font-size: 13px;
  line-height: 1.55;
}

.main.main-merchant :is(.desc, .subtitle, .section-subtitle) {
  color: #5a6b82;
  font-size: 14px;
  line-height: 1.55;
}

.main.main-admin :is(.toolbar, .filters),
.main.main-merchant :is(.toolbar, .filters) {
  gap: 12px;
  margin-bottom: 16px;
}

.main.main-admin :is(.tool-btn, .create-btn, .search-btn, .reset-btn, .action-btn, .tab-btn, .btn, .sort-th) {
  min-height: 36px;
  border-radius: 10px;
  font-size: 13px;
  font-weight: 700;
}

.main.main-merchant :is(.tool-btn, .create-btn, .search-btn, .reset-btn, .action-btn, .tab-btn, .btn, .sort-th) {
  min-height: 38px;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 700;
}

.main.main-admin :is(.filter-input, .filter-select, .cell-input) {
  min-height: 36px;
  border-radius: 10px;
  font-size: 13px;
}

.main.main-merchant :is(.filter-input, .filter-select, .cell-input) {
  min-height: 38px;
  border-radius: 10px;
  font-size: 14px;
}

.main.main-admin :is(.panel, .section, .card, .metric-card, .chart-section, .orders-section, .inventory-section),
.main.main-merchant :is(.panel, .section, .card, .metric-card, .chart-section, .orders-section, .inventory-section) {
  border-radius: 14px;
  border-color: #dbe3ef;
}

.main.main-admin :is(.stats, .card-list, .metrics-grid, .content-grid),
.main.main-merchant :is(.stats, .card-list, .metrics-grid, .content-grid) {
  gap: 16px;
}

.main.main-admin :is(table, .table),
.main.main-merchant :is(table, .table) {
  width: 100%;
  border-collapse: separate;
  border-spacing: 0;
  overflow: hidden;
  border: 1px solid #d0dcee;
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 4px 14px rgba(12, 24, 48, 0.05);
}

.main.main-merchant :is(table, .table) {
  border-radius: 0;
  box-shadow: none;
}

.main.main-admin :is(table, .table) thead th {
  background: linear-gradient(180deg, #f2f6fc 0%, #e8eef7 100%);
  color: #1f2d44;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.04em;
  border-bottom: 1px solid #d0dcee;
}

.main.main-merchant :is(table, .table) thead th {
  background: linear-gradient(180deg, #f2f6fc 0%, #e8eef7 100%);
  color: #1f2d44;
  font-size: 13px;
  font-weight: 800;
  border-bottom: 1px solid #d0dcee;
}

.main.main-admin :is(table, .table) th,
.main.main-admin :is(table, .table) td {
  padding: 11px 12px;
  font-size: 13px;
}

.main.main-admin :is(table, .table) tbody td {
  color: #1e293b;
}

.main.main-merchant :is(table, .table) th,
.main.main-merchant :is(table, .table) td {
  padding: 12px 12px;
  font-size: 14px;
}

.main.main-admin :is(table, .table) tbody tr:nth-child(even) td,
.main.main-merchant :is(table, .table) tbody tr:nth-child(even) td {
  background: #fafbfd;
}

.main.main-admin :is(table, .table) tbody tr:hover td,
.main.main-merchant :is(table, .table) tbody tr:hover td {
  background: #eef4ff;
}

/* 二次兜底：防止局部页面把字体/按钮压得过小 */
.main.main-admin :is(button, .btn, .action-btn, .tool-btn, .tab-btn) {
  min-height: 36px;
  font-size: 13px;
}

.main.main-merchant :is(button, .btn, .action-btn, .tool-btn, .tab-btn) {
  min-height: 38px;
  font-size: 14px;
}

/* 排除 checkbox/radio：否则会被 min-height 拉成「细长输入框」状 */
.main.main-admin :is(input:not([type='checkbox']):not([type='radio']), select, textarea) {
  min-height: 36px;
  font-size: 13px;
}

.main.main-merchant :is(input:not([type='checkbox']):not([type='radio']), select, textarea) {
  min-height: 38px;
  font-size: 14px;
}

/* 管理端：贴近控制台模板的西文 UI 栈（中文回退保留） */
.main.main-admin {
  margin-top: 0;
  margin-bottom: 0;
  /* 壳层为浅色白板；系统深色偏好下 body 仍可能为浅色字，须在此强制可读正文色 */
  color: #1e293b;
  color-scheme: light;
  font-family:
    system-ui,
    -apple-system,
    'Segoe UI',
    Roboto,
    'PingFang SC',
    'Microsoft YaHei',
    sans-serif;
}

/* 商家端主区同为浅色壳，避免系统深色偏好下继承 body 浅色字 */
.main.main-merchant {
  color: #1e293b;
  color-scheme: light;
}

.main.main-merchant :is(table, .table) tbody td {
  color: #1e293b;
}

/* 用户端指定页面：订单/通知/联系商家及其子页整体放大 10% */
</style>
