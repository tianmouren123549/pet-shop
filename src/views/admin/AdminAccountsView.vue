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
const statusScope = ref('')
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
  statusScope.value = ''
  if (t === 'users') await loadUsers()
  else await loadMerchants()
})

function filterRowsByStatus(list, scope) {
  const arr = Array.isArray(list) ? list : []
  if (scope === '1') return arr.filter((r) => Number(r.status) === 1)
  if (scope === '0') return arr.filter((r) => Number(r.status) === 0)
  return arr
}

const scopedUsers = computed(() => filterRowsByStatus(users.value, statusScope.value))
const scopedMerchants = computed(() => filterRowsByStatus(merchants.value, statusScope.value))

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
  const list = scopedUsers.value || []
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
  const list = scopedMerchants.value || []
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
      String(m.phone || '').includes(kw) ||
      String(m.email || '')
        .toLowerCase()
        .includes(kw)
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

function onStatusScopeChange() {
  page.value = 1
}

const statCards = computed(() => {
  const base = activeTab.value === 'users' ? users.value || [] : merchants.value || []
  const total = base.length
  const active = base.filter((r) => Number(r.status) === 1).length
  const inactive = base.filter((r) => Number(r.status) === 0).length
  const listed = filteredList.value.length
  const isUser = activeTab.value === 'users'
  return [
    {
      key: 'total',
      label: isUser ? '用户总数' : '商家总数',
      value: total,
      ratio: total ? Math.round((active / total) * 100) : 0,
    },
    {
      key: 'active',
      label: '正常',
      value: active,
      ratio: total ? Math.round((active / total) * 100) : 0,
    },
    {
      key: 'inactive',
      label: '已禁用',
      value: inactive,
      ratio: total ? Math.round((inactive / total) * 100) : 0,
    },
    {
      key: 'listed',
      label: '当前列表',
      value: listed,
      ratio: total ? Math.min(100, Math.round((listed / Math.max(total, 1)) * 100)) : 0,
    },
  ]
})

function formatShortTime(iso) {
  const t = new Date(iso || 0)
  if (!Number.isFinite(t.getTime())) return '—'
  const m = String(t.getMonth() + 1).padStart(2, '0')
  const d = String(t.getDate()).padStart(2, '0')
  const hh = String(t.getHours()).padStart(2, '0')
  const mm = String(t.getMinutes()).padStart(2, '0')
  return `${m}-${d} ${hh}:${mm}`
}

function formatRelative(iso) {
  const t = new Date(iso || 0).getTime()
  if (!Number.isFinite(t)) return '—'
  const diff = Date.now() - t
  if (diff < 0) return formatShortTime(iso)
  const sec = Math.floor(diff / 1000)
  if (sec < 45) return '刚刚'
  const min = Math.floor(sec / 60)
  if (min < 60) return `${min} 分钟前`
  const h = Math.floor(min / 60)
  if (h < 48) return `${h} 小时前`
  const d = Math.floor(h / 24)
  if (d < 40) return `${d} 天前`
  return formatShortTime(iso)
}

function userRowInitials(row) {
  const nick = String(row?.nickname || '').trim()
  if (nick) return nick.slice(0, 1).toUpperCase()
  const id = row?.userId
  if (id != null && String(id)) return String(id).slice(-1)
  return '?'
}

