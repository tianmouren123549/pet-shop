<script setup>
const props = defineProps({
  count: { type: Number, default: 8 },
})
</script>

<template>
  <div class="app-skeleton-grid">
    <div v-for="idx in count" :key="`app-skeleton-${idx}`" class="app-skeleton-card">
      <div class="app-skeleton-image"></div>
      <div class="app-skeleton-body">
        <div class="app-skeleton-line app-skeleton-line--title"></div>
        <div class="app-skeleton-line"></div>
        <div class="app-skeleton-line app-skeleton-line--footer"></div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.app-skeleton-grid {
  display: contents;
}

.app-skeleton-card {
  pointer-events: none;
  background: linear-gradient(180deg, #ffffff 0%, #f8fbff 100%);
  border: 1px solid #dde5f0;
  border-radius: 14px;
  overflow: hidden;
}

.app-skeleton-image {
  height: 206px;
  background: linear-gradient(90deg, #edf2f8 25%, #dfe6f0 37%, #edf2f8 63%);
  background-size: 400% 100%;
  animation: shimmer 1.4s ease infinite;
}

.app-skeleton-body {
  padding: 14px;
}

.app-skeleton-line {
  height: 14px;
  border-radius: 10px;
  margin-bottom: 10px;
  background: linear-gradient(90deg, #edf2f8 25%, #dfe6f0 37%, #edf2f8 63%);
  background-size: 400% 100%;
  animation: shimmer 1.4s ease infinite;
}

.app-skeleton-line--title {
  height: 20px;
  width: 88%;
}

.app-skeleton-line--footer {
  width: 58%;
  margin-top: 14px;
}

@keyframes shimmer {
  0% { background-position: 100% 0; }
  100% { background-position: 0 0; }
}
</style>
