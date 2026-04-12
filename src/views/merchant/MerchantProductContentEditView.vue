<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../../utils/request'
import { showAppMessage } from '../../utils/appMessage'

const route = useRoute()
const router = useRouter()
const productId = Number(route.params.id)
const merchantId = Number(localStorage.getItem('adminId') || 0)

const loading = ref(false)
const errorMsg = ref('')

const productTitle = ref('')
const price = ref('0')
const stock = ref('0')
const status = ref(1) // 1=上架,0=下架（沿用现有产品字段）
const savingBiz = ref(false)
const description = ref('')
/** 预览用：外链、已保存路径或本地选择的 data URL */
const imageUrlPreview = ref('')
const imageFile = ref(null) // File (for multipart upload)
const specEntries = ref([{ key: '', value: '' }])
const BASE_SPEC_TEMPLATE = {
  name: '基础模板',
  // 字段尽量少，方便不同商品也能套用；商家后续可继续“添加一行”补充。
  spec: {
    '适用对象/场景': '',
    '净含量/规格': '',
    '主要成分/材质': '',
    '执行标准': '',
    '原产地': '',
    '注意事项': '',
  },
}

async function load() {
  loading.value = true
  errorMsg.value = ''
  const res = await api.merchantGetProduct(merchantId, productId)
  if (res.code !== 200) {
    errorMsg.value = res.message || '商品加载失败'
    loading.value = false
    return
  }

  productTitle.value = res.data?.title || ''
  price.value = String(res.data?.price ?? '0')
  stock.value = String(res.data?.stock ?? '0')
  status.value = Number(res.data?.status ?? 1)
  description.value = res.data?.detail?.description || ''
  const specObj = res.data?.detail?.specJson || {}
  const entries = []
  try {
    const obj = typeof specObj === 'string' ? JSON.parse(specObj) : specObj
    if (obj && typeof obj === 'object' && !Array.isArray(obj)) {
      for (const [k, v] of Object.entries(obj)) {
        entries.push({
          key: String(k),
          value: v == null ? '' : String(v),
        })
      }
    }
  } catch (e) {
    // ignore
  }
  // 严格按后端返回的有多少字段就展示多少行；空规格时不额外补一行空输入。
  specEntries.value = entries
  imageUrlPreview.value = res.data?.detail?.imageUrl || ''
  imageFile.value = null
  loading.value = false
}

function parseNonNegativeNumber(v) {
  const n = Number(v)
  if (!Number.isFinite(n) || n < 0) return null
  return n
}

