<script setup>
import { computed, onMounted, onUnmounted, ref, watch, nextTick } from 'vue'
import { api } from '../../utils/request'

const loading = ref(false)
const sending = ref(false)
const errorMsg = ref('')
const sessions = ref([])
const activeSessionId = ref(null)
const messages = ref([])
const inputText = ref('')
const listRef = ref(null)
/** 收件箱筛选：全部 / 仅用户→平台 / 仅商家→平台 */
const inboxTab = ref('all')

let pollTimer = null

const filteredSessions = computed(() => {
  const list = Array.isArray(sessions.value) ? sessions.value : []
  if (inboxTab.value === 'user') return list.filter((s) => s.sessionType === 'USER_TO_ADMIN')
  if (inboxTab.value === 'merchant') return list.filter((s) => s.sessionType === 'MERCHANT_TO_ADMIN')
  return list
})

const sessionCount = computed(() => (Array.isArray(sessions.value) ? sessions.value.length : 0))

const unreadSessionCount = computed(() =>
  (sessions.value || []).filter((s) => Boolean(s.unreadFromCounterparty)).length,
)

const activeSession = computed(() => {
  const id = Number(activeSessionId.value || 0)
  if (!id) return null
  return sessions.value.find((x) => Number(x.sessionId) === id) || null
})

watch(inboxTab, () => {
  const id = activeSessionId.value
  if (id == null) return
  const ok = filteredSessions.value.some((s) => Number(s.sessionId) === Number(id))
  if (!ok) {
    activeSessionId.value = null
    messages.value = []
  }
})

/**
 * 列表头像缩写（中文取首字，英文取首字母）。
 * @param {string} title
 */
function initials(title) {
  const s = String(title || '?').trim()
  if (!s) return '?'
  if (/[\u4e00-\u9fff]/.test(s)) return s.slice(0, 1)
  const parts = s.split(/[\s._-]+/).filter(Boolean)
  if (parts.length >= 2) {
    return (parts[0][0] + parts[1][0]).toUpperCase().slice(0, 2)
  }
  return s.slice(0, 2).toUpperCase()
}

function fmtTime(iso) {
  if (!iso) return '--'
  const d = new Date(iso)
  const hh = String(d.getHours()).padStart(2, '0')
  const mm = String(d.getMinutes()).padStart(2, '0')
  return `${hh}:${mm}`
}

/** 侧栏与列表用短时间戳 */
function fmtSessionTime(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return ''
  const now = new Date()
  const sameDay =
    d.getFullYear() === now.getFullYear() && d.getMonth() === now.getMonth() && d.getDate() === now.getDate()
  if (sameDay) return fmtTime(iso)
  return `${d.getMonth() + 1}/${d.getDate()} ${fmtTime(iso)}`
}

function sessionTypeLabel(st) {
  if (st === 'USER_TO_ADMIN') return '用户'
  if (st === 'MERCHANT_TO_ADMIN') return '商家'
  return '咨询'
}

function sessionTagClass(st) {
  if (st === 'USER_TO_ADMIN') return 'asp-tag asp-tag--user'
  if (st === 'MERCHANT_TO_ADMIN') return 'asp-tag asp-tag--merchant'
  return 'asp-tag'
}

function isMine(msg) {
  return msg?.senderType === 'ADMIN'
}

function senderLabel(msg) {
  if (isMine(msg)) return '平台客服'
  if (msg?.senderType === 'USER') return '用户'
  if (msg?.senderType === 'MERCHANT') return '商家'
  return msg?.senderType || '—'
}

async function scrollBottom() {
  await nextTick()
  if (listRef.value) listRef.value.scrollTop = listRef.value.scrollHeight
}

async function loadSessions(preserveSelection = true) {
  const isInitial = !preserveSelection
  if (isInitial) {
    loading.value = true
    errorMsg.value = ''
  }
  const res = await api.adminListSupportSessions()
  if (isInitial) loading.value = false
  if (res.code !== 200) {
    errorMsg.value = res.message || '会话加载失败'
    return
  }
  const list = Array.isArray(res.data) ? res.data : []
  const prev = activeSessionId.value
  sessions.value = list
  if (!preserveSelection) {
    activeSessionId.value = null
    messages.value = []
  } else if (prev != null) {
    const still = list.some((x) => Number(x.sessionId) === Number(prev))
    if (still) {
      activeSessionId.value = prev
    } else {
      activeSessionId.value = null
      messages.value = []
    }
  }
  window.dispatchEvent(new CustomEvent('petshop-admin-support-unread-updated'))
}

