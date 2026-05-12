<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../utils/request'
import { clearAuthLocalStorage, dispatchAuthUpdatedEvent } from '../utils/authStorage.js'
import { showAppMessage } from '../utils/appMessage'
import { isValidEmailFormat } from '../utils/emailFormat.js'

const router = useRouter()
const mode = ref('login') // login | register
const loginRole = ref('user') // user | merchant
/** 注册页：用户注册 | 商家注册 */
const registerRole = ref('user')
/** 用户登录 / 用户注册：登录邮箱 */
const userEmail = ref('')
/** 用户注册：手机号选填 */
const userPhone = ref('')
/** 商家注册：店铺联系电话 */
const phone = ref('')
const password = ref('')
const nickname = ref('')
const adminUsername = ref('')
const shopName = ref('')
const contactName = ref('')
/** 商家注册：联系邮箱（可选） */
const email = ref('')
const errorMsg = ref('')

/** 保存登录会话令牌 */
function persistAccessToken(data) {
  const t = data?.token
  if (t) localStorage.setItem('accessToken', t)
}

async function handleLogin() {
  errorMsg.value = ''
  clearAuthLocalStorage()
  const res = loginRole.value === 'merchant'
    ? await api.merchantLogin({ username: adminUsername.value, password: password.value })
    : await api.login({ email: userEmail.value, password: password.value })
  if (res.code === 200) {
    if (loginRole.value === 'merchant') {
      const roleToStore = 'MERCHANT'
      localStorage.setItem('role', roleToStore)
      localStorage.setItem('adminId', String(res.data.adminId))
      localStorage.setItem('adminName', res.data.username || '')
      persistAccessToken(res.data)
      dispatchAuthUpdatedEvent()
      showAppMessage('商家登录成功', '欢迎')
      router.push('/merchant')
      return
    }

    localStorage.setItem('role', 'USER')
    localStorage.setItem('userId', String(res.data.userId))
    localStorage.setItem('nickname', res.data.nickname || '')
    persistAccessToken(res.data)
    dispatchAuthUpdatedEvent()
    showAppMessage('登录成功', '欢迎')
    router.push('/')
  } else {
    errorMsg.value = res.message || '登录失败'
  }
}

async function handleRegister() {
  errorMsg.value = ''
  if (registerRole.value === 'merchant') {
    const optEmail = String(email.value || '').trim()
    if (optEmail && !isValidEmailFormat(optEmail)) {
      errorMsg.value = '联系邮箱格式不正确'
      return
    }
    const res = await api.merchantRegister({
      username: adminUsername.value,
      password: password.value,
      shopName: shopName.value,
      contactName: contactName.value,
      phone: phone.value,
      email: email.value,
    })
    if (res.code === 200) {
      persistAccessToken(res.data)
      showAppMessage('商家注册成功，请使用商家登录', '提示')
      mode.value = 'login'
      loginRole.value = 'merchant'
      password.value = ''
    } else {
      errorMsg.value = res.message || '注册失败'
    }
    return
  }
  if (!nickname.value.trim()) {
    errorMsg.value = '请填写昵称'
    return
  }
  const regEmail = String(userEmail.value || '').trim()
  if (!regEmail) {
    errorMsg.value = '请填写登录邮箱'
    return
  }
  if (!isValidEmailFormat(regEmail)) {
    errorMsg.value = '邮箱格式不正确'
    return
  }
  const res = await api.register({
    nickname: nickname.value,
    email: userEmail.value,
    phone: userPhone.value,
    password: password.value,
  })
  if (res.code === 200) {
    persistAccessToken(res.data)
    showAppMessage('注册成功，请登录', '提示')
    mode.value = 'login'
    loginRole.value = 'user'
    password.value = ''
  } else {
    errorMsg.value = res.message || '注册失败'
  }
}

function switchMode(nextMode) {
  mode.value = nextMode
  errorMsg.value = ''
  password.value = ''
  if (nextMode === 'register') {
    registerRole.value = loginRole.value === 'merchant' ? 'merchant' : 'user'
  }
}

function switchLoginRole(nextRole) {
  loginRole.value = nextRole
  errorMsg.value = ''
}

function switchRegisterRole(next) {
  registerRole.value = next
  errorMsg.value = ''
}
</script>

