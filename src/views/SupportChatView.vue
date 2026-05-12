<script setup>
import { onMounted, ref, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../utils/request'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const sending = ref(false)
const errorMsg = ref('')
const inputText = ref('')
const sessionId = ref(null)
const messages = ref([])
const listRef = ref(null)
const merchantName = ref('商家')
const topicTabs = [
  { id: 'refund', title: '退换与售后', subtitle: '退款进度、售后处理' },
  { id: 'shipping', title: '物流与发货', subtitle: '发货时效、配送问题' },
  { id: 'product', title: '商品咨询', subtitle: '规格、库存、适配建议' },
]
const activeTopic = ref('shipping')

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
  const res = await api.userGetSupportMessages(sessionId.value, userId())
  if (res.code !== 200) {
    errorMsg.value = res.message || '消息加载失败'
    return
  }
  messages.value = Array.isArray(res.data) ? res.data : []
  await scrollBottom()
  window.dispatchEvent(new CustomEvent('petshop-chat-unread-updated'))
}

async function initSession() {
  loading.value = true
  errorMsg.value = ''
  const uid = userId()
  if (!uid) {
    loading.value = false
    errorMsg.value = '请先登录后再联系商家'
    return
  }
  const merchantId = Number(route.query.merchantId || 0)
  if (!merchantId) {
    loading.value = false
    router.replace('/merchant-contact')
    return
  }
  const orderId = Number(route.query.orderId || 0)
  const res = await api.userGetMerchantSession({
    userId: uid,
    merchantId,
    orderId: orderId || null,
  })
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
  <div class="pw-page chat-page chat-page--apex">
    <header class="chat-shell-head">
      <button type="button" class="chat-back-btn" @click="router.push('/merchant-contact')">
        ← 返回会话列表
      </button>
      <div class="chat-shell-head-main">
        <h1 class="chat-shell-title">{{ merchantName }}</h1>
        <p class="chat-shell-lead">
          {{
            route.query.orderId
              ? `关联订单 #${route.query.orderId} · 留言将同步商家`
              : '如有订单、商品或售后问题，请尽量说明订单号或商品信息。'
          }}
        </p>
      </div>
    </header>

    <div v-if="loading" class="chat-state chat-state--muted">加载中…</div>
    <div v-else-if="errorMsg" class="chat-state chat-state--error">{{ errorMsg }}</div>
    <div v-else class="chat-layout">
      <aside class="chat-left">
        <div class="chat-left-kicker">TOPICS</div>
        <div class="chat-left-title">咨询主题</div>
        <p class="chat-left-desc">选择场景便于组织话术（演示用途，不影响发送）。</p>
        <button
          v-for="topic in topicTabs"
          :key="topic.id"
          type="button"
          class="topic-item"
          :class="{ 'topic-item--on': activeTopic === topic.id }"
          @click="activeTopic = topic.id"
        >
          <div class="topic-item-title">{{ topic.title }}</div>
          <div class="topic-item-sub">{{ topic.subtitle }}</div>
        </button>
        <div class="chat-left-divider"></div>
        <div class="chat-left-kicker chat-left-kicker--small">SESSION</div>
        <div class="chat-left-title chat-left-title--small">当前会话</div>
        <div class="active-conversation">
          <div class="active-conversation-name">{{ merchantName }}</div>
          <div class="active-conversation-sub">进行中 · 消息送达商家工作台</div>
        </div>
      </aside>

      <div class="pw-chat-wrap">
        <div class="pw-chat-head">
          <h2>对话</h2>
          <p class="pw-chat-head-lead">请在下方输入内容，Enter 或点击发送。</p>
        </div>
        <div ref="listRef" class="pw-msg-list">
          <div v-if="messages.length === 0" class="pw-empty-inner">暂无消息，开始对话吧。</div>
          <div v-for="msg in messages" :key="msg.messageId" class="pw-msg-row" :class="{ mine: isMine(msg) }">
            <div class="pw-msg-sender">{{ isMine(msg) ? '我' : '商家' }}</div>
            <div class="pw-bubble">{{ msg.content }}</div>
            <div class="pw-msg-time">{{ fmtTime(msg.createdAt) }}</div>
          </div>
        </div>
        <div class="quick-actions">
          <button type="button" class="quick-btn" @click="inputText = '请问订单大概什么时候发货？'">催发货</button>
          <button type="button" class="quick-btn" @click="inputText = '想确认一下当前规格是否还有库存？'">询问库存</button>
          <button type="button" class="quick-btn" @click="inputText = '需要咨询售后/退换货处理进度。'">申请售后</button>
        </div>
        <div class="pw-input-bar">
          <input
            v-model="inputText"
            class="pw-input"
            type="text"
            maxlength="200"
            placeholder="输入咨询内容…"
            @keyup.enter="sendMessage"
          />
          <button type="button" class="pw-send" :disabled="sending || !inputText.trim()" @click="sendMessage">
            {{ sending ? '发送中…' : '发送' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.chat-page--apex {
  --cx-ink: #0a0a0a;
  --cx-muted: #737373;
  --cx-line: #e5e5e5;
  --cx-panel: #ffffff;
  --cx-soft: #fafafa;
  --cx-radius: 2px;
  font-family: 'Inter', 'Microsoft YaHei', 'PingFang SC', system-ui, sans-serif;
  padding-bottom: clamp(24px, 3vh, 36px);
}

.chat-shell-head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  gap: 16px 24px;
  margin-bottom: 18px;
  padding-bottom: 18px;
  border-bottom: 1px solid var(--cx-line);
}

.chat-back-btn {
  padding: 8px 14px;
  border: 1px solid var(--cx-line);
  border-radius: var(--cx-radius);
  background: var(--cx-panel);
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.04em;
  color: var(--cx-ink);
  cursor: pointer;
}

.chat-back-btn:hover {
  border-color: var(--cx-ink);
}

.chat-shell-head-main {
  flex: 1;
  min-width: 0;
}

.chat-shell-title {
  margin: 0 0 6px;
  font-size: clamp(20px, 2vw, 24px);
  font-weight: 800;
  letter-spacing: -0.02em;
  color: var(--cx-ink);
}

.chat-shell-lead {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--cx-muted);
  line-height: 1.5;
}

.chat-state {
  padding: 28px 20px;
  text-align: center;
  border-radius: var(--cx-radius);
  border: 1px solid var(--cx-line);
  background: var(--cx-panel);
  font-size: 14px;
  font-weight: 600;
}

.chat-state--muted {
  color: var(--cx-muted);
}

.chat-state--error {
  border-color: #fecaca;
  background: #fef2f2;
  color: #b91c1c;
}

.chat-layout {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  gap: 20px;
  align-items: stretch;
}

.chat-left {
  border: 1px solid var(--cx-line);
  border-radius: var(--cx-radius);
  background: var(--cx-panel);
  padding: 18px 16px;
  min-height: min(640px, 70vh);
  display: flex;
  flex-direction: column;
  box-shadow: 0 1px 0 rgba(0, 0, 0, 0.04);
}

.chat-left-kicker {
  margin: 0 0 6px;
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.14em;
  color: #a3a3a3;
}

.chat-left-kicker--small {
  margin-top: 8px;
}

.chat-left-title {
  font-size: 15px;
  font-weight: 800;
  color: var(--cx-ink);
  margin-bottom: 6px;
}

.chat-left-title--small {
  margin-top: 4px;
  margin-bottom: 8px;
}

.chat-left-desc {
  margin: 0 0 14px;
  font-size: 12px;
  line-height: 1.45;
  color: var(--cx-muted);
}

.topic-item {
  width: 100%;
  text-align: left;
  border: 1px solid var(--cx-line);
  border-radius: var(--cx-radius);
  background: var(--cx-soft);
  padding: 10px 12px;
  margin-bottom: 8px;
  cursor: pointer;
  transition:
    border-color 0.15s,
    background 0.15s;
}

.topic-item:hover {
  border-color: #bdbdbd;
  background: #fff;
}

.topic-item--on {
  background: var(--cx-ink);
  border-color: var(--cx-ink);
}

.topic-item--on:hover {
  background: var(--cx-ink);
  border-color: var(--cx-ink);
}

.topic-item-title {
  font-size: 13px;
  font-weight: 800;
  color: var(--cx-ink);
}

.topic-item--on .topic-item-title {
  color: #fff;
}

.topic-item-sub {
  margin-top: 4px;
  font-size: 12px;
  font-weight: 600;
  color: var(--cx-muted);
}

.topic-item--on .topic-item-sub {
  color: rgba(255, 255, 255, 0.82);
}

.chat-left-divider {
  border-top: 1px solid var(--cx-line);
  margin: 12px 0 10px;
}

.active-conversation {
  border: 1px solid var(--cx-line);
  border-radius: var(--cx-radius);
  background: var(--cx-soft);
  padding: 12px;
  margin-top: auto;
}

.active-conversation-name {
  font-size: 14px;
  font-weight: 800;
  color: var(--cx-ink);
}

.active-conversation-sub {
  margin-top: 6px;
  font-size: 12px;
  font-weight: 600;
  color: var(--cx-muted);
  line-height: 1.45;
}

:deep(.pw-chat-wrap) {
  border-radius: var(--cx-radius);
  border: 1px solid var(--cx-line);
  background: var(--cx-panel);
  box-shadow: 0 1px 0 rgba(0, 0, 0, 0.04);
  min-height: min(640px, 70vh);
  display: flex;
  flex-direction: column;
}

:deep(.pw-chat-head) {
  background: var(--cx-panel);
  border-bottom: 1px solid var(--cx-line);
  padding: 16px 18px;
}

.pw-chat-head-lead {
  margin: 6px 0 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--cx-muted);
}

