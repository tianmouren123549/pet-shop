<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { api } from '../../utils/request'
import { formatAuditAdmin, formatAuditSummary, formatAuditTime } from '../../utils/adminAuditDisplay.js'

const loading = ref(false)
const errorMsg = ref('')
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const records = ref([])

const totalPages = computed(() => {
  const ps = Number(pageSize.value) || 20
  const t = Number(total.value) || 0
  return Math.max(1, Math.ceil(t / ps))
})

async function load() {
  loading.value = true
  errorMsg.value = ''
  const res = await api.adminAuditLogs(page.value, pageSize.value)
  loading.value = false
  if (res.code !== 200) {
    errorMsg.value = res.message || '加载失败'
    records.value = []
    return
  }
  const d = res.data || {}
  total.value = Number(d.total) || 0
  records.value = Array.isArray(d.records) ? d.records : []
}

onMounted(load)

watch([page, pageSize], () => {
  void load()
})

function prevPage() {
  if (page.value > 1) page.value -= 1
}

function nextPage() {
  if (page.value < totalPages.value) page.value += 1
}
</script>

<template>
  <div class="admin-page audit-page">
    <header class="audit-head">
      <div>
        <h1 class="audit-title">操作审计</h1>
        <p class="audit-lead">管理端关键操作留痕，便于安全审计与责任追溯。</p>
      </div>
      <div class="audit-head-meta">
        <span class="audit-count">共 {{ total }} 条</span>
        <button type="button" class="audit-refresh" :disabled="loading" @click="load">刷新</button>
      </div>
    </header>

    <div v-if="loading && !records.length" class="audit-state">加载中…</div>
    <div v-else-if="errorMsg" class="audit-state audit-state--error">{{ errorMsg }}</div>

    <div v-else class="audit-table-wrap">
      <table class="audit-table">
        <thead>
          <tr>
            <th>时间</th>
            <th>操作人</th>
            <th>操作说明</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in records" :key="row.logId">
            <td class="audit-td-time">{{ formatAuditTime(row.createdAt) }}</td>
            <td class="audit-td-admin">{{ formatAuditAdmin(row) }}</td>
            <td class="audit-td-summary"><span class="audit-summary">{{ formatAuditSummary(row) }}</span></td>
          </tr>
          <tr v-if="!records.length">
            <td colspan="3" class="audit-empty">暂无记录</td>
          </tr>
        </tbody>
      </table>
    </div>

    <footer v-if="records.length || total > 0" class="audit-pager">
      <label class="audit-pager-label">
        每页
        <select v-model.number="pageSize" class="audit-pager-select">
          <option :value="10">10</option>
          <option :value="20">20</option>
          <option :value="50">50</option>
        </select>
      </label>
      <div class="audit-pager-nav">
        <button type="button" class="audit-pager-btn" :disabled="page <= 1 || loading" @click="prevPage">上一页</button>
        <span class="audit-pager-info">{{ page }} / {{ totalPages }}</span>
        <button type="button" class="audit-pager-btn" :disabled="page >= totalPages || loading" @click="nextPage">
          下一页
        </button>
      </div>
    </footer>
  </div>
</template>

<style scoped>
.audit-page {
  max-width: 1120px;
  margin: 0 auto;
  padding: 0;
  color: #0f172a;
  color-scheme: light;
}
.audit-head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 1.25rem;
}
.audit-title {
  margin: 0;
  font-size: 1.35rem;
  font-weight: 700;
  letter-spacing: 0.02em;
  color: #0f1a2e;
}
.audit-lead {
  margin: 0.35rem 0 0;
  color: #475569;
  font-size: 0.92rem;
}
.audit-head-meta {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}
.audit-count {
  font-size: 0.9rem;
  font-weight: 600;
  color: #334155;
}
.audit-refresh {
  border: 1px solid #94a3b8;
  background: #fff;
  border-radius: 10px;
  padding: 0.45rem 0.9rem;
  cursor: pointer;
  font-size: 0.88rem;
  font-weight: 700;
  color: #0f172a;
}
.audit-refresh:hover:not(:disabled) {
  background: #f8fafc;
  border-color: #64748b;
}
.audit-refresh:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
.audit-state {
  padding: 2rem;
  text-align: center;
  color: #475569;
  font-weight: 600;
}
.audit-state--error {
  color: #b91c1c;
}
.audit-table-wrap {
  overflow: auto;
  border-radius: 14px;
  background: #fff;
}
.audit-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.audit-table th,
.audit-table td {
  padding: 0.65rem 0.75rem;
  border-bottom: 1px solid #e2e8f0;
  text-align: left;
  vertical-align: top;
}
.audit-table tbody td {
  color: #0f172a;
  font-weight: 500;
}
.audit-table th {
  font-weight: 700;
  color: #1e293b;
  background: linear-gradient(180deg, #f8fafc 0%, #f1f5f9 100%);
  white-space: nowrap;
  border-bottom: 1px solid #cbd5e1;
}
.audit-td-time {
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}
.audit-td-admin {
  white-space: nowrap;
  font-weight: 600;
  color: #334155;
}
.audit-td-summary {
  min-width: 280px;
}
.audit-summary {
  display: block;
  font-size: 13px;
  font-weight: 500;
  line-height: 1.55;
  color: #0f172a;
}
.audit-empty {
  text-align: center;
  color: #64748b;
  font-weight: 600;
  padding: 1.5rem !important;
}
.audit-pager {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  margin-top: 1rem;
}
.audit-pager-label {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  font-size: 0.88rem;
  font-weight: 600;
  color: #334155;
}
.audit-pager-select {
  border-radius: 10px;
  border: 1px solid #94a3b8;
  padding: 0.35rem 0.6rem;
  font-size: 13px;
  font-weight: 600;
  color: #0f172a;
  background: #fff;
}
.audit-pager-nav {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}
.audit-pager-btn {
  border: 1px solid #94a3b8;
  background: #fff;
  border-radius: 10px;
  padding: 0.45rem 0.85rem;
  cursor: pointer;
  font-size: 0.88rem;
  font-weight: 700;
  color: #0f172a;
}
.audit-pager-btn:hover:not(:disabled) {
  background: #f8fafc;
  border-color: #64748b;
}
.audit-pager-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.audit-pager-info {
  font-size: 0.88rem;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  color: #334155;
}
</style>
