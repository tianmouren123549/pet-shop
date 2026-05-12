<script setup>
import { onMounted, ref, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../utils/request'

const router = useRouter()
const loading = ref(false)
const sending = ref(false)
const errorMsg = ref('')
const inputText = ref('')
const sessionId = ref(null)
const messages = ref([])
const listRef = ref(null)
const peerLabel = ref('平台客服')

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

function senderLabel(msg) {
  if (isMine(msg)) return '我'
  if (msg?.senderType === 'ADMIN') return '平台'
  return '对方'
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
    errorMsg.value = '请先登录后再联系平台'
    return
  }
  const res = await api.userGetAdminSession({ userId: uid })
  loading.value = false
  if (res.code !== 200) {
    errorMsg.value = res.message || '会话创建失败'
    return
  }
  if (res.data?.merchantName) peerLabel.value = String(res.data.merchantName)
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

async function refreshChat() {
  if (!sessionId.value) {
    await initSession()
    return
  }
  await loadMessages()
}

onMounted(initSession)
</script>

<template>
  <div class="pw-page ca-page ca-page--apex">
    <section class="layout-shell">
      <aside class="layout-sidebar">
        <div class="sidebar-head">
          <h2 class="sidebar-title">联系平台</h2>
          <p class="sidebar-lead">账号、订单纠纷、投诉建议等可在此留言，平台管理员会尽快回复。</p>
        </div>

        <div class="facet-block">
          <div class="facet-title">快捷入口</div>
          <button type="button" class="facet-chip" @click="router.push('/merchant-contact')">联系商家</button>
          <button type="button" class="facet-chip" @click="router.push('/orders')">我的订单</button>
          <button type="button" class="facet-chip" @click="router.push('/')">返回首页</button>
        </div>

        <div class="facet-block">
          <div class="facet-title">咨询范围</div>
          <div class="ca-scope-card">
            <p class="ca-scope-title">可说明的问题类型</p>
            <p class="ca-scope-text">订单与支付异常、商家违规行为、账号与隐私、平台功能与体验反馈等。</p>
          </div>
        </div>

        <div class="sidebar-tip">
          <p class="sidebar-tip-label">提示</p>
          <p class="sidebar-tip-text">
            留言时尽量带订单号、店铺名或发生时间，便于平台核查；平台回复后您会在通知中心收到提醒（若已开启）。
          </p>
        </div>

        <div v-if="sessionId" class="ca-side-session">
          <div class="facet-title">会话对象</div>
          <div class="ca-side-session-card">
            <div class="ca-side-session-name">{{ peerLabel }}</div>
            <div class="ca-side-session-sub">与平台一对一沟通</div>
          </div>
        </div>
      </aside>

      <div class="layout-main">
        <header class="ca-toolbar">
          <div class="ca-toolbar__left">
            <h1 class="ca-toolbar-title">平台会话</h1>
            <p class="ca-toolbar-meta">
              <span v-if="sessionId">对话已就绪 · {{ peerLabel }}</span>
              <span v-else>登录并建立会话后即可发送消息</span>
            </p>
          </div>
          <div class="ca-toolbar__actions">
            <button type="button" class="ca-btn-ghost" :disabled="loading" @click="refreshChat">
              {{ loading ? '加载中…' : '刷新' }}
            </button>
          </div>
        </header>

        <div v-if="loading" class="ca-state ca-state--muted">加载中…</div>
        <div v-else-if="errorMsg" class="ca-state ca-state--error">
          <p class="ca-state-text">{{ errorMsg }}</p>
          <button type="button" class="ca-btn-primary ca-btn-primary--sm" @click="initSession">重试</button>
        </div>

        <div v-else class="ca-chat-wrap">
          <div class="pw-chat-wrap">
            <div class="pw-chat-head">
              <h2>{{ peerLabel }}</h2>
              <p class="pw-chat-head-lead">请尽量说明订单号、店铺名或发生时间，便于快速处理。</p>
            </div>
            <div ref="listRef" class="pw-msg-list">
              <div v-if="messages.length === 0" class="pw-empty-inner">暂无消息，开始对话吧。</div>
              <div v-for="msg in messages" :key="msg.messageId" class="pw-msg-row" :class="{ mine: isMine(msg) }">
                <div class="pw-msg-sender">{{ senderLabel(msg) }}</div>
                <div class="pw-bubble">{{ msg.content }}</div>
                <div class="pw-msg-time">{{ fmtTime(msg.createdAt) }}</div>
              </div>
            </div>
            <div class="ca-quick-actions">
              <button
                type="button"
                class="ca-quick-btn"
                @click="inputText = '订单号：请帮我核实支付状态与发货进度。'"
              >
                订单核实
              </button>
              <button
                type="button"
                class="ca-quick-btn"
                @click="inputText = '需要反馈商家疑似违规行为，请平台协助处理。'"
              >
                投诉商家
              </button>
              <button
                type="button"
                class="ca-quick-btn"
                @click="inputText = '账号登录异常 / 绑定手机变更，请求协助。'"
              >
                账号问题
              </button>
            </div>
            <div class="pw-input-bar">
              <input
                v-model="inputText"
                class="pw-input"
                type="text"
                maxlength="200"
                placeholder="输入你要反馈的内容…"
                @keyup.enter="sendMessage"
              />
              <button type="button" class="pw-send" :disabled="sending || !inputText.trim()" @click="sendMessage">
                {{ sending ? '发送中…' : '发送消息' }}
              </button>
            </div>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.ca-page--apex {
  --ca-ink: #0a0a0a;
  --ca-muted: #737373;
  --ca-line: #e5e5e5;
  --ca-panel: #ffffff;
  --ca-soft: #fafafa;
  --ca-radius: 2px;
  font-family: 'Inter', 'Microsoft YaHei', 'PingFang SC', system-ui, sans-serif;
}

.ca-page--apex.pw-page {
  padding-bottom: clamp(24px, 3vh, 36px);
}

.layout-shell {
  display: grid;
  grid-template-columns: minmax(200px, 248px) minmax(0, 1fr);
  gap: clamp(16px, 2.5vw, 24px);
  align-items: start;
}

.layout-sidebar {
  position: sticky;
  top: 72px;
  background: var(--ca-panel);
  border: 1px solid var(--ca-line);
  border-radius: var(--ca-radius);
  padding: 16px 14px;
  display: flex;
  flex-direction: column;
  gap: 2px;
  box-shadow: 0 1px 0 rgba(0, 0, 0, 0.04);
}

.sidebar-head {
  margin-bottom: 8px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--ca-line);
}

