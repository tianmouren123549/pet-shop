<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { api } from '../../utils/request'
import { showAppMessage } from '../../utils/appMessage'
import PaginationBar from '../../components/PaginationBar.vue'
import ConfirmModal from '../../components/ConfirmModal.vue'

const activeTab = ref('users')
const users = ref([])
const merchants = ref([])
const loading = ref(false)
const keyword = ref('')
const appliedKeyword = ref('')
const page = ref(1)
const pageSize = ref(10)

const pwdOpen = ref(false)
const pwdSubmitting = ref(false)
const pwdTarget = ref({ kind: 'user', id: 0 })
const newPassword = ref('')

async function loadUsers() {
  loading.value = true
  const res = await api.adminListUsers()
  if (res.code === 200) {
    users.value = Array.isArray(res.data) ? res.data : []
  } else {
    showAppMessage(res.message || '用户列表加载失败', '提示')
    users.value = []
  }
  loading.value = false
}

async function loadMerchants() {
  loading.value = true
  const res = await api.adminListMerchants()
  if (res.code === 200) {
    merchants.value = Array.isArray(res.data) ? res.data : []
  } else {
    showAppMessage(res.message || '商家列表加载失败', '提示')
    merchants.value = []
  }
  loading.value = false
}

async function reload() {
  if (activeTab.value === 'users') await loadUsers()
  else await loadMerchants()
}

onMounted(async () => {
  await loadUsers()
})

watch(activeTab, async (t) => {
  page.value = 1
  appliedKeyword.value = ''
  keyword.value = ''
  if (t === 'users') await loadUsers()
  else await loadMerchants()
})

function runSearch() {
  appliedKeyword.value = keyword.value
  page.value = 1
}

function resetSearch() {
  keyword.value = ''
  appliedKeyword.value = ''
  page.value = 1
}

const filteredUsers = computed(() => {
  const kw = appliedKeyword.value.trim().toLowerCase()
  const list = users.value || []
  if (!kw) return list
  return list.filter(
    (u) =>
      String(u.userId).includes(kw) ||
      String(u.nickname || '')
        .toLowerCase()
        .includes(kw) ||
      String(u.email || '')
        .toLowerCase()
        .includes(kw) ||
      String(u.phone || '').includes(kw)
  )
})

const filteredMerchants = computed(() => {
  const kw = appliedKeyword.value.trim().toLowerCase()
  const list = merchants.value || []
  if (!kw) return list
  return list.filter(
    (m) =>
      String(m.merchantId).includes(kw) ||
      String(m.username || '')
        .toLowerCase()
        .includes(kw) ||
      String(m.shopName || '')
        .toLowerCase()
        .includes(kw) ||
      String(m.phone || '').includes(kw)
  )
})

const filteredList = computed(() => (activeTab.value === 'users' ? filteredUsers.value : filteredMerchants.value))
const total = computed(() => filteredList.value.length)
const pagedRows = computed(() => {
  const list = filteredList.value
  const p = Math.max(1, Number(page.value || 1))
  const ps = Math.max(1, Number(pageSize.value || 1))
  const start = (p - 1) * ps
  return list.slice(start, start + ps)
})

function setPageSize(n) {
  pageSize.value = Number(n || 10)
  page.value = 1
}

function statusLabel(s) {
  return Number(s) === 1 ? '正常' : '已禁用'
}

async function toggleUserStatus(row) {
  const next = Number(row.status) === 1 ? 0 : 1
  const res = await api.adminUpdateUserStatus(row.userId, { status: next })
  if (res.code === 200) {
    showAppMessage(next === 1 ? '已启用' : '已禁用', '提示')
    await loadUsers()
  } else {
    showAppMessage(res.message || '操作失败', '提示')
  }
}

async function toggleMerchantStatus(row) {
  const next = Number(row.status) === 1 ? 0 : 1
  const res = await api.adminUpdateMerchantStatus(row.merchantId, { status: next })
  if (res.code === 200) {
    showAppMessage(next === 1 ? '已启用' : '已禁用', '提示')
    await loadMerchants()
  } else {
    showAppMessage(res.message || '操作失败', '提示')
  }
}

function openPwdUser(row) {
  pwdTarget.value = { kind: 'user', id: row.userId }
  newPassword.value = ''
  pwdOpen.value = true
}

