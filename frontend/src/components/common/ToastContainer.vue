<template>
  <div class="toast-container">
    <div
      v-for="toast in toastStore.toasts"
      :key="toast.id"
      :class="['toast', `toast-${toast.type}`]"
      @click="toastStore.remove(toast.id)"
    >
      {{ toast.message }}
    </div>
  </div>
</template>

<script setup lang="ts">
import { useToastStore } from '@/stores/toast'
const toastStore = useToastStore()
</script>
