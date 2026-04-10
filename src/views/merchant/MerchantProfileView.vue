<script setup>
import { onMounted, ref } from 'vue'
import { api } from '../../utils/request'

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
  saving.value = true
  errorMsg.value = ''
  const res = await api.merchantUpdateProfile(mid, form.value)
  saving.value = false
  if (res.code !== 200) {
    errorMsg.value = res.message || '保存失败'
    return
  }
  localStorage.setItem('adminName', form.value.username)
  alert('保存成功')
}

onMounted(loadProfile)
</script>

<template>
  <div class="pw-page profile-page profile-page--merchant">
    <section class="pw-hero">
      <h1 class="pw-title">商家资料</h1>
      <p class="pw-lead">可修改头像、店铺基础信息与联系方式。</p>
    </section>

    <div v-if="loading" class="pw-state">加载中...</div>
    <div v-else-if="errorMsg" class="pw-state pw-state--error">{{ errorMsg }}</div>
    <section v-else class="pw-section profile-card">
      <div class="profile-layout">
        <aside class="profile-aside">
          <div class="profile-avatar-row">
            <img v-if="form.avatarUrl" :src="form.avatarUrl" class="profile-avatar" alt="avatar" />
            <div v-else class="profile-avatar profile-avatar--empty">店铺头像</div>
            <label class="profile-file">
              <span class="profile-file-btn">更换图片</span>
              <input type="file" accept="image/*" class="profile-file-input" @change="onAvatarChange" />
            </label>
          </div>
        </aside>

        <div class="profile-body">
          <div class="profile-form">
            <div class="profile-field">
              <label class="profile-label" for="mf-username">商家账号（只读）</label>
              <div class="profile-field-control">
                <input id="mf-username" v-model="form.username" class="pw-input pw-input--readonly profile-input" type="text" readonly />
              </div>
            </div>
            <div class="profile-field">
              <label class="profile-label" for="mf-shop">店铺名称</label>
              <div class="profile-field-control">
                <input id="mf-shop" v-model="form.shopName" class="pw-input profile-input" type="text" maxlength="40" />
              </div>
            </div>
            <div class="profile-field">
              <label class="profile-label" for="mf-contact">联系人</label>
              <div class="profile-field-control">
                <input id="mf-contact" v-model="form.contactName" class="pw-input profile-input" type="text" maxlength="30" />
              </div>
            </div>
            <div class="profile-field">
              <label class="profile-label" for="mf-phone">联系电话</label>
              <div class="profile-field-control">
                <input id="mf-phone" v-model="form.phone" class="pw-input profile-input" type="text" maxlength="20" />
              </div>
            </div>
            <div class="profile-field">
              <label class="profile-label" for="mf-email">联系邮箱</label>
              <div class="profile-field-control">
                <input id="mf-email" v-model="form.email" class="pw-input profile-input" type="email" maxlength="80" placeholder="可选" />
              </div>
            </div>
          </div>

          <div class="profile-actions">
            <button type="button" class="pw-btn profile-save-btn" :disabled="saving" @click="saveProfile">
              {{ saving ? '保存中...' : '保存资料' }}
            </button>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.profile-page {
  --profile-label-w: 112px;
  max-width: 1120px;
  padding-bottom: 28px;
}

.profile-page--merchant {
  --profile-label-w: 152px;
}

.profile-page .pw-hero {
  margin-bottom: 14px;
}

.profile-card {
  background: #f4f6f9;
  padding: 22px 20px 24px;
}

.profile-layout {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.profile-body {
  min-width: 0;
}

.profile-avatar-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.profile-avatar {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  object-fit: cover;
  border: 1px solid #dbe3ee;
  flex-shrink: 0;
}

.profile-avatar--empty {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #6b7b91;
  font-size: 13px;
  font-weight: 600;
  text-align: center;
  padding: 8px;
  background: #e8ecf2;
}

.profile-file {
  position: relative;
  display: inline-flex;
  align-items: center;
}

.profile-file-input {
  position: absolute;
  width: 0;
  height: 0;
  opacity: 0;
  overflow: hidden;
}

.profile-file-btn {
  display: inline-flex;
  align-items: center;
  min-height: 36px;
  padding: 0 12px;
  border-radius: 2px;
  border: 1px solid #cad4e1;
  background: #fcfdff;
  color: #506078;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: border-color 0.2s, color 0.2s;
  box-sizing: border-box;
}

.profile-file-btn:hover {
  border-color: #0b1630;
  color: #0b1630;
}

.profile-form {
  display: flex;
  flex-direction: column;
  gap: 0;
  border-top: 1px solid #dbe3ee;
  padding-top: 4px;
}

.profile-field {
  display: grid;
  grid-template-columns: var(--profile-label-w) minmax(0, 1fr);
  column-gap: 18px;
  align-items: center;
  padding: 14px 0;
  border-bottom: 1px solid #e8ecf0;
}

.profile-field:last-of-type {
  border-bottom: none;
  padding-bottom: 8px;
}

.profile-label {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  color: #3d4d63;
  text-align: right;
  line-height: 1.4;
  align-self: center;
}

.profile-label::after {
  content: '：';
}

.profile-field-control {
  min-width: 0;
  max-width: min(100%, 420px);
}

.profile-input {
  width: 100%;
  height: 40px;
  padding: 0 12px;
  font-size: 14px;
  line-height: 1.4;
  border-radius: 2px;
  box-sizing: border-box;
}

.pw-input--readonly {
  background: #e8ecf2;
  color: #506078;
  cursor: default;
}

.profile-actions {
  display: grid;
  grid-template-columns: var(--profile-label-w) minmax(0, 1fr);
  column-gap: 18px;
  align-items: center;
  margin-top: 6px;
  padding-top: 18px;
  border-top: 1px solid #dbe3ee;
}

.profile-save-btn {
  grid-column: 2;
  justify-self: start;
  min-height: 40px;
  height: 40px;
  padding: 0 22px;
  font-size: 14px;
}

@media (max-width: 640px) {
  .profile-field {
    grid-template-columns: 1fr;
    row-gap: 8px;
    align-items: stretch;
    padding: 14px 0;
  }

  .profile-label {
    text-align: left;
  }

  .profile-field-control {
    max-width: none;
  }

  .profile-actions {
    grid-template-columns: 1fr;
    padding-top: 18px;
  }

  .profile-save-btn {
    grid-column: 1;
    width: 100%;
    justify-self: stretch;
  }
}

@media (min-width: 768px) {
  .profile-layout {
    display: grid;
    grid-template-columns: minmax(180px, 240px) minmax(0, 1fr);
    gap: 24px 32px;
    align-items: start;
  }

  .profile-aside {
    padding: 4px 24px 8px 0;
    border-right: 1px solid #dbe3ee;
  }

  .profile-avatar-row {
    flex-direction: column;
    align-items: stretch;
    text-align: center;
    gap: 12px;
    max-width: 200px;
    margin: 0 auto;
  }

  .profile-avatar,
  .profile-avatar--empty {
    width: 96px;
    height: 96px;
    margin: 0 auto;
  }

  .profile-file {
    width: 100%;
    justify-content: center;
  }

  .profile-file-btn {
    width: 100%;
    justify-content: center;
  }
}
</style>
