<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../utils/request'
import { clearAuthLocalStorage, dispatchAuthUpdatedEvent } from '../utils/authStorage.js'
import { showAppMessage } from '../utils/appMessage'

const router = useRouter()
const username = ref('')
const password = ref('')
const errorMsg = ref('')

async function handleAdminLogin() {
  errorMsg.value = ''
  clearAuthLocalStorage()
  const res = await api.adminLogin({ username: username.value, password: password.value })
  if (res.code === 200) {
    const roleToStore = String(res.data?.role || 'ADMIN').toUpperCase()
    if (roleToStore === 'MERCHANT') {
      errorMsg.value = '该账号为商家账号，请使用用户/商家登录入口'
      return
    }
    localStorage.setItem('role', roleToStore)
    localStorage.setItem('adminId', String(res.data.adminId))
    localStorage.setItem('adminName', res.data.username || '')
    if (res.data?.token) localStorage.setItem('accessToken', res.data.token)
    dispatchAuthUpdatedEvent()
    showAppMessage('管理员登录成功', '欢迎')
    router.push('/admin')
  } else {
    errorMsg.value = res.message || '登录失败'
  }
}
</script>

<template>
  <div class="admin-login-page">
    <div class="admin-login-card">
      <div class="brand-mark">APEX PAWS ADMIN</div>
      <h2>管理员访问入口</h2>
      <p>此入口仅供平台管理员使用。请使用运营方分配的账号登录。</p>

      <div class="form-item">
        <label>管理员账号</label>
        <input v-model="username" type="text" placeholder="请输入管理员账号" />
      </div>
      <div class="form-item">
        <label>密码</label>
        <input v-model="password" type="password" placeholder="请输入密码" />
      </div>

      <div v-if="errorMsg" class="error-msg">{{ errorMsg }}</div>

      <div class="security-note">内部系统，登录行为将被记录用于安全审计。</div>
      <button class="login-btn" @click="handleAdminLogin">安全登录</button>
      <button class="back-btn" @click="router.push('/login')">返回用户/商家登录</button>
    </div>
  </div>
</template>

<style scoped>
.admin-login-page {
  --login-navy: #0a1128;
  --login-navy-hover: #121c38;
  --login-bg: #eef2f6;
  --login-border: #e2e8f0;
  --login-placeholder: #94a3b8;
  --login-muted: #64748b;
  --login-link: #1e4b7a;
  --login-radius: 10px;

  min-height: calc(100vh - 180px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px 0;
  background: var(--login-bg);
  font-family:
    'Inter',
    'Microsoft YaHei',
    'PingFang SC',
    system-ui,
    -apple-system,
    sans-serif;
}
.admin-login-card {
  width: 100%;
  max-width: 540px;
  background: #ffffff;
  border: 1px solid var(--login-border);
  border-radius: 12px;
  padding: 34px;
  box-shadow: 0 4px 24px rgba(15, 23, 42, 0.06);
}
.brand-mark {
  display: inline-block;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 1.15px;
  color: #182846;
  border: 1px solid #d9e2ee;
  border-radius: 999px;
  padding: 5px 13px;
  margin-bottom: 14px;
}
h2 { font-size: 28px; color: var(--login-navy); margin-bottom: 10px; font-weight: 800; letter-spacing: -0.02em; }
p { font-size: 14px; color: var(--login-muted); margin-bottom: 16px; line-height: 1.6; font-weight: 500; }
.form-item { display: flex; flex-direction: column; gap: 8px; margin-bottom: 14px; }
label { font-size: 13px; color: #475569; font-weight: 600; }
input {
  height: 50px;
  padding: 0 16px;
  border: 1px solid var(--login-border);
  border-radius: var(--login-radius);
  font-size: 15px;
  background: #fff;
  color: #0f172a;
  transition: border-color 0.2s, box-shadow 0.2s;
}
input:focus {
  outline: none;
  border-color: #94a3b8;
  box-shadow: 0 0 0 3px rgba(148, 163, 184, 0.25);
}
input::placeholder { color: var(--login-placeholder); font-size: 14px; }
.error-msg {
  padding: 11px 13px; background: #fff1f1; border: 1px solid #f0c1c1;
  border-radius: 10px; color: #a73636; font-size: 14px; text-align: center; margin-bottom: 10px;
}
.security-note {
  margin: 4px 0 12px;
  padding: 11px 13px;
  border: 1px solid #dce4ef;
  border-radius: 10px;
  background: #f7faff;
  color: #4a5a70;
  font-size: 14px;
  line-height: 1.5;
}
.login-btn, .back-btn {
  width: 100%;
  min-height: 52px;
  padding: 14px 20px;
  border-radius: var(--login-radius);
  font-size: 16px;
  cursor: pointer;
  font-weight: 700;
  letter-spacing: 0.04em;
}
.login-btn {
  background: var(--login-navy);
  color: #fff;
  border: 1px solid var(--login-navy);
  margin-bottom: 8px;
}
.login-btn:hover {
  background: var(--login-navy-hover);
  border-color: var(--login-navy-hover);
}
.back-btn {
  background: #f4f8fd; color: #2b3d58; border: 1px solid #cdd8e7;
}
</style>
