<script setup>
import { computed, onMounted, onUnmounted, ref, nextTick, watch } from 'vue'
import { api } from '../../utils/request'
import AppImage from '../../components/AppImage.vue'

const loading = ref(false)
const sending = ref(false)
const errorMsg = ref('')
const sessions = ref([])
const activeSessionId = ref(null)
const messages = ref([])
const inputText = ref('')
const listRef = ref(null)
/** 会话列表筛选：全部 / 待回复 / 关联订单 */
const sessionFilter = ref('all')

/** 定时刷新会话列表上的未读标识（不拉消息，不会误标已读） */
let sessionPollTimer = null

/**
 * 当前选中的会话（含订单/商品摘要，供侧栏与顶部上下文展示）。
 */
const activeSession = computed(() => {
  const id = Number(activeSessionId.value || 0)
  if (!id) return null
  return sessions.value.find((x) => Number(x.sessionId) === id) || null
})

const SESSION_FILTERS = [
  { id: 'all', label: '全部' },
  { id: 'unread', label: '待回复' },
  { id: 'with_order', label: '关联订单' },
]

const filteredSessions = computed(() => {
  const arr = Array.isArray(sessions.value) ? sessions.value : []
  const f = sessionFilter.value
  if (f === 'unread') return arr.filter((s) => s.unreadFromUser)
  if (f === 'with_order') return arr.filter((s) => String(s.orderNo || '').trim())
  return arr
})

watch(filteredSessions, (list) => {
  const cur = Number(activeSessionId.value || 0)
  if (!cur) return
  const still = list.some((s) => Number(s.sessionId) === cur)
  if (!still) {
    activeSessionId.value = null
    messages.value = []
  }
})

function merchantId() {
  return Number(localStorage.getItem('adminId') || 0)
}

function fmtTime(iso) {
  if (!iso) return '--'
  const d = new Date(iso)
  const hh = String(d.getHours()).padStart(2, '0')
  const mm = String(d.getMinutes()).padStart(2, '0')
  return `${hh}:${mm}`
}

function dayStartTs(d) {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime()
}

