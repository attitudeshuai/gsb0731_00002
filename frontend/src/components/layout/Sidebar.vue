<template>
  <aside class="sidebar">
    <div class="sidebar-tabs">
      <button
        :class="['sidebar-tab', { active: activePanel === 'connections' }]"
        @click="activePanel = 'connections'"
      >
        连接
      </button>
      <button
        :class="['sidebar-tab', { active: activePanel === 'saved' }]"
        @click="activePanel = 'saved'"
      >
        收藏
      </button>
      <button
        :class="['sidebar-tab', { active: activePanel === 'history' }]"
        @click="activePanel = 'history'"
      >
        历史
      </button>
    </div>

    <div v-show="activePanel === 'connections'" class="sidebar-panel">
      <div class="sidebar-toolbar">
        <input
          v-model="searchText"
          type="text"
          placeholder="搜索连接..."
          class="search-input"
        />
        <button class="btn btn-ghost btn-sm" title="新建连接" @click="uiStore.openNewConnection()">
          +
        </button>
      </div>
      <ConnectionTree :search="searchText" />
    </div>

    <div v-show="activePanel === 'saved'" class="sidebar-panel">
      <SavedQueryPanel />
    </div>

    <div v-show="activePanel === 'history'" class="sidebar-panel">
      <HistoryPanel />
    </div>
  </aside>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import ConnectionTree from './ConnectionTree.vue'
import SavedQueryPanel from '../saved/SavedQueryPanel.vue'
import HistoryPanel from '../history/HistoryPanel.vue'
import { useUiStore } from '@/stores/ui'

const uiStore = useUiStore()
const activePanel = ref<'connections' | 'saved' | 'history'>('connections')
const searchText = ref('')
</script>

<style scoped>
.sidebar {
  width: var(--sidebar-width);
  min-width: var(--sidebar-width);
  background: var(--bg-secondary);
  border-right: 1px solid var(--border-color);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.sidebar-tabs {
  display: flex;
  border-bottom: 1px solid var(--border-color);
}
.sidebar-tab {
  flex: 1;
  padding: 8px;
  font-size: 12px;
  color: var(--text-secondary);
  background: transparent;
  border-bottom: 2px solid transparent;
  transition: all 0.15s;
}
.sidebar-tab:hover { color: var(--text-primary); background: var(--bg-hover); }
.sidebar-tab.active {
  color: var(--accent);
  border-bottom-color: var(--accent);
  background: var(--bg-primary);
}
.sidebar-panel {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
.sidebar-toolbar {
  display: flex;
  gap: 6px;
  padding: 8px;
  border-bottom: 1px solid var(--border-color);
}
.search-input {
  flex: 1;
  font-size: 12px;
  padding: 4px 8px;
}
</style>
