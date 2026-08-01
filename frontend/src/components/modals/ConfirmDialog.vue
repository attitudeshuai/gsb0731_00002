<script setup lang="ts">
import { confirmState, resolveConfirm } from '@/ui/confirm'
</script>

<template>
  <Teleport to="body">
    <div v-if="confirmState.visible" class="modal-overlay" style="z-index: 3000" @mousedown.self="resolveConfirm(false)">
      <div class="modal" style="width: 380px">
        <div class="modal-header">
          <span>{{ confirmState.title }}</span>
          <button class="modal-close" @click="resolveConfirm(false)">×</button>
        </div>
        <div class="modal-body">
          <p class="confirm-message">{{ confirmState.message }}</p>
        </div>
        <div class="modal-footer">
          <button class="btn btn-sm" @click="resolveConfirm(false)">取消</button>
          <button
            class="btn btn-sm"
            :class="confirmState.danger ? 'btn-danger' : 'btn-primary'"
            @click="resolveConfirm(true)"
          >
            {{ confirmState.okText }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.confirm-message {
  margin: 0;
  color: var(--text-0);
  font-size: 13px;
  line-height: 1.6;
  word-break: break-all;
}
</style>
