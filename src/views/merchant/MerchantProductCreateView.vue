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

const categories = ref([])

const categoryOptgroups = computed(() => categorySelectOptgroups(categories.value))

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

  loading.value = false

  if (res.code === 200) {
    const newId = res.data?.productId || 0
    if (!newId) {
      showAppMessage('创建成功，但无法定位新商品')
      return
    }
    router.push(`/merchant/product/${newId}/edit`)
    return
  }

  showAppMessage(res.message || '创建失败')
}

function goBack() {
  router.back()
}

onMounted(loadCategories)
</script>

<template>
  <div class="page">
    <div class="header">
      <h2>上架新商品</h2>
      <p class="desc">先把商品信息创建出来，后续再到“编辑内容”完善规格参数与描述。</p>
    </div>

    <div v-if="loading" class="panel">创建中...</div>
    <div v-else-if="errorMsg" class="panel error">
      <div class="err-title">{{ errorMsg }}</div>
      <button class="primary" @click="loadCategories">重试</button>
    </div>

    <div v-else class="form">
      <div class="grid">
        <div class="left">
          <div class="form-group">
            <label>商品名称</label>
            <input v-model="title" class="input" placeholder="例如：皇家金毛幼犬粮 12kg" />
          </div>

          <div class="form-group">
            <label>类目</label>
            <select v-model.number="categoryId" class="input">
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

          <div class="form-group two">
            <div>
              <label>价格</label>
              <input v-model="price" class="input" placeholder="例如：458.00" />
            </div>
            <div>
              <label>库存</label>
              <input v-model="stock" class="input" placeholder="例如：120" />
            </div>
          </div>

          <div class="form-group">
            <label>描述（可选）</label>
            <textarea v-model="description" class="textarea" rows="6" placeholder="可先留空，之后再编辑规格参数"></textarea>
          </div>

          <div class="actions">
            <button class="ghost" type="button" @click="goBack">返回</button>
            <button class="primary" type="button" @click="create">创建并编辑内容</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page { width: 100%; }
.header { background: #08142a; padding: 24px; border-radius: 2px; margin-bottom: 16px; border: 1px solid #08142a; }
.header h2 { font-size: 34px; color: #e7eef9; margin-bottom: 8px; font-weight: 800; }
.desc { color: #b8c7dc; font-size: 13px; line-height: 1.8; }

.panel { background: #f9fbfe; border: 1px solid #dce4ef; border-radius: 2px; padding: 70px 40px; text-align: center; color: #6c7d93; }
.panel.error { background: #fff1f1; border-color: #f0c1c1; color: #a73636; }
.err-title { font-weight: 800; margin-bottom: 14px; }

.form { background: #f9fbfe; border: 1px solid #dce4ef; border-radius: 2px; padding: 16px; }
.grid { display: grid; grid-template-columns: 1fr 380px; gap: 16px; }
.left { padding-right: 8px; }
.form-group { margin-bottom: 16px; }
.form-group label { display: block; font-size: 13px; color: #5e6e84; font-weight: 900; margin-bottom: 8px; }

.input {
  width: 100%;
  border: 1px solid #d9e1ec;
  border-radius: 2px;
  padding: 10px;
  font-size: 13px;
  background: #fff;
}
.textarea {
  width: 100%;
  border: 1px solid #d9e1ec;
  border-radius: 2px;
  padding: 10px;
  font-size: 13px;
  background: #fff;
  resize: vertical;
}
.form-group.two {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.actions { display: flex; gap: 12px; justify-content: flex-end; }
.ghost, .primary { height: 36px; padding: 0 14px; border-radius: 2px; cursor: pointer; font-weight: 900; font-size: 13px; }
.ghost { border: 1px solid #cad6e6; background: #f4f8fd; color: #2b3d58; }
.primary { border: 1px solid #0b1630; background: #0b1630; color: #f4f6fb; }
</style>

