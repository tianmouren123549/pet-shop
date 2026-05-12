<script setup>
import { appMessageState, hideAppMessage } from '../utils/appMessage'

function onBackdrop() {
  hideAppMessage()
}
</script>

<template>
  <Teleport to="body">
    <Transition name="app-msg-fade">
      <div
        v-if="appMessageState.open"
        class="app-msg-overlay"
        role="alertdialog"
        aria-modal="true"
        :aria-labelledby="'app-msg-title'"
        @click.self="onBackdrop"
      >
        <div class="app-msg-panel" @click.stop>
          <h3 id="app-msg-title" class="app-msg-title">{{ appMessageState.title }}</h3>
          <div class="app-msg-body">{{ appMessageState.body }}</div>
          <div class="app-msg-actions">
            <button type="button" class="app-msg-btn app-msg-btn--primary" @click="hideAppMessage">知道了</button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
/* 与登录页（LoginView）一致：冷灰轻遮罩、白卡片、深蓝主按钮，避免全暗弹窗 */
.app-msg-overlay {
  --msg-navy: #0a1128;
  --msg-navy-hover: #121c38;
  --msg-border: #e2e8f0;
  --msg-radius: 10px;

  position: fixed;
  inset: 0;
  z-index: 10060;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgba(15, 23, 42, 0.25);
  backdrop-filter: blur(6px);
}

.app-msg-panel {
  width: 100%;
  max-width: 400px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 4px 24px rgba(15, 23, 42, 0.08);
  border: 1px solid var(--msg-border);
  padding: 24px 24px 20px;
  font-family:
    'Inter',
    'Microsoft YaHei',
    'PingFang SC',
    system-ui,
    -apple-system,
    sans-serif;
}

.app-msg-title {
  margin: 0 0 10px;
  font-size: 18px;
  font-weight: 800;
  color: var(--msg-navy);
  letter-spacing: -0.02em;
}

.app-msg-body {
  font-size: 14px;
  line-height: 1.65;
  color: #64748b;
  margin-bottom: 20px;
  white-space: pre-line;
  font-weight: 500;
}

.app-msg-actions {
  display: flex;
  justify-content: flex-end;
}

.app-msg-btn {
  min-height: 44px;
  padding: 0 24px;
  border-radius: var(--msg-radius);
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 0.04em;
  cursor: pointer;
  border: 1px solid transparent;
  transition: background 0.15s, border-color 0.15s, color 0.15s;
}

.app-msg-btn--primary {
  background: var(--msg-navy);
  color: #fff;
  border: 1px solid var(--msg-navy);
}

.app-msg-btn--primary:hover {
  background: var(--msg-navy-hover);
  border-color: var(--msg-navy-hover);
}

.app-msg-btn--primary:focus-visible {
  outline: 2px solid #94a3b8;
  outline-offset: 2px;
}

.app-msg-fade-enter-active,
.app-msg-fade-leave-active {
  transition: opacity 0.2s ease;
}

.app-msg-fade-enter-active .app-msg-panel,
.app-msg-fade-leave-active .app-msg-panel {
  transition: transform 0.2s ease, opacity 0.2s ease;
}

.app-msg-fade-enter-from,
.app-msg-fade-leave-to {
  opacity: 0;
}

.app-msg-fade-enter-from .app-msg-panel,
.app-msg-fade-leave-to .app-msg-panel {
  transform: scale(0.96);
  opacity: 0;
}
</style>
