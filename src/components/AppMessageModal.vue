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
.app-msg-overlay {
  position: fixed;
  inset: 0;
  z-index: 10060;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgba(15, 25, 42, 0.45);
  backdrop-filter: blur(2px);
}

.app-msg-panel {
  width: 100%;
  max-width: 420px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 20px 50px rgba(8, 20, 42, 0.18);
  border: 1px solid #e2e8f0;
  padding: 22px 22px 18px;
}

.app-msg-title {
  margin: 0 0 14px;
  font-size: 17px;
  font-weight: 800;
  color: #0f1f36;
  letter-spacing: 0.02em;
}

.app-msg-body {
  font-size: 14px;
  line-height: 1.65;
  color: #3d4d63;
  margin-bottom: 20px;
  white-space: pre-line;
}

.app-msg-actions {
  display: flex;
  justify-content: flex-end;
}

.app-msg-btn {
  min-height: 38px;
  padding: 0 22px;
  border-radius: 4px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  border: none;
  transition: background 0.15s;
}

.app-msg-btn--primary {
  background: #0b1630;
  color: #f4f6fb;
}

.app-msg-btn--primary:hover {
  background: #152a4d;
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
