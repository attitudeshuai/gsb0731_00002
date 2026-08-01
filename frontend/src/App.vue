<template>
  <div class="app-container">
    <AppHeader />
    <div class="app-body">
      <Sidebar />
      <main class="main-content">
        <router-view />
      </main>
    </div>
    <ConnectionDialog />
    <ToastContainer />
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import AppHeader from '@/components/layout/AppHeader.vue'
import Sidebar from '@/components/layout/Sidebar.vue'
import ConnectionDialog from '@/components/connection/ConnectionDialog.vue'
import ToastContainer from '@/components/common/ToastContainer.vue'
import { useConnectionStore } from '@/stores/connection'

const connectionStore = useConnectionStore()

onMounted(() => {
  connectionStore.loadAll()
})
</script>

<style scoped>
.app-container {
  display: flex;
  flex-direction: column;
  height: 100vh;
  width: 100vw;
  overflow: hidden;
}
.app-body {
  display: flex;
  flex: 1;
  overflow: hidden;
}
.main-content {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
</style>