async function loadMessages(sessionId) {
  const res = await api.adminGetSupportMessages(sessionId)
  if (res.code !== 200) {
    errorMsg.value = res.message || '消息加载失败'
    return
  }
  messages.value = Array.isArray(res.data) ? res.data : []
  await scrollBottom()
  await loadSessions(true)
}

async function selectSession(sessionId) {
  activeSessionId.value = sessionId
  await loadMessages(sessionId)
}

async function sendMessage() {
  if (sending.value) return
  const content = inputText.value.trim()
  if (!content || !activeSessionId.value) return
  sending.value = true
  const res = await api.adminSendSupportMessage({
    sessionId: activeSessionId.value,
    content,
  })
  sending.value = false
  if (res.code !== 200) {
    errorMsg.value = res.message || '发送失败'
    return
  }
  inputText.value = ''
  await loadMessages(activeSessionId.value)
}

function previewSnippet(s) {
  const sub = String(s?.counterpartySub || '').trim()
  if (sub) return sub.length > 42 ? `${sub.slice(0, 42)}…` : sub
  return '暂无摘要 · 点击进入会话'
}

onMounted(() => {
  loadSessions(false)
  pollTimer = setInterval(() => loadSessions(true), 10000)
})

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
  pollTimer = null
})
</script>

<template>
  <div class="admin-page admin-support-page">
    <header class="asp-page-hero">
      <div class="asp-page-hero-main">
        <h1 class="asp-title">咨询工作台</h1>
        <div class="asp-hero-chips" aria-label="会话概览">
          <span class="asp-hero-chip">会话 {{ sessionCount }}</span>
          <span v-if="unreadSessionCount > 0" class="asp-hero-chip asp-hero-chip--warn">待回复 {{ unreadSessionCount }}</span>
        </div>
      </div>
      <div class="asp-page-hero-aside">
        <button type="button" class="asp-refresh-btn" @click="loadSessions(true)">刷新列表</button>
      </div>
    </header>

    <div class="asp-shell">
      <div v-if="loading" class="asp-shell-fill asp-shell-fill--center">加载中...</div>
      <div v-else-if="errorMsg && sessions.length === 0" class="asp-shell-fill asp-shell-fill--center asp-shell-fill--error">
        {{ errorMsg }}
      </div>
      <template v-else>
      <!-- 左：收件箱 -->
      <aside class="asp-inbox">
        <div class="asp-inbox-head">
          <span class="asp-inbox-title">会话收件箱</span>
        </div>
        <div class="asp-tabs" role="tablist">
          <button
            type="button"
            role="tab"
            class="asp-tab"
            :class="{ 'asp-tab--on': inboxTab === 'all' }"
            @click="inboxTab = 'all'"
          >
            全部
          </button>
          <button
            type="button"
            role="tab"
            class="asp-tab"
            :class="{ 'asp-tab--on': inboxTab === 'user' }"
            @click="inboxTab = 'user'"
          >
            用户
          </button>
          <button
            type="button"
            role="tab"
            class="asp-tab"
            :class="{ 'asp-tab--on': inboxTab === 'merchant' }"
            @click="inboxTab = 'merchant'"
          >
            商家
          </button>
        </div>
        <div class="asp-inbox-body">
          <div v-if="filteredSessions.length === 0" class="asp-empty">暂无会话</div>
          <button
            v-for="s in filteredSessions"
            :key="s.sessionId"
            type="button"
            class="asp-card"
            :class="{
              'asp-card--active': activeSessionId === s.sessionId,
              'asp-card--unread': s.unreadFromCounterparty,
            }"
            @click="selectSession(s.sessionId)"
          >
            <div class="asp-card-top">
              <div class="asp-avatar" aria-hidden="true">{{ initials(s.counterpartyTitle) }}</div>
              <div class="asp-card-main">
                <div class="asp-card-row">
                  <span class="asp-card-name">{{ s.counterpartyTitle }}</span>
                  <span class="asp-card-time">{{ fmtSessionTime(s.updatedAt) }}</span>
                </div>
                <p class="asp-card-preview">{{ previewSnippet(s) }}</p>
                <div class="asp-card-tags">
                  <span :class="sessionTagClass(s.sessionType)">{{ sessionTypeLabel(s.sessionType) }}</span>
                  <span v-if="s.unreadFromCounterparty" class="asp-tag asp-tag--priority">待处理</span>
                </div>
              </div>
            </div>
          </button>
        </div>
      </aside>

      <!-- 中：对话 -->
      <section class="asp-chat-col">
        <div v-if="errorMsg" class="asp-banner">{{ errorMsg }}</div>
        <template v-if="activeSession">
          <div class="asp-chat-toolbar">
            <div class="asp-chat-toolbar-text">
              <div class="asp-chat-title">{{ activeSession.counterpartyTitle }}</div>
              <div class="asp-chat-sub">在线咨询 · 对方为{{ sessionTypeLabel(activeSession.sessionType) }}</div>
            </div>
            <div class="asp-chat-actions">
              <button type="button" class="asp-link-btn" disabled title="即将开放">转交</button>
              <button type="button" class="asp-close-btn" disabled title="即将开放">结束会话</button>
            </div>
          </div>

          <div ref="listRef" class="asp-msg-feed">
            <div class="asp-system-line" role="status">会话已打开 · 消息按时间排序</div>
            <div v-if="messages.length === 0" class="asp-msg-empty">暂无消息，在下方输入回复。</div>
            <div
              v-for="msg in messages"
              :key="msg.messageId"
              class="asp-msg-block"
              :class="{ 'asp-msg-block--mine': isMine(msg) }"
            >
              <div class="asp-msg-meta">{{ senderLabel(msg) }}</div>
              <div class="asp-msg-bubble">{{ msg.content }}</div>
              <div class="asp-msg-when">{{ fmtTime(msg.createdAt) }}</div>
            </div>
          </div>

          <div class="asp-composer">
            <textarea
              v-model="inputText"
              class="asp-textarea"
              rows="3"
              maxlength="500"
              placeholder="输入回复内容…（Ctrl+Enter 发送）"
              @keydown.ctrl.enter="sendMessage"
            />
            <div class="asp-composer-bar">
              <div class="asp-icon-group">
                <button type="button" class="asp-icon-btn" disabled title="即将开放">📎</button>
                <button type="button" class="asp-icon-btn" disabled title="即将开放">😊</button>
                <button type="button" class="asp-icon-btn" disabled title="即将开放">&lt;/&gt;</button>
              </div>
              <button
                type="button"
                class="asp-send-primary"
                :disabled="sending || !inputText.trim() || !activeSessionId"
                @click="sendMessage"
              >
                <span>{{ sending ? '发送中…' : '发送消息' }}</span>
                <span class="asp-send-glyph" aria-hidden="true">➤</span>
              </button>
            </div>
          </div>
        </template>
        <div v-else class="asp-chat-empty">
          <p class="asp-chat-empty-title">请选择左侧会话</p>
          <p class="asp-chat-empty-hint">打开会话后会加载历史消息并将对方消息标为已读。</p>
        </div>
      </section>

      <!-- 右：上下文 -->
      <aside v-if="activeSession" class="asp-context-col">
        <div class="asp-profile-card">
          <div class="asp-profile-avatar">{{ initials(activeSession.counterpartyTitle) }}</div>
          <div class="asp-profile-name">{{ activeSession.counterpartyTitle }}</div>
          <div class="asp-profile-role">
            <span :class="sessionTagClass(activeSession.sessionType)">{{ sessionTypeLabel(activeSession.sessionType) }}</span>
          </div>
          <dl class="asp-profile-dl">
            <div class="asp-profile-row">
              <dt>联系信息</dt>
              <dd>{{ activeSession.counterpartySub || '—' }}</dd>
            </div>
            <div class="asp-profile-row">
              <dt>会话 ID</dt>
              <dd class="asp-mono">#{{ activeSession.sessionId }}</dd>
            </div>
          </dl>
        </div>
        <div class="asp-notes-card">
          <div class="asp-notes-title">内部备注</div>
          <button type="button" class="asp-notes-add" disabled title="敬请期待">+ 添加备注</button>
        </div>
      </aside>
      <aside v-else class="asp-context-col asp-context-col--muted">
        <p class="asp-context-placeholder">选中会话后显示对方摘要</p>
      </aside>
      </template>
    </div>
  </div>