function openPwdMerchant(row) {
  pwdTarget.value = { kind: 'merchant', id: row.merchantId }
  newPassword.value = ''
  pwdOpen.value = true
}

async function submitPassword() {
  const pwd = String(newPassword.value || '').trim()
  if (pwd.length < 6) {
    showAppMessage('新密码至少 6 位', '提示')
    return
  }
  pwdSubmitting.value = true
  const t = pwdTarget.value
  const res =
    t.kind === 'user'
      ? await api.adminResetUserPassword(t.id, { newPassword: pwd })
      : await api.adminResetMerchantPassword(t.id, { newPassword: pwd })
  pwdSubmitting.value = false
  if (res.code === 200) {
    showAppMessage('密码已重置', '提示')
    pwdOpen.value = false
    await reload()
  } else {
    showAppMessage(res.message || '重置失败', '提示')
  }
}
</script>

<template>
  <div class="admin-page">
    <h2>账号管理</h2>
    <p class="desc">
      管理平台内<strong>购物用户</strong>与<strong>商家账号</strong>：查看状态、禁用或恢复登录、重置登录密码（密码加密保存，运营侧无法查看明文）。
    </p>

    <div class="tabs">
      <button
        type="button"
        :class="['tab', { active: activeTab === 'users' }]"
        @click="activeTab = 'users'"
      >
        用户账号
      </button>
      <button
        type="button"
        :class="['tab', { active: activeTab === 'merchants' }]"
        @click="activeTab = 'merchants'"
      >
        商家账号
      </button>
    </div>

    <div class="toolbar">
      <input
        v-model="keyword"
        class="filter-input"
        :placeholder="activeTab === 'users' ? '昵称 / 邮箱 / 手机 / ID' : '登录名 / 店铺 / 手机 / ID'"
        @keyup.enter="runSearch"
      />
      <button type="button" class="btn" @click="runSearch">搜索</button>
      <button type="button" class="btn ghost" @click="resetSearch">清空</button>
      <button type="button" class="btn" @click="reload">刷新</button>
    </div>

    <div v-if="loading" class="panel">加载中...</div>

    <template v-else-if="activeTab === 'users'">
      <div v-if="users.length === 0" class="empty">暂无用户数据</div>
      <div v-else-if="filteredUsers.length === 0" class="empty">无匹配记录</div>
      <table v-else class="table">
        <thead>
          <tr>
            <th>ID</th>
            <th>昵称</th>
            <th>邮箱</th>
            <th>手机</th>
            <th>状态</th>
            <th>注册时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in pagedRows" :key="row.userId">
            <td>{{ row.userId }}</td>
            <td>{{ row.nickname || '—' }}</td>
            <td class="muted">{{ row.email || '—' }}</td>
            <td>{{ row.phone || '—' }}</td>
            <td>
              <span :class="['pill', Number(row.status) === 1 ? 'ok' : 'off']">{{ statusLabel(row.status) }}</span>
            </td>
            <td class="muted">{{ row.createdAt ? new Date(row.createdAt).toLocaleString() : '—' }}</td>
            <td>
              <div class="btn-group">
                <button type="button" class="btn sm" @click="toggleUserStatus(row)">
                  {{ Number(row.status) === 1 ? '禁用' : '启用' }}
                </button>
                <button type="button" class="btn sm primary" @click="openPwdUser(row)">重置密码</button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </template>

    <template v-else>
      <div v-if="merchants.length === 0" class="empty">暂无商家数据</div>
      <div v-else-if="filteredMerchants.length === 0" class="empty">无匹配记录</div>
      <table v-else class="table">
        <thead>
          <tr>
            <th>ID</th>
            <th>登录名</th>
            <th>店铺名</th>
            <th>联系人</th>
            <th>电话</th>
            <th>状态</th>
            <th>注册 / 最近登录</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in pagedRows" :key="row.merchantId">
            <td>{{ row.merchantId }}</td>
            <td>{{ row.username }}</td>
            <td>{{ row.shopName }}</td>
            <td>{{ row.contactName || '—' }}</td>
            <td>{{ row.phone || '—' }}</td>
            <td>
              <span :class="['pill', Number(row.status) === 1 ? 'ok' : 'off']">{{ statusLabel(row.status) }}</span>
            </td>
            <td class="muted small">
              <div>{{ row.createdAt ? new Date(row.createdAt).toLocaleString() : '—' }}</div>
              <div v-if="row.lastLoginAt">最近登录：{{ new Date(row.lastLoginAt).toLocaleString() }}</div>
            </td>
            <td>
              <div class="btn-group">
                <button type="button" class="btn sm" @click="toggleMerchantStatus(row)">
                  {{ Number(row.status) === 1 ? '禁用' : '启用' }}
                </button>
                <button type="button" class="btn sm primary" @click="openPwdMerchant(row)">重置密码</button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </template>

    <PaginationBar
      v-if="!loading && total > 0"
      :page="page"
      :page-size="pageSize"
      :total="total"
      :page-size-options="[8, 10, 20, 50]"
      @update:page="page = $event"
      @update:page-size="setPageSize"
    />

    <ConfirmModal
      :open="pwdOpen"
      title="重置登录密码"
      confirm-label="确认重置"
      :loading="pwdSubmitting"
      @update:open="pwdOpen = $event"
      @confirm="submitPassword"
    >
      <p class="pwd-hint">为 {{ pwdTarget.kind === 'user' ? '用户 ID ' + pwdTarget.id : '商家 ID ' + pwdTarget.id }} 设置新密码（至少 6 位）。</p>
      <input v-model="newPassword" type="password" class="pwd-input" placeholder="新密码" autocomplete="new-password" />
    </ConfirmModal>
  </div>
