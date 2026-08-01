<script setup lang="ts">
const emit = defineEmits<{
  (e: 'new-connection'): void
  (e: 'manage-groups'): void
  (e: 'open-drawer', which: 'history' | 'saved' | 'exports'): void
}>()
</script>

<template>
  <header class="top-toolbar">
    <div class="brand">
      <svg viewBox="0 0 16 16" width="16" height="16" fill="var(--accent)">
        <ellipse cx="8" cy="3.4" rx="6" ry="2.4" />
        <path d="M2 3.4v9.2c0 1.3 2.7 2.4 6 2.4s6-1.1 6-2.4V3.4c0 1.3-2.7 2.4-6 2.4s-6-1.1-6-2.4zm12 4.6c0 1.3-2.7 2.4-6 2.4s-6-1.1-6-2.4" />
      </svg>
      <span class="brand-name">DBManager</span>
    </div>

    <div class="toolbar-actions">
      <button class="btn btn-primary btn-sm" @click="emit('new-connection')">＋ 新建连接</button>
      <button class="btn btn-sm" @click="emit('manage-groups')">分组管理</button>
      <span class="toolbar-sep"></span>
      <button class="btn btn-sm" @click="emit('open-drawer', 'history')">历史</button>
      <button class="btn btn-sm" @click="emit('open-drawer', 'saved')">收藏</button>
      <button class="btn btn-sm" @click="emit('open-drawer', 'exports')">导出记录</button>
    </div>
  </header>
</template>

<style scoped>
.top-toolbar {
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 12px;
  background: var(--bg-2);
  border-bottom: 1px solid var(--border);
  flex-shrink: 0;
}

.brand {
  display: flex;
  align-items: center;
  gap: 8px;
}

.brand-name {
  font-weight: 700;
  font-size: 14px;
  color: var(--text-0);
  letter-spacing: 0.3px;
}

.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.toolbar-sep {
  width: 1px;
  height: 18px;
  background: var(--border);
  margin: 0 4px;
}
</style>
