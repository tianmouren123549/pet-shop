<script setup>
import { computed, onMounted, onUnmounted, ref, nextTick } from 'vue'
import { api } from '../../utils/request'

const loading = ref(false)
const sending = ref(false)
const errorMsg = ref('')
const sessions = ref([])
const activeSessionId = ref(null)
const messages = ref([])
const inputText = ref('')
const listRef = ref(null)

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

function isMine(msg) {
  return msg?.senderType === 'MERCHANT'
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
  loadSessions(false)
  sessionPollTimer = setInterval(() => loadSessions(true), 8000)
})

onUnmounted(() => {
  if (sessionPollTimer) clearInterval(sessionPollTimer)
  sessionPollTimer = null
})
</script>

<template>
  <div class="pw-page">
    <section class="pw-hero">
      <h1 class="pw-title">商家客服会话</h1>
      <p class="pw-lead">查看用户咨询并进行回复。</p>
    </section>

    <div v-if="loading" class="pw-state">加载中...</div>
    <div v-else-if="errorMsg" class="pw-state pw-state--error">{{ errorMsg }}</div>
    <div v-else class="msw-layout">
      <aside class="msw-session-panel">
        <div class="msw-panel-title">会话列表</div>
        <div v-if="sessions.length === 0" class="msw-session-empty">暂无会话</div>
        <button
          v-for="s in sessions"
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
              <img
                v-if="s.productImageUrl"
                :src="s.productImageUrl"
                class="msw-session-img"
                alt=""
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
      </aside>

      <div class="pw-chat-wrap msw-chat-wrap-main">
        <template v-if="activeSession">
          <div class="msw-context">
            <div class="msw-context-thumb">
              <img
                v-if="activeSession.productImageUrl"
                :src="activeSession.productImageUrl"
                class="msw-context-img"
                :alt="activeSession.productTitle || '商品'"
              />
              <div v-else class="msw-context-thumb-ph">暂无图片</div>
            </div>
            <div class="msw-context-body">
              <div class="msw-context-title">{{ activeSession.productTitle || '—' }}</div>
              <div class="msw-context-sub">
                咨询用户：{{ activeSession.userNickname }}
              </div>
              <div class="msw-context-sub">
                <template v-if="activeSession.orderNo">订单编号：{{ activeSession.orderNo }}</template>
                <template v-else>未关联订单</template>
              </div>
              <div
                v-if="Number(activeSession.relatedLineCount) > 1"
                class="msw-context-sub msw-context-sub--muted"
              >
                本单本店共 {{ activeSession.relatedLineCount }} 件商品
              </div>
            </div>
          </div>
          <div ref="listRef" class="pw-msg-list">
            <div v-if="messages.length === 0" class="msw-msg-placeholder">暂无消息。</div>
            <div v-for="msg in messages" :key="msg.messageId" class="pw-msg-row" :class="{ mine: isMine(msg) }">
              <div class="pw-msg-sender">{{ isMine(msg) ? '我(商家)' : '用户' }}</div>
              <div class="pw-bubble">{{ msg.content }}</div>
              <div class="pw-msg-time">{{ fmtTime(msg.createdAt) }}</div>
            </div>
          </div>
          <div class="pw-input-bar">
            <input
              v-model="inputText"
              class="pw-input"
              type="text"
              maxlength="200"
              placeholder="输入回复内容..."
              @keyup.enter="sendMessage"
            />
            <button
              type="button"
              class="pw-send"
              :disabled="sending || !inputText.trim() || !activeSessionId"
              @click="sendMessage"
            >
              {{ sending ? '发送中...' : '发送' }}
            </button>
          </div>
        </template>
        <div v-else class="msw-chat-empty">
          <p class="msw-chat-empty-title">请从左侧选择一个会话</p>
          <p class="msw-chat-empty-hint">有新消息时，会话旁会显示红点；进入「用户咨询」不会自动把消息标为已读。</p>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.msw-chat-wrap-main {
  display: flex;
  flex-direction: column;
  min-height: 520px;
}

.msw-chat-wrap-main > .msw-chat-empty {
  flex: 1;
}

.msw-layout {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: 12px;
  align-items: stretch;
}

.msw-session-panel {
  background: #f4f6f9;
  border: 1px solid #dbe2ea;
  border-radius: 6px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.msw-panel-title {
  font-size: 12px;
  font-weight: 800;
  color: #506078;
  letter-spacing: 0.3px;
  padding: 12px 14px;
  border-bottom: 1px solid #dbe3ee;
  background: linear-gradient(180deg, #f8fafc 0%, #f4f6f9 100%);
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
  padding: 11px 14px;
  cursor: pointer;
  transition: background 0.15s;
}

.msw-session-item:hover {
  background: #f1f3f6;
}

.msw-session-item--active {
  background: #e8ecf2;
  border-left: 3px solid #0b1630;
  padding-left: 11px;
}

.msw-session-item--unread:not(.msw-session-item--active) {
  background: #fff8f5;
  box-shadow: inset 3px 0 0 #ff4d4f;
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
  border-radius: 4px;
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

.msw-context {
  display: flex;
  gap: 14px;
  align-items: flex-start;
  padding: 12px 14px;
  border-bottom: 1px solid #e3e8f0;
  background: linear-gradient(180deg, #fafbfd 0%, #f6f8fb 100%);
}

.msw-context-thumb {
  flex: 0 0 64px;
  width: 64px;
  height: 64px;
  border-radius: 4px;
  overflow: hidden;
  border: 1px solid #dbe3ee;
  background: #fff;
}

.msw-context-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.msw-context-thumb-ph {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  color: #8a96a8;
  text-align: center;
  padding: 4px;
}

.msw-context-body {
  flex: 1;
  min-width: 0;
}

.msw-context-title {
  font-size: 15px;
  font-weight: 800;
  color: #131e30;
  line-height: 1.4;
  word-break: break-word;
}

.msw-context-sub {
  font-size: 12px;
  color: #506078;
  margin-top: 6px;
}

.msw-context-sub--muted {
  color: #7b8798;
  font-weight: 600;
}

.msw-msg-placeholder {
  text-align: center;
  color: #6b7788;
  font-size: 13px;
  padding: 32px 0;
}

.msw-chat-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 24px;
  text-align: center;
  min-height: 280px;
}

.msw-chat-empty-title {
  margin: 0 0 10px;
  font-size: 16px;
  font-weight: 800;
  color: #1a2740;
}

.msw-chat-empty-hint {
  margin: 0;
  font-size: 13px;
  color: #6b7b91;
  line-height: 1.55;
  max-width: 360px;
}

@media (max-width: 900px) {
  .msw-layout {
    grid-template-columns: 1fr;
  }
}
</style>
