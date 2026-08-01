<script setup lang="ts">
import ToastContainer from '@/components/ToastContainer.vue'
import ConfirmDialog from '@/components/modals/ConfirmDialog.vue'
</script>

<template>
  <router-view />
  <ToastContainer />
  <ConfirmDialog />
</template>