/** 日期分隔条文案（对齐客服工作台：今天 / 昨天 / 具体日期 + 时间） */
function formatDaySeparator(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  const today = new Date()
  const yesterday = new Date(today)
  yesterday.setDate(yesterday.getDate() - 1)
  const hh = String(d.getHours()).padStart(2, '0')
  const mm = String(d.getMinutes()).padStart(2, '0')
  const t = dayStartTs(d)
  if (t === dayStartTs(today)) return `今天 ${hh}:${mm}`
  if (t === dayStartTs(yesterday)) return `昨天 ${hh}:${mm}`
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${hh}:${mm}`
}

const QUICK_REPLIES = ['您好，很高兴为您服务', '请稍等，正在为您核实', '感谢您的咨询，祝您生活愉快！']
const EMOJI_PICK = ['😊', '👍', '🙏', '❤️', '✅', '📦']

const userInitial = computed(() => {
  const n = String(activeSession.value?.userNickname || '').trim()
  return n ? n.slice(0, 1).toUpperCase() : '用'
})

const messageBlocks = computed(() => {
  const arr = Array.isArray(messages.value) ? messages.value : []
  const blocks = []
  let lastDayKey = ''
  for (const msg of arr) {
    const iso = msg.createdAt
    const d = iso ? new Date(iso) : null
    const dayKey = d ? `${d.getFullYear()}-${d.getMonth()}-${d.getDate()}` : ''
    if (dayKey !== lastDayKey) {
      lastDayKey = dayKey
      blocks.push({
        kind: 'sep',
        key: `sep-${msg.messageId}-${blocks.length}`,
        label: formatDaySeparator(iso),
      })
    }
    blocks.push({ kind: 'msg', key: msg.messageId, msg })
  }
  return blocks
})

function isMine(msg) {
  return msg?.senderType === 'MERCHANT'
}

function appendToInput(bit) {
  if (!bit) return
  inputText.value = `${inputText.value}${bit}`
}

function insertQuickReply(line) {
  const cur = inputText.value.trimEnd()
  inputText.value = cur ? `${cur}\n${line}` : line
}

function onComposerKeydown(e) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    sendMessage()
  }
}

async function scrollBottom() {
  await nextTick()
  if (listRef.value) listRef.value.scrollTop = listRef.value.scrollHeight
}

async function loadMessages(sessionId) {
  const res = await api.merchantGetSupportMessages(sessionId, merchantId())
  if (res.code !== 200) {
    errorMsg.value = res.message || '消息加载失败'
    return
  }
  messages.value = Array.isArray(res.data) ? res.data : []
  await scrollBottom()
  await loadSessions(true)
  window.dispatchEvent(new CustomEvent('petshop-chat-unread-updated'))
}

/**
 * @param {boolean} preserveSelection 为 false 时用于首次进入：只展示列表，不自动打开会话（避免一进页面就把用户消息标已读导致顶栏无红点）。
 */
async function loadSessions(preserveSelection = true) {
  const isInitial = !preserveSelection
  if (isInitial) {
    loading.value = true
    errorMsg.value = ''
  }
  const mid = merchantId()
  if (!mid) {
    if (isInitial) loading.value = false
    errorMsg.value = '请先登录商家账号'
    return
  }
  const res = await api.merchantGetSupportSessions(mid)
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
  const res = await api.merchantSendSupportMessage({
    sessionId: activeSessionId.value,
    merchantId: merchantId(),
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

onMounted(() => {
  if (!SESSION_FILTERS.some((x) => x.id === sessionFilter.value)) {
    sessionFilter.value = 'all'
  }
  loadSessions(false)
  sessionPollTimer = setInterval(() => loadSessions(true), 8000)
})

onUnmounted(() => {
  if (sessionPollTimer) clearInterval(sessionPollTimer)
  sessionPollTimer = null
})
</script>

<template>
  <div class="pw-page msw-support-page">
    <div v-if="loading" class="pw-state">加载中...</div>
    <div v-else-if="errorMsg" class="pw-state pw-state--error">{{ errorMsg }}</div>
    <div v-else class="msw-body">
      <div class="msw-workbench">
      <div class="msw-layout">
      <aside class="msw-session-panel">
        <div class="msw-filters" role="tablist" aria-label="筛选会话">
          <button
            v-for="f in SESSION_FILTERS"
            :key="f.id"
            type="button"
            role="tab"
            class="msw-filter-chip"
            :class="{ 'msw-filter-chip--on': sessionFilter === f.id }"
            @click="sessionFilter = f.id"
          >
            {{ f.label }}
          </button>
        </div>
        <div class="msw-panel-title">会话列表</div>
        <div class="msw-session-scroll">
        <div v-if="sessions.length === 0" class="msw-session-empty">暂无会话</div>
        <div v-else-if="filteredSessions.length === 0" class="msw-session-empty">该筛选下暂无会话</div>
        <button
          v-for="s in filteredSessions"
          :key="s.sessionId"
          type="button"
          class="msw-session-item"
          :class="{
            'msw-session-item--active': activeSessionId === s.sessionId,
            'msw-session-item--unread': s.unreadFromUser,
          }"
          @click="selectSession(s.sessionId)"
        >
          <div class="msw-session-row">
            <div class="msw-session-thumb">
              <AppImage
                v-if="s.productImageUrl"
                :src="s.productImageUrl"
                class="msw-session-img"
                alt=""
                loading="lazy"
                decoding="async"
                aspect-ratio="1 / 1"
              />
              <div v-else class="msw-session-thumb-ph">图</div>
            </div>
            <div class="msw-session-main">
              <div class="msw-session-name-row">
                <span class="msw-session-name">{{ s.userNickname }}</span>
                <span
                  v-if="s.unreadFromUser"
                  class="msw-unread-dot"
                  title="有新消息"
                  aria-label="有新消息"
                />
              </div>
              <div class="msw-session-product">{{ s.productTitle || '—' }}</div>
              <div class="msw-session-meta">
                <template v-if="s.orderNo">订单：{{ s.orderNo }}</template>
                <template v-else>未关联订单</template>
              </div>
            </div>
          </div>
        </button>
        </div>
      </aside>

      <div class="pw-chat-wrap msw-chat-wrap-main">
        <template v-if="activeSession">
          <header class="msw-chat-head">
            <div class="msw-chat-head-main">
              <div class="msw-chat-peer">
                <span class="msw-chat-peer-name">{{ activeSession.userNickname || '用户' }}</span>
                <span class="msw-chat-online" title="在线" aria-label="在线" />
              </div>
              <p class="msw-chat-head-line">
                <span class="msw-chat-head-product">{{ activeSession.productTitle || '商品咨询' }}</span>
                <span class="msw-chat-head-dot" aria-hidden="true">·</span>
                <span v-if="activeSession.orderNo" class="msw-chat-head-order">订单 {{ activeSession.orderNo }}</span>
                <span v-else class="msw-chat-head-order msw-chat-head-order--muted">未关联订单</span>
                <template v-if="Number(activeSession.relatedLineCount) > 1">
                  <span class="msw-chat-head-dot" aria-hidden="true">·</span>
                  <span class="msw-chat-head-order--muted">本单 {{ activeSession.relatedLineCount }} 件</span>
                </template>
              </p>
            </div>
            <div class="msw-chat-head-actions" aria-hidden="true">
              <button type="button" class="msw-chat-icon-btn" title="电话（即将开放）">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
                  <path
                    d="M8.5 4h2l1.5 5-1.2 1a12 12 0 006.2 6.2l1-1.2 5 1.5v2a2 2 0 01-2.2 2C10.7 18.2 5.8 13.3 4 7.2A2 2 0 016 5z"
                    stroke-linejoin="round"
                  />
                </svg>
              </button>
              <button type="button" class="msw-chat-icon-btn" title="更多（即将开放）">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor">
                  <circle cx="12" cy="5" r="2" />
                  <circle cx="12" cy="12" r="2" />
                  <circle cx="12" cy="19" r="2" />
                </svg>
              </button>
            </div>
          </header>

          <div ref="listRef" class="msw-feed">
            <div v-if="messages.length === 0" class="msw-msg-placeholder">暂无消息</div>
            <div v-for="block in messageBlocks" :key="block.key">
              <div v-if="block.kind === 'sep'" class="msw-day-sep">
                <span>{{ block.label }}</span>
              </div>
              <div
                v-else
                class="msw-msg-row"
                :class="{ 'msw-msg-row--mine': isMine(block.msg) }"
              >
                <template v-if="!isMine(block.msg)">
                  <div class="msw-avatar msw-avatar--user" aria-hidden="true">{{ userInitial }}</div>
                  <div class="msw-msg-col">
                    <div class="msw-bubble msw-bubble--user">{{ block.msg.content }}</div>
                    <div class="msw-msg-time">{{ fmtTime(block.msg.createdAt) }}</div>
                  </div>
                </template>
                <template v-else>
                  <div class="msw-msg-col msw-msg-col--mine">
                    <div class="msw-bubble msw-bubble--merchant">{{ block.msg.content }}</div>
                    <div class="msw-msg-time">{{ fmtTime(block.msg.createdAt) }}</div>
                  </div>
                  <div class="msw-avatar msw-avatar--shop" aria-hidden="true">店</div>
                </template>
              </div>
            </div>
          </div>

          <div class="msw-compose">
            <div class="msw-compose-toolbar">
              <div class="msw-compose-tools">
                <span class="msw-toolbar-label">表情</span>
                <button
                  v-for="em in EMOJI_PICK"
                  :key="em"
                  type="button"
                  class="msw-tool-emoji"
                  @click="appendToInput(em)"
                >
                  {{ em }}
                </button>
              </div>
              <div class="msw-compose-quick">
                <span class="msw-toolbar-label">常用语</span>
                <button
                  v-for="(line, idx) in QUICK_REPLIES"
                  :key="idx"
                  type="button"
                  class="msw-quick-btn"
                  @click="insertQuickReply(line)"
                >
                  {{ line }}
                </button>
              </div>
            </div>
            <div class="msw-compose-field">
              <textarea
                v-model="inputText"
                class="msw-compose-input"
                rows="4"
                maxlength="500"
                placeholder="按 Shift+Enter 换行，Enter 发送"
                @keydown="onComposerKeydown"
              />
              <div class="msw-compose-actions">
                <button
                  type="button"
                  class="msw-send-btn"
                  :disabled="sending || !inputText.trim() || !activeSessionId"
                  @click="sendMessage"
                >
                  {{ sending ? '发送中…' : '发送' }}
                </button>
              </div>
            </div>
          </div>
        </template>
        <div v-else class="msw-chat-empty">
          <div class="msw-chat-empty-icon" aria-hidden="true">
            <svg viewBox="0 0 64 64" width="56" height="56" fill="none">
              <circle cx="32" cy="32" r="30" stroke="currentColor" stroke-width="2" opacity="0.2" />
              <path
                d="M22 28h20M22 36h14M28 44h8"
                stroke="currentColor"
                stroke-width="2.5"
                stroke-linecap="round"
                opacity="0.45"
              />
            </svg>
          </div>
          <p class="msw-chat-empty-title">请选择一个咨询会话</p>
        </div>
      </div>
      </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 取消 pw-page 默认 max-width / 居中，使工作台在 main 内左右充盈 */
.msw-support-page.pw-page {
  max-width: none;
  width: 100%;
  margin: 0;
  padding: 0 0 24px;
  box-sizing: border-box;
}

.msw-support-page {
  background: #f0f2f5;
  min-height: 100%;
}

.msw-body {
  max-width: none;
  width: 100%;
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

.msw-workbench {
  border-radius: 16px;
  border: 1px solid #e2e8f0;
  background: #fff;
  box-shadow: 0 4px 22px rgba(15, 23, 42, 0.06);
  overflow: hidden;
}

.msw-chat-wrap-main {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  height: 100%;
  border: none !important;
  border-radius: 0 !important;
  background: #fff !important;
}

.msw-chat-wrap-main > .msw-chat-empty {
  flex: 1;
}

.msw-layout {
  display: grid;
  grid-template-columns: minmax(260px, 340px) minmax(0, 1fr);
  gap: 0;
  align-items: stretch;
  min-height: clamp(520px, calc(100dvh - 132px), 920px);
}

.msw-filters {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 12px 14px;
  border-bottom: 1px solid #e8ecf1;
  background: #fff;
}

.msw-filter-chip {
  padding: 6px 12px;
  border-radius: 999px;
  border: 1px solid #e2e8f0;
  background: #fff;
  font-size: 12px;
  font-weight: 700;
  color: #475569;
  cursor: pointer;
  transition:
    background 0.15s,
    border-color 0.15s,
    color 0.15s;
}

.msw-filter-chip:hover {
  border-color: #cbd5e1;
  color: #0f172a;
}

.msw-filter-chip--on {
  border-color: #93c5fd;
  background: #eff6ff;
  color: #1d4ed8;
  box-shadow: none;
}

.msw-session-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
}

.msw-session-panel {
  background: #f8fafc;
  border: none;
  border-radius: 0;
  border-right: 1px solid #e8ecf1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.msw-panel-title {
  font-size: 13px;
  font-weight: 800;
  color: #506078;
  letter-spacing: 0.3px;
  padding: 12px 16px;
  border-bottom: 1px solid #e8ecf1;
  background: #f8fafc;
}

.msw-session-empty {
  text-align: center;
  color: #6b7788;
  font-size: 13px;
  padding: 24px 12px;
}

.msw-session-item {
  width: 100%;
  border: none;
  border-bottom: 1px solid #e3e8f0;
  background: #fcfdff;
  text-align: left;
  padding: 12px 14px;
  cursor: pointer;
  transition: background 0.15s, box-shadow 0.15s, transform 0.15s;
}

.msw-session-item:hover {
  background: #eef3fa;
  box-shadow: inset 0 0 0 1px #e0e8f3;
  transform: translateY(-1px);
}

.msw-session-item--active {
  background: #e9f0fb;
  border-left: 3px solid #0b1630;
  padding-left: 11px;
}

.msw-session-item--unread:not(.msw-session-item--active) {
  background: #f4f8ff;
  box-shadow: inset 3px 0 0 #1a3358;
}

.msw-session-name-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.msw-unread-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #ff4d4f;
  flex-shrink: 0;
  box-shadow: 0 0 0 1px #fcfdff;
}

.msw-session-row {
  display: flex;
  gap: 10px;
  align-items: flex-start;
}

.msw-session-thumb {
  flex: 0 0 44px;
  width: 44px;
  height: 44px;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #dbe3ee;
  background: #f4f7fb;
}

.msw-session-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.msw-session-thumb-ph {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  color: #8a96a8;
}

.msw-session-main {
  flex: 1;
  min-width: 0;
}

.msw-session-name {
  font-size: 14px;
  color: #131e30;
  font-weight: 700;
  min-width: 0;
}

.msw-session-product {
  font-size: 12px;
  color: #3d4d63;
  font-weight: 600;
  margin-top: 4px;
  line-height: 1.35;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.msw-session-meta {
  font-size: 11px;
  color: #7b8798;
  margin-top: 4px;
}

/* ---------- 中间聊天区（参考客服工作台气泡与输入区） ---------- */

.msw-chat-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 12px;
  border-bottom: 1px solid #eef1f4;
  background: #fff;
}

.msw-chat-head-main {
  min-width: 0;
  flex: 1;
}

.msw-chat-peer {
  display: flex;
  align-items: center;
  gap: 8px;
}

.msw-chat-peer-name {
  font-size: 16px;
  font-weight: 800;
  color: #1a1d21;
  letter-spacing: 0.02em;
}

.msw-chat-online {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #22c55e;
  box-shadow: 0 0 0 2px #fff;
  flex-shrink: 0;
}

.msw-chat-head-line {
  margin: 6px 0 0;
  font-size: 12px;
  color: #64748b;
  line-height: 1.45;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
}

.msw-chat-head-product {
  font-weight: 600;
  color: #475569;
  word-break: break-word;
}

.msw-chat-head-dot {
  color: #cbd5e1;
}

.msw-chat-head-order {
  font-weight: 600;
  color: #334155;
}

.msw-chat-head-order--muted {
  color: #94a3b8;
  font-weight: 500;
}

.msw-chat-head-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}

.msw-chat-icon-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: none;
  border-radius: 10px;
  background: transparent;
  color: #64748b;
  cursor: default;
  transition:
    background 0.15s,
    color 0.15s;
}

.msw-chat-icon-btn:hover {
  background: #f1f5f9;
  color: #1a1d21;
}

.msw-feed {
  flex: 1 1 auto;
  min-height: 280px;
  overflow-y: auto;
  /* 收窄左右留白，气泡区域贴近面板两侧，形成「左右充盈」 */
  padding: 12px 10px 18px;
  background: #f8f9fa;
  -webkit-overflow-scrolling: touch;
}

.msw-feed::-webkit-scrollbar {
  width: 8px;
}

.msw-feed::-webkit-scrollbar-thumb {
  background: #cbd5e1;
  border-radius: 999px;
}

.msw-day-sep {
  display: flex;
  justify-content: center;
  margin: 14px 0 18px;
}

.msw-day-sep span {
  padding: 5px 14px;
  font-size: 12px;
  font-weight: 600;
  color: #64748b;
  background: rgba(255, 255, 255, 0.92);
  border-radius: 999px;
  border: 1px solid #e8ecf1;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
}

.msw-msg-row {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  margin-bottom: 6px;
  width: 100%;
  box-sizing: border-box;
}

.msw-msg-row--mine {
  flex-direction: row;
  justify-content: flex-end;
}

.msw-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 800;
}

.msw-avatar--user {
  background: linear-gradient(145deg, #e8eef7 0%, #dce6f5 100%);
  color: #334155;
  border: 1px solid #e2e8f0;
}

.msw-avatar--shop {
  border-radius: 8px;
  background: #1a1d21;
  color: #fff;
  font-size: 14px;
  letter-spacing: 0.04em;
  border: none;
}

.msw-msg-col {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  flex: 1 1 auto;
  min-width: 0;
  max-width: calc(100% - 44px);
}

.msw-msg-col--mine {
  align-items: flex-end;
  flex: 0 1 auto;
  max-width: calc(100% - 44px);
}

.msw-bubble {
  display: inline-block;
  width: fit-content;
  max-width: 100%;
  padding: 11px 14px;
  border-radius: 14px;
  font-size: 14px;
  line-height: 1.55;
  word-break: break-word;
  white-space: pre-wrap;
  box-sizing: border-box;
  vertical-align: top;
}

.msw-bubble--user {
  background: #fff;
  border: 1px solid #e8ecf1;
  color: #1a1d21;
  border-bottom-left-radius: 6px;
}

.msw-bubble--merchant {
  background: #1a1d21;
  color: #fff;
  border-bottom-right-radius: 6px;
}

.msw-msg-time {
  font-size: 11px;
  color: #94a3b8;
  margin-top: 6px;
  padding: 0 2px;
}

.msw-compose {
  border-top: 1px solid #f0f2f5;
  background: #fff;
  padding: 14px 12px 16px;
}

.msw-compose-toolbar {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 12px;
}

.msw-compose-tools,
.msw-compose-quick {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.msw-toolbar-label {
  flex-shrink: 0;
  font-size: 12px;
  font-weight: 600;
  color: #8c8c8c;
  margin-right: 2px;
}

.msw-tool-emoji {
  border: none;
  background: #f5f5f5;
  border-radius: 50%;
  width: 34px;
  height: 34px;
  padding: 0;
  font-size: 17px;
  line-height: 1;
  cursor: pointer;
  transition: background 0.15s;
  box-shadow: inset 0 0 0 1px rgba(0, 0, 0, 0.04);
}

.msw-tool-emoji:hover {
  background: #ebebeb;
}

.msw-quick-btn {
  max-width: 100%;
  padding: 6px 14px;
  border-radius: 999px;
  border: none;
  background: #f5f5f5;
  font-size: 12px;
  font-weight: 500;
  color: #595959;
  cursor: pointer;
  transition:
    background 0.15s,
    color 0.15s;
}

.msw-quick-btn:hover {
  background: #ebebeb;
}

.msw-compose-field {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.msw-compose-input {
  width: 100%;
  box-sizing: border-box;
  min-height: 96px;
  max-height: 220px;
  padding: 10px 8px 6px;
  margin: 0;
  border: none;
  border-radius: 0;
  font-size: 14px;
  line-height: 1.55;
  color: #262626;
  background: transparent;
  resize: vertical;
  outline: none;
}

.msw-compose-input:focus {
  outline: none;
}

.msw-compose-input::placeholder {
  color: #bfbfbf;
}

.msw-compose-actions {
  display: flex;
  justify-content: flex-end;
  align-items: center;
}

.msw-send-btn {
  flex-shrink: 0;
  padding: 8px 26px;
  min-height: 36px;
  border: none;
  border-radius: 6px;
  background: #8c8c8c;
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition:
    background 0.15s,
    opacity 0.15s;
}

.msw-send-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.msw-msg-placeholder {
  text-align: center;
  color: #64748b;
  font-size: 14px;
  padding: 48px 16px;
  line-height: 1.55;
}

.msw-chat-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 24px;
  text-align: center;
  min-height: 420px;
  background: linear-gradient(180deg, #ffffff 0%, #f1f5f9 100%);
  border-radius: 14px;
  border: 1px solid #e2e8f0;
}

.msw-chat-empty-icon {
  color: #64748b;
  margin-bottom: 16px;
}

.msw-chat-empty-title {
  margin: 0;
  font-size: 18px;
  font-weight: 800;
  color: #1a2740;
}

@media (max-width: 900px) {
  .msw-layout {
    grid-template-columns: 1fr;
    min-height: auto;
  }

  .msw-session-panel {
    border-right: none;
    border-bottom: 1px solid #e8ecf1;
    max-height: min(42vh, 360px);
  }

  .msw-chat-wrap-main {
    min-height: min(52vh, 520px);
  }
}
</style>
