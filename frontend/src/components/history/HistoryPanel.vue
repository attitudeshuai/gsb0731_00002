<template>
  <div class="history-panel">
    <div class="panel-toolbar">
      <button class="btn btn-ghost btn-sm" @click="load">刷新</button>
      <button class="btn btn-danger btn-sm" @click="clearAll">清空</button>
    </div>
    <div class="history-list">
      <div
        v-for="item in history"
        :key="item.id"
        class="history-item"
        :class="{ error: item.status === 'error' }"
        @click="openHistory(item)"
      >
        <div class="history-meta">
          <span :class="['status-dot', item.status]"></span>
          <span class="history-time">{{ formatTime(item.executedAt) }}</span>
          <span class="history-elapsed">{{ item.elapsedMs }}ms</span>
        </div>
        <div class="history-sql">{{ item.sqlText.substring(0, 100) }}{{ item.sqlText.length > 100 ? '...' : '' }}</div>
        <div v-if="item.errorMessage" class="history-error">{{ item.errorMessage.substring(0, 80) }}</div>
      </div>
      <div v-if="history.length === 0" class="empty-list">暂无查询历史</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { historyApi } from '@/api'
import { useWorkspaceStore } from '@/stores/workspace'
import { useToastStore } from '@/stores/toast'
import type { QueryHistoryItem } from '@/types'

const workspaceStore = useWorkspaceStore()
const toastStore = useToastStore()

const history = ref<QueryHistoryItem[]>([])

async function load() {
  try {
    const page = await historyApi.list({ page: 1, size: 100 })
    history.value = page.content || []
  } catch (e: any) {
    toastStore.error(e.message)
  }
}

function openHistory(item: QueryHistoryItem) {
  workspaceStore.createQueryTab(
    item.connectionId || undefined,
    item.databaseName || undefined,
    item.sqlText,
    '历史查询'
  )
}

async function clearAll() {
  if (!confirm('确定清空所有查询历史？')) return
  try {
    await historyApi.clear()
    toastStore.success('已清空')
    load()
  } catch (e: any) {
    toastStore.error(e.message)
  }
}

function formatTime(iso: string): string {
  if (!iso) return ''
  const d = new Date(iso)
  return d.toLocaleString('zh-CN', { hour12: false })
}

onMounted(load)
</script>

<style scoped>
.history-panel {
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
.history-list { flex: 1; overflow-y: auto; }
.history-item {
  padding: 8px 10px;
  cursor: pointer;
  border-bottom: 1px solid rgba(58,58,82,0.3);
}
.history-item:hover { background: var(--bg-hover); }
.history-item.error { border-left: 2px solid var(--danger); }
.history-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 11px;
  color: var(--text-muted);
  margin-bottom: 4px;
}
.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--success);
}
.status-dot.error { background: var(--danger); }
.history-time { flex: 1; }
.history-elapsed { font-family: var(--font-mono); }
.history-sql {
  font-size: 12px;
  font-family: var(--font-mono);
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.history-error {
  font-size: 11px;
  color: var(--danger);
  margin-top: 3px;
}
.empty-list {
  padding: 20px;
  text-align: center;
  color: var(--text-muted);
  font-size: 12px;
}
</style>
