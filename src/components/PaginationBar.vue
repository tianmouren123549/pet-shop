<script setup>
import { computed } from 'vue'

const props = defineProps({
  page: { type: Number, required: true },
  pageSize: { type: Number, required: true },
  total: { type: Number, required: true },
  pageSizeOptions: { type: Array, default: () => [] }, // number[]
  compact: { type: Boolean, default: false },
  /** 是否展示左侧「显示 x–y / 共 z」摘要；其它页默认开启 */
  showRangeMeta: { type: Boolean, default: true },
})

const emit = defineEmits(['update:page', 'update:pageSize'])

const totalPages = computed(() => {
  const t = Number(props.total || 0)
  const ps = Math.max(1, Number(props.pageSize || 1))
  return Math.max(1, Math.ceil(t / ps))
})

const safePage = computed(() => {
  const p = Math.floor(Number(props.page || 1))
  return Math.min(Math.max(1, p), totalPages.value)
})

const startIndex = computed(() => (safePage.value - 1) * props.pageSize + 1)
const endIndex = computed(() => Math.min(props.total, safePage.value * props.pageSize))

const pagesToShow = computed(() => {
  const tp = totalPages.value
  const p = safePage.value
  const span = props.compact ? 3 : 5
  const half = Math.floor(span / 2)
  let start = Math.max(1, p - half)
  let end = Math.min(tp, start + span - 1)
  start = Math.max(1, end - span + 1)
  const out = []
  for (let i = start; i <= end; i += 1) out.push(i)
  return out
})

function go(next) {
  const tp = totalPages.value
  const n = Math.min(Math.max(1, Number(next || 1)), tp)
  if (n === safePage.value) return
  emit('update:page', n)
}

function onPageSizeChange(e) {
  const v = Number(e?.target?.value || 0)
  if (!v) return
  emit('update:pageSize', v)
}
</script>

<template>
  <div
    v-if="total > 0"
    class="pw-pagination"
    :class="{ 'pw-pagination--compact': compact, 'pw-pagination--no-meta': !showRangeMeta }"
  >
    <div v-if="showRangeMeta" class="pw-pagination-left">
      <span class="pw-pagination-meta">
        <template v-if="totalPages > 1">显示 {{ startIndex }}-{{ endIndex }} / {{ total }}</template>
        <template v-else>共 {{ total }} 条</template>
      </span>
    </div>

    <div class="pw-pagination-right">
      <select
        v-if="(pageSizeOptions || []).length > 0"
        class="pw-pagination-size"
        :value="pageSize"
        @change="onPageSizeChange"
      >
        <option v-for="n in pageSizeOptions" :key="n" :value="n">{{ n }}/页</option>
      </select>

      <template v-if="totalPages > 1">
        <button type="button" class="pw-page-btn" :disabled="safePage <= 1" @click="go(1)">首页</button>
        <button type="button" class="pw-page-btn" :disabled="safePage <= 1" @click="go(safePage - 1)">上一页</button>

        <button
          v-for="p in pagesToShow"
          :key="p"
          type="button"
          class="pw-page-num"
          :class="{ active: p === safePage }"
          @click="go(p)"
        >
          {{ p }}
        </button>

        <button type="button" class="pw-page-btn" :disabled="safePage >= totalPages" @click="go(safePage + 1)">下一页</button>
        <button type="button" class="pw-page-btn" :disabled="safePage >= totalPages" @click="go(totalPages)">末页</button>
      </template>
    </div>
  </div>
</template>

