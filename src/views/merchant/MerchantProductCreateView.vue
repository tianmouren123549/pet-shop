<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../../utils/request'
import { showAppMessage } from '../../utils/appMessage'
import { categorySelectOptgroups, firstSelectableCategoryId } from '../../utils/categoryNav'

const router = useRouter()
const merchantId = ref(Number(localStorage.getItem('adminId') || 0))

const loading = ref(false)
const errorMsg = ref('')

const title = ref('')
const categoryId = ref(null)
const price = ref('0')
const stock = ref('0')
const description = ref('')

/** 主图：本地预览 / 外链 */
const imageUrlPreview = ref('')
const imageFile = ref(null)
const externalImageUrl = ref('')

const categories = ref([])

const categoryOptgroups = computed(() => categorySelectOptgroups(categories.value))

const hasImageToSave = computed(() => {
  if (imageFile.value) return true
  const ext = String(externalImageUrl.value || '').trim()
  return /^https?:\/\//i.test(ext)
})

async function loadCategories() {
  const res = await api.getCategories()
  if (res.code === 200) {
    categories.value = res.data || []
    const og = categorySelectOptgroups(categories.value)
    if (og.length && (categoryId.value == null || categoryId.value === '')) {
      categoryId.value = firstSelectableCategoryId(og)
    }
  }
}

function parseNonNegativeNumber(v) {
  const n = Number(v)
  if (!Number.isFinite(n) || n < 0) return null
  return n
}

function onImageFileChange(e) {
  const file = e.target.files?.[0]
  if (!file) return
  if (!file.type.startsWith('image/')) {
    showAppMessage('请选择图片文件')
    return
  }
  imageFile.value = file
  externalImageUrl.value = ''
  const reader = new FileReader()
  reader.onload = () => {
    imageUrlPreview.value = String(reader.result || '')
  }
  reader.readAsDataURL(file)
}

function clearMainImage() {
  imageFile.value = null
  externalImageUrl.value = ''
  imageUrlPreview.value = ''
}

function applyExternalImageUrl() {
  const u = String(externalImageUrl.value || '').trim()
  if (!u) {
    if (!imageFile.value) imageUrlPreview.value = ''
    return
  }
  if (!/^https?:\/\//i.test(u)) {
    showAppMessage('外链请以 http:// 或 https:// 开头')
    return
  }
  imageFile.value = null
  imageUrlPreview.value = u
}

async function create() {
  const t = title.value.trim()
  if (!t) {
    showAppMessage('商品名称不能为空')
    return
  }
  const cid = Number(categoryId.value || 0)
  if (!cid) {
    showAppMessage('请选择类目')
    return
  }

  const p = parseNonNegativeNumber(price.value)
  if (p === null) {
    showAppMessage('价格必须为非负数')
    return
  }
  const s = parseNonNegativeNumber(stock.value)
  if (s === null) {
    showAppMessage('库存必须为非负数')
    return
  }

  loading.value = true
  errorMsg.value = ''
  const res = await api.merchantCreateProduct({
    merchantId: merchantId.value,
    title: t,
    categoryId: cid,
    price: p,
    stock: Math.floor(s),
    description: description.value,
  })

  if (res.code !== 200) {
    loading.value = false
    showAppMessage(res.message || '创建失败')
    return
  }

  const newId = res.data?.productId || 0
  if (!newId) {
    loading.value = false
    showAppMessage('创建成功，但无法定位新商品')
    return
  }

  if (hasImageToSave.value) {
    const ext = String(externalImageUrl.value || '').trim()
    const payload = {
      description: description.value,
      specJson: {},
    }
    if (imageFile.value) {
      payload.imageFile = imageFile.value
      if (ext && !ext.startsWith('data:')) payload.imageUrl = ext
    } else if (ext && /^https?:\/\//i.test(ext)) {
      payload.imageUrl = ext
    }

    const cRes = await api.merchantUpdateProductContent(newId, payload)
    if (cRes.code !== 200) {
      showAppMessage(cRes.message || '商品已创建，但主图保存失败，请在下一步补充')
    }
  }

  loading.value = false
  router.push(`/merchant/product/${newId}/edit`)
}

function goBack() {
  router.back()
}

onMounted(loadCategories)
</script>

