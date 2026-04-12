<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../utils/request'
import { showAppMessage } from '../utils/appMessage'

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
      showAppMessage('商家登录成功', '欢迎')
      router.push('/merchant')
      return
    }

    localStorage.setItem('role', 'USER')
    localStorage.setItem('userId', String(res.data.userId))
    localStorage.setItem('nickname', res.data.nickname || '')
    persistAccessToken(res.data)
    showAppMessage('登录成功', '欢迎')
    router.push('/')
  } else {
    errorMsg.value = res.message || '登录失败'
  }
}

async function handleRegister() {
  errorMsg.value = ''
  if (registerRole.value === 'merchant') {
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
.login-page {
  min-height: calc(100vh - 180px);
  display: grid;
  grid-template-columns: 1.15fr 0.95fr;
  gap: 14px;
  align-items: stretch;
  padding: 14px 0;
}

.hero-panel {
  border-radius: 4px;
  background: linear-gradient(120deg, rgba(5, 14, 30, 0.95), rgba(12, 36, 62, 0.78)),
    url('https://images.unsplash.com/photo-1518020382113-a7e8fc38eac9?auto=format&fit=crop&w=1400&q=80');
  background-size: cover;
  background-position: center;
  color: #e8eef8;
  padding: 52px 40px;
  border: 1px solid #0a1a32;
}

.hero-tag {
  font-size: 10px;
  letter-spacing: 1.4px;
  color: #e2a457;
  margin-bottom: 10px;
}

.hero-panel h2 {
  font-size: 42px;
  line-height: 1.08;
  margin-bottom: 14px;
  color: #eef3fb;
}

.hero-desc {
  color: #bcc9dc;
  font-size: 14px;
  line-height: 1.8;
  max-width: 450px;
}

.login-container {
  width: 100%;
  background: #f8fafe;
  border-radius: 4px;
  padding: 30px 28px;
  border: 1px solid #d8e2ee;
}

.login-header {
  margin-bottom: 20px;
}

.login-header h3 {
  font-size: 30px;
  color: #0f1f36;
  font-weight: 700;
  margin-bottom: 6px;
}

.login-header p {
  font-size: 12px;
  color: #68798f;
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
  background: #eef3f9;
  border: 1px solid #d4deea;
  border-radius: 4px;
  padding: 4px;
  width: fit-content;
}

.tab-btn {
  min-width: 110px;
  height: 34px;
  padding: 0 14px;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 2px;
  cursor: pointer;
  font-size: 12px;
  color: #566781;
  font-weight: 600;
  transition: all 0.18s ease;
}

.tab-btn.active {
  background: #0b1630;
  border-color: #0b1630;
  color: #f4f8ff;
  box-shadow: 0 2px 8px rgba(11, 22, 48, 0.24);
}

.tab-btn:not(.active):hover {
  background: #e5edf8;
  color: #30445f;
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.form-item label {
  font-size: 12px;
  color: #5d6e85;
  font-weight: 600;
  letter-spacing: 0.2px;
}

.form-item input {
  height: 38px;
  padding: 0 12px;
  border: 1px solid #cdd8e7;
  border-radius: 2px;
  font-size: 13px;
  transition: all 0.2s;
  background: #fff;
  color: #1a2940;
}

.form-item input:focus {
  outline: none;
  border-color: #173057;
  box-shadow: 0 0 0 2px rgba(14, 35, 68, 0.08);
}

.form-item input::placeholder {
  color: #bbb;
}

.error-msg {
  padding: 8px 12px;
  background: #fff1f1;
  border: 1px solid #f0c1c1;
  border-radius: 2px;
  color: #a73636;
  font-size: 12px;
  text-align: center;
}

.login-btn {
  height: 38px;
  background: #0b1630;
  color: #f2f7ff;
  border: 1px solid #0b1630;
  border-radius: 2px;
  font-size: 13px;
  cursor: pointer;
  transition: background 0.2s;
  font-weight: 700;
}

.login-btn:hover {
  background: #172d4f;
}

.switch-line {
  margin-top: 4px;
  display: flex;
  align-items: center;
  gap: 6px;
  color: #6b7b91;
  font-size: 12px;
}

.divider {
  color: #a1afc2;
}

.text-btn {
  border: none;
  background: transparent;
  color: #16355f;
  font-size: 12px;
  cursor: pointer;
  text-decoration: underline;
  padding: 0;
}

.text-btn:hover {
  color: #0b1630;
}

@media (max-width: 980px) {
  .login-page {
    grid-template-columns: 1fr;
  }
  .hero-panel {
    min-height: 240px;
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
