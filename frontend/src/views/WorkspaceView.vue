<template>
  <div class="workspace">
    <div class="workspace-tabs">
      <button
        :class="['ws-tab', { active: mode === 'query' }]"
        @click="mode = 'query'"
      >
        SQL 查询
      </button>
      <button
        v-if="workspaceStore.activeTableViewer"
        :class="['ws-tab', { active: mode === 'table' }]"
        @click="mode = 'table'"
      >
        📊 {{ workspaceStore.activeTableViewer.tableName }}
        <span class="ws-tab-close" @click.stop="closeTableViewer">×</span>
      </button>
    </div>

    <div class="workspace-content">
      <QueryWorkspace v-show="mode === 'query'" />
      <TableDataViewer
        v-if="mode === 'table' && workspaceStore.activeTableViewer"
        :key="workspaceStore.activeTableViewer.tableName + workspaceStore.activeTableViewer.connectionId"
        :connection-id="workspaceStore.activeTableViewer.connectionId"
        :database-name="workspaceStore.activeTableViewer.databaseName"
        :table-name="workspaceStore.activeTableViewer.tableName"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import QueryWorkspace from '@/components/editor/QueryWorkspace.vue'
import TableDataViewer from '@/components/table/TableDataViewer.vue'
import { useWorkspaceStore } from '@/stores/workspace'

const workspaceStore = useWorkspaceStore()
const mode = ref<'query' | 'table'>('query')

watch(() => workspaceStore.activeTableViewer, (tv) => {
  if (tv) mode.value = 'table'
}, { immediate: true })

function closeTableViewer() {
  workspaceStore.activeTableViewer = null
  mode.value = 'query'
}
</script>

<style scoped>
.workspace {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}
.workspace-tabs {
  display: flex;
  background: var(--bg-secondary);
  border-bottom: 1px solid var(--border-color);
  min-height: 34px;
}
.ws-tab {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 0 16px;
  height: 34px;
  font-size: 12px;
  color: var(--text-secondary);
  background: transparent;
  border-bottom: 2px solid transparent;
}
.ws-tab:hover { color: var(--text-primary); background: var(--bg-hover); }
.ws-tab.active {
  color: var(--accent);
  border-bottom-color: var(--accent);
  background: var(--bg-primary);
}
.ws-tab-close {
  font-size: 14px;
  margin-left: 4px;
}
.ws-tab-close:hover { color: var(--danger); }
.workspace-content {
  flex: 1;
  overflow: hidden;
}
</style>