.sidebar-title {
  margin: 0 0 6px;
  font-size: clamp(17px, 1.4vw, 20px);
  font-weight: 800;
  letter-spacing: -0.02em;
  color: var(--ca-ink);
}

.sidebar-lead {
  margin: 0;
  font-size: 12px;
  color: var(--ca-muted);
  line-height: 1.55;
}

.facet-block {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px 0 10px;
  border-top: 1px solid var(--ca-line);
}

.facet-block:first-of-type {
  border-top: none;
  padding-top: 4px;
}

.facet-title {
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--ca-muted);
}

.facet-chip {
  width: 100%;
  text-align: left;
  padding: 10px 12px;
  border: 1px solid var(--ca-line);
  border-radius: var(--ca-radius);
  background: var(--ca-soft);
  font-size: 13px;
  font-weight: 600;
  color: var(--ca-ink);
  cursor: pointer;
  transition:
    border-color 0.15s,
    background 0.15s;
}

.facet-chip:hover {
  border-color: #bdbdbd;
  background: #fff;
}

.ca-scope-card {
  padding: 10px 12px;
  border: 1px solid var(--ca-line);
  border-radius: var(--ca-radius);
  background: var(--ca-soft);
}

.ca-scope-title {
  margin: 0 0 6px;
  font-size: 13px;
  font-weight: 800;
  color: var(--ca-ink);
}

.ca-scope-text {
  margin: 0;
  font-size: 12px;
  line-height: 1.55;
  font-weight: 600;
  color: var(--ca-muted);
}

.sidebar-tip {
  margin-top: 4px;
  padding: 10px 12px;
  border: 1px solid var(--ca-line);
  border-radius: var(--ca-radius);
  background: var(--ca-soft);
}

.sidebar-tip-label {
  margin: 0 0 6px;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: #a3a3a3;
}

.sidebar-tip-text {
  margin: 0;
  font-size: 12px;
  line-height: 1.55;
  color: var(--ca-muted);
}

.ca-side-session {
  margin-top: 8px;
  padding-top: 12px;
  border-top: 1px solid var(--ca-line);
}

.ca-side-session-card {
  margin-top: 8px;
  padding: 12px;
  border: 1px dashed var(--ca-line);
  border-radius: var(--ca-radius);
  background: var(--ca-panel);
}

.ca-side-session-name {
  font-size: 14px;
  font-weight: 800;
  color: var(--ca-ink);
}

.ca-side-session-sub {
  margin-top: 6px;
  font-size: 12px;
  font-weight: 600;
  color: var(--ca-muted);
  line-height: 1.45;
}

.layout-main {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.ca-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 14px;
  padding: clamp(18px, 2vw, 22px);
  background: var(--ca-panel);
  border: 1px solid var(--ca-line);
  border-radius: var(--ca-radius);
  box-shadow: 0 1px 0 rgba(0, 0, 0, 0.04);
}