<template>
  <div class="mpc-page">
    <header class="mpc-hero">
      <div class="mpc-hero-text">
        <h1 class="mpc-title">上架新商品</h1>
        <p class="mpc-sub">填写基础信息与主图；提交后可继续在「编辑内容」里补充规格参数与描述。</p>
      </div>
    </header>

    <div v-if="loading" class="mpc-panel mpc-panel--muted">创建中…</div>
    <div v-else-if="errorMsg" class="mpc-panel mpc-panel--error">
      <div class="mpc-err-title">{{ errorMsg }}</div>
      <button class="mpc-btn mpc-btn--primary" type="button" @click="loadCategories">重试</button>
    </div>

    <div v-else class="mpc-card">
      <div class="mpc-grid">
        <div class="mpc-main">
          <section class="mpc-section">
            <h2 class="mpc-section-title">基础信息</h2>
            <div class="mpc-field">
              <label for="mpc-title">商品名称</label>
              <input
                id="mpc-title"
                v-model="title"
                class="mpc-input"
                type="text"
                autocomplete="off"
                placeholder="例如：皇家金毛幼犬粮 12kg"
              />
            </div>

            <div class="mpc-field">
              <label for="mpc-cat">类目</label>
              <select id="mpc-cat" v-model.number="categoryId" class="mpc-input">
                <optgroup v-for="g in categoryOptgroups" :key="g.groupLabel" :label="g.groupLabel">
                  <option
                    v-for="o in g.options"
                    :key="`${g.groupLabel}-${o.categoryId}`"
                    :value="o.categoryId"
                  >
                    {{ o.label }}
                  </option>
                </optgroup>
              </select>
            </div>

            <div class="mpc-row2">
              <div class="mpc-field">
                <label for="mpc-price">价格（元）</label>
                <input id="mpc-price" v-model="price" class="mpc-input" inputmode="decimal" placeholder="例如：458" />
              </div>
              <div class="mpc-field">
                <label for="mpc-stock">库存</label>
                <input id="mpc-stock" v-model="stock" class="mpc-input" inputmode="numeric" placeholder="例如：120" />
              </div>
            </div>
          </section>

          <section class="mpc-section">
            <h2 class="mpc-section-title">描述（可选）</h2>
            <div class="mpc-field">
              <label for="mpc-desc">商品描述</label>
              <textarea
                id="mpc-desc"
                v-model="description"
                class="mpc-textarea"
                rows="7"
                placeholder="卖点、成分、适用对象等；也可先留空，创建后在编辑页完善。"
              />
            </div>
          </section>

          <div class="mpc-actions">
            <button class="mpc-btn mpc-btn--ghost" type="button" @click="goBack">返回</button>
            <button class="mpc-btn mpc-btn--primary" type="button" @click="create">创建商品</button>
          </div>
        </div>

        <aside class="mpc-aside">
          <div class="mpc-preview-card">
            <h2 class="mpc-section-title mpc-section-title--compact">主图</h2>
            <p class="mpc-aside-hint">列表与详情页展示用；支持本地上传或图片外链。</p>
            <div class="mpc-preview">
              <img
                v-if="imageUrlPreview"
                :src="imageUrlPreview"
                class="mpc-preview-img"
                alt="主图预览"
                loading="lazy"
                decoding="async"
              />
              <div v-else class="mpc-preview-placeholder">暂无主图</div>
            </div>

            <div class="mpc-field mpc-field--tight">
              <label for="mpc-file">上传图片</label>
              <input id="mpc-file" type="file" accept="image/png,image/jpeg,image/webp,image/gif" @change="onImageFileChange" />
              <p class="mpc-micro">推荐正方形或 4:3；保存时随商品一并提交。</p>
            </div>

            <div class="mpc-field mpc-field--tight">
              <label for="mpc-url">或填写图片外链</label>
              <input
                id="mpc-url"
                v-model.trim="externalImageUrl"
                class="mpc-input"
                type="url"
                autocomplete="off"
                placeholder="https://…"
                @blur="applyExternalImageUrl"
              />
              <div class="mpc-inline-actions">
                <button type="button" class="mpc-linkish" @click="applyExternalImageUrl">应用外链预览</button>
                <button v-if="imageUrlPreview || imageFile" type="button" class="mpc-linkish mpc-linkish--danger" @click="clearMainImage">
                  清除主图
                </button>
              </div>
            </div>
          </div>
        </aside>
      </div>
    </div>
  </div>
