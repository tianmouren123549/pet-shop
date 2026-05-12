<script setup>
import { computed, ref, watch } from 'vue'

const props = defineProps({
  src: { type: String, default: '' },
  alt: { type: String, default: '' },
  loading: { type: String, default: 'lazy' },
  decoding: { type: String, default: 'async' },
  fetchpriority: { type: String, default: undefined },
  class: { type: [String, Array, Object], default: '' },
  /**
   * 比例占位（如 "16 / 9"、"1 / 1"），用于减少图片加载时布局跳动。
   * 留空表示不启用占位。
   */
  aspectRatio: { type: String, default: '' },
  /** 开启比例占位时的背景色 */
  placeholderBg: { type: String, default: '#eef3fa' },
  fallbackSrc: {
    type: String,
    default:
      'data:image/svg+xml,' +
      encodeURIComponent(
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 320 220">' +
          '<rect width="320" height="220" fill="#dfe5ee"/>' +
          '<text x="50%" y="50%" dominant-baseline="middle" text-anchor="middle" fill="#7e8a9d" font-size="16">image unavailable</text>' +
          '</svg>',
      ),
  },
})

const currentSrc = ref(String(props.src || '').trim())
const failed = ref(false)

watch(
  () => props.src,
  (v) => {
    currentSrc.value = String(v || '').trim()
    failed.value = false
  },
)

const effectiveSrc = computed(() => {
  if (!currentSrc.value) return ''
  return failed.value ? props.fallbackSrc : currentSrc.value
})

const wrapperStyle = computed(() => {
  const ratio = String(props.aspectRatio || '').trim()
  if (!ratio) return null
  return {
    aspectRatio: ratio,
    width: '100%',
    background: props.placeholderBg,
    overflow: 'hidden',
  }
})

function onError() {
  if (failed.value) return
  failed.value = true
}
</script>

<template>
  <div v-if="effectiveSrc && wrapperStyle" :style="wrapperStyle">
    <img
      :src="effectiveSrc"
      :alt="alt"
      :class="props.class"
      :loading="loading"
      :decoding="decoding"
      :fetchpriority="fetchpriority"
      @error="onError"
    />
  </div>
  <img
    v-else-if="effectiveSrc"
    :src="effectiveSrc"
    :alt="alt"
    :class="props.class"
    :loading="loading"
    :decoding="decoding"
    :fetchpriority="fetchpriority"
    @error="onError"
  />
</template>
