<script setup lang="ts">
import { dismiss, toastState } from '@/ui/toast'
</script>

<template>
  <Teleport to="body">
    <div class="toast-container">
      <TransitionGroup name="toast">
        <div
          v-for="t in toastState.items"
          :key="t.id"
          class="toast"
          :class="`toast-${t.type}`"
          @click="dismiss(t.id)"
        >
          {{ t.message }}
        </div>
      </TransitionGroup>
    </div>
  </Teleport>
</template>

<style scoped>
.toast-container {
  position: fixed;
  top: 56px;
  right: 16px;
  z-index: 4000;
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-width: 420px;
}

.toast {
  padding: 9px 14px;
  border-radius: 6px;
  font-size: 12.5px;
  color: var(--text-0);
  background: var(--bg-3);
  border: 1px solid var(--border);
  border-left-width: 3px;
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.45);
  cursor: pointer;
  word-break: break-all;
  line-height: 1.5;
}

.toast-success {
  border-left-color: var(--green);
}

.toast-error {
  border-left-color: var(--red);
}

.toast-info {
  border-left-color: var(--accent);
}

.toast-enter-active,
.toast-leave-active {
  transition: all 0.2s ease;
}

.toast-enter-from {
  opacity: 0;
  transform: translateX(24px);
}

.toast-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}
</style>