.ca-toolbar-title {
  margin: 0 0 6px;
  font-size: clamp(17px, 1.45vw, 20px);
  font-weight: 800;
  color: var(--ca-ink);
  letter-spacing: -0.02em;
}

.ca-toolbar-meta {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--ca-muted);
}

.ca-toolbar__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.ca-btn-ghost {
  padding: 10px 18px;
  border: 1px solid var(--ca-line);
  border-radius: var(--ca-radius);
  background: var(--ca-panel);
  color: var(--ca-ink);
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.05em;
  cursor: pointer;
}

.ca-btn-ghost:hover:not(:disabled) {
  border-color: var(--ca-ink);
}

.ca-btn-ghost:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.ca-state {
  padding: 28px 20px;
  text-align: center;
  border-radius: var(--ca-radius);
  border: 1px solid var(--ca-line);
  background: var(--ca-panel);
  font-size: 14px;
  font-weight: 600;
}

.ca-state--muted {
  color: var(--ca-muted);
}

.ca-state--error {
  border-color: #fecaca;
  background: #fef2f2;
}

.ca-state-text {
  margin: 0 0 14px;
  color: #b91c1c;
  font-weight: 700;
}

.ca-btn-primary {
  padding: 10px 20px;
  border: 1px solid var(--ca-ink);
  border-radius: var(--ca-radius);
  background: var(--ca-ink);
  color: #fff;
  font-size: 13px;
  font-weight: 800;
  cursor: pointer;
}

.ca-btn-primary:hover {
  background: #262626;
  border-color: #262626;
}

.ca-btn-primary--sm {
  padding: 8px 16px;
  font-size: 12px;
}

.ca-chat-wrap {
  min-width: 0;
}

.ca-quick-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 10px 14px;
  border-top: 1px solid var(--ca-line);
  background: var(--ca-panel);
}

.ca-quick-btn {
  height: 36px;
  padding: 0 12px;
  border: 1px solid var(--ca-line);
  border-radius: var(--ca-radius);
  background: var(--ca-soft);
  color: var(--ca-ink);
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
}

.ca-quick-btn:hover {
  border-color: #bdbdbd;
  background: #fff;
}

:deep(.pw-chat-wrap) {
  border-radius: var(--ca-radius);
  border: 1px solid var(--ca-line);
  background: var(--ca-panel);
  box-shadow: 0 1px 0 rgba(0, 0, 0, 0.04);
  min-height: min(640px, 70vh);
  display: flex;
  flex-direction: column;
}

:deep(.pw-chat-head) {
  background: var(--ca-panel);
  border-bottom: 1px solid var(--ca-line);
  padding: 16px 18px;
}

.pw-chat-head-lead {
  margin: 6px 0 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--ca-muted);
}

:deep(.pw-chat-head h2) {
  margin: 0;
  font-size: clamp(16px, 1.2vw, 18px);
  font-weight: 800;
  color: var(--ca-ink);
}

.pw-empty-inner {
  text-align: center;
  color: var(--ca-muted);
  font-size: 14px;
  font-weight: 600;
  padding: 40px 16px;
}

:deep(.pw-msg-list) {
  flex: 1;
  min-height: 0;
  height: clamp(320px, 48vh, 560px);
  background: var(--ca-soft);
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
  border-radius: var(--ca-radius);
  border: 1px solid var(--ca-line);
  background: var(--ca-panel);
  color: #262626;
}

:deep(.pw-msg-row.mine .pw-bubble) {
  background: var(--ca-ink);
  border-color: var(--ca-ink);
  color: #fff;
}

:deep(.pw-msg-time) {
  font-size: 12px;
  font-weight: 600;
  color: #a3a3a3;
}

:deep(.pw-input-bar) {
  padding: 12px 14px 14px;
  border-top: 1px solid var(--ca-line);
  gap: 10px;
}

:deep(.pw-input) {
  font-size: 14px;
  height: 46px;
  border-radius: var(--ca-radius);
  border-color: var(--ca-line);
}

:deep(.pw-send) {
  font-size: 13px;
  font-weight: 800;
  height: 46px;
  min-width: 100px;
  border-radius: var(--ca-radius);
  background: var(--ca-ink);
  border-color: var(--ca-ink);
  color: #fff;
}

@media (max-width: 980px) {
  .layout-shell {
    grid-template-columns: 1fr;
  }

  .layout-sidebar {
    position: static;
    order: 2;
  }

  .layout-main {
    order: 1;
  }

  .ca-toolbar__actions {
    width: 100%;
  }

  .ca-btn-ghost {
    flex: 1;
  }
}
</style>
