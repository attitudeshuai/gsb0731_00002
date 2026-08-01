<template>
  <div class="info-panel">
    <div class="panel-tabs">
      <button :class="{ active: tab === 'history' }" @click="tab = 'history'; loadHistory()">History</button>
      <button :class="{ active: tab === 'saved' }" @click="tab = 'saved'; loadSaved()">Saved</button>
    </div>

    <div class="panel-body">
      <template v-if="tab === 'history'">
        <div v-if="history.length === 0" class="empty">No history</div>
        <div
          v-for="h in history"
          :key="h.id"
          class="hist-item"
          :class="{ failed: !h.success }"
          @click="$emit('use-sql', h.sqlText)"
          :title="h.sqlText"
        >
          <div class="sql">{{ truncate(h.sqlText) }}</div>
          <div class="meta">
            <span>{{ formatTime(h.executedAt) }}</span>
            <span v-if="h.durationMs != null">{{ h.durationMs }}ms</span>
            <span v-if="!h.success" class="err">error</span>
          </div>
        </div>
      </template>

      <template v-else>
        <div v-if="saved.length === 0" class="empty">No saved queries</div>
        <div
          v-for="q in saved"
          :key="q.id"
          class="saved-item"
          @click="$emit('use-sql', q.sqlText)"
        >
          <div class="row">
            <span class="name">★ {{ q.name }}</span>
            <button class="icon-btn" @click.stop="remove(q.id)">✕</button>
          </div>
          <div class="sql">{{ truncate(q.sqlText) }}</div>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import type { QueryHistory, SavedQuery } from '@/types'
import { historyApi, savedQueryApi } from '@/api'
import { useUiStore } from '@/stores/ui'

const props = defineProps<{ connectionId: number | null }>()
defineEmits<{ (e: 'use-sql', sql: string): void }>()

const ui = useUiStore()
const tab = ref<'history' | 'saved'>('history')
const history = ref<QueryHistory[]>([])
const saved = ref<SavedQuery[]>([])

async function loadHistory() {
  try {
    const res = await historyApi.list({
      connectionId: props.connectionId ?? undefined,
      page: 0,
      size: 50
    })
    history.value = res.content
  } catch (e: any) {
    ui.error(e.message)
  }
}

async function loadSaved() {
  try {
    saved.value = await savedQueryApi.list()
  } catch (e: any) {
    ui.error(e.message)
  }
}

async function remove(id: number) {
  try {
    await savedQueryApi.remove(id)
    await loadSaved()
  } catch (e: any) {
    ui.error(e.message)
  }
}

function truncate(s: string) {
  const one = s.replace(/\s+/g, ' ').trim()
  return one.length > 80 ? one.slice(0, 80) + '…' : one
}

function formatTime(iso: string) {
  return new Date(iso).toLocaleString()
}

defineExpose({ loadHistory, loadSaved })

onMounted(loadHistory)
</script>

<style scoped>
.info-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--bg-alt);
  border-left: 1px solid var(--border);
}
.panel-tabs {
  display: flex;
  border-bottom: 1px solid var(--border);
}
.panel-tabs button {
  flex: 1;
  border: none;
  border-radius: 0;
  background: transparent;
  padding: 8px;
  color: var(--text-dim);
}
.panel-tabs button.active {
  color: var(--text);
  border-bottom: 2px solid var(--accent);
}
.panel-body {
  flex: 1;
  overflow-y: auto;
  padding: 4px;
}
.hist-item, .saved-item {
  padding: 6px 8px;
  border-radius: var(--radius);
  cursor: pointer;
  margin-bottom: 2px;
}
.hist-item:hover, .saved-item:hover {
  background: var(--bg-hover);
}
.hist-item.failed .sql { color: var(--red); }
.sql {
  font-family: var(--mono);
  font-size: 11px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.meta {
  display: flex;
  gap: 8px;
  font-size: 10px;
  color: var(--text-dim);
  margin-top: 2px;
}
.meta .err { color: var(--red); }
.saved-item .row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.saved-item .name {
  color: var(--yellow);
  font-size: 12px;
}
.empty {
  padding: 16px;
  text-align: center;
  color: var(--text-dim);
  font-size: 12px;
}
</style>
