<template>
  <div class="saved-panel">
    <div class="panel-toolbar">
      <button class="btn btn-ghost btn-sm" @click="load">刷新</button>
      <button class="btn btn-secondary btn-sm" @click="showFolderInput = !showFolderInput">
        + 文件夹
      </button>
    </div>
    <div v-if="showFolderInput" class="folder-input-row">
      <input v-model="newFolderName" placeholder="文件夹名称" @keyup.enter="createFolder" />
      <button class="btn btn-primary btn-sm" @click="createFolder">确定</button>
    </div>
    <div class="saved-list">
      <div
        v-for="item in savedQueries"
        :key="item.id"
        class="saved-item"
        @click="openSaved(item)"
      >
        <span class="saved-icon">★</span>
        <div class="saved-info">
          <div class="saved-title">{{ item.title }}</div>
          <div class="saved-sql">{{ item.sqlText.substring(0, 60) }}{{ item.sqlText.length > 60 ? '...' : '' }}</div>
        </div>
        <button class="btn btn-ghost btn-sm saved-delete" @click.stop="deleteSaved(item.id)">×</button>
      </div>
      <div v-if="savedQueries.length === 0" class="empty-list">暂无收藏查询</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { savedQueryApi } from '@/api'
import { useWorkspaceStore } from '@/stores/workspace'
import { useToastStore } from '@/stores/toast'
import type { SavedQuery } from '@/types'

const workspaceStore = useWorkspaceStore()
const toastStore = useToastStore()

const savedQueries = ref<SavedQuery[]>([])
const showFolderInput = ref(false)
const newFolderName = ref('')

async function load() {
  try {
    savedQueries.value = await savedQueryApi.list()
  } catch (e: any) {
    toastStore.error(e.message)
  }
}

function openSaved(item: SavedQuery) {
  workspaceStore.createQueryTab(
    item.connectionId || undefined,
    item.databaseName || undefined,
    item.sqlText,
    item.title
  )
}

async function deleteSaved(id: number) {
  if (!confirm('确定删除该收藏？')) return
  try {
    await savedQueryApi.delete(id)
    toastStore.success('已删除')
    load()
  } catch (e: any) {
    toastStore.error(e.message)
  }
}

async function createFolder() {
  if (!newFolderName.value.trim()) return
  try {
    await savedQueryApi.createFolder({ name: newFolderName.value })
    newFolderName.value = ''
    showFolderInput.value = false
    toastStore.success('文件夹已创建')
  } catch (e: any) {
    toastStore.error(e.message)
  }
}

onMounted(load)
</script>

<style scoped>
.saved-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
}
.panel-toolbar {
  display: flex;
  gap: 6px;
  padding: 8px;
  border-bottom: 1px solid var(--border-color);
}
.folder-input-row {
  display: flex;
  gap: 6px;
  padding: 6px 8px;
  border-bottom: 1px solid var(--border-color);
}
.folder-input-row input { flex: 1; font-size: 12px; padding: 3px 6px; }
.saved-list { flex: 1; overflow-y: auto; }
.saved-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  cursor: pointer;
  border-bottom: 1px solid rgba(58,58,82,0.3);
}
.saved-item:hover { background: var(--bg-hover); }
.saved-icon { color: var(--warning); }
.saved-info { flex: 1; overflow: hidden; }
.saved-title { font-size: 13px; }
.saved-sql {
  font-size: 11px;
  color: var(--text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  font-family: var(--font-mono);
}
.saved-delete { opacity: 0; color: var(--danger); }
.saved-item:hover .saved-delete { opacity: 1; }
.empty-list {
  padding: 20px;
  text-align: center;
  color: var(--text-muted);
  font-size: 12px;
}
</style>
