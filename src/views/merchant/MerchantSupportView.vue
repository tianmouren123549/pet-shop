<script setup>
import { onMounted, ref, nextTick } from 'vue'
import { api } from '../../utils/request'

const loading = ref(false)
const sending = ref(false)
const errorMsg = ref('')
const sessions = ref([])
const activeSessionId = ref(null)
const messages = ref([])
const inputText = ref('')
const listRef = ref(null)

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
  const res = await api.merchantGetSupportMessages(sessionId)
  if (res.code !== 200) {
    errorMsg.value = res.message || '消息加载失败'
    return
  }
  messages.value = Array.isArray(res.data) ? res.data : []
  await scrollBottom()
}

async function loadSessions() {
  loading.value = true
  errorMsg.value = ''
  const mid = merchantId()
  if (!mid) {
    loading.value = false
    errorMsg.value = '请先登录商家账号'
    return
  }
  const res = await api.merchantGetSupportSessions(mid)
  loading.value = false
  if (res.code !== 200) {
    errorMsg.value = res.message || '会话加载失败'
    return
  }
  sessions.value = Array.isArray(res.data) ? res.data : []
  if (sessions.value.length > 0) {
    activeSessionId.value = sessions.value[0].sessionId
    await loadMessages(activeSessionId.value)
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
  await loadSessions()
}

onMounted(loadSessions)
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
          :class="{ 'msw-session-item--active': activeSessionId === s.sessionId }"
          @click="selectSession(s.sessionId)"
        >
          <div class="msw-session-name">{{ s.userNickname }}</div>
          <div class="msw-session-meta">会话ID: {{ s.sessionId }}</div>
        </button>
      </aside>

      <div class="pw-chat-wrap">
        <div ref="listRef" class="pw-msg-list">
          <div v-if="messages.length === 0" class="msw-msg-placeholder">选择会话后开始回复用户。</div>
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
      </div>
    </div>
  </div>
</template>

<style scoped>
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

.msw-session-name {
  font-size: 14px;
  color: #131e30;
  font-weight: 700;
}

.msw-session-meta {
  font-size: 12px;
  color: #7b8798;
  margin-top: 3px;
}

.msw-msg-placeholder {
  text-align: center;
  color: #6b7788;
  font-size: 13px;
  padding: 32px 0;
}

@media (max-width: 900px) {
  .msw-layout {
    grid-template-columns: 1fr;
  }
}
</style>