function merchantRowInitials(row) {
  const shop = String(row?.shopName || '').trim()
  if (shop) return shop.slice(0, 1).toUpperCase()
  const u = String(row?.username || '').trim()
  if (u) return u.slice(0, 1).toUpperCase()
  return '?'
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

async function resetSingleUserPetPreference(row) {
  const ok = window.confirm(`确认重置用户 ${row.userId} 的首页偏好引导吗？`)
  if (!ok) return
  const res = await api.adminResetUserPetPreference(row.userId)
  if (res.code === 200) {
    showAppMessage('已重置该用户的猫狗偏好引导', '提示')
    await loadUsers()
  } else {
    showAppMessage(res.message || '重置失败', '提示')
  }
}

async function resetAllUsersPetPreference() {
  const ok = window.confirm('确认一键重置全部用户的猫狗偏好引导吗？')
  if (!ok) return
  const res = await api.adminResetAllUserPetPreference()
  if (res.code === 200) {
    const affected = Number(res.data?.affectedRows || 0)
    showAppMessage(`已重置 ${affected} 个用户的猫狗偏好引导`, '提示')
    await loadUsers()
  } else {
    showAppMessage(res.message || '一键重置失败', '提示')
  }
}
</script>

<template>
  <div class="admin-page acc-view">
    <header class="acc-manifest">
      <div class="acc-manifest-text">
        <h1 class="acc-title">账号管理</h1>
      </div>
      <div class="acc-manifest-actions">
        <button type="button" class="acc-tool acc-tool--primary" @click="reload">刷新列表</button>
      </div>
    </header>

    <section class="acc-stats" aria-label="账号概览">
      <article v-for="card in statCards" :key="card.key" class="acc-stat-card">
        <div class="acc-stat-top">
          <span class="acc-stat-label">{{ card.label }}</span>
          <strong class="acc-stat-num">{{ card.value }}</strong>
        </div>
        <div class="acc-stat-bar" role="presentation">
          <span class="acc-stat-bar-fill" :style="{ width: `${card.ratio}%` }" />
        </div>
      </article>
    </section>

    <div class="acc-tabs" role="tablist">
      <button
        type="button"
        role="tab"
        :class="['acc-tab', { 'acc-tab--on': activeTab === 'users' }]"
        :aria-selected="activeTab === 'users'"
        @click="activeTab = 'users'"
      >
        用户账号
        <span class="acc-tab-count">({{ users.length }})</span>
      </button>
      <button
        type="button"
        role="tab"
        :class="['acc-tab', { 'acc-tab--on': activeTab === 'merchants' }]"
        :aria-selected="activeTab === 'merchants'"
        @click="activeTab = 'merchants'"
      >
        商家账号
        <span class="acc-tab-count">({{ merchants.length }})</span>
      </button>
    </div>

    <section class="acc-panel">
      <div class="acc-panel-head">
        <div class="acc-panel-head-left">
          <label class="acc-filter-label">
            <span class="acc-filter-cap">按状态筛选</span>
            <select v-model="statusScope" class="acc-filter-select" @change="onStatusScopeChange">
              <option value="">全部状态</option>
              <option value="1">仅正常</option>
              <option value="0">仅已禁用</option>
            </select>
          </label>
        </div>
        <div class="acc-panel-head-right">
          <input
            v-model="keyword"
            class="acc-search"
            :placeholder="activeTab === 'users' ? '昵称 / 邮箱 / 手机 / ID' : '登录名 / 店铺 / 邮箱 / 手机 / ID'"
            @keyup.enter="runSearch"
          />
          <button type="button" class="acc-chip acc-chip--primary" @click="runSearch">搜索</button>
          <button type="button" class="acc-chip acc-chip--ghost" @click="resetSearch">清空</button>
          <button
            v-if="activeTab === 'users'"
            type="button"
            class="acc-chip acc-chip--warn"
            @click="resetAllUsersPetPreference"
          >
            一键重置偏好
          </button>
        </div>
      </div>

      <div v-if="loading" class="acc-state">加载中…</div>

      <template v-else-if="activeTab === 'users'">
        <div v-if="users.length === 0" class="acc-empty">暂无用户数据</div>
        <div v-else-if="filteredUsers.length === 0" class="acc-empty">当前筛选下无匹配记录</div>
        <div v-else class="acc-table-wrap">
          <table class="acc-table">
            <thead>
              <tr>
                <th class="acc-th-user">账号</th>
                <th class="acc-th-role">类型</th>
                <th class="acc-th-phone">手机</th>
                <th class="acc-th-time">注册时间</th>
                <th class="acc-th-status">状态</th>
                <th class="acc-th-actions">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in pagedRows" :key="row.userId" class="acc-tr">
                <td class="acc-td-user">
                  <div class="acc-user">
                    <span class="acc-ava" aria-hidden="true">{{ userRowInitials(row) }}</span>
                    <div class="acc-user-text">
                      <div class="acc-user-name">{{ row.nickname || `用户 ${row.userId}` }}</div>
                      <div class="acc-user-meta">{{ row.email || '未绑定邮箱' }} · ID {{ row.userId }}</div>
                    </div>
                  </div>
                </td>
                <td>
                  <span class="acc-role acc-role--user">购物用户</span>
                </td>
                <td class="acc-td-mono">{{ row.phone || '—' }}</td>
                <td class="acc-td-time">
                  <span class="acc-rel">{{ formatRelative(row.createdAt) }}</span>
                  <span class="acc-abs" :title="row.createdAt">{{ formatShortTime(row.createdAt) }}</span>
                </td>
                <td>
                  <span class="acc-status">
                    <span :class="['acc-dot', Number(row.status) === 1 ? 'acc-dot--on' : 'acc-dot--off']" />
                    {{ statusLabel(row.status) }}
                  </span>
                </td>
                <td class="acc-td-actions">
                  <div class="acc-act-row" role="group" :aria-label="`用户 ${row.userId} 操作`">
                    <button type="button" class="acc-row-btn acc-row-btn--muted" @click="toggleUserStatus(row)">
                      {{ Number(row.status) === 1 ? '禁用' : '启用' }}
                    </button>
                    <button type="button" class="acc-row-btn acc-row-btn--primary" @click="openPwdUser(row)">
                      重置密码
                    </button>
                    <button
                      type="button"
                      class="acc-row-btn acc-row-btn--ghost"
                      title="重置首页猫狗偏好引导"
                      @click="resetSingleUserPetPreference(row)"
                    >
                      偏好
                    </button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </template>

      <template v-else>
        <div v-if="merchants.length === 0" class="acc-empty">暂无商家数据</div>
        <div v-else-if="filteredMerchants.length === 0" class="acc-empty">当前筛选下无匹配记录</div>
        <div v-else class="acc-table-wrap">
          <table class="acc-table">
            <thead>
              <tr>
                <th class="acc-th-user">账号</th>
                <th class="acc-th-role">类型</th>
                <th class="acc-th-phone">联系人 / 电话</th>
                <th class="acc-th-time">活跃</th>
                <th class="acc-th-status">状态</th>
                <th class="acc-th-actions">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in pagedRows" :key="row.merchantId" class="acc-tr">
                <td class="acc-td-user">
                  <div class="acc-user">
                    <span class="acc-ava acc-ava--merchant" aria-hidden="true">{{ merchantRowInitials(row) }}</span>
                    <div class="acc-user-text">
                      <div class="acc-user-name">{{ row.shopName || row.username }}</div>
                      <div class="acc-user-meta">{{ row.username }} · {{ row.email || '—' }} · ID {{ row.merchantId }}</div>
                    </div>
                  </div>
                </td>
                <td>
                  <span class="acc-role acc-role--merchant">商家</span>
                </td>
                <td>
                  <div class="acc-td-stack">
                    <span>{{ row.contactName || '—' }}</span>
                    <span class="acc-td-sub">{{ row.phone || '—' }}</span>
                  </div>
                </td>
                <td class="acc-td-time">
                  <span class="acc-rel">{{ row.lastLoginAt ? formatRelative(row.lastLoginAt) : '—' }}</span>
                  <span class="acc-abs" :title="row.lastLoginAt || row.createdAt">
                    {{ row.lastLoginAt ? formatShortTime(row.lastLoginAt) : formatShortTime(row.createdAt) }}
                  </span>
                  <span v-if="row.createdAt" class="acc-td-sub">注册 {{ formatShortTime(row.createdAt) }}</span>
                </td>
                <td>
                  <span class="acc-status">
                    <span :class="['acc-dot', Number(row.status) === 1 ? 'acc-dot--on' : 'acc-dot--off']" />
                    {{ statusLabel(row.status) }}
                  </span>
                </td>
                <td class="acc-td-actions">
                  <div class="acc-act-row" role="group" :aria-label="`商家 ${row.merchantId} 操作`">
                    <button type="button" class="acc-row-btn acc-row-btn--muted" @click="toggleMerchantStatus(row)">
                      {{ Number(row.status) === 1 ? '禁用' : '启用' }}
                    </button>
                    <button type="button" class="acc-row-btn acc-row-btn--primary" @click="openPwdMerchant(row)">
                      重置密码
                    </button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </template>

      <PaginationBar
        v-if="!loading && total > 0"
        class="acc-pagination"
        :page="page"
        :page-size="pageSize"
        :total="total"
        @update:page="page = $event"
      />
    </section>

    <footer class="acc-protocol" role="note">
      <strong class="acc-protocol-title">安全说明</strong>
      <p class="acc-protocol-text">
        登录密码经哈希存储且不可逆；重置密码与启用 / 禁用等敏感操作建议二次确认。运营动作可能记入审计日志（若后端已启用）。
      </p>
    </footer>

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
.acc-view {
  max-width: 100%;
  width: 100%;
  box-sizing: border-box;
  min-width: 0;
}

.acc-manifest {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.acc-title {
  margin: 0;
  font-size: clamp(22px, 2.2vw, 28px);
  font-weight: 800;
  letter-spacing: -0.02em;
  color: #0f172a;
}

.acc-manifest-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}

.acc-tool {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 40px;
  padding: 0 16px;
  border-radius: 10px;
  font-size: 13px;
  font-weight: 800;
  cursor: pointer;
  border: 1px solid #c9d4e4;
  background: #fff;
  color: #1e293b;
}

.acc-tool--primary {
  border-color: #0b1630;
  background: linear-gradient(145deg, #0b1630 0%, #1e3a5f 100%);
  color: #f4f6fb;
}

.acc-tool--primary:hover {
  filter: brightness(1.06);
}

.acc-stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 18px;
}

.acc-stat-card {
  border-radius: 14px;
  border: 1px solid #dde3ec;
  background: linear-gradient(165deg, #fff 0%, #f8fafc 100%);
  padding: 14px 16px 12px;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.75);
  min-width: 0;
}

.acc-stat-top {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
}

.acc-stat-label {
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.04em;
  color: #64748b;
  text-transform: uppercase;
}

.acc-stat-num {
  font-size: clamp(22px, 2.4vw, 28px);
  font-weight: 800;
  letter-spacing: -0.03em;
  color: #0f172a;
  line-height: 1;
}

.acc-stat-bar {
  margin-top: 10px;
  height: 4px;
  border-radius: 999px;
  background: #e8ecf2;
  overflow: hidden;
}

.acc-stat-bar-fill {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #8b5a2e 0%, #6d471f 40%, #0b1630 100%);
  min-width: 4px;
  transition: width 0.25s ease;
}

.acc-tabs {
  display: flex;
  gap: 4px;
  margin-bottom: 12px;
  padding: 4px;
  border-radius: 12px;
  background: #e8ecf4;
  width: fit-content;
  max-width: 100%;
}

.acc-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 38px;
  padding: 0 18px;
  border: none;
  border-radius: 10px;
  background: transparent;
  color: #475569;
  font-size: 13px;
  font-weight: 800;
  cursor: pointer;
}

