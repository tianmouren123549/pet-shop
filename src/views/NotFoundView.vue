<script setup>
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <div class="not-found">
    <h2>页面不存在</h2>
    <p>你访问的页面路径无效，请返回首页继续浏览。</p>
    <button @click="router.push('/')">返回首页</button>
  </div>
</template>

<style scoped>
.not-found {
  background: #fff;
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  padding: 48px 24px;
  text-align: center;
}
button {
  margin-top: 12px;
  padding: 8px 16px;
  border: none;
  background: #1890ff;
  color: #fff;
  border-radius: 4px;
  cursor: pointer;
}
</style>