</template>

<style scoped>
.admin-support-page {
  min-width: 0;
}

.asp-page-hero {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  padding-bottom: 12px;
  margin-bottom: 12px;
  border-bottom: 1px solid #eef2f7;
}

.asp-page-hero-main {
  flex: 1;
  min-width: 220px;
}

.asp-page-hero-aside {
  flex-shrink: 0;
  display: flex;
  align-items: flex-start;
}

.asp-hero-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.asp-hero-chip {
  font-size: 11px;
  font-weight: 700;
  color: #475569;
  background: #f1f5f9;
  padding: 5px 11px;
  border-radius: 999px;
  border: 1px solid #e2e8f0;
}

.asp-hero-chip--warn {
  color: #9a3412;
  background: #fff7ed;
  border-color: #fed7aa;
}

.asp-refresh-btn {
  border: none;
  border-radius: 10px;
  padding: 8px 16px;
  font-size: 13px;
  font-weight: 800;
  color: #f8fafc;
  background: #0b1630;
  cursor: pointer;
  transition: opacity 0.15s ease;
}

.asp-refresh-btn:hover {
  opacity: 0.92;
}

.asp-title {
  margin: 0;
  font-size: clamp(20px, 2vw, 24px);
  font-weight: 800;
  color: #0e1628;
  letter-spacing: 0.02em;
}

