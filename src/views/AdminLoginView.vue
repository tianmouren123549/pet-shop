<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../utils/request'

const router = useRouter()
const username = ref('')
const password = ref('')
const errorMsg = ref('')

async function handleAdminLogin() {
  errorMsg.value = ''
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
    alert('管理员登录成功')
    router.push('/admin')
  } else {
    errorMsg.value = res.message || '登录失败'
  }
}
</script>

<template>
  <div class="admin-login-page">
    <div class="admin-login-card">
      <h2>管理员登录</h2>
      <p>此入口仅用于平台管理员（Mock 账号：admin123）</p>

      <div class="form-item">
        <label>管理员账号</label>
        <input v-model="username" type="text" placeholder="请输入管理员账号" />
      </div>
      <div class="form-item">
        <label>密码</label>
        <input v-model="password" type="password" placeholder="请输入密码" />
      </div>

      <div v-if="errorMsg" class="error-msg">{{ errorMsg }}</div>

      <button class="login-btn" @click="handleAdminLogin">登录</button>
      <button class="back-btn" @click="router.push('/login')">返回用户/商家登录</button>
    </div>
  </div>
</template>

<style scoped>
.admin-login-page {
  min-height: calc(100vh - 180px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 12px 0;
}
.admin-login-card {
  width: 100%;
  max-width: 460px;
  background: #f8fafe;
  border: 1px solid #d8e2ee;
  border-radius: 4px;
  padding: 26px;
}
h2 { font-size: 30px; color: #0f1f36; margin-bottom: 6px; }
p { font-size: 12px; color: #68798f; margin-bottom: 14px; }
.form-item { display: flex; flex-direction: column; gap: 8px; margin-bottom: 12px; }
label { font-size: 12px; color: #5d6e85; font-weight: 600; }
input {
  height: 38px; padding: 0 12px; border: 1px solid #cdd8e7; border-radius: 2px;
  font-size: 13px; background: #fff; color: #1a2940;
}
.error-msg {
  padding: 8px 12px; background: #fff1f1; border: 1px solid #f0c1c1;
  border-radius: 2px; color: #a73636; font-size: 12px; text-align: center; margin-bottom: 10px;
}
.login-btn, .back-btn {
  width: 100%; height: 38px; border-radius: 2px; font-size: 13px; cursor: pointer; font-weight: 700;
}
.login-btn {
  background: #0b1630; color: #f2f7ff; border: 1px solid #0b1630; margin-bottom: 8px;
}
.back-btn {
  background: #f4f8fd; color: #2b3d58; border: 1px solid #cdd8e7;
}
</style>
