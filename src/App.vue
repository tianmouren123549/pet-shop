<script setup>
import { RouterLink, RouterView } from 'vue-router'
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from './utils/request'

const route = useRoute()
const router = useRouter()
const role = ref('')
const nickname = ref('')
const adminName = ref('')
const unreadCount = ref(0)
let unreadTimer = null

function handleNoticeUpdated() {
  loadUnreadCount()
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
}

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
    unreadCount.value = arr.filter((n) => Number(n.readStatus) !== 1).length
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
    unreadCount.value = arr.filter((n) => Number(n.readStatus) !== 1).length
    return
  }

  unreadCount.value = 0
}

watch(
  () => route.fullPath,
  async () => {
    syncAuthState()
    await loadUnreadCount()
  },
  { immediate: true }
)

onMounted(() => {
  unreadTimer = setInterval(() => {
    loadUnreadCount()
  }, 15000)
  window.addEventListener('petshop-notice-updated', handleNoticeUpdated)
})

onUnmounted(() => {
  if (unreadTimer) clearInterval(unreadTimer)
  unreadTimer = null
  window.removeEventListener('petshop-notice-updated', handleNoticeUpdated)
})

function logout() {
  localStorage.removeItem('role')
  localStorage.removeItem('userId')
  localStorage.removeItem('nickname')
  localStorage.removeItem('adminId')
  localStorage.removeItem('adminName')
  syncAuthState()
  window.location.href = '#/login'
}

function requireLoginThenGo(path, expectedRole) {
  syncAuthState()
  if (!role.value) {
    alert('请先登录')
    return
  }
  if (expectedRole && role.value !== expectedRole) {
    alert('请先登录')
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
            <RouterLink to="/admin/orders">订单管理</RouterLink>
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
            <RouterLink to="/merchant-contact">联系商家</RouterLink>
            <a href="javascript:void(0)" @click="requireLoginThenGo('/profile', 'USER')">个人中心</a>
          </template>
          <template v-else-if="role === 'ADMIN'">
            <RouterLink to="/admin">管理首页</RouterLink>
            <RouterLink to="/admin/products">商品管理</RouterLink>
            <RouterLink to="/admin/orders">订单管理</RouterLink>
          </template>
          <template v-else>
            <RouterLink to="/merchant">商家首页</RouterLink>
            <RouterLink to="/merchant/products">我的商品</RouterLink>
            <RouterLink to="/merchant/orders">我的订单管理</RouterLink>
            <RouterLink to="/merchant/notifications" class="nav-link-with-badge">
              通知
              <span v-if="unreadCount > 0" class="nav-badge">{{ unreadCount > 99 ? '99+' : unreadCount }}</span>
            </RouterLink>
            <a href="javascript:void(0)" @click="requireLoginThenGo('/merchant/support', 'MERCHANT')">用户咨询</a>
            <a href="javascript:void(0)" @click="requireLoginThenGo('/merchant/profile', 'MERCHANT')">商家资料</a>
          </template>

          <RouterLink v-if="!role" to="/login">登录</RouterLink>
          <span v-if="role" class="user-badge">
            {{
              role === 'ADMIN'
                ? `管理员：${adminName || 'admin'}`
                : role === 'MERCHANT'
                  ? `商家：${adminName || 'merchant'}`
                  : `用户：${nickname || 'user'}`
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
        <p class="footer-sub">毕业设计项目 © 2024</p>
      </div>
    </footer>
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

.user-badge {
  color: #58667a;
  font-size: 12px;
  padding: 7px 10px;
  background: #eef2f7;
  border: 1px solid #d9e0ea;
  border-radius: 3px;
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
