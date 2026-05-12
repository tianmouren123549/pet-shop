<script setup>
/**
 * 页面内确认弹层，替代浏览器原生 confirm（避免「localhost 显示」突兀样式）。
 */
const props = defineProps({
  /** 是否显示 */
  open: { type: Boolean, default: false },
  /** 标题 */
  title: { type: String, default: '请确认' },
  /** 主按钮文案 */
  confirmLabel: { type: String, default: '确定' },
  /** 取消按钮文案 */
  cancelLabel: { type: String, default: '取消' },
  /** 提交中禁用关闭与重复点击 */
  loading: { type: Boolean, default: false },
})

const emit = defineEmits(['update:open', 'confirm'])

function close() {
  emit('update:open', false)
}

function onBackdrop() {
  if (!props.loading) close()
}

function onCancel() {
  if (!props.loading) close()
}

function onConfirm() {
  emit('confirm')
}
</script>

<template>
  <Teleport to="body">
    <Transition name="confirm-modal-fade">
      <div v-if="open" class="confirm-modal-overlay" role="dialog" aria-modal="true" @click.self="onBackdrop">
        <div class="confirm-modal-panel" @click.stop>
          <h3 class="confirm-modal-title">{{ title }}</h3>
          <div class="confirm-modal-body">
            <slot />
          </div>
          <div class="confirm-modal-actions">
            <button type="button" class="confirm-modal-btn confirm-modal-btn--ghost" :disabled="loading" @click="onCancel">
              {{ cancelLabel }}
            </button>
            <button type="button" class="confirm-modal-btn confirm-modal-btn--primary" :disabled="loading" @click="onConfirm">
              {{ loading ? '提交中…' : confirmLabel }}
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.confirm-modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 10050;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgba(0, 0, 0, 0.42);
  backdrop-filter: blur(2px);
}

.confirm-modal-panel {
  width: 100%;
  max-width: 420px;
  background: #fff;
  border-radius: 2px;
  box-shadow: 0 18px 40px rgba(0, 0, 0, 0.14);
  border: 1px solid #e5e5e5;
  padding: 22px 22px 18px;
}

.confirm-modal-title {
  margin: 0 0 14px;
  font-size: 17px;
  font-weight: 800;
  color: #0a0a0a;
  letter-spacing: -0.02em;
}

.confirm-modal-body {
  font-size: 14px;
  line-height: 1.65;
  color: #525252;
  margin-bottom: 20px;
}

.confirm-modal-body :deep(p) {
  margin: 0 0 10px;
}

.confirm-modal-body :deep(p:last-child) {
  margin-bottom: 0;
}

.confirm-modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.confirm-modal-btn {
  min-height: 40px;
  padding: 0 18px;
  border-radius: 2px;
  font-size: 13px;
  font-weight: 800;
  cursor: pointer;
  border: 1px solid transparent;
  transition:
    opacity 0.15s,
    background 0.15s,
    border-color 0.15s;
}

.confirm-modal-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.confirm-modal-btn--ghost {
  background: #fff;
  color: #0a0a0a;
  border-color: #e5e5e5;
  font-weight: 700;
}

.confirm-modal-btn--ghost:hover:not(:disabled) {
  border-color: #0a0a0a;
}

.confirm-modal-btn--primary {
  background: #0a0a0a;
  color: #fff;
  border-color: #0a0a0a;
}

.confirm-modal-btn--primary:hover:not(:disabled) {
  background: #262626;
  border-color: #262626;
}

.confirm-modal-fade-enter-active,
.confirm-modal-fade-leave-active {
  transition: opacity 0.2s ease;
}

.confirm-modal-fade-enter-active .confirm-modal-panel,
.confirm-modal-fade-leave-active .confirm-modal-panel {
  transition: transform 0.2s ease, opacity 0.2s ease;
}

.confirm-modal-fade-enter-from,
.confirm-modal-fade-leave-to {
  opacity: 0;
}

.confirm-modal-fade-enter-from .confirm-modal-panel,
.confirm-modal-fade-leave-to .confirm-modal-panel {
  transform: scale(0.96);
  opacity: 0;
}
</style>
