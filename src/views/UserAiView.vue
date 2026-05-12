<script setup>
import { nextTick, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../utils/request'
import { showAppMessage } from '../utils/appMessage'
import AppImage from '../components/AppImage.vue'

const router = useRouter()

const pageLoading = ref(true)
const needLogin = ref(false)

/** doubao | model — 默认展开 AI 多轮推荐，避免每次手动点选 */
const activeTool = ref('doubao')

const proactiveSessionId = ref(null)
const proactiveMessages = ref([])
const proactiveInput = ref('')
const proactiveLoading = ref(false)
const proactiveCandidates = ref([])

const proactiveSessions = ref([])
const proactiveHistoryPick = ref('')

const nlQuestion = ref('')
const nlLoading = ref(false)
const nlCandidates = ref([])
const nlAssistantMsg = ref('')

/** 评论强推：按买家评价等对当前页候选商品重新排序 */
const commentRerankLoading = ref(false)

function userId() {
  return Number(localStorage.getItem('userId') || 0)
}

function candidateTitle(it) {
  return String(it?.product?.title || '').trim() || `商品 #${it?.productId || ''}`
}

function candidateCover(it) {
  const u =
    it?.product?.detail?.imageUrl ||
    it?.product?.detail?.mainImageUrl ||
    it?.product?.imageUrl ||
    ''
  return String(u || '').trim()
}

function formatSessionTime(dt) {
  if (!dt) return ''
  const d = typeof dt === 'string' ? new Date(dt.replace(' ', 'T')) : new Date(dt)
  if (Number.isNaN(d.getTime())) return String(dt).slice(0, 16)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

const PROACTIVE_SESSION_LABEL_MAX = 40

function proactiveSessionDisplayName(s) {
  const t = String(s?.title ?? '').trim()
  if (t) return t
  return `会话 #${s?.sessionId ?? ''}`
}

/** 下拉项展示：名称过长加省略号；完整文案用 title 属性 */
function proactiveSessionOptionLabel(s) {
  const name = proactiveSessionDisplayName(s)
  const suf = ` · ${formatSessionTime(s?.updatedAt)}`
  const line = name + suf
  if (line.length <= PROACTIVE_SESSION_LABEL_MAX) return line
  const budget = Math.max(8, PROACTIVE_SESSION_LABEL_MAX - suf.length - 1)
  const head = name.slice(0, budget)
  return `${head}…${suf}`
}

function proactiveSessionOptionTitle(s) {
  return `${proactiveSessionDisplayName(s)}\n${formatSessionTime(s?.updatedAt)}`
}

async function loadProactiveSessionSummaries() {
  const uid = userId()
  if (!uid) return
  const sid = proactiveSessionId.value
  const includeSid = sid != null && Number(sid) > 0 ? Number(sid) : null
  const res = await api.proactiveSessionSummaries(uid, 5, includeSid)
  if (res.code === 200 && Array.isArray(res.data)) {
    proactiveSessions.value = res.data
  }
}

/** 历史会话列表非关键路径：空闲时再拉，避免与 latest-session 抢首包 */
function scheduleLazyLoadSummaries() {
  const run = () => {
    void loadProactiveSessionSummaries()
  }
  if (typeof requestIdleCallback === 'function') {
    requestIdleCallback(run, { timeout: 2500 })
  } else {
    setTimeout(run, 300)
  }
}

/** 恢复「最近一条含用户发言的会话」或清空草稿；不拉历史列表（由懒加载负责） */
async function restoreOrClearProactiveSession() {
  if (proactiveLoading.value) return
  if (proactiveSessionId.value) return
  const uid = userId()
  if (!uid) return
  proactiveLoading.value = true
  try {
    const res = await api.proactiveLatestSession(uid)
    if (res.code === 200 && res.data?.sessionId && Array.isArray(res.data.messages) && res.data.messages.length > 0) {
      applyProactiveRestore(res.data)
    } else {
      clearProactiveDraftState()
    }
  } catch {
    clearProactiveDraftState()
  } finally {
    proactiveLoading.value = false
  }
}

async function onProactiveHistoryFocus() {
  await loadProactiveSessionSummaries()
}

function clearProactiveDraftState() {
  proactiveMessages.value = []
  proactiveCandidates.value = []
  proactiveSessionId.value = null
  proactiveHistoryPick.value = ''
  proactiveInput.value = ''
}

async function onProactiveHistoryChange() {
  const uid = userId()
  if (!uid) return
  const pick = String(proactiveHistoryPick.value || '')
  proactiveLoading.value = true
  try {
    if (pick === '') {
      const res = await api.proactiveLatestSession(uid)
      if (res.code === 200 && res.data?.sessionId && Array.isArray(res.data.messages) && res.data.messages.length > 0) {
        applyProactiveRestore(res.data)
      } else {
        clearProactiveDraftState()
      }
      await loadProactiveSessionSummaries()
      return
    }
    const sid = Number(pick)
    if (!Number.isFinite(sid) || sid <= 0) return
    const res = await api.proactiveSessionRestoreById(uid, sid)
    if (res.code === 200 && res.data?.sessionId) {
      applyProactiveRestore(res.data)
    } else {
      showAppMessage(res.message || '加载会话失败', '提示')
    }
    await loadProactiveSessionSummaries()
  } catch {
    showAppMessage('网络异常，请稍后重试', '提示')
  } finally {
    proactiveLoading.value = false
  }
}

function applyProactiveResponse(data) {
  if (!data) return
  proactiveSessionId.value = data.sessionId ?? null
  if (data.sessionId != null) {
    proactiveHistoryPick.value = String(data.sessionId)
  }
  if (data.assistantMessage) {
    proactiveMessages.value.push({ role: 'assistant', content: String(data.assistantMessage) })
  }
  proactiveCandidates.value = Array.isArray(data.candidateProducts) ? data.candidateProducts : []
}

async function startProactiveChat() {
  const uid = userId()
  if (!uid) {
    needLogin.value = true
    return
  }
  clearProactiveDraftState()
  scheduleLazyLoadSummaries()
}

function applyProactiveRestore(data) {
  if (!data) return
  proactiveSessionId.value = data.sessionId ?? null
  if (data.sessionId != null) {
    proactiveHistoryPick.value = String(data.sessionId)
  }
  proactiveMessages.value = (data.messages || []).map((m) => ({
    role: m.role === 'assistant' ? 'assistant' : 'user',
    content: String(m.content || ''),
  }))
  proactiveCandidates.value = Array.isArray(data.candidateProducts) ? data.candidateProducts : []
}

async function selectDoubaoMode() {
  activeTool.value = 'doubao'
  if (proactiveSessionId.value) {
    scheduleLazyLoadSummaries()
    return
  }
  await restoreOrClearProactiveSession()
  scheduleLazyLoadSummaries()
}

function selectModelMode() {
  activeTool.value = 'model'
}

/** 底部快捷短语（贴合养宠场景） */
const proactiveQuickSnippets = [
  { label: '主粮', text: '我想给宠物买主食粮' },
  { label: '幼宠', text: '家里是幼猫/幼犬' },
  { label: '零食', text: '更想买点零食或罐头' },
  { label: '预算', text: '预算大概两百以内' },
  { label: '进口', text: '偏好进口粮' },
  { label: '国产', text: '偏好国产粮' },
]

function insertQuickSnippet(text) {
  const cur = String(proactiveInput.value || '').trim()
  proactiveInput.value = cur ? `${cur}，${text}` : text
}

async function sendProactive() {
  const uid = userId()
  const text = String(proactiveInput.value || '').trim()
  if (!uid || !text || proactiveLoading.value) return
  proactiveMessages.value.push({ role: 'user', content: text })
  proactiveInput.value = ''
  proactiveCandidates.value = []
  proactiveLoading.value = true
  const sid = proactiveSessionId.value
  const body = sid ? { sessionId: sid, userMessage: text } : { userMessage: text }
  const res = await api.proactiveRecommendTurn(uid, body)
  proactiveLoading.value = false
  if (res.code !== 200) {
    proactiveMessages.value.pop()
    showAppMessage(res.message || '发送失败', '提示')
    return
  }
  applyProactiveResponse(res.data)
  scheduleLazyLoadSummaries()
}

async function runNlRecommend() {
  const uid = userId()
  const text = String(nlQuestion.value || '').trim()
  if (!uid || !text || nlLoading.value) return
  nlLoading.value = true
  nlAssistantMsg.value = ''
  nlCandidates.value = []
  const res = await api.nlRecommendQuery(uid, { question: text, topN: 5 })
  nlLoading.value = false
  if (res.code !== 200) {
    showAppMessage(res.message || '请求失败', '提示')
    return
  }
  const d = res.data || {}
  const inter = d.interpretation
  nlAssistantMsg.value = inter?.assistantMessage ? String(inter.assistantMessage) : ''
  nlCandidates.value = Array.isArray(d.candidateProducts) ? d.candidateProducts : []
}

async function commentRerankProactive() {
  const uid = userId()
  const list = proactiveCandidates.value
  if (!uid || !list.length || commentRerankLoading.value) return
  const ids = list.map((c) => c.productId).filter((id) => id != null && Number(id) > 0)
  if (!ids.length) return
  commentRerankLoading.value = true
  const body = { productIds: ids }
  if (proactiveSessionId.value) {
    body.sessionId = proactiveSessionId.value
  }
  const res = await api.aiGuideCommentRerank(uid, body)
  commentRerankLoading.value = false
  if (res.code !== 200) {
    showAppMessage(res.message || '重排失败', '提示')
    return
  }
  proactiveCandidates.value = Array.isArray(res.data) ? res.data : []
  scheduleLazyLoadSummaries()
}

async function commentRerankNl() {
  const uid = userId()
  const list = nlCandidates.value
  if (!uid || !list.length || commentRerankLoading.value) return
  const ids = list.map((c) => c.productId).filter((id) => id != null && Number(id) > 0)
  if (!ids.length) return
  commentRerankLoading.value = true
  const res = await api.aiGuideCommentRerank(uid, { productIds: ids })
  commentRerankLoading.value = false
  if (res.code !== 200) {
    showAppMessage(res.message || '重排失败', '提示')
    return
  }
  nlCandidates.value = Array.isArray(res.data) ? res.data : []
}

onMounted(async () => {
  pageLoading.value = false
  needLogin.value = !userId()
  if (!needLogin.value && userId()) {
    activeTool.value = 'doubao'
    await nextTick()
    void restoreOrClearProactiveSession()
    scheduleLazyLoadSummaries()
  }
})
</script>

<template>
  <div class="ai-page" :class="{ 'ai-page--full': !pageLoading && !needLogin }">
    <header class="ai-hero">
      <p class="ai-hero__eyebrow">智能导购</p>
      <h1 class="ai-hero__title">AI 帮你挑好物</h1>
        <p class="ai-hero__lead">
        <strong>首页「为你推荐」</strong>与本页导购相互独立。本页提供：<strong>AI推荐</strong>（多轮对话）与<strong>解析并推荐</strong>（一句话）。在得到商品候选后，可点击<strong>「评论强推」</strong>，结合买家评价等信息帮您把本批商品顺序再整理一遍。进入「AI推荐」后<strong>不会</strong>自动开启新对话；发送首条消息后才会开始。再次进入会接续<strong>您最近发过言的那次对话</strong>；「历史」下列表规则相同。点「新对话」可清空当前界面。
      </p>
      <button type="button" class="ai-hero__link" @click="router.push('/products')">去逛商品</button>
    </header>

    <div v-if="pageLoading" class="ai-state">加载中…</div>

    <template v-else-if="needLogin">
      <div class="ai-gate">
        <p class="ai-gate__text">请先登录，以便我们更好地理解您的养宠需求。</p>
        <button type="button" class="ai-btn ai-btn--dark" @click="router.push('/login')">去登录</button>
      </div>
    </template>

    <template v-else>
      <div class="ai-body">
        <div class="ai-modes" role="group" aria-label="选择推荐方式">
        <button
          type="button"
          class="ai-mode-card"
          :class="{ 'ai-mode-card--on': activeTool === 'doubao' }"
          :disabled="proactiveLoading"
          @click="selectDoubaoMode"
        >
          <span class="ai-mode-card__title">AI推荐</span>
          <span class="ai-mode-card__desc">多轮对话理清需求；出现候选后，可在输入框下方使用「评论强推」，按买家评价等对本批商品重新排序（与首页推荐无关）。</span>
        </button>
        <button
          type="button"
          class="ai-mode-card"
          :class="{ 'ai-mode-card--on': activeTool === 'model' }"
          :disabled="nlLoading"
          @click="selectModelMode"
        >
          <span class="ai-mode-card__title">解析并推荐</span>
          <span class="ai-mode-card__desc">用一句话描述需求并得到候选；在列表下同样可使用「评论强推」按评价等对本批结果重新排序（与首页推荐无关）。</span>
        </button>
        </div>

        <section v-show="activeTool === 'doubao'" class="db-shell">
        <header class="db-toolbar">
          <span class="db-toolbar__title">AI推荐</span>
          <div class="db-toolbar__sessions">
            <label class="db-toolbar__sess-label" for="ai-proactive-history">历史</label>
            <select
              id="ai-proactive-history"
              v-model="proactiveHistoryPick"
              class="db-toolbar__select"
              :disabled="proactiveLoading"
              @focus="onProactiveHistoryFocus"
              @change="onProactiveHistoryChange"
            >
              <option value="">最近会话（自动）</option>
              <option v-for="s in proactiveSessions" :key="s.sessionId" :value="String(s.sessionId)" :title="proactiveSessionOptionTitle(s)">
                {{ proactiveSessionOptionLabel(s) }}
              </option>
            </select>
          </div>
          <button type="button" class="db-toolbar__link" :disabled="proactiveLoading" @click="startProactiveChat">
            新对话
          </button>
        </header>

        <div class="db-scroll" aria-live="polite">
          <div class="db-scroll__inner">
            <p
              v-if="!proactiveSessionId && proactiveMessages.length === 0 && !proactiveLoading"
              class="db-empty-hint"
            >
              在下方输入首条消息即开始会话；仅浏览本页不会自动开始新对话。
            </p>
            <template v-for="(m, idx) in proactiveMessages">
              <div v-if="m.role === 'user'" :key="'u-' + idx" class="db-turn db-turn--user">
                <div class="db-bubble db-bubble--user">{{ m.content }}</div>
              </div>
              <div v-else :key="'b-' + idx" class="db-turn db-turn--bot">
                <div class="db-bot-col">
                  <div class="db-bubble db-bubble--bot">{{ m.content }}</div>
                </div>
              </div>
            </template>

            <div v-if="proactiveLoading" class="db-turn db-turn--bot">
              <div class="db-bot-col">
                <div class="db-bubble db-bubble--bot db-bubble--typing">
                  <span class="db-dot" /><span class="db-dot" /><span class="db-dot" />
                </div>
              </div>
            </div>
          </div>

          <div v-if="proactiveCandidates.length" class="db-media-strip">
            <p class="db-media-strip__label">推荐</p>
            <div class="db-media-strip__row">
              <button
                v-for="c in proactiveCandidates"
                :key="c.productId"
                type="button"
                class="db-media-card"
                @click="router.push(`/product/${c.productId}`)"
              >
                <div class="db-media-card__img-wrap">
                  <span v-if="c.commentStrongRecommend" class="db-media-card__badge">评论强推</span>
                  <AppImage :src="candidateCover(c)" class="db-media-card__img" alt="" />
                </div>
                <span class="db-media-card__cap">{{ candidateTitle(c) }}</span>
              </button>
            </div>
          </div>
        </div>

        <div class="db-composer">
          <textarea
            v-model="proactiveInput"
            class="db-composer__input db-composer__field"
            rows="2"
            maxlength="500"
            placeholder="发消息或描述养宠需求…"
            :disabled="proactiveLoading"
            @keydown.enter.exact.prevent="sendProactive"
          />
          <div class="db-composer__row2">
            <div class="db-composer__side">
              <div class="db-composer__chips" role="toolbar" aria-label="快捷短语">
                <button
                  v-for="chip in proactiveQuickSnippets"
                  :key="chip.label"
                  type="button"
                  class="db-chip"
                  :disabled="proactiveLoading"
                  @click="insertQuickSnippet(chip.text)"
                >
                  {{ chip.label }}
                </button>
              </div>
              <button
                v-if="proactiveCandidates.length"
                type="button"
                class="db-rerank-btn db-composer__rerank"
                :disabled="commentRerankLoading || proactiveLoading"
                title="结合买家评价等，对本批推荐重新排序"
                @click="commentRerankProactive"
              >
                {{ commentRerankLoading ? '正在重排…' : '评论强推' }}
              </button>
            </div>
            <button
              type="button"
              class="db-composer__send"
              :disabled="proactiveLoading || !String(proactiveInput || '').trim()"
              title="发送"
              aria-label="发送"
              @click="sendProactive"
            >
              <svg class="db-composer__send-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2">
                <path d="M12 19V5M12 5l-6 6M12 5l6 6" stroke-linecap="round" stroke-linejoin="round" />
              </svg>
            </button>
          </div>
        </div>
      </section>

        <section v-show="activeTool === 'model'" class="ai-panel ai-panel--nl">
        <div class="ai-panel__bar">
          <span class="ai-panel__label">一句话推荐</span>
        </div>
        <div class="ai-nl-body">
          <input
            v-model="nlQuestion"
            class="ai-input ai-input--block"
            type="text"
            maxlength="500"
            placeholder="例如：我的猫猫还小，推荐吃什么"
            :disabled="nlLoading"
            @keyup.enter="runNlRecommend"
          />
          <button
            type="button"
            class="ai-btn ai-btn--dark ai-btn--block"
            :disabled="nlLoading || !String(nlQuestion || '').trim()"
            @click="runNlRecommend"
          >
            {{ nlLoading ? '正在推荐…' : '解析并推荐' }}
          </button>
          <p v-if="nlAssistantMsg" class="ai-nl-reply">{{ nlAssistantMsg }}</p>
        </div>

        <div v-if="nlCandidates.length" class="ai-candidates">
          <p class="ai-cand-title">为您挑选</p>
          <div class="ai-cand-grid">
            <button
              v-for="c in nlCandidates"
              :key="'nl-' + c.productId"
              type="button"
              class="ai-cand-card"
              @click="router.push(`/product/${c.productId}`)"
            >
              <div class="ai-cand-img-wrap">
                <span v-if="c.commentStrongRecommend" class="ai-cand-badge">评论强推</span>
                <AppImage :src="candidateCover(c)" class="ai-cand-img" alt="" />
              </div>
              <span class="ai-cand-name">{{ candidateTitle(c) }}</span>
            </button>
          </div>
          <div class="ai-cand-rerank-bar">
            <button
              type="button"
              class="db-rerank-btn"
              title="结合买家评价等，对本批推荐重新排序"
              :disabled="commentRerankLoading || nlLoading"
              @click="commentRerankNl"
            >
              {{ commentRerankLoading ? '正在重排…' : '评论强推' }}
            </button>
            <span class="db-ai-rerank-hint">仅作用于本页当前候选，不影响首页「为你推荐」。</span>
          </div>
        </div>
      </section>
      </div>
    </template>
  </div>
</template>

<style scoped>
.ai-page {
  max-width: 720px;
  margin: 0 auto;
  padding: 1.5rem 1rem 3rem;
  font-family: 'Inter', 'Microsoft YaHei', 'PingFang SC', system-ui, sans-serif;
}

/* 登录后整页仅本功能：全宽 + 尽量占满顶栏以下视口 */
.ai-page--full {
  max-width: none;
  width: 100%;
  margin: 0;
  padding: 0;
  flex: 1 1 0%;
  min-height: calc(100vh - 68px - env(safe-area-inset-top, 0px));
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  background: #f4f5f7;
}

.ai-page--full .ai-hero {
  border-radius: 0;
  margin: 0;
  padding: 0.75rem clamp(16px, 4vw, 64px) 0.85rem;
  flex-shrink: 0;
}

.ai-body {
  flex: 1 1 0%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  width: 100%;
}

.ai-page--full .ai-modes {
  flex-shrink: 0;
  padding: 8px clamp(12px, 3vw, 48px) 8px;
  margin: 0;
  width: 100%;
  box-sizing: border-box;
}

.ai-page--full .db-shell {
  flex: 1 1 0%;
  min-height: 0;
  max-height: none;
  margin: 0 clamp(0px, 2vw, 32px) max(10px, env(safe-area-inset-bottom, 0px));
  border-radius: 14px;
}

.ai-page--full .ai-panel--nl {
  flex: 1 1 0%;
  min-height: 0;
  margin: 0 clamp(0px, 2vw, 32px) 16px;
  border-radius: 14px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.ai-page--full .ai-nl-body {
  flex: 0 0 auto;
}

.ai-page--full .ai-candidates {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

.ai-page--full .db-scroll__inner {
  padding: 22px clamp(14px, 4vw, 48px) 18px;
}

.ai-page--full .db-toolbar {
  padding-left: clamp(14px, 4vw, 48px);
  padding-right: clamp(14px, 4vw, 48px);
}

.ai-page--full .db-composer {
  margin-left: clamp(14px, 4vw, 48px);
  margin-right: clamp(14px, 4vw, 48px);
  margin-bottom: max(14px, env(safe-area-inset-bottom, 0px));
  padding: 14px 14px 12px;
}

.ai-page--full .db-composer__send {
  width: 46px;
  height: 46px;
  align-self: flex-end;
}

.ai-page--full .db-composer__row2 {
  align-items: flex-end;
  padding-top: 10px;
}

.ai-page--full .db-media-strip {
  padding-left: clamp(14px, 4vw, 48px);
  padding-right: clamp(14px, 4vw, 48px);
}

/* —— AI 对话壳 —— */
.db-shell {
  display: flex;
  flex-direction: column;
  min-height: min(82vh, 820px);
  max-height: min(92vh, 960px);
  background: #fff;
  border-radius: 16px;
  border: 1px solid #e8eaed;
  box-shadow: 0 8px 32px rgba(15, 23, 42, 0.06);
  overflow: hidden;
}

.db-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 12px 14px 10px;
  border-bottom: 1px solid #f0f1f4;
  flex-shrink: 0;
}

.db-toolbar__sessions {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-width: 0;
}

.db-toolbar__sess-label {
  font-size: 12px;
  font-weight: 600;
  color: #6b7280;
  white-space: nowrap;
}

.db-toolbar__select {
  max-width: min(240px, 42vw);
  min-width: 0;
  flex: 1;
  padding: 6px 10px;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  font-size: 12px;
  font-weight: 600;
  color: #111827;
  background: #fff;
}

.db-toolbar__title {
  font-size: 15px;
  font-weight: 700;
  color: #111827;
  letter-spacing: -0.02em;
}

.db-toolbar__link {
  padding: 6px 12px;
  border: none;
  border-radius: 999px;
  background: #f3f4f6;
  color: #374151;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}

.db-toolbar__link:hover:not(:disabled) {
  background: #e5e7eb;
}

.db-toolbar__link:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.db-scroll {
  flex: 1;
  min-height: 0;
  background: #fafbfc;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.db-scroll__inner {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 20px 14px 16px;
}

.db-empty-hint {
  margin: 0 0 14px;
  padding: 10px 12px;
  font-size: 13px;
  line-height: 1.5;
  color: #64748b;
  background: #f1f5f9;
  border-radius: 10px;
  border: 1px solid #e2e8f0;
}

.db-turn {
  display: flex;
  margin-bottom: 14px;
}

.db-turn--user {
  justify-content: flex-end;
}

.db-turn--bot {
  justify-content: flex-start;
  align-items: flex-start;
  width: 100%;
}

.db-bot-col {
  max-width: min(100%, 720px);
  min-width: 0;
}

.db-bubble {
  border-radius: 14px;
  padding: 10px 14px;
  font-size: 15px;
  line-height: 1.55;
  white-space: pre-wrap;
  word-break: break-word;
}

.db-bubble--user {
  background: #eceef2;
  color: #111827;
  max-width: min(85%, 520px);
}

.db-bubble--bot {
  background: #fff;
  border: 1px solid #e8eaed;
  color: #1f2937;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.03);
}

.db-bubble--typing {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 12px 16px;
}

.db-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #9ca3af;
  animation: db-bounce 1.1s ease-in-out infinite;
}

.db-dot:nth-child(2) {
  animation-delay: 0.15s;
}

.db-dot:nth-child(3) {
  animation-delay: 0.3s;
}

@keyframes db-bounce {
  0%,
  80%,
  100% {
    opacity: 0.35;
    transform: translateY(0);
  }
  40% {
    opacity: 1;
    transform: translateY(-3px);
  }
}

.db-media-strip {
  padding: 4px 14px 14px;
  border-top: 1px solid #f0f1f4;
  background: #fafbfc;
  flex-shrink: 0;
}

.db-media-strip__label {
  margin: 0 0 8px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: #9ca3af;
  text-transform: uppercase;
}

.db-media-strip__row {
  display: flex;
  gap: 10px;
  overflow-x: auto;
  padding-bottom: 4px;
  scrollbar-width: thin;
}

.db-media-card {
  flex: 0 0 auto;
  width: 108px;
  border: 1px solid #e8eaed;
  border-radius: 12px;
  background: #fff;
  padding: 8px;
  cursor: pointer;
  text-align: left;
  transition: border-color 0.15s, box-shadow 0.15s;
}

.db-media-card:hover {
  border-color: #c7cad1;
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.06);
}

.db-media-card__img-wrap {
  position: relative;
  border-radius: 10px;
  overflow: hidden;
  border: 1px solid #f0f1f4;
  aspect-ratio: 1;
}

.db-media-card__badge {
  position: absolute;
  top: 4px;
  left: 4px;
  z-index: 1;
  padding: 2px 6px;
  border-radius: 6px;
  font-size: 9px;
  font-weight: 800;
  letter-spacing: 0.02em;
  color: #fffbeb;
  background: linear-gradient(135deg, #b45309 0%, #d97706 55%, #f59e0b 100%);
  box-shadow: 0 2px 8px rgba(217, 119, 6, 0.35);
  pointer-events: none;
}

.db-ai-rerank-hint {
  font-size: 11px;
  line-height: 1.45;
  color: #64748b;
  flex: 1 1 200px;
}

.db-media-card__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.db-media-card__cap {
  margin-top: 6px;
  font-size: 11px;
  font-weight: 600;
  color: #374151;
  line-height: 1.35;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.db-composer {
  flex-shrink: 0;
  margin: 0 12px 12px;
  padding: 12px 14px 10px;
  background: #fff;
  border: 1px solid #e3e6ec;
  border-radius: 22px;
  box-shadow: 0 4px 20px rgba(15, 23, 42, 0.06);
}

.db-composer__input {
  width: 100%;
  border: none;
  background: transparent;
  font-size: 15px;
  color: #111827;
  padding: 4px 2px 6px;
  outline: none;
}

.db-composer__field {
  display: block;
  min-height: 52px;
  max-height: 168px;
  resize: none;
  line-height: 1.5;
  font-family: inherit;
  overflow-y: auto;
}

.db-composer__input::placeholder {
  color: #9ca3af;
}

.db-composer__input:disabled {
  color: #9ca3af;
}

.db-composer__row2 {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-top: 8px;
  border-top: 1px solid #f3f4f6;
}

.db-composer__side {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
}

.db-composer__rerank {
  flex-shrink: 0;
  align-self: flex-start;
}

/* 与对话区 db-chip / 发送钮同系的紫靛强调，区别于首页橙色「强推」 */
.db-rerank-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 34px;
  padding: 0 14px;
  border-radius: 999px;
  border: 1px solid #c7b8f3;
  background: linear-gradient(180deg, #faf8ff 0%, #f4f4f5 100%);
  color: #5b21b6;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.02em;
  cursor: pointer;
  box-shadow: 0 1px 2px rgba(99, 102, 241, 0.08), 0 2px 8px rgba(124, 58, 237, 0.06);
  transition: border-color 0.15s, background 0.15s, box-shadow 0.15s, color 0.15s;
}

.db-rerank-btn:hover:not(:disabled) {
  border-color: #8b5cf6;
  background: linear-gradient(180deg, #f5f3ff 0%, #ede9fe 100%);
  color: #4c1d95;
  box-shadow: 0 2px 10px rgba(99, 102, 241, 0.18);
}

.db-rerank-btn:active:not(:disabled) {
  transform: scale(0.98);
}

.db-rerank-btn:disabled {
  opacity: 0.42;
  cursor: not-allowed;
  box-shadow: none;
}

.db-composer__chips {
  width: 100%;
  flex: 0 0 auto;
  min-width: 0;
  display: flex;
  gap: 6px;
  overflow-x: auto;
  padding: 2px 0;
  scrollbar-width: none;
}

.db-composer__chips::-webkit-scrollbar {
  display: none;
}

.db-chip {
  flex: 0 0 auto;
  padding: 6px 11px;
  border-radius: 999px;
  border: 1px solid #e8eaed;
  background: #fafbfc;
  color: #4b5563;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  white-space: nowrap;
}

.db-chip:hover:not(:disabled) {
  background: #f3f4f6;
  border-color: #d1d5db;
}

.db-chip:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.db-composer__send {
  flex-shrink: 0;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  border: none;
  background: linear-gradient(135deg, #6366f1 0%, #7c3aed 100%);
  color: #fff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 8px rgba(99, 102, 241, 0.35);
}

.db-composer__send:hover:not(:disabled) {
  filter: brightness(1.06);
}

.db-composer__send:disabled {
  opacity: 0.38;
  cursor: not-allowed;
  box-shadow: none;
}

.db-composer__send-icon {
  width: 18px;
  height: 18px;
}

.ai-hero {
  padding: 1.5rem 1.15rem 1.35rem;
  border-radius: 14px;
  background: linear-gradient(135deg, #0f172a 0%, #1e293b 48%, #0f172a 100%);
  color: #f8fafc;
  margin-bottom: 1.25rem;
  position: relative;
}

.ai-hero__eyebrow {
  margin: 0;
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: #94a3b8;
}

.ai-hero__title {
  margin: 0.45rem 0 0;
  font-size: 1.5rem;
  font-weight: 800;
  letter-spacing: -0.02em;
}

.ai-hero__lead {
  margin: 0.55rem 0 0;
  font-size: 0.9rem;
  line-height: 1.55;
  color: #cbd5e1;
  max-width: 40rem;
}

.ai-hero__link {
  margin-top: 0.85rem;
  padding: 0;
  border: none;
  background: none;
  color: #94a3b8;
  font-size: 0.82rem;
  cursor: pointer;
  text-decoration: underline;
  text-underline-offset: 3px;
}

.ai-hero__link:hover {
  color: #e2e8f0;
}

.ai-state {
  text-align: center;
  padding: 2rem;
  color: #64748b;
}

.ai-gate {
  text-align: center;
  padding: 2.5rem 1rem;
  border: 1px dashed #cbd5e1;
  border-radius: 12px;
  background: #f8fafc;
}

.ai-gate__text {
  margin: 0 0 1rem;
  color: #475569;
  font-size: 0.95rem;
  line-height: 1.5;
}

.ai-modes {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.75rem;
  margin-bottom: 1rem;
}

@media (max-width: 560px) {
  .ai-modes {
    grid-template-columns: 1fr;
  }
}

.ai-mode-card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.35rem;
  padding: 1rem 1rem 1.05rem;
  border-radius: 12px;
  border: 2px solid #e2e8f0;
  background: #fff;
  cursor: pointer;
  text-align: left;
  transition: border-color 0.15s, box-shadow 0.15s;
}

.ai-mode-card:hover:not(:disabled) {
  border-color: #94a3b8;
}

.ai-mode-card--on {
  border-color: #0f172a;
  box-shadow: 0 0 0 1px #0f172a;
}

.ai-mode-card:disabled {
  opacity: 0.55;
  cursor: wait;
}

.ai-mode-card__title {
  font-size: 0.98rem;
  font-weight: 800;
  color: #0f172a;
}

.ai-mode-card__desc {
  font-size: 0.78rem;
  line-height: 1.45;
  color: #64748b;
}

.ai-panel {
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  background: #fff;
  overflow: hidden;
}

.ai-panel + .ai-panel {
  margin-top: 0.75rem;
}

.ai-panel__bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  padding: 0.65rem 1rem;
  background: #f8fafc;
  border-bottom: 1px solid #e2e8f0;
}

.ai-panel__label {
  font-size: 0.78rem;
  font-weight: 800;
  letter-spacing: 0.06em;
  color: #64748b;
  text-transform: uppercase;
}

.ai-chat {
  padding: 1rem 1rem 0.75rem;
  min-height: 160px;
  max-height: 320px;
  overflow: auto;
  background: #fafafa;
}

.ai-msg {
  margin-bottom: 0.65rem;
  padding: 0.65rem 0.85rem;
  border-radius: 12px;
  font-size: 0.9rem;
  line-height: 1.45;
  white-space: pre-wrap;
}

.ai-msg--bot {
  background: #fff;
  border: 1px solid #e2e8f0;
  color: #0f172a;
}

.ai-msg--user {
  margin-left: 1.25rem;
  background: #0f172a;
  color: #f8fafc;
}

.ai-placeholder {
  margin: 0;
  font-size: 0.88rem;
  color: #64748b;
}

.ai-candidates {
  padding: 0.75rem 1rem 0;
  border-top: 1px solid #e2e8f0;
  background: #fff;
}

.ai-cand-title {
  margin: 0 0 0.5rem;
  font-size: 0.72rem;
  font-weight: 800;
  letter-spacing: 0.06em;
  color: #64748b;
  text-transform: uppercase;
}

.ai-cand-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(108px, 1fr));
  gap: 0.65rem;
  padding-bottom: 0.75rem;
}

.ai-cand-card {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  padding: 0.5rem;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #fafafa;
  cursor: pointer;
  text-align: left;
}

.ai-cand-card:hover {
  border-color: #0f172a;
}

.ai-cand-img-wrap {
  position: relative;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #e2e8f0;
  aspect-ratio: 1;
}

.ai-cand-badge {
  position: absolute;
  top: 4px;
  left: 4px;
  z-index: 1;
  padding: 2px 6px;
  border-radius: 6px;
  font-size: 9px;
  font-weight: 800;
  letter-spacing: 0.02em;
  color: #fffbeb;
  background: linear-gradient(135deg, #b45309 0%, #d97706 55%, #f59e0b 100%);
  box-shadow: 0 2px 8px rgba(217, 119, 6, 0.35);
  pointer-events: none;
}

.ai-cand-rerank-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px 14px;
  margin-top: 0.25rem;
  padding-top: 0.65rem;
  border-top: 1px solid #f1f5f9;
}

.ai-cand-img {
  width: 100%;
  height: 100%;
  display: block;
  border-radius: 0;
  object-fit: cover;
}

.ai-cand-name {
  font-size: 0.72rem;
  font-weight: 700;
  color: #0f172a;
  line-height: 1.3;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.ai-input-row {
  display: flex;
  gap: 0.65rem;
  padding: 0.85rem 1rem 1rem;
  align-items: center;
  border-top: 1px solid #e2e8f0;
  background: #fff;
}

.ai-nl-body {
  padding: 1rem;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.ai-input {
  flex: 1;
  height: 42px;
  padding: 0 0.85rem;
  border: 1px solid #cbd5e1;
  border-radius: 10px;
  font-size: 0.9rem;
  color: #0f172a;
}

.ai-input--block {
  width: 100%;
  flex: none;
}

.ai-input:focus {
  outline: 2px solid #0f172a;
  outline-offset: 1px;
}

.ai-input:disabled {
  background: #f1f5f9;
  color: #94a3b8;
}

.ai-nl-reply {
  margin: 0;
  font-size: 0.9rem;
  line-height: 1.55;
  color: #334155;
}

.ai-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 40px;
  padding: 0 1rem;
  border-radius: 10px;
  font-size: 0.88rem;
  font-weight: 700;
  cursor: pointer;
  border: 1px solid transparent;
}

.ai-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.ai-btn--dark {
  background: #0f172a;
  color: #f8fafc;
  border-color: #0f172a;
}

.ai-btn--dark:hover:not(:disabled) {
  background: #1e293b;
}

.ai-btn--sm {
  min-height: 34px;
  padding: 0 0.75rem;
  font-size: 0.8rem;
  background: #fff;
  color: #0f172a;
  border-color: #e2e8f0;
}

.ai-btn--sm:hover:not(:disabled) {
  background: #f1f5f9;
}

.ai-btn--block {
  width: 100%;
}
</style>
