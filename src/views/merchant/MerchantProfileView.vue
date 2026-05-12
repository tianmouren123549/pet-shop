<script setup>
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { api } from '../../utils/request'
import { showAppMessage } from '../../utils/appMessage'
import { isValidEmailFormat } from '../../utils/emailFormat.js'

const loading = ref(false)
const saving = ref(false)
const errorMsg = ref('')
const form = ref({
  username: '',
  shopName: '',
  contactName: '',
  phone: '',
  email: '',
  avatarUrl: '',
  /** 每周销售额目标（元），空表示不设目标 */
  salesTargetWeekly: '',
})

const displayShopName = computed(() => {
  const s = String(form.value.shopName || '').trim()
  return s || String(form.value.username || '').trim() || '店铺'
})

function merchantId() {
  return Number(localStorage.getItem('adminId') || 0)
}

async function loadProfile() {
  loading.value = true
  errorMsg.value = ''
  const mid = merchantId()
  if (!mid) {
    loading.value = false
    errorMsg.value = '请先登录商家账号'
    return
  }
  const res = await api.merchantGetProfile(mid)
  loading.value = false
  if (res.code !== 200) {
    errorMsg.value = res.message || '加载失败'
    return
  }
  form.value = {
    username: String(res.data?.username || ''),
    shopName: String(res.data?.shopName || ''),
    contactName: String(res.data?.contactName || ''),
    phone: String(res.data?.phone || ''),
    email: String(res.data?.email || ''),
    avatarUrl: String(res.data?.avatarUrl || ''),
    salesTargetWeekly:
      res.data?.salesTargetWeekly != null && res.data.salesTargetWeekly !== ''
        ? String(res.data.salesTargetWeekly)
        : '',
  }
}

function onAvatarChange(e) {
  const file = e.target.files?.[0]
  if (!file) return
  const reader = new FileReader()
  reader.onload = () => {
    form.value.avatarUrl = String(reader.result || '')
  }
  reader.readAsDataURL(file)
}

async function saveProfile() {
  const mid = merchantId()
  if (!mid) {
    errorMsg.value = '请先登录商家账号'
    return
  }
  const emailTrim = String(form.value.email || '').trim()
  if (emailTrim && !isValidEmailFormat(emailTrim)) {
    errorMsg.value = '联系邮箱格式不正确'
    return
  }
  saving.value = true
  errorMsg.value = ''
  const res = await api.merchantUpdateProfile(mid, {
    ...form.value,
    email: emailTrim,
    salesTargetWeekly: String(form.value.salesTargetWeekly ?? '').trim(),
  })
  saving.value = false
  if (res.code !== 200) {
    errorMsg.value = res.message || '保存失败'
    return
  }
  errorMsg.value = ''
  localStorage.setItem('adminName', form.value.username)
  showAppMessage('保存成功', '提示')
}

onMounted(loadProfile)
</script>

