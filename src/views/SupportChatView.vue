<script setup>
import { onMounted, ref, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { api } from '../utils/request'

const route = useRoute()
const loading = ref(false)
const sending = ref(false)
const errorMsg = ref('')
const inputText = ref('')
const sessionId = ref(null)
const messages = ref([])
const listRef = ref(null)
const merchantName = ref('商家')

function userId() {
  return Number(localStorage.getItem('userId') || 0)
}

function fmtTime(iso) {
  if (!iso) return '--'
  const d = new Date(iso)
  const hh = String(d.getHours()).padStart(2, '0')
  const mm = String(d.getMinutes()).padStart(2, '0')
  return `${hh}:${mm}`
}

function isMine(msg) {
  return msg?.senderType === 'USER'
}

async function scrollBottom() {
  await nextTick()
  if (listRef.value) listRef.value.scrollTop = listRef.value.scrollHeight
}

async function loadMessages() {
  if (!sessionId.value) return
  const res = await api.userGetSupportMessages(sessionId.value)
  if (res.code !== 200) {
    errorMsg.value = res.message || '消息加载失败'
    return
  }
  messages.value = Array.isArray(res.data) ? res.data : []
  await scrollBottom()
}

async function initSession() {
  loading.value = true
  errorMsg.value = ''
  const uid = userId()
  if (!uid) {
    loading.value = false
    errorMsg.value = '请先登录后再联系客服'
    return
  }
  const merchantId = Number(route.query.merchantId || 0)
  const orderId = Number(route.query.orderId || 0)
  const res = merchantId
    ? await api.userGetMerchantSession({
        userId: uid,
        merchantId,
        orderId: orderId || null,
      })
    : await api.userGetSupportSession(uid)
  loading.value = false
  if (res.code !== 200) {
    errorMsg.value = res.message || '会话创建失败'
    return
  }
  if (res.data?.merchantName) merchantName.value = String(res.data.merchantName)
  sessionId.value = res.data?.sessionId || null
  await loadMessages()
}

async function sendMessage() {
  if (sending.value) return
  const content = inputText.value.trim()
  if (!content) return
  const uid = userId()
  if (!uid || !sessionId.value) return
  sending.value = true
  const res = await api.userSendSupportMessage({
    sessionId: sessionId.value,
    userId: uid,
    content,
  })
  sending.value = false
  if (res.code !== 200) {
    errorMsg.value = res.message || '发送失败'
    return
  }
  inputText.value = ''
  await loadMessages()
}

onMounted(initSession)
</script>

<template>
  <div class="pw-page">
    <section class="pw-hero">
      <h1 class="pw-title">{{ route.query.merchantId ? `联系商家：${merchantName}` : '联系客服' }}</h1>
      <p class="pw-lead">
        {{
          route.query.orderId
            ? `订单 #${route.query.orderId} 相关咨询`
            : '如有订单、商品或售后问题，可在此留言。'
        }}
      </p>
    </section>

    <div v-if="loading" class="pw-state">加载中...</div>
    <div v-else-if="errorMsg" class="pw-state pw-state--error">{{ errorMsg }}</div>
    <div v-else class="pw-chat-wrap">
      <div class="pw-chat-head">
        <h2>会话</h2>
        <p class="pw-lead">消息将同步给对方，请尽量说明订单号或商品信息。</p>
      </div>
      <div ref="listRef" class="pw-msg-list">
        <div v-if="messages.length === 0" class="pw-empty-inner">暂无消息，开始对话吧。</div>
        <div v-for="msg in messages" :key="msg.messageId" class="pw-msg-row" :class="{ mine: isMine(msg) }">
          <div class="pw-msg-sender">{{ isMine(msg) ? '我' : route.query.merchantId ? '商家' : '客服' }}</div>
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
          placeholder="输入你要咨询的问题..."
          @keyup.enter="sendMessage"
        />
        <button type="button" class="pw-send" :disabled="sending || !inputText.trim()" @click="sendMessage">
          {{ sending ? '发送中...' : '发送' }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.pw-chat-head h2 {
  margin: 0 0 6px;
  font-size: 14px;
  font-weight: 800;
  color: #506078;
  letter-spacing: 0.3px;
}

.pw-empty-inner {
  text-align: center;
  color: #6b7788;
  font-size: 13px;
  padding: 32px 0;
}
</style>