.acc-tab-count {
  font-size: 11px;
  font-weight: 800;
  color: #94a3b8;
}

.acc-tab--on {
  background: #fff;
  color: #0b1630;
  box-shadow: 0 2px 10px rgba(11, 22, 48, 0.1);
}

.acc-tab--on .acc-tab-count {
  color: #64748b;
}

.acc-panel {
  border-radius: 16px;
  border: 1px solid #dde3ec;
  background: #fff;
  box-shadow: 0 14px 36px rgba(15, 23, 42, 0.06);
  overflow: hidden;
  margin-bottom: 18px;
}

.acc-panel-head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
  background: linear-gradient(180deg, #f8fafc 0%, #fff 100%);
  border-bottom: 1px solid #edf0f5;
}

.acc-panel-head-right {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.acc-filter-label {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 140px;
}

.acc-filter-cap {
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.06em;
  color: #64748b;
  text-transform: uppercase;
}

.acc-filter-select {
  height: 38px;
  padding: 0 12px;
  border-radius: 10px;
  border: 1px solid #c5d0e0;
  background: #fff;
  font-size: 13px;
  font-weight: 700;
  color: #0f172a;
}

.acc-search {
  height: 38px;
  min-width: 200px;
  flex: 1;
  max-width: 320px;
  padding: 0 12px;
  border-radius: 10px;
  border: 1px solid #c5d0e0;
  font-size: 13px;
  font-weight: 600;
}

.acc-chip {
  height: 38px;
  padding: 0 14px;
  border-radius: 10px;
  border: 1px solid #c9d4e4;
  background: #fff;
  font-size: 12px;
  font-weight: 800;
  cursor: pointer;
  color: #1e293b;
}

.acc-chip--primary {
  border-color: #0b1630;
  background: linear-gradient(145deg, #0b1630 0%, #1e3a5f 100%);
  color: #f4f6fb;
}

.acc-chip--ghost {
  background: #f8fafc;
}

.acc-chip--warn {
  border-color: #fdba74;
  background: #fff7ed;
  color: #c2410c;
}

.acc-state {
  padding: 48px;
  text-align: center;
  color: #64748b;
  font-weight: 600;
}

.acc-empty {
  padding: 48px 24px;
  text-align: center;
  color: #64748b;
  font-weight: 600;
  border-top: 1px dashed #e8ecf2;
}

.acc-table-wrap {
  width: 100%;
  overflow-x: auto;
  box-sizing: border-box;
}

.acc-table {
  width: 100%;
  min-width: 980px;
  border-collapse: collapse;
  table-layout: fixed;
}

.acc-table th,
.acc-table td {
  padding: 14px 14px;
  text-align: left;
  vertical-align: middle;
  border-bottom: 1px solid #f1f5f9;
  font-size: 13px;
}

.acc-table th {
  background: #f8fafc;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: #64748b;
}

.acc-th-user {
  width: 26%;
}
.acc-th-role {
  width: 10%;
}
.acc-th-phone {
  width: 13%;
}
.acc-th-time {
  width: 15%;
}
.acc-th-status {
  width: 11%;
}
.acc-th-actions {
  width: 25%;
  min-width: 240px;
  text-align: right;
}

.acc-user {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.acc-ava {
  flex-shrink: 0;
  width: 42px;
  height: 42px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
  font-weight: 800;
  color: #0b1630;
  background: linear-gradient(145deg, #e8ecf4 0%, #d4dce8 100%);
  border: 1px solid #b8c4d6;
}

.acc-ava--merchant {
  color: #9a3412;
  background: linear-gradient(145deg, #ffedd5 0%, #fed7aa 100%);
  border-color: #fdba74;
}

.acc-user-text {
  min-width: 0;
}

.acc-user-name {
  font-size: 14px;
  font-weight: 800;
  color: #0f172a;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.acc-user-meta {
  margin-top: 3px;
  font-size: 11px;
  font-weight: 600;
  color: #94a3b8;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.acc-role {
  display: inline-flex;
  padding: 5px 10px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.02em;
}

.acc-role--user {
  background: #f0f2f7;
  color: #0b1630;
  border: 1px solid #c9d4e4;
}

.acc-role--merchant {
  background: #fff7ed;
  color: #c2410c;
  border: 1px solid #fed7aa;
}

.acc-td-mono {
  font-variant-numeric: tabular-nums;
  font-weight: 600;
  color: #334155;
}

.acc-td-stack {
  display: flex;
  flex-direction: column;
  gap: 3px;
  font-weight: 600;
  color: #334155;
}

.acc-td-sub {
  font-size: 11px;
  font-weight: 600;
  color: #94a3b8;
}

.acc-td-time {
  font-weight: 600;
  color: #475569;
}

.acc-rel {
  display: block;
  font-size: 13px;
  color: #0f172a;
}

.acc-abs {
  display: block;
  margin-top: 2px;
  font-size: 11px;
  font-weight: 600;
  color: #94a3b8;
}

.acc-status {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  font-weight: 800;
  color: #334155;
}

.acc-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.acc-dot--on {
  background: #22c55e;
  box-shadow: 0 0 0 3px rgba(34, 197, 94, 0.25);
}

.acc-dot--off {
  background: #cbd5e1;
  box-shadow: 0 0 0 3px rgba(148, 163, 184, 0.25);
}

.acc-td-actions {
  text-align: right;
  vertical-align: middle;
}

.acc-act-row {
  display: inline-flex;
  flex-wrap: nowrap;
  align-items: center;
  justify-content: flex-end;
  gap: 6px;
  max-width: 100%;
}

.acc-row-btn {
  flex: 0 0 auto;
  height: 30px;
  padding: 0 8px;
  border-radius: 8px;
  border: 1px solid #d0d8e6;
  background: #fff;
  font-size: 11px;
  font-weight: 800;
  cursor: pointer;
  color: #334155;
  white-space: nowrap;
}

.acc-row-btn--primary {
  border-color: #0b1630;
  background: linear-gradient(145deg, #0b1630 0%, #1e3a5f 100%);
  color: #f4f6fb;
}

.acc-row-btn--muted:hover {
  background: #f8fafc;
}

.acc-row-btn--ghost {
  border-style: dashed;
  background: #fafbfc;
}

.acc-pagination {
  padding: 12px 16px 16px;
  border-top: 1px solid #f1f5f9;
  background: #fafbfd;
}

.acc-pagination :deep(.pw-page-num.active) {
  border-color: #0b1630;
  background: #0b1630;
  color: #f4f6fb;
}

.acc-protocol {
  border-radius: 14px;
  padding: 16px 18px;
  background: linear-gradient(110deg, #0f172a 0%, #1e3a5f 45%, #172554 100%);
  color: #e2e8f0;
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.2);
}

.acc-protocol-title {
  display: block;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: #d4a574;
  margin-bottom: 8px;
}

.acc-protocol-text {
  margin: 0;
  font-size: 12px;
  line-height: 1.65;
  font-weight: 600;
  color: #cbd5e1;
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
  border-radius: 8px;
  font-size: 13px;
}

@media (max-width: 1100px) {
  .acc-stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 560px) {
  .acc-stats {
    grid-template-columns: 1fr;
  }
  .acc-manifest {
    flex-direction: column;
  }
}
</style>