<template>
  <div class="pw-page merchant-profile-page">
    <div class="mp-canvas">
      <div v-if="loading" class="mp-state mp-state--muted">加载中…</div>

      <template v-else-if="errorMsg">
        <div class="mp-state mp-state--error">
          <p class="mp-state-msg">{{ errorMsg }}</p>
          <button type="button" class="mp-btn-primary mp-btn-primary--sm" @click="loadProfile">重试</button>
        </div>
      </template>

      <section v-else class="mp-shell">
        <aside class="mp-sidebar">
          <div class="mp-sidebar-head">
            <h2 class="mp-sidebar-title">商家中心</h2>
            <p class="mp-sidebar-lead">维护店铺对外信息与联系方式；头像会用于商家端展示。</p>
          </div>

          <div class="mp-side-profile">
            <label class="mp-avatar-hit">
              <img
                v-if="form.avatarUrl"
                :src="form.avatarUrl"
                class="mp-avatar-lg"
                alt=""
                loading="lazy"
                decoding="async"
              />
              <div v-else class="mp-avatar-lg mp-avatar-lg--empty">店铺头像</div>
              <input type="file" accept="image/*" class="mp-file-input" @change="onAvatarChange" />
            </label>
            <p class="mp-side-name">{{ displayShopName }}</p>
            <p class="mp-side-sub">账号 {{ form.username }}</p>

            <div class="mp-side-links">
              <RouterLink class="mp-meta-link" to="/merchant">运营总览</RouterLink>
              <RouterLink class="mp-meta-link" to="/merchant/notifications">站内通知</RouterLink>
            </div>
          </div>

          <div class="mp-sidebar-tip">
            <p class="mp-sidebar-tip-label">提示</p>
            <p class="mp-sidebar-tip-text">点击头像区域即可更换图片；修改表单后请在右侧点击「保存资料」。</p>
          </div>
        </aside>

        <div class="mp-main">
          <header class="mp-toolbar">
            <div class="mp-toolbar__left">
              <h1 class="mp-toolbar-title">店铺资料</h1>
              <p class="mp-toolbar-meta">名称与联系方式会用于订单与客服场景，请保持准确。</p>
            </div>
          </header>

          <section class="mp-card" aria-labelledby="mp-card-title">
            <div id="mp-card-title" class="mp-card-head">
              <span class="mp-card-title">基本信息</span>
            </div>

            <p class="mp-lead">带「可选」的字段可不填；联系邮箱填写后需为有效格式。</p>

            <div v-if="errorMsg" class="mp-inline-error">{{ errorMsg }}</div>

            <div class="mp-form">
              <div class="mp-field">
                <label class="mp-label" for="mf-username">商家账号（只读）</label>
                <div class="mp-field-control">
                  <input id="mf-username" v-model="form.username" class="mp-input mp-input--readonly" type="text" readonly />
                </div>
              </div>
              <div class="mp-field">
                <label class="mp-label" for="mf-shop">店铺名称</label>
                <div class="mp-field-control">
                  <input id="mf-shop" v-model="form.shopName" class="mp-input" type="text" maxlength="40" />
                </div>
              </div>
              <div class="mp-field">
                <label class="mp-label" for="mf-contact">联系人</label>
                <div class="mp-field-control">
                  <input id="mf-contact" v-model="form.contactName" class="mp-input" type="text" maxlength="30" />
                </div>
              </div>
              <div class="mp-field">
                <label class="mp-label" for="mf-phone">联系电话</label>
                <div class="mp-field-control">
                  <input id="mf-phone" v-model="form.phone" class="mp-input" type="text" maxlength="20" />
                </div>
              </div>
              <div class="mp-field">
                <label class="mp-label" for="mf-email">联系邮箱（可选）</label>
                <div class="mp-field-control">
                  <input
                    id="mf-email"
                    v-model="form.email"
                    class="mp-input"
                    type="email"
                    maxlength="80"
                    placeholder="用于接收平台或客户相关通知"
                    autocomplete="email"
                  />
                </div>
              </div>
              <div class="mp-field">
                <label class="mp-label" for="mf-sales-target">每周销售目标（元）</label>
                <div class="mp-field-control mp-field-control--stack">
                  <input
                    id="mf-sales-target"
                    v-model="form.salesTargetWeekly"
                    class="mp-input"
                    type="number"
                    min="0"
                    step="0.01"
                    placeholder="留空则不在趋势图中显示目标参考线"
                  />
                  <p class="mp-hint">
                    设定后可在「店铺运营总览」中对照近几周表现；仅作店内经营参考，可随时修改或清空。
                  </p>
                </div>
              </div>
            </div>

            <div class="mp-actions">
              <div class="mp-actions-spacer" aria-hidden="true" />
              <button type="button" class="mp-btn-primary" :disabled="saving" @click="saveProfile">
                {{ saving ? '保存中…' : '保存资料' }}
              </button>
            </div>
          </section>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.merchant-profile-page {
  --mp-ink: #0a0a0a;
  --mp-muted: #737373;
  --mp-line: #e5e5e5;
  --mp-panel: #ffffff;
  --mp-soft: #fafafa;
  --mp-canvas: #f5f5f5;
  --mp-radius: 2px;
  font-family: 'Inter', 'Microsoft YaHei', 'PingFang SC', system-ui, sans-serif;
}

.merchant-profile-page.pw-page {
  padding-bottom: clamp(26px, 3.2vh, 40px);
}