</template>

<style scoped>
.admin-page {
  background: #f4f6f9;
  padding: 8px;
  max-width: 1200px;
}
h2 {
  font-size: 34px;
  color: #1a2740;
  margin-bottom: 8px;
}
.desc {
  color: #68788d;
  font-size: 13px;
  margin: 0 0 16px;
  line-height: 1.6;
}
.tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 14px;
}
.tab {
  height: 36px;
  padding: 0 14px;
  border-radius: 2px;
  border: 1px solid #c9d4e4;
  background: #fff;
  color: #33465f;
  font-weight: 700;
  font-size: 13px;
  cursor: pointer;
}
.tab.active {
  border-color: #0b1630;
  background: #0b1630;
  color: #f4f6fb;
}
.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
  align-items: center;
}
.filter-input {
  height: 32px;
  min-width: 260px;
  padding: 0 10px;
  border: 1px solid #ccd7e6;
  border-radius: 2px;
  font-size: 12px;
}
.btn {
  height: 32px;
  padding: 0 12px;
  border-radius: 2px;
  border: 1px solid #cad6e6;
  background: #f9fbff;
  color: #24344f;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
}
.btn.ghost {
  background: #fff;
}
.btn.primary {
  border-color: #0b1630;
  background: #0b1630;
  color: #f4f6fb;
}
.btn.sm {
  height: 28px;
  padding: 0 10px;
  font-size: 11px;
}
.panel {
  padding: 16px;
  background: #fff;
  border: 1px solid #dbe3ed;
}
.empty {
  padding: 48px;
  text-align: center;
  color: #68788d;
  background: #fff;
  border: 1px dashed #d9e1ec;
}
.table {
  width: 100%;
  border-collapse: collapse;
  background: #fff;
  border: 1px solid #dbe3ed;
}
.table th,
.table td {
  border-bottom: 1px solid #ecf0f5;
  padding: 10px 8px;
  font-size: 13px;
  text-align: left;
  vertical-align: middle;
}
.table th {
  background: #f1f4f8;
  font-size: 11px;
  color: #5f6d80;
}
.muted {
  color: #6c7d93;
}
.small {
  font-size: 12px;
}
.pill {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 2px;
  font-size: 11px;
  font-weight: 700;
}
.pill.ok {
  background: #d9f4df;
  color: #166b2d;
}
.pill.off {
  background: #ffe2e2;
  color: #8a1d1d;
}
.btn-group {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.pwd-hint {
  font-size: 13px;
  color: #33465f;
  margin: 0 0 10px;
}
.pwd-input {
  width: 100%;
  box-sizing: border-box;
  height: 36px;
  padding: 0 10px;
  border: 1px solid #c9d4e4;
  border-radius: 2px;
  font-size: 13px;
}
</style>