<template>
  <div class="login-page">
    <div class="hero-panel">
      <p class="hero-tag">账户认证中心</p>
      <h2>宠物电商运营系统</h2>
      <p class="hero-desc">一站式宠物商品选购与店铺运营：登录后可浏览下单，或使用商家账号管理商品与订单。</p>
    </div>

    <div class="login-container">
      <div class="login-header">
        <h3>{{ mode === 'login' ? '欢迎登录' : '注册新账号' }}</h3>
        <p>{{ mode === 'login' ? '请填写必要信息完成身份验证' : '选择注册类型并填写资料' }}</p>
      </div>

      <form class="login-form" @submit.prevent="mode === 'login' ? handleLogin() : handleRegister()">
        <div v-if="mode === 'register'" class="form-toggle segmented">
          <button type="button" class="tab-btn" :class="{ active: registerRole === 'user' }" @click="switchRegisterRole('user')">
            注册用户
          </button>
          <button type="button" class="tab-btn" :class="{ active: registerRole === 'merchant' }" @click="switchRegisterRole('merchant')">
            注册商家
          </button>
        </div>

        <div v-if="mode === 'login'" class="form-toggle segmented">
          <button type="button" class="tab-btn" :class="{ active: loginRole === 'user' }" @click="switchLoginRole('user')">
            用户登录
          </button>
            <button type="button" class="tab-btn" :class="{ active: loginRole === 'merchant' }" @click="switchLoginRole('merchant')">
            商家登录
          </button>
        </div>

        <div v-if="mode === 'register' && registerRole === 'user'" class="form-item">
          <label>昵称</label>
          <input v-model="nickname" type="text" placeholder="请输入昵称" required />
        </div>

        <div v-if="mode === 'register' && registerRole === 'merchant'" class="form-item">
          <label>商家登录账号</label>
          <input v-model="adminUsername" type="text" placeholder="用于登录的账号（唯一）" required />
        </div>

        <div v-if="mode === 'register' && registerRole === 'merchant'" class="form-item">
          <label>店铺名称</label>
          <input v-model="shopName" type="text" placeholder="请输入店铺名称" required />
        </div>

        <div v-if="mode === 'register' && registerRole === 'merchant'" class="form-item">
          <label>联系人</label>
          <input v-model="contactName" type="text" placeholder="请输入联系人姓名" required />
        </div>

        <div
          v-if="(mode === 'register' && registerRole === 'user') || (mode === 'login' && loginRole === 'user')"
          class="form-item"
        >
          <label>登录邮箱</label>
          <input v-model="userEmail" type="email" placeholder="如 test@example.com" required autocomplete="email" />
        </div>

        <div v-if="mode === 'register' && registerRole === 'user'" class="form-item">
          <label>手机号（可选）</label>
          <input v-model="userPhone" type="text" placeholder="可不填" maxlength="20" />
        </div>

        <div v-if="mode === 'register' && registerRole === 'merchant'" class="form-item">
          <label>联系邮箱（可选）</label>
          <input v-model="email" type="email" placeholder="店铺联系邮箱" />
        </div>

        <div v-if="mode === 'register' && registerRole === 'merchant'" class="form-item">
          <label>联系电话</label>
          <input v-model="phone" type="text" placeholder="店铺联系电话" required />
        </div>

        <div v-if="mode === 'login' && loginRole === 'merchant'" class="form-item">
          <label>商家账号</label>
          <input v-model="adminUsername" type="text" placeholder="请输入商家账号（如 merchant123）" required />
        </div>

        <div class="form-item">
          <label>密码</label>
          <input v-model="password" type="password" placeholder="请输入密码" required />
        </div>

        <div v-if="errorMsg" class="error-msg">{{ errorMsg }}</div>

        <button
          v-if="mode === 'login'"
          type="button"
          class="login-btn"
          @click="handleLogin"
        >
          登录
        </button>

        <button
          v-else
          type="button"
          class="login-btn"
          @click="handleRegister"
        >
          注册
        </button>

        <div v-if="mode === 'login'" class="switch-line">
          <span>还没有账号？</span>
          <button type="button" class="text-btn" @click="switchMode('register')">点击注册</button>
          <span class="divider">|</span>
          <button type="button" class="text-btn" @click="router.push('/admin-login')">管理员入口</button>
        </div>
        <div v-else class="switch-line">
          <button type="button" class="text-btn" @click="switchMode('login')">返回登录</button>
        </div>
      </form>
    </div>
  </div>
</template>

<style scoped>
/* 冷灰底 + 白卡片 + 深蓝主按钮（与用户参考稿一致） */
.login-page {
  --login-navy: #0a1128;
  --login-navy-hover: #121c38;
  --login-bg: #eef2f6;
  --login-card: #ffffff;
  --login-border: #e2e8f0;
  --login-placeholder: #94a3b8;
  --login-muted: #64748b;
  --login-link: #1e4b7a;
  --login-link-hover: #163454;
  --login-radius: 10px;

  min-height: calc(100vh - 180px);
  display: grid;
  grid-template-columns: 1.15fr 0.95fr;
  gap: 18px;
  align-items: stretch;
  padding: 18px 0;
  background: var(--login-bg);
  font-family:
    'Inter',
    'Microsoft YaHei',
    'PingFang SC',
    system-ui,
    -apple-system,
    sans-serif;
}

