<template>
  <header class="app-header">
    <div class="header-left">
      <span class="logo">DB Manager</span>
      <span class="subtitle">数据库可视化管理工具</span>
    </div>
    <div class="header-right">
      <button class="btn btn-primary btn-sm" @click="uiStore.openNewConnection()">
        + 新建连接
      </button>
    </div>
  </header>
</template>

<script setup lang="ts">
import { useUiStore } from '@/stores/ui'
const uiStore = useUiStore()
</script>

<style scoped>
.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: var(--header-height);
  background: var(--bg-secondary);
  border-bottom: 1px solid var(--border-color);
  padding: 0 16px;
  flex-shrink: 0;
}
.header-left {
  display: flex;
  align-items: baseline;
  gap: 12px;
}
.logo {
  font-size: 16px;
  font-weight: 700;
  color: var(--accent);
}
.subtitle {
  font-size: 12px;
  color: var(--text-muted);
}
</style>