.mp-canvas {
  background: var(--mp-canvas);
  min-height: min(72vh, 880px);
  margin: 0;
  padding: clamp(12px, 2.7vw, 20px) 0 28px;
}

.mp-shell {
  display: grid;
  grid-template-columns: minmax(220px, 280px) minmax(0, 1fr);
  gap: clamp(18px, 2.4vw, 28px);
  align-items: start;
  width: 100%;
  max-width: min(1180px, 100%);
  margin: 0 auto;
}

.mp-sidebar {
  position: sticky;
  top: 72px;
  background: var(--mp-panel);
  border: 1px solid var(--mp-line);
  border-radius: var(--mp-radius);
  padding: 18px 16px;
  box-shadow: 0 1px 0 rgba(0, 0, 0, 0.04);
}

.mp-sidebar-head {
  margin-bottom: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--mp-line);
}

.mp-sidebar-title {
  margin: 0 0 6px;
  font-size: clamp(17px, 1.4vw, 20px);
  font-weight: 800;
  letter-spacing: -0.02em;
  color: var(--mp-ink);
}

.mp-sidebar-lead {
  margin: 0;
  font-size: 13px;
  line-height: 1.55;
  color: var(--mp-muted);
}

.mp-side-profile {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  gap: 10px;
  padding: 8px 0 14px;
  border-bottom: 1px solid var(--mp-line);
}

.mp-avatar-hit {
  position: relative;
  cursor: pointer;
  border-radius: 50%;
}

.mp-avatar-hit:focus-within {
  outline: 2px solid var(--mp-ink);
  outline-offset: 2px;
}

.mp-avatar-lg {
  width: 88px;
  height: 88px;
  border-radius: 50%;
  object-fit: cover;
  border: 1px solid var(--mp-line);
  display: block;
  background: var(--mp-soft);
}

.mp-avatar-lg--empty {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #a3a3a3;
  font-size: 12px;
  font-weight: 700;
}

.mp-file-input {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  opacity: 0;
  cursor: pointer;
}

.mp-side-name {
  margin: 4px 0 0;
  font-size: 16px;
  font-weight: 800;
  color: var(--mp-ink);
  line-height: 1.3;
  max-width: 100%;
  word-break: break-all;
}

.mp-side-sub {
  margin: 0;
  font-size: 12px;
  font-weight: 600;
  color: var(--mp-muted);
}

.mp-side-links {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
  margin-top: 4px;
}

.mp-meta-link {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 38px;
  padding: 0 12px;
  border-radius: var(--mp-radius);
  border: 1px solid var(--mp-line);
  background: var(--mp-panel);
  color: var(--mp-ink);
  font-size: 13px;
  font-weight: 700;
  text-decoration: none;
  transition: border-color 0.15s, background 0.15s;
}

.mp-meta-link:hover {
  border-color: var(--mp-ink);
  background: var(--mp-soft);
}

.mp-sidebar-tip {
  margin-top: 12px;
  padding: 10px 12px;
  border: 1px solid var(--mp-line);
  border-radius: var(--mp-radius);
  background: var(--mp-soft);
}

.mp-sidebar-tip-label {
  margin: 0 0 6px;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: #a3a3a3;
}

.mp-sidebar-tip-text {
  margin: 0;
  font-size: 12px;
  line-height: 1.55;
  color: var(--mp-muted);
}

.mp-main {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.mp-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  padding: clamp(18px, 1.95vw, 22px) clamp(20px, 2.1vw, 26px);
  background: var(--mp-panel);
  border: 1px solid var(--mp-line);
  border-radius: var(--mp-radius);
  box-shadow: 0 1px 0 rgba(0, 0, 0, 0.04);
}

.mp-toolbar-title {
  margin: 0 0 6px;
  font-size: clamp(19px, 1.55vw, 24px);
  font-weight: 800;
  color: var(--mp-ink);
  letter-spacing: -0.02em;
}

.mp-toolbar-meta {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--mp-muted);
}

.mp-card {
  background: var(--mp-panel);
  border: 1px solid var(--mp-line);
  border-radius: var(--mp-radius);
  box-shadow: 0 1px 0 rgba(0, 0, 0, 0.03);
  overflow: hidden;
}