.hero-panel {
  border-radius: 16px;
  background: linear-gradient(120deg, rgba(5, 14, 30, 0.95), rgba(12, 36, 62, 0.78)),
    url('https://images.unsplash.com/photo-1518020382113-a7e8fc38eac9?auto=format&fit=crop&w=1400&q=80');
  background-size: cover;
  background-position: center;
  color: #e8eef8;
  padding: 60px 44px;
  border: 1px solid #0a1a32;
  box-shadow: 0 16px 32px rgba(9, 21, 40, 0.22);
}

.hero-tag {
  font-size: 12px;
  letter-spacing: 1.5px;
  color: #f0b85a;
  margin-bottom: 12px;
  font-weight: 700;
}

.hero-panel h2 {
  font-size: 48px;
  line-height: 1.08;
  margin-bottom: 16px;
  color: #eef3fb;
}

.hero-desc {
  color: #d8e4f2;
  font-size: 16px;
  line-height: 1.75;
  max-width: 480px;
  font-weight: 500;
}

.login-container {
  width: 100%;
  background: var(--login-card);
  border-radius: 12px;
  padding: 32px 28px;
  border: 1px solid var(--login-border);
  box-shadow: 0 4px 24px rgba(15, 23, 42, 0.06);
}

.login-header {
  margin-bottom: 22px;
}

.login-header h3 {
  font-size: 28px;
  color: var(--login-navy);
  font-weight: 800;
  margin-bottom: 8px;
  letter-spacing: -0.02em;
}

.login-header p {
  font-size: 14px;
  color: var(--login-muted);
  line-height: 1.55;
  font-weight: 500;
}

.login-form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.form-toggle {
  display: flex;
  gap: 8px;
}

.segmented {
  background: #f1f5f9;
  border: 1px solid var(--login-border);
  border-radius: var(--login-radius);
  padding: 4px;
  width: fit-content;
}

.tab-btn {
  min-width: 128px;
  height: 44px;
  padding: 0 16px;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 8px;
  cursor: pointer;
  font-size: 14px;
  color: var(--login-muted);
  font-weight: 600;
  transition: background 0.18s ease, color 0.18s ease;
}

.tab-btn.active {
  background: var(--login-navy);
  border-color: var(--login-navy);
  color: #fff;
  box-shadow: 0 2px 8px rgba(10, 17, 40, 0.2);
}

.tab-btn:not(.active):hover {
  background: #e8eef4;
  color: #475569;
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.form-item label {
  font-size: 13px;
  color: #475569;
  font-weight: 600;
  letter-spacing: 0.01em;
}

.form-item input {
  height: 50px;
  padding: 0 16px;
  border: 1px solid var(--login-border);
  border-radius: var(--login-radius);
  font-size: 15px;
  transition: border-color 0.2s, box-shadow 0.2s;
  background: #fff;
  color: #0f172a;
}

.form-item input:focus {
  outline: none;
  border-color: #94a3b8;
  box-shadow: 0 0 0 3px rgba(148, 163, 184, 0.25);
}

.form-item input::placeholder {
  color: var(--login-placeholder);
  font-size: 14px;
}

.error-msg {
  padding: 11px 13px;
  background: #fff1f1;
  border: 1px solid #f0c1c1;
  border-radius: 10px;
  color: #a73636;
  font-size: 14px;
  text-align: center;
}

.login-btn {
  width: 100%;
  min-height: 52px;
  padding: 14px 20px;
  background: var(--login-navy);
  color: #fff;
  border: 1px solid var(--login-navy);
  border-radius: var(--login-radius);
  font-size: 16px;
  cursor: pointer;
  transition: background 0.2s, border-color 0.2s;
  font-weight: 700;
  letter-spacing: 0.04em;
}

.login-btn:hover {
  background: var(--login-navy-hover);
  border-color: var(--login-navy-hover);
}

.switch-line {
  margin-top: 6px;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px 8px;
  color: var(--login-muted);
  font-size: 13px;
  font-weight: 400;
}

.divider {
  color: #cbd5e1;
  font-weight: 300;
  user-select: none;
}

.text-btn {
  border: none;
  background: transparent;
  color: var(--login-link);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  text-decoration: none;
  padding: 0;
}

.text-btn:hover {
  color: var(--login-link-hover);
}

@media (max-width: 980px) {
  .login-page {
    grid-template-columns: 1fr;
    padding: 14px 12px;
  }
  .hero-panel {
    min-height: 240px;
    padding: 34px 26px;
  }
}

.login-tips {
  text-align: center;
  padding-top: 16px;
  border-top: 1px solid #f0f0f0;
}

.login-tips p {
  font-size: 12px;
  color: #999;
  margin: 4px 0;
}
</style>
