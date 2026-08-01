<template>
  <transition name="fade">
    <div v-if="visible" class="confirm-overlay" @click.self="onCancel">
      <div class="confirm-dialog" :class="`confirm-${type}`">
        <div class="confirm-header">
          <span class="confirm-icon">{{ iconMap[type] }}</span>
          <span class="confirm-title">{{ title }}</span>
        </div>
        <div class="confirm-body">{{ message }}</div>
        <div class="confirm-footer">
          <button class="btn btn-default" @click="onCancel">{{ cancelText }}</button>
          <button class="btn" :class="confirmBtnClass" @click="onConfirm">{{ confirmText }}</button>
        </div>
      </div>
    </div>
  </transition>
</template>

<script setup lang="ts">
import { computed } from 'vue'

type ConfirmType = 'danger' | 'warning' | 'info'

const props = withDefaults(
  defineProps<{
    visible: boolean
    title?: string
    message?: string
    confirmText?: string
    cancelText?: string
    type?: ConfirmType
  }>(),
  {
    title: '提示',
    message: '',
    confirmText: '确定',
    cancelText: '取消',
    type: 'info'
  }
)

const emit = defineEmits<{
  confirm: []
  cancel: []
}>()

const iconMap: Record<ConfirmType, string> = {
  danger: '🗑',
  warning: '⚠',
  info: 'ℹ'
}

const confirmBtnClass = computed(() => {
  if (props.type === 'danger') return 'btn-danger'
  if (props.type === 'warning') return 'btn-primary'
  return 'btn-primary'
})

function onConfirm() {
  emit('confirm')
}

function onCancel() {
  emit('cancel')
}
</script>

<style scoped lang="scss">
@use '@/styles/index.scss' as *;

.confirm-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9998;
}

.confirm-dialog {
  width: 420px;
  max-width: 90vw;
  background: #fff;
  border-radius: $radius-md;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.2);
  overflow: hidden;
}

.confirm-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 18px 20px 12px;
  font-size: 16px;
  font-weight: 600;
}

.confirm-icon {
  font-size: 20px;
}

.confirm-body {
  padding: 0 20px 20px;
  color: $text-secondary;
  font-size: 14px;
  line-height: 1.6;
}

.confirm-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 12px 20px;
  border-top: 1px solid $border-color;
  background: #fafafa;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