.mp-card-head {
  padding: 15px 18px 11px;
  border-bottom: 1px solid var(--mp-line);
}

.mp-card-title {
  font-size: 17px;
  font-weight: 800;
  color: var(--mp-ink);
}

.mp-lead {
  margin: 0;
  padding: 14px 18px 0;
  font-size: 14px;
  line-height: 1.55;
  font-weight: 600;
  color: var(--mp-muted);
}

.mp-inline-error {
  margin: 10px 18px 0;
  padding: 10px 12px;
  border-radius: var(--mp-radius);
  border: 1px solid #fecaca;
  background: #fef2f2;
  color: #b91c1c;
  font-size: 13px;
  font-weight: 600;
}

.mp-form {
  display: flex;
  flex-direction: column;
  padding: 8px 18px 4px;
}

.mp-field {
  display: grid;
  grid-template-columns: minmax(100px, 148px) minmax(0, 1fr);
  column-gap: 18px;
  align-items: center;
  padding: 14px 0;
  border-bottom: 1px solid var(--mp-line);
}

.mp-field:last-of-type {
  border-bottom: none;
}

.mp-label {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--mp-muted);
  text-align: right;
}

.mp-label::after {
  content: '：';
}

.mp-field-control {
  min-width: 0;
  max-width: min(100%, 480px);
}

.mp-field-control--stack {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-width: min(100%, 480px);
}

.mp-field-control--stack .mp-input[type='number'] {
  width: 100%;
  box-sizing: border-box;
}

.mp-hint {
  margin: 0;
  font-size: 12px;
  color: var(--mp-muted);
  line-height: 1.5;
  font-weight: 500;
}

.mp-input {
  width: 100%;
  height: 44px;
  padding: 0 11px;
  font-size: 15px;
  font-weight: 600;
  color: var(--mp-ink);
  border: 1px solid var(--mp-line);
  border-radius: var(--mp-radius);
  background: var(--mp-panel);
  box-sizing: border-box;
}

.mp-input:focus {
  outline: none;
  border-color: var(--mp-ink);
}

.mp-input--readonly {
  background: var(--mp-soft);
  color: #525252;
  cursor: default;
}

.mp-actions {
  display: grid;
  grid-template-columns: minmax(100px, 148px) minmax(0, 1fr);
  column-gap: 18px;
  padding: 17px 18px 20px;
  margin-top: 4px;
  border-top: 1px solid var(--mp-line);
}

.mp-actions-spacer {
  min-height: 1px;
}

.mp-btn-primary {
  padding: 12px 24px;
  border: 1px solid var(--mp-ink);
  border-radius: var(--mp-radius);
  background: var(--mp-ink);
  color: #fff;
  font-size: 15px;
  font-weight: 800;
  cursor: pointer;
  justify-self: start;
}

.mp-btn-primary:hover:not(:disabled) {
  background: #262626;
  border-color: #262626;
}

.mp-btn-primary:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.mp-btn-primary--sm {
  padding: 8px 16px;
  font-size: 13px;
}

.mp-state {
  padding: 28px 20px;
  text-align: center;
  border-radius: var(--mp-radius);
  border: 1px solid var(--mp-line);
  background: var(--mp-panel);
  max-width: 420px;
  margin: 0 auto;
}

.mp-state--muted {
  color: var(--mp-muted);
  font-weight: 600;
}

.mp-state--error {
  border-color: #fecaca;
  background: #fef2f2;
}

.mp-state-msg {
  margin: 0 0 14px;
  color: #b91c1c;
  font-weight: 700;
}

@media (max-width: 640px) {
  .mp-field {
    grid-template-columns: 1fr;
    row-gap: 6px;
  }

  .mp-label {
    text-align: left;
  }

  .mp-field-control,
  .mp-field-control--stack {
    max-width: none;
  }

  .mp-actions {
    grid-template-columns: 1fr;
  }

  .mp-actions-spacer {
    display: none;
  }

  .mp-btn-primary {
    width: 100%;
    justify-self: stretch;
  }
}

@media (max-width: 979px) {
  .mp-shell {
    grid-template-columns: 1fr;
    max-width: 640px;
  }

  .mp-sidebar {
    position: static;
  }
}
</style>
