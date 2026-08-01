<template>
  <div class="workspace">
    <div class="titlebar">
      <span class="brand">◈ DBTool</span>
      <span class="subtitle">Lightweight Database Manager</span>
      <span class="spacer"></span>
      <button @click="newQueryTab" :disabled="activeConnectionId === null">＋ New Query</button>
    </div>

    <div class="main">
      <div class="col-sidebar" :style="{ width: sidebarWidth + 'px' }">
        <ConnectionTree
          @open-table="onOpenTable"
          @view-ddl="onViewDdl"
          @export-table="onExportTable"
          @edit-connection="onEditConnection"
          @new-connection="onNewConnection"
          @select-connection="onSelectConnection"
        />
      </div>

      <div class="resizer" @mousedown="startSidebarDrag"></div>

      <div class="col-center">
        <div class="tabbar">
          <div
            v-for="t in tabs"
            :key="t.id"
            class="tab"
            :class="{ active: t.id === activeTabId }"
            @click="activeTabId = t.id"
          >
            <span class="tab-icon">{{ tabIcon(t.kind) }}</span>
            <span class="tab-title">{{ t.title }}</span>
            <button class="tab-close" @click.stop="closeTab(t.id)">✕</button>
          </div>
          <div v-if="tabs.length === 0" class="tab-empty">
            Open a table or create a query to get started
          </div>
        </div>

        <div class="tab-content">
          <template v-for="t in tabs" :key="t.id">
            <div v-show="t.id === activeTabId" class="tab-pane">
              <TableDataTab
                v-if="t.kind === 'table'"
                :connection-id="t.connectionId"
                :database="t.database!"
                :table="t.table!"
              />
              <StructureTab
                v-else-if="t.kind === 'ddl'"
                :connection-id="t.connectionId"
                :database="t.database!"
                :table="t.table!"
                initial-view="ddl"
              />
              <QueryTab
                v-else-if="t.kind === 'query'"
                :ref="(el) => registerQueryTab(t.id, el)"
                :connection-id="t.connectionId"
                :database="t.database"
                :initial-sql="t.sql"
              />
            </div>
          </template>
        </div>
      </div>

      <div class="resizer" @mousedown="startPanelDrag"></div>

      <div class="col-panel" :style="{ width: panelWidth + 'px' }">
        <InfoPanel :connection-id="activeConnectionId" @use-sql="onUseSql" />
      </div>
    </div>

    <ConnectionModal
      v-if="showModal"
      :editing="editingConnection"
      :groups="store.groups"
      @close="showModal = false"
      @saved="onConnectionSaved"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import ConnectionTree from '@/components/ConnectionTree.vue'
import TableDataTab from '@/components/TableDataTab.vue'
import StructureTab from '@/components/StructureTab.vue'
import QueryTab from '@/components/QueryTab.vue'
import InfoPanel from '@/components/InfoPanel.vue'
import ConnectionModal from '@/components/ConnectionModal.vue'
import type { Connection, TableInfo } from '@/types'
import { useConnectionStore } from '@/stores/connection'

interface Tab {
  id: string
  kind: 'table' | 'ddl' | 'query'
  title: string
  connectionId: number
  database?: string
  table?: string
  sql?: string
}

const store = useConnectionStore()

const tabs = reactive<Tab[]>([])
const activeTabId = ref<string | null>(null)
const activeConnectionId = ref<number | null>(null)

const sidebarWidth = ref(260)
const panelWidth = ref(280)

const showModal = ref(false)
const editingConnection = ref<Connection | null>(null)

const queryTabRefs = reactive<Record<string, any>>({})

let seq = 1
function nextId() {
  return 'tab-' + seq++
}

function tabIcon(kind: string) {
  return kind === 'table' ? '▦' : kind === 'ddl' ? '⌗' : '➤'
}

function onSelectConnection(id: number) {
  activeConnectionId.value = id
}

function onOpenTable(payload: { connectionId: number; database: string; table: TableInfo }) {
  activeConnectionId.value = payload.connectionId
  const existing = tabs.find(
    (t) =>
      t.kind === 'table' &&
      t.connectionId === payload.connectionId &&
      t.database === payload.database &&
      t.table === payload.table.name
  )
  if (existing) {
    activeTabId.value = existing.id
    return
  }
  const tab: Tab = {
    id: nextId(),
    kind: 'table',
    title: payload.table.name,
    connectionId: payload.connectionId,
    database: payload.database,
    table: payload.table.name
  }
  tabs.push(tab)
  activeTabId.value = tab.id
}

function onViewDdl(payload: { connectionId: number; database: string; table: string }) {
  activeConnectionId.value = payload.connectionId
  const tab: Tab = {
    id: nextId(),
    kind: 'ddl',
    title: payload.table + ' (DDL)',
    connectionId: payload.connectionId,
    database: payload.database,
    table: payload.table
  }
  tabs.push(tab)
  activeTabId.value = tab.id
}

