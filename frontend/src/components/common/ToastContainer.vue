<template>
  <div class="toast-container">
    <transition-group name="toast">
      <div
        v-for="toast in toasts"
        :key="toast.id"
        class="toast-item"
        :class="`toast-${toast.type}`"
        @click="remove(toast.id)"
      >
        <span class="toast-icon">{{ iconMap[toast.type] }}</span>
        <span class="toast-message">{{ toast.message }}</span>
      </div>
    </transition-group>
  </div>
</template>

<script setup lang="ts">
import { useToast } from '@/composables/useToast'

const { toasts, remove } = useToast()

const iconMap = {
  success: '✓',
  error: '✕',
  warning: '⚠',
  info: 'ℹ'
}
</script>

<style scoped lang="scss">
@use '@/styles/index.scss' as *;

.toast-container {
  position: fixed;
  top: 20px;
  right: 20px;
  z-index: 10000;
  display: flex;
  flex-direction: column;
  gap: 10px;
  pointer-events: none;
}

.toast-item {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 280px;
  max-width: 480px;
  padding: 12px 18px;
  border-radius: $radius-md;
  background: #fff;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.15);
  font-size: 14px;
  color: $text-primary;
  pointer-events: auto;
  cursor: pointer;
  border-left: 4px solid transparent;
  transition: all 0.3s;
}

.toast-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  font-size: 13px;
  font-weight: 700;
  color: #fff;
  flex-shrink: 0;
}

.toast-success {
  border-left-color: $success-color;
  .toast-icon {
    background: $success-color;
  }
}

.toast-error {
  border-left-color: $error-color;
  .toast-icon {
    background: $error-color;
  }
}

.toast-warning {
  border-left-color: $warning-color;
  .toast-icon {
    background: $warning-color;
  }
}

.toast-info {
  border-left-color: $primary-color;
  .toast-icon {
    background: $primary-color;
  }
}

.toast-message {
  flex: 1;
  word-break: break-word;
}

.toast-enter-active,
.toast-leave-active {
  transition: all 0.3s ease;
}

.toast-enter-from {
  opacity: 0;
  transform: translateX(100%);
}

.toast-leave-to {
  opacity: 0;
  transform: translateX(100%);
}
</style>