.asp-shell {
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr) 268px;
  grid-template-rows: minmax(0, 1fr);
  gap: 0;
  align-items: stretch;
  min-height: calc(100vh - 248px);
  max-height: calc(100vh - 208px);
  border-radius: 14px;
  border: 1px solid #e8ecf2;
  background: #fff;
  box-shadow: 0 10px 28px rgba(15, 23, 42, 0.05);
  overflow: hidden;
}

.asp-shell-fill {
  grid-column: 1 / -1;
  grid-row: 1;
  min-height: 240px;
}

.asp-shell-fill--center {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px 24px;
  color: #64748b;
  font-size: 14px;
}

.asp-shell-fill--error {
  color: #c62828;
}

/* ----- 收件箱 ----- */
.asp-inbox {
  grid-column: 1;
  grid-row: 1;
  display: flex;
  flex-direction: column;
  border-right: 1px solid #e8edf5;
  background: #f8fafc;
  min-height: 0;
}

.asp-inbox-head {
  padding: 14px 16px 10px;
  border-bottom: 1px solid #e8edf5;
  background: linear-gradient(180deg, #fff 0%, #f8fafc 100%);
}

.asp-inbox-title {
  font-size: 13px;
  font-weight: 800;
  color: #334155;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.asp-tabs {
  display: flex;
  gap: 6px;
  padding: 10px 12px 12px;
  border-bottom: 1px solid #e8edf5;
  background: #f8fafc;
}

.asp-tab {
  flex: 1;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 7px 8px;
  font-size: 12px;
  font-weight: 700;
  color: #64748b;
  background: #fff;
  cursor: pointer;
  transition:
    background 0.15s,
    border-color 0.15s,
    color 0.15s;
}

.asp-tab:hover {
  border-color: #cbd5e1;
  color: #0f172a;
}

.asp-tab--on {
  border-color: #1e293b;
  background: #1e293b;
  color: #fff;
}

.asp-inbox-body {
  flex: 1;
  overflow-y: auto;
  padding: 10px 10px 16px;
  min-height: 0;
}

.asp-empty {
  text-align: center;
  color: #94a3b8;
  font-size: 13px;
  padding: 28px 8px;
}

.asp-card {
  width: 100%;
  border: 1px solid #e8edf5;
  border-radius: 12px;
  padding: 12px;
  margin-bottom: 10px;
  background: #fff;
  text-align: left;
  cursor: pointer;
  transition:
    box-shadow 0.15s,
    border-color 0.15s,
    transform 0.12s;
}

.asp-card:hover {
  border-color: #cbd5e1;
  box-shadow: 0 6px 16px rgba(15, 23, 42, 0.06);
}

.asp-card--active {
  border-color: #1e293b;
  box-shadow: 0 0 0 1px #1e293b;
}

.asp-card--unread:not(.asp-card--active) {
  border-left: 3px solid #3b82f6;
  padding-left: 9px;
}

.asp-card-top {
  display: flex;
  gap: 10px;
  align-items: flex-start;
}

.asp-avatar {
  flex-shrink: 0;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: linear-gradient(145deg, #e2e8f0 0%, #cbd5e1 100%);
  color: #1e293b;
  font-size: 14px;
  font-weight: 800;
  display: flex;
  align-items: center;
  justify-content: center;
}

.asp-card-main {
  min-width: 0;
  flex: 1;
}

.asp-card-row {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: 8px;
}

.asp-card-name {
  font-weight: 700;
  font-size: 14px;
  color: #0f172a;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.asp-card-time {
  font-size: 11px;
  color: #94a3b8;
  flex-shrink: 0;
}

.asp-card-preview {
  margin: 6px 0 0;
  font-size: 12px;
  color: #64748b;
  line-height: 1.45;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.asp-card-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.asp-tag {
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  padding: 3px 8px;
  border-radius: 999px;
  background: #f1f5f9;
  color: #475569;
}

.asp-tag--user {
  background: #f1f5f9;
  color: #475569;
}

.asp-tag--merchant {
  background: #e2e8f0;
  color: #334155;
}

.asp-tag--priority {
  background: #fff7ed;
  color: #c2410c;
  border: 1px solid #fed7aa;
}

/* ----- 中间对话 ----- */
.asp-chat-col {
  grid-column: 2;
  grid-row: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  background: #fff;
}

.asp-banner {
  padding: 10px 16px;
  background: #fffbeb;
  color: #92400e;
  font-size: 13px;
  border-bottom: 1px solid #fde68a;
}

.asp-chat-toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 18px;
  border-bottom: 1px solid #e8edf5;
  background: linear-gradient(180deg, #fff 0%, #f8fafc 100%);
}

.asp-chat-title {
  font-weight: 800;
  font-size: 17px;
  color: #0f172a;
}

.asp-chat-sub {
  margin-top: 4px;
  font-size: 13px;
  color: #64748b;
}

.asp-chat-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.asp-link-btn {
  border: none;
  background: none;
  color: #475569;
  font-size: 13px;
  font-weight: 700;
  cursor: not-allowed;
  opacity: 0.5;
}

.asp-close-btn {
  border: 1px solid #fecaca;
  background: #fff1f2;
  color: #b91c1c;
  font-size: 12px;
  font-weight: 700;
  padding: 8px 14px;
  border-radius: 10px;
  cursor: not-allowed;
  opacity: 0.65;
}

.asp-msg-feed {
  flex: 1;
  overflow-y: auto;
  padding: 16px 18px 20px;
  min-height: 0;
  background: #fafbfc;
}

.asp-system-line {
  text-align: center;
  font-size: 12px;
  color: #94a3b8;
  margin-bottom: 16px;
  padding: 6px 12px;
  background: #f1f5f9;
  border-radius: 999px;
  max-width: 92%;
  margin-left: auto;
  margin-right: auto;
}

.asp-msg-empty {
  text-align: center;
  color: #94a3b8;
  font-size: 14px;
  padding: 24px;
}

.asp-msg-block {
  margin-bottom: 16px;
  max-width: 82%;
}

.asp-msg-block--mine {
  margin-left: auto;
  text-align: right;
}

.asp-msg-meta {
  font-size: 11px;
  font-weight: 700;
  color: #64748b;
  margin-bottom: 4px;
  letter-spacing: 0.02em;
}

.asp-msg-bubble {
  display: inline-block;
  text-align: left;
  padding: 11px 14px;
  border-radius: 14px;
  font-size: 14px;
  line-height: 1.55;
  color: #0f172a;
  background: #f1f5f9;
  border: 1px solid #e2e8f0;
}

.asp-msg-block--mine .asp-msg-bubble {
  background: #1e293b;
  border-color: #1e293b;
  color: #fff;
}

.asp-msg-when {
  font-size: 11px;
  color: #94a3b8;
  margin-top: 4px;
}

.asp-composer {
  border-top: 1px solid #e8edf5;
  padding: 12px 16px 14px;
  background: #fff;
}

.asp-textarea {
  width: 100%;
  resize: vertical;
  min-height: 72px;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 12px 14px;
  font-size: 14px;
  line-height: 1.5;
  color: #0f172a;
  font-family: inherit;
}

.asp-textarea:focus {
  outline: none;
  border-color: #94a3b8;
  box-shadow: 0 0 0 3px rgba(30, 41, 59, 0.08);
}

.asp-composer-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 10px;
}

.asp-icon-group {
  display: flex;
  gap: 4px;
}

.asp-icon-btn {
  width: 36px;
  height: 36px;
  border: 1px solid #e8edf5;
  border-radius: 10px;
  background: #fff;
  cursor: not-allowed;
  opacity: 0.45;
  font-size: 15px;
  line-height: 1;
}

.asp-send-primary {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  border: none;
  border-radius: 12px;
  padding: 10px 18px;
  font-size: 13px;
  font-weight: 800;
  letter-spacing: 0.02em;
  color: #fff;
  background: #1e293b;
  cursor: pointer;
  transition: opacity 0.15s;
}

.asp-send-primary:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.asp-send-glyph {
  font-size: 12px;
  opacity: 0.9;
}

.asp-chat-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 24px;
  color: #64748b;
}

.asp-chat-empty-title {
  font-weight: 700;
  font-size: 16px;
  margin: 0 0 8px;
}

.asp-chat-empty-hint {
  margin: 0;
  font-size: 13px;
  text-align: center;
  max-width: 320px;
}

/* ----- 右侧上下文 ----- */
.asp-context-col {
  grid-column: 3;
  grid-row: 1;
  border-left: 1px solid #e8edf5;
  background: #f8fafc;
  padding: 16px 14px 20px;
  overflow-y: auto;
  min-height: 0;
}

.asp-context-col--muted {
  display: flex;
  align-items: center;
  justify-content: center;
}

.asp-context-placeholder {
  font-size: 13px;
  color: #94a3b8;
  text-align: center;
  padding: 24px 12px;
}

.asp-profile-card {
  background: #fff;
  border: 1px solid #e8edf5;
  border-radius: 14px;
  padding: 18px 14px;
  box-shadow: 0 4px 14px rgba(15, 23, 42, 0.04);
}

.asp-profile-avatar {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  margin: 0 auto 12px;
  background: linear-gradient(145deg, #e2e8f0 0%, #cbd5e1 100%);
  color: #1e293b;
  font-size: 20px;
  font-weight: 800;
  display: flex;
  align-items: center;
  justify-content: center;
}

.asp-profile-name {
  text-align: center;
  font-weight: 800;
  font-size: 15px;
  color: #0f172a;
  line-height: 1.35;
}

.asp-profile-role {
  text-align: center;
  margin-top: 10px;
}

.asp-profile-dl {
  margin: 16px 0 0;
}

.asp-profile-row {
  margin-bottom: 12px;
}

.asp-profile-row dt {
  font-size: 11px;
  font-weight: 800;
  color: #94a3b8;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  margin-bottom: 4px;
}

.asp-profile-row dd {
  margin: 0;
  font-size: 13px;
  color: #334155;
  line-height: 1.45;
  word-break: break-all;
}

.asp-mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
  font-size: 12px;
}

.asp-notes-card {
  margin-top: 14px;
  background: #fff;
  border: 1px dashed #cbd5e1;
  border-radius: 14px;
  padding: 14px;
}

.asp-notes-title {
  font-size: 12px;
  font-weight: 800;
  color: #475569;
  margin-bottom: 10px;
}

.asp-notes-add {
  width: 100%;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 8px;
  font-size: 12px;
  font-weight: 700;
  color: #64748b;
  background: #f8fafc;
  cursor: not-allowed;
  opacity: 0.7;
}

@media (max-width: 1100px) {
  .asp-shell {
    grid-template-columns: 1fr;
    grid-template-rows: auto auto auto auto;
    max-height: none;
    min-height: auto;
  }

  .asp-shell-fill {
    grid-column: 1;
    grid-row: 1;
  }

  .asp-inbox {
    grid-column: 1;
    grid-row: 2;
    border-right: none;
    border-bottom: 1px solid #e8edf5;
    max-height: 320px;
  }

  .asp-chat-col {
    grid-column: 1;
    grid-row: 3;
  }

  .asp-context-col {
    grid-column: 1;
    grid-row: 4;
    border-left: none;
    border-top: 1px solid #e8edf5;
  }
}
</style>