function onExportTable(payload: { connectionId: number; database: string; table: string }) {
  // open table tab; user can use its Export button
  onOpenTable({
    connectionId: payload.connectionId,
    database: payload.database,
    table: { name: payload.table, type: 'BASE TABLE', estimatedRows: null, comment: null }
  })
}

function newQueryTab() {
  if (activeConnectionId.value === null) return
  const tab: Tab = {
    id: nextId(),
    kind: 'query',
    title: 'Query ' + seq,
    connectionId: activeConnectionId.value,
    database: undefined,
    sql: 'SELECT * FROM '
  }
  tabs.push(tab)
  activeTabId.value = tab.id
}

function registerQueryTab(id: string, el: any) {
  if (el) queryTabRefs[id] = el
}

function onUseSql(sql: string) {
  // put SQL into active query tab, or create a new one
  const active = tabs.find((t) => t.id === activeTabId.value)
  if (active && active.kind === 'query') {
    active.sql = sql
    // force re-render by replacing tab sql through key? QueryTab watches initialSql only on mount,
    // so we set via a fresh tab if needed. Simpler: open new query tab with sql.
  }
  if (activeConnectionId.value === null) return
  const tab: Tab = {
    id: nextId(),
    kind: 'query',
    title: 'Query ' + seq,
    connectionId: activeConnectionId.value,
    sql
  }
  tabs.push(tab)
  activeTabId.value = tab.id
}

function closeTab(id: string) {
  const idx = tabs.findIndex((t) => t.id === id)
  if (idx === -1) return
  tabs.splice(idx, 1)
  delete queryTabRefs[id]
  if (activeTabId.value === id) {
    activeTabId.value = tabs.length ? tabs[Math.max(0, idx - 1)].id : null
  }
}

// --- connection modal ---
function onNewConnection() {
  editingConnection.value = null
  showModal.value = true
}
function onEditConnection(conn: Connection) {
  editingConnection.value = conn
  showModal.value = true
}
async function onConnectionSaved() {
  showModal.value = false
  await store.loadAll()
}

// --- resizers ---
function startSidebarDrag(e: MouseEvent) {
  const startX = e.clientX
  const startW = sidebarWidth.value
  const move = (ev: MouseEvent) => {
    sidebarWidth.value = Math.max(180, Math.min(500, startW + ev.clientX - startX))
  }
  const up = () => {
    document.removeEventListener('mousemove', move)
    document.removeEventListener('mouseup', up)
  }
  document.addEventListener('mousemove', move)
  document.addEventListener('mouseup', up)
}

function startPanelDrag(e: MouseEvent) {
  const startX = e.clientX
  const startW = panelWidth.value
  const move = (ev: MouseEvent) => {
    panelWidth.value = Math.max(200, Math.min(500, startW - (ev.clientX - startX)))
  }
  const up = () => {
    document.removeEventListener('mousemove', move)
    document.removeEventListener('mouseup', up)
  }
  document.addEventListener('mousemove', move)
  document.addEventListener('mouseup', up)
}
</script>

<style scoped>
.workspace {
  display: flex;
  flex-direction: column;
  height: 100vh;
}
.titlebar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  background: var(--bg-alt);
  border-bottom: 1px solid var(--border);
}
.brand {
  font-weight: 700;
  color: var(--accent);
}
.subtitle {
  color: var(--text-dim);
  font-size: 12px;
}
.spacer { flex: 1; }
.main {
  flex: 1;
  display: flex;
  overflow: hidden;
  min-height: 0;
}
.col-sidebar {
  flex-shrink: 0;
  overflow: hidden;
}
.col-center {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-width: 0;
}
.col-panel {
  flex-shrink: 0;
  overflow: hidden;
}
.resizer {
  width: 4px;
  cursor: col-resize;
  background: var(--border);
}
.resizer:hover {
  background: var(--accent);
}
.tabbar {
  display: flex;
  background: var(--bg-alt);
  border-bottom: 1px solid var(--border);
  overflow-x: auto;
  min-height: 34px;
}
.tab {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-right: 1px solid var(--border);
  cursor: pointer;
  white-space: nowrap;
  font-size: 12px;
}
.tab.active {
  background: var(--bg);
  border-bottom: 2px solid var(--accent);
}
.tab-close {
  border: none;
  background: transparent;
  color: var(--text-dim);
  padding: 0 2px;
}
.tab-close:hover {
  color: var(--red);
  background: transparent;
}
.tab-empty {
  padding: 8px 14px;
  color: var(--text-dim);
  font-size: 12px;
}
.tab-content {
  flex: 1;
  overflow: hidden;
  min-height: 0;
  position: relative;
}
.tab-pane {
  position: absolute;
  inset: 0;
}
</style>