:deep(.pw-chat-head h2) {
  margin: 0;
  font-size: 13px;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: #a3a3a3;
}

.pw-empty-inner {
  text-align: center;
  color: var(--cx-muted);
  font-size: 14px;
  font-weight: 600;
  padding: 40px 16px;
}

:deep(.pw-msg-list) {
  flex: 1;
  min-height: 0;
  height: clamp(320px, 48vh, 560px);
  background: var(--cx-soft);
  border: none;
}

:deep(.pw-msg-row) {
  margin-bottom: 12px;
}

:deep(.pw-msg-row:last-child) {
  margin-bottom: 4px;
}

:deep(.pw-msg-sender) {
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #a3a3a3;
}

:deep(.pw-bubble) {
  font-size: 14px;
  line-height: 1.6;
  padding: 11px 14px;
  border-radius: var(--cx-radius);
  border: 1px solid var(--cx-line);
  background: var(--cx-panel);
  color: #262626;
}

:deep(.pw-msg-row.mine .pw-bubble) {
  background: var(--cx-ink);
  border-color: var(--cx-ink);
  color: #fff;
}

:deep(.pw-msg-time) {
  font-size: 12px;
  font-weight: 600;
  color: #a3a3a3;
}

.quick-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 10px 14px;
  border-top: 1px solid var(--cx-line);
  background: var(--cx-panel);
}

.quick-btn {
  height: 36px;
  padding: 0 12px;
  border: 1px solid var(--cx-line);
  border-radius: var(--cx-radius);
  background: var(--cx-soft);
  color: var(--cx-ink);
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
}

.quick-btn:hover {
  border-color: #bdbdbd;
  background: #fff;
}

:deep(.pw-input-bar) {
  padding: 12px 14px 14px;
  border-top: 1px solid var(--cx-line);
  gap: 10px;
}

:deep(.pw-input) {
  font-size: 14px;
  height: 46px;
  border-radius: var(--cx-radius);
  border-color: var(--cx-line);
}

:deep(.pw-send) {
  font-size: 13px;
  font-weight: 800;
  height: 46px;
  min-width: 100px;
  border-radius: var(--cx-radius);
  background: var(--cx-ink);
  border-color: var(--cx-ink);
  color: #fff;
}

@media (max-width: 1100px) {
  .chat-layout {
    grid-template-columns: 1fr;
  }

  .chat-left {
    min-height: unset;
  }
}

</style>