</template>

<style scoped>
.mpc-page {
  width: 100%;
  max-width: 1100px;
  margin: 0 auto;
}

.mpc-hero {
  background: linear-gradient(135deg, #0a162e 0%, #132a52 100%);
  border: 1px solid #0a162e;
  border-radius: 10px;
  padding: 22px 24px;
  margin-bottom: 18px;
  color: #e8eef9;
}
.mpc-title {
  margin: 0 0 8px;
  font-size: 22px;
  font-weight: 800;
  letter-spacing: 0.02em;
}
.mpc-sub {
  margin: 0;
  font-size: 13px;
  line-height: 1.55;
  color: rgba(232, 238, 249, 0.78);
  max-width: 640px;
}

.mpc-panel {
  border-radius: 10px;
  padding: 48px 24px;
  text-align: center;
  font-size: 14px;
}
.mpc-panel--muted {
  background: #f4f7fc;
  border: 1px solid #dce4ef;
  color: #5e6e84;
}
.mpc-panel--error {
  background: #fff5f5;
  border: 1px solid #f0c4c4;
  color: #a73636;
}
.mpc-err-title {
  font-weight: 800;
  margin-bottom: 14px;
}

.mpc-card {
  background: #fafbfd;
  border: 1px solid #dce4ef;
  border-radius: 10px;
  padding: 20px 22px 24px;
}

.mpc-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 22px;
  align-items: start;
}

.mpc-section {
  margin-bottom: 22px;
}
.mpc-section-title {
  margin: 0 0 14px;
  font-size: 15px;
  font-weight: 800;
  color: #0e1d33;
}
.mpc-section-title--compact {
  margin-bottom: 8px;
}

.mpc-field {
  margin-bottom: 16px;
}
.mpc-field--tight {
  margin-bottom: 12px;
}
.mpc-field label {
  display: block;
  font-size: 12px;
  font-weight: 800;
  color: #5e6e84;
  margin-bottom: 8px;
}

.mpc-input,
.mpc-textarea {
  width: 100%;
  box-sizing: border-box;
  border: 1px solid #c9d4e4;
  border-radius: 8px;
  background: #fff;
  color: #1a2740;
  font-size: 14px;
}
.mpc-input {
  height: 40px;
  padding: 0 12px;
}
.mpc-textarea {
  padding: 12px;
  resize: vertical;
  min-height: 120px;
  line-height: 1.5;
}
.mpc-field input[type='file'] {
  width: 100%;
  font-size: 13px;
}

.mpc-row2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}

.mpc-aside-hint {
  margin: 0 0 12px;
  font-size: 12px;
  line-height: 1.5;
  color: #6b7c93;
}

.mpc-preview-card {
  background: #fff;
  border: 1px solid #dce4ef;
  border-radius: 10px;
  padding: 16px;
  position: sticky;
  top: 12px;
}
.mpc-preview {
  height: 200px;
  border-radius: 8px;
  border: 1px dashed #c5d0e3;
  background: #f8fafc;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  margin-bottom: 4px;
}
.mpc-preview-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.mpc-preview-placeholder {
  font-size: 13px;
  color: #8b9ab3;
}
.mpc-micro {
  margin: 6px 0 0;
  font-size: 11px;
  color: #8b9ab3;
  line-height: 1.45;
}

.mpc-inline-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 8px;
}
.mpc-linkish {
  border: none;
  background: none;
  padding: 0;
  font-size: 12px;
  font-weight: 700;
  color: #1d4ed8;
  cursor: pointer;
  text-decoration: underline;
  text-underline-offset: 2px;
}
.mpc-linkish--danger {
  color: #b42318;
}

.mpc-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding-top: 4px;
}

.mpc-btn {
  height: 40px;
  padding: 0 18px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 800;
  cursor: pointer;
  border: 1px solid transparent;
}
.mpc-btn--ghost {
  background: #fff;
  border-color: #c9d4e4;
  color: #243652;
}
.mpc-btn--primary {
  background: #0b1630;
  border-color: #0b1630;
  color: #f4f6fb;
}

@media (max-width: 900px) {
  .mpc-grid {
    grid-template-columns: 1fr;
  }
  .mpc-preview-card {
    position: static;
  }
}
</style>
