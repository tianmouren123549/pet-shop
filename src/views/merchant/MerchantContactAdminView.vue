<script setup>
import { onMounted, ref, nextTick } from 'vue'
import { RouterLink } from 'vue-router'
import { api } from '../../utils/request'

const loading = ref(false)
const sending = ref(false)
const errorMsg = ref('')
const inputText = ref('')
const sessionId = ref(null)
const messages = ref([])
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

function senderLabel(msg) {
  if (isMine(msg)) return '我(商家)'
  if (msg?.senderType === 'ADMIN') return '平台'
  return '对方'
}

async function scrollBottom() {
  await nextTick()
  if (listRef.value) listRef.value.scrollTop = listRef.value.scrollHeight
}

async function loadMessages() {
  if (!sessionId.value) return
  const res = await api.merchantGetSupportMessages(sessionId.value, merchantId())
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
  const mid = merchantId()
  if (!mid) {
    loading.value = false
    errorMsg.value = '请先登录商家账号'
    return
  }
  const res = await api.merchantGetAdminSession({ merchantId: mid })
  loading.value = false
  if (res.code !== 200) {
    errorMsg.value = res.message || '会话创建失败'
    return
  }
  sessionId.value = res.data?.sessionId || null
  await loadMessages()
}

async function sendMessage() {
  if (sending.value) return
  const content = inputText.value.trim()
  if (!content || !sessionId.value) return
  const mid = merchantId()
  if (!mid) return
  sending.value = true
  const res = await api.merchantSendSupportMessage({
    sessionId: sessionId.value,
    merchantId: mid,
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
  <div class="mca-page">
    <div v-if="loading" class="mca-state">加载中…</div>
    <div v-else-if="errorMsg" class="mca-state mca-state--err">{{ errorMsg }}</div>

    <div v-else class="mca-grid">
      <section class="mca-chat-shell" aria-label="平台客服会话">
        <div ref="listRef" class="mca-msg-list">
          <div v-if="messages.length === 0" class="mca-msg-empty">
            <div class="mca-msg-empty-icon" aria-hidden="true">
              <svg viewBox="0 0 72 72" width="48" height="48" fill="none">
                <circle cx="36" cy="36" r="34" stroke="currentColor" stroke-width="2" opacity="0.15" />
                <path
                  d="M24 38c4-8 10-12 18-12s14 4 18 12"
                  stroke="currentColor"
                  stroke-width="2.5"
                  stroke-linecap="round"
                  opacity="0.4"
                />
                <circle cx="28" cy="30" r="3" fill="currentColor" opacity="0.35" />
                <circle cx="44" cy="30" r="3" fill="currentColor" opacity="0.35" />
              </svg>
            </div>
            <p class="mca-msg-empty-title">向平台发起第一条咨询</p>
          </div>
          <div
            v-for="msg in messages"
            :key="msg.messageId"
            class="mca-msg-row"
            :class="{ 'mca-msg-row--mine': isMine(msg) }"
          >
            <div class="mca-msg-meta">
              <span class="mca-msg-sender">{{ senderLabel(msg) }}</span>
              <span class="mca-msg-time">{{ fmtTime(msg.createdAt) }}</span>
            </div>
            <div class="mca-bubble">{{ msg.content }}</div>
          </div>
        </div>

        <div class="mca-input-row">
          <input
            v-model="inputText"
            class="mca-input"
            type="text"
            maxlength="200"
            placeholder="输入咨询内容，回车发送"
            @keyup.enter="sendMessage"
          />
          <button type="button" class="mca-send" :disabled="sending || !inputText.trim()" @click="sendMessage">
            {{ sending ? '发送中…' : '发送' }}
          </button>
        </div>
      </section>

      <aside class="mca-aside" aria-label="支持与快捷入口">
        <div class="mca-emergency">
          <h3 class="mca-emergency-title">紧急与风控</h3>
          <p class="mca-emergency-text">
            账户异常、盗刷风险或大额结算争议：请先在本页留言说明订单号与商家 ID；工作日优先通过文字会话处理，便于留存凭证。
          </p>
          <p class="mca-emergency-tip">电话等其他通道请以平台公示或入驻协议为准。</p>
        </div>

        <div class="mca-quick">
          <p class="mca-quick-label">快速入口</p>
          <RouterLink class="mca-quick-card" to="/merchant/profile">
            <span class="mca-quick-card-title">店铺资料</span>
            <span class="mca-quick-card-sub">核对联系人、资质信息</span>
          </RouterLink>
          <RouterLink class="mca-quick-card" to="/merchant/orders">
            <span class="mca-quick-card-title">订单管理</span>
            <span class="mca-quick-card-sub">查看订单号与履约进度</span>
          </RouterLink>
        </div>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.mca-page {
  min-height: 100%;
  padding: 8px 4px 36px;
  max-width: 1180px;
  margin: 0 auto;
  box-sizing: border-box;
}

.mca-state {
  padding: 40px 20px;
  text-align: center;
  font-size: 14px;
  font-weight: 600;
  color: #64748b;
  border-radius: 14px;
  border: 1px solid #e2e8f0;
  background: #fff;
}

.mca-state--err {
  color: #b91c1c;
  background: #fef2f2;
  border-color: #fecaca;
}

.mca-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: 20px;
  align-items: stretch;
  /* 壳层顶栏 + 页头后，尽量占满视口高度，避免消息区下方大块空灰 */
  min-height: clamp(520px, calc(100dvh - 120px), 900px);
}

.mca-chat-shell {
  border-radius: 16px;
  border: 1px solid #e2e8f0;
  background: #fff;
  box-shadow: 0 8px 28px rgba(15, 23, 42, 0.07);
  display: flex;
  flex-direction: column;
  min-height: 0;
  height: 100%;
  overflow: hidden;
}

.mca-msg-list {
  flex: 1 1 auto;
  min-height: 200px;
  overflow-y: auto;
  padding: 16px 18px;
  background: #f8fafc;
}

.mca-msg-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 48px 20px 56px;
  min-height: 280px;
}

.mca-msg-empty-icon {
  color: #94a3b8;
  margin-bottom: 14px;
}

.mca-msg-empty-title {
  margin: 0;
  font-size: 17px;
  font-weight: 800;
  color: #334155;
}

.mca-msg-row {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  margin-bottom: 14px;
  /* 短消息随内容收窄；长消息最多占容器约 85% 并换行 */
  width: fit-content;
  max-width: min(85%, 36rem);
  box-sizing: border-box;
}

.mca-msg-row--mine {
  margin-left: auto;
  align-items: flex-end;
}

.mca-msg-row--mine .mca-bubble {
  background: #0f172a;
  color: #f8fafc;
  border-color: #0f172a;
}

.mca-msg-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
  font-size: 11px;
  font-weight: 700;
  color: #64748b;
}