async function saveBusiness() {
  if (savingBiz.value || loading.value) return
  if (!merchantId) {
    showAppMessage('未获取到商家ID，请重新登录')
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
  savingBiz.value = true
  const res = await api.merchantUpdateProduct(merchantId, productId, {
    price: p,
    stock: Math.floor(s),
    status: Number(status.value) === 1 ? 1 : 0,
  })
  savingBiz.value = false
  if (res.code === 200) {
    showAppMessage('经营信息已保存')
    await load()
    return
  }
  showAppMessage(res.message || '保存失败')
}

function buildSpecObjectFromEntries() {
  const seen = new Set()
  const obj = {}

  for (const row of specEntries.value) {
    const key = String(row.key ?? '').trim()
    const value = row.value == null ? '' : String(row.value)

    if (!key) {
      // 忽略空行（但如果用户在空 key 上填了值，算输入错误）
      if (value.trim() !== '') return { invalid: true, message: '规格参数：键不能为空（请检查空键行）' }
      continue
    }
    if (seen.has(key)) return { invalid: true, message: `规格参数：存在重复键「${key}」` }

    seen.add(key)
    obj[key] = value
  }

  return { invalid: false, message: '', value: obj }
}

async function save() {
  if (loading.value) return
  const built = buildSpecObjectFromEntries()
  if (built.invalid) {
    showAppMessage(built.message || '规格参数不正确，请检查后重试')
    return
  }

  const res = await api.merchantUpdateProductContent(productId, {
    description: description.value,
    specJson: built.value,
    imageUrl: imageUrlPreview.value,
    imageFile: imageFile.value,
  })
  if (res.code === 200) {
    showAppMessage('保存成功')
    router.push('/merchant/products')
    return
  }
  showAppMessage(res.message || '保存失败')
}

function onImageFileChange(e) {
  const file = e.target.files?.[0]
  if (!file) return
  if (!file.type.startsWith('image/')) {
    showAppMessage('请选择图片文件')
    return
  }
  imageFile.value = file
  const reader = new FileReader()
  reader.onload = () => {
    imageUrlPreview.value = String(reader.result || '')
  }
  reader.readAsDataURL(file)
}

function applyRecommendedSpecTemplate() {
  const tpl = BASE_SPEC_TEMPLATE
  const existingMap = {}
  for (const row of specEntries.value) {
    const k = String(row.key ?? '').trim()
    if (!k) continue
    existingMap[k] = row.value == null ? '' : String(row.value)
  }

  const templateKeys = Object.keys(tpl.spec)

  // 套用时直接“替换为模板字段”，避免旧字段与模板字段同时存在造成“重复”感。
  specEntries.value = templateKeys.map((k) => ({
    key: k,
    value: typeof existingMap[k] === 'undefined' ? tpl.spec[k] : existingMap[k],
  }))
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="header">
      <h2>商品内容编辑</h2>
      <p class="desc">
        正在编辑：{{ productTitle || `商品ID ${productId}` }}；可编辑描述、规格参数，并上传主图（保存后由服务端存储并返回访问路径）。
      </p>
      <div v-if="productTitle" class="chips">
        <span class="chip">ID {{ productId }}</span>
        <span class="chip" :class="status === 1 ? 'ok' : 'off'">{{ status === 1 ? '上架中' : '已下架' }}</span>
        <span class="chip" :class="Number(stock) < 20 ? 'warn' : 'muted'">
          库存 {{ stock }}
        </span>
      </div>
    </div>

    <div v-if="loading" class="panel">加载中...</div>
    <div v-else-if="errorMsg" class="panel error">
      <div class="err-title">{{ errorMsg }}</div>
      <button class="primary" @click="load">重试</button>
    </div>

    <div v-else class="form">
      <div class="panel biz">
        <div class="panel-title">经营信息（补货 / 改价 / 上下架）</div>
        <div class="biz-grid">
          <div class="biz-item">
            <label>价格</label>
            <input v-model="price" class="input" placeholder="例如：458.00" />
          </div>
          <div class="biz-item">
            <label>库存</label>
            <input v-model="stock" class="input" placeholder="例如：120" />
            <div class="quick">
              <span v-if="Number(stock) < 20" class="warn">库存紧张</span>
            </div>
          </div>
          <div class="biz-item">
            <label>上架状态</label>
            <select v-model.number="status" class="input">
              <option :value="1">上架中</option>
              <option :value="0">已下架</option>
            </select>
          </div>
        </div>
        <div class="biz-actions">
          <button class="primary" type="button" :disabled="savingBiz" @click="saveBusiness">
            {{ savingBiz ? '保存中...' : '保存经营信息' }}
          </button>
        </div>
      </div>

      <div class="grid">
        <div class="left">
          <div class="form-group">
            <label>描述</label>
            <textarea v-model="description" rows="10" placeholder="请输入商品描述"></textarea>
          </div>

          <div class="form-group">
            <label>规格参数</label>
            <div class="spec-template-bar">
              <span class="spec-template-label">基础模板：</span>
              <span class="spec-template-name">{{ BASE_SPEC_TEMPLATE.name }}</span>
            </div>
            <button class="spec-template-btn" type="button" @click="applyRecommendedSpecTemplate">
              一键套用基础版
            </button>

            <div class="spec-editor">
              <div v-for="(row, idx) in specEntries" :key="idx" class="spec-editor-row">
                <input v-model="row.key" class="spec-key-input" placeholder="参数名" />
                <input v-model="row.value" class="spec-val-input" placeholder="参数值" />
                <button
                  v-if="specEntries.length > 1"
                  class="spec-del-btn"
                  type="button"
                  @click="specEntries.splice(idx, 1)"
                >
                  删除
                </button>
              </div>
              <button
                class="spec-add-btn"
                type="button"
                @click="specEntries.push({ key: '', value: '' })"
              >
                添加一行
              </button>
              <div class="spec-hint">请按行填写：每一行代表一项规格（参数名+参数值）。</div>
            </div>
          </div>

          <div class="actions">
            <button class="ghost" @click="$router.back()">返回</button>
            <button class="primary" @click="save">保存</button>
          </div>
        </div>

        <div class="right">
          <div class="preview-card">
            <div class="preview-title">图片预览</div>
            <div class="preview">
              <img v-if="imageUrlPreview" :src="imageUrlPreview" class="preview-img" />
              <div v-else class="placeholder">暂无图片</div>
            </div>

            <div class="upload">
              <input type="file" accept="image/*" @change="onImageFileChange" />
              <div class="hint">支持 png / jpg / webp；选择文件后先本地预览，保存时上传到服务器。</div>
            </div>
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
.chips { margin-top: 10px; display: flex; flex-wrap: wrap; gap: 8px; }
.chip {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 2px;
  font-size: 11px;
  font-weight: 900;
  border: 1px solid rgba(255,255,255,0.18);
  background: rgba(255,255,255,0.06);
  color: #e7eef9;
}
.chip.ok { border-color: rgba(82, 196, 26, 0.35); background: rgba(82, 196, 26, 0.18); color: #d9f4df; }
.chip.off { border-color: rgba(255, 77, 79, 0.35); background: rgba(255, 77, 79, 0.18); color: #ffd2d2; }
.chip.warn { border-color: rgba(229, 158, 63, 0.35); background: rgba(229, 158, 63, 0.18); color: #f3cf9f; }
.chip.muted { border-color: rgba(173, 198, 255, 0.22); background: rgba(173, 198, 255, 0.12); color: #d8e5ff; }

.panel { background: #f9fbfe; border: 1px solid #dce4ef; border-radius: 2px; padding: 70px 40px; text-align: center; color: #6c7d93; }
.panel.error { background: #fff1f1; border-color: #f0c1c1; color: #a73636; }
.err-title { font-weight: 800; margin-bottom: 14px; }

.form { background: #f9fbfe; border: 1px solid #dce4ef; border-radius: 2px; padding: 16px; }
.panel.biz {
  padding: 14px;
  text-align: left;
  margin-bottom: 16px;
  background: #f7f9fc;
  border: 1px solid #d9e1ec;
  color: #2a3b52;
}
.panel-title {
  font-weight: 900;
  color: #0e1d33;
  margin-bottom: 12px;
}
.biz-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(160px, 1fr));
  gap: 12px;
  align-items: start;
}
.biz-item {
  display: flex;
  flex-direction: column;
}
.biz-item label {
  display: block;
  font-size: 12px;
  color: #5e6e84;
  font-weight: 900;
  margin-bottom: 8px;
  line-height: 1;
  min-height: 12px;
}
.input {
  width: 100%;
  height: 32px;
  border: 1px solid #c9d4e4;
  border-radius: 2px;
  background: #fff;
  color: #243652;
  font-size: 12px;
  padding: 0 10px;
  box-sizing: border-box;
  line-height: 32px;
}
.input[type='number'],
.input[type='text'],
select.input {
  appearance: none;
}
.quick {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
  min-height: 20px;
}
.warn {
  margin-left: auto;
  padding: 2px 8px;
  border-radius: 2px;
  border: 1px solid #f0c1c1;
  background: #fff1f1;
  color: #a73636;
  font-size: 11px;
  font-weight: 900;
}
.biz-actions {
  margin-top: 10px;
  display: flex;
  justify-content: flex-end;
}
.grid { display: grid; grid-template-columns: 1fr 320px; gap: 16px; }
.form-group { margin-bottom: 16px; }
.form-group label { display: block; font-size: 13px; color: #5e6e84; font-weight: 800; margin-bottom: 8px; }
textarea { width: 100%; border: 1px solid #d9e1ec; border-radius: 2px; padding: 12px; font-size: 13px; background: #fff; resize: vertical; min-height: 150px; }
.actions { display: flex; gap: 12px; justify-content: flex-end; }
.ghost, .primary { height: 36px; padding: 0 14px; border-radius: 2px; cursor: pointer; font-weight: 900; font-size: 13px; }
.ghost { border: 1px solid #cad6e6; background: #f4f8fd; color: #2b3d58; }
.primary { border: 1px solid #0b1630; background: #0b1630; color: #f4f6fb; }

@media (max-width: 980px) {
  .biz-grid { grid-template-columns: repeat(2, minmax(160px, 1fr)); }
  .grid { grid-template-columns: 1fr; }
}

.preview-card { border: 1px solid #dce4ef; background: #fff; border-radius: 2px; padding: 14px; }
.preview-title { font-weight: 900; color: #0e1d33; margin-bottom: 10px; }
.preview { height: 220px; border: 1px dashed #d9e1ec; border-radius: 2px; display: flex; align-items: center; justify-content: center; overflow: hidden; background: #fbfcff; }
.preview-img { width: 100%; height: 100%; object-fit: cover; }
.placeholder { color: #9aa7bd; font-size: 13px; }
.upload { margin-top: 12px; }
.upload input { width: 100%; }
.hint { margin-top: 8px; font-size: 12px; color: #7b8aa3; line-height: 1.5; }

.spec-preview-list { display: flex; flex-direction: column; gap: 10px; }
.spec-preview-item { display: flex; gap: 10px; align-items: flex-start; }
.spec-preview-key { width: 120px; color: #5e6e84; font-size: 13px; font-weight: 800; flex-shrink: 0; }
.spec-preview-val { flex: 1; color: #26354b; font-size: 13px; white-space: pre-wrap; word-break: break-word; }

.spec-editor { margin-top: 6px; }
.spec-editor-row { display: flex; gap: 10px; align-items: center; margin-bottom: 10px; }
.spec-key-input { width: 180px; border: 1px solid #d9e1ec; border-radius: 2px; padding: 10px; font-size: 13px; background: #fff; }
.spec-val-input { flex: 1; border: 1px solid #d9e1ec; border-radius: 2px; padding: 10px; font-size: 13px; background: #fff; }
.spec-del-btn { border: 1px solid #cad6e6; background: #f4f8fd; color: #2b3d58; height: 36px; padding: 0 12px; border-radius: 2px; cursor: pointer; font-weight: 800; font-size: 13px; }
.spec-add-btn { border: 1px dashed #cad6e6; background: transparent; color: #2b3d58; height: 36px; padding: 0 14px; border-radius: 2px; cursor: pointer; font-weight: 900; font-size: 13px; }
.spec-hint { margin-top: 8px; font-size: 12px; color: #7b8aa3; line-height: 1.5; }

.spec-template-bar {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-bottom: 10px;
  padding: 8px 10px;
  border: 1px solid #d9e1ec;
  background: #fff;
  border-radius: 2px;
}
.spec-template-label { color: #5e6e84; font-weight: 900; font-size: 12px; }
.spec-template-name { color: #26354b; font-weight: 700; font-size: 12px; flex: 1; }
.spec-template-btn {
  height: 32px;
  padding: 0 12px;
  border: 1px solid #cad6e6;
  background: #f4f8fd;
  color: #2b3d58;
  border-radius: 2px;
  cursor: pointer;
  font-weight: 900;
  font-size: 12px;
}
</style>