.mca-msg-row--mine .mca-msg-meta {
  justify-content: flex-end;
}

.mca-msg-time {
  font-weight: 600;
  color: #94a3b8;
}

.mca-bubble {
  display: inline-block;
  width: fit-content;
  max-width: 100%;
  padding: 12px 14px;
  border-radius: 14px;
  border: 1px solid #e2e8f0;
  background: #fff;
  font-size: 14px;
  font-weight: 500;
  color: #334155;
  line-height: 1.5;
  word-break: break-word;
  box-sizing: border-box;
  vertical-align: top;
}

.mca-input-row {
  display: flex;
  gap: 10px;
  padding: 14px 16px;
  border-top: 1px solid #f1f5f9;
  background: #fff;
}

.mca-input {
  flex: 1;
  min-width: 0;
  height: 44px;
  padding: 0 14px;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
  outline: none;
}

.mca-input:focus {
  border-color: #93c5fd;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.2);
}

.mca-send {
  flex-shrink: 0;
  height: 44px;
  padding: 0 22px;
  border-radius: 12px;
  border: none;
  background: #0f172a;
  color: #f8fafc;
  font-size: 13px;
  font-weight: 800;
  cursor: pointer;
}

.mca-send:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.mca-aside {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 100%;
}

.mca-emergency {
  padding: 18px 18px 20px;
  border-radius: 14px;
  background: linear-gradient(145deg, #1e293b 0%, #0f172a 100%);
  color: #e2e8f0;
  box-shadow: 0 10px 28px rgba(15, 23, 42, 0.22);
}

.mca-emergency-title {
  margin: 0 0 10px;
  font-size: 14px;
  font-weight: 800;
  color: #fff;
}

.mca-emergency-text {
  margin: 0 0 10px;
  font-size: 13px;
  font-weight: 600;
  line-height: 1.55;
  opacity: 0.92;
}

.mca-emergency-tip {
  margin: 0;
  font-size: 11px;
  font-weight: 600;
  color: #94a3b8;
  line-height: 1.45;
}

.mca-quick-label {
  margin: 0 0 8px 10px;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.06em;
  color: #94a3b8;
}

.mca-quick {
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: 1 1 auto;
  min-height: 0;
}

.mca-quick-card {
  display: block;
  padding: 14px 16px;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  background: #fff;
  text-decoration: none;
  transition:
    border-color 0.15s,
    box-shadow 0.15s;
}

.mca-quick-card:hover {
  border-color: #cbd5e1;
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.06);
}

.mca-quick-card-title {
  display: block;
  font-size: 14px;
  font-weight: 800;
  color: #0f172a;
}

.mca-quick-card-sub {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  font-weight: 600;
  color: #64748b;
}

@media (max-width: 960px) {
  .mca-grid {
    grid-template-columns: 1fr;
  }

  .mca-aside {
    order: -1;
  }
}
</style>
