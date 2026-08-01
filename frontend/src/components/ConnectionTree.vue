<template>
  <div class="sidebar">
    <div class="sidebar-header">
      <span>CONNECTIONS</span>
      <div>
        <button class="icon-btn" title="New connection" @click="openNew">＋</button>
        <button class="icon-btn" title="Refresh" @click="reload">⟳</button>
      </div>
    </div>

    <div class="tree">
      <div v-for="conn in store.connections" :key="conn.id" class="tree-node">
        <div
          class="node-row conn-row"
          :class="{ active: selectedConnection === conn.id }"
          @click="toggleConn(conn)"
          @contextmenu.prevent="onConnContext($event, conn)"
        >
          <span class="twisty">{{ expanded.has(conn.id) ? '▾' : '▸' }}</span>
          <span class="node-icon">🖧</span>
          <span class="node-label">{{ conn.name }}</span>
          <span class="node-meta">{{ conn.host }}</span>
        </div>

        <div v-if="expanded.has(conn.id)" class="children">
          <div v-if="loadingConn === conn.id" class="loading-hint">Loading…</div>
          <template v-else>
            <div v-for="db in databases[conn.id] || []" :key="db" class="tree-node">
              <div class="node-row db-row" @click="toggleDb(conn.id, db)">
                <span class="twisty">{{ dbExpanded.has(conn.id + ':' + db) ? '▾' : '▸' }}</span>
                <span class="node-icon">🗄</span>
                <span class="node-label">{{ db }}</span>
              </div>
              <div v-if="dbExpanded.has(conn.id + ':' + db)" class="children">
                <div class="filter-box">
                  <input
                    v-model="tableFilter[conn.id + ':' + db]"
                    placeholder="Filter tables…"
                    @click.stop
                  />
                </div>
                <div v-if="loadingTables === conn.id + ':' + db" class="loading-hint">Loading…</div>
                <div
                  v-for="t in filteredTables(conn.id, db)"
                  :key="t.name"
                  class="node-row table-row"
                  @click="openTable(conn.id, db, t)"
                  @contextmenu.prevent="onTableContext($event, conn.id, db, t)"
                >
                  <span class="node-icon">{{ t.type === 'VIEW' ? '👁' : '▦' }}</span>
                  <span class="node-label">{{ t.name }}</span>
                </div>
              </div>
            </div>
          </template>
        </div>
      </div>
      <div v-if="store.connections.length === 0" class="empty-hint">
        No connections yet.<br />Click ＋ to add one.
      </div>
    </div>

    <!-- context menu -->
    <div
      v-if="ctxMenu.visible"
      class="ctx-menu"
      :style="{ top: ctxMenu.y + 'px', left: ctxMenu.x + 'px' }"
    >
      <template v-if="ctxMenu.kind === 'connection'">
        <div class="ctx-item" @click="editConn">Edit</div>
        <div class="ctx-item" @click="testConn">Test Connection</div>
        <div class="ctx-item danger" @click="deleteConn">Delete</div>
      </template>
      <template v-else-if="ctxMenu.kind === 'table'">
        <div class="ctx-item" @click="ctxOpenTable">Open Table</div>
        <div class="ctx-item" @click="ctxViewDdl">View DDL</div>
        <div class="ctx-item" @click="ctxExport">Export…</div>
        <div class="ctx-item" @click="ctxCopyName">Copy Name</div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted, onBeforeUnmount } from 'vue'
import type { Connection, TableInfo } from '@/types'
import { useConnectionStore } from '@/stores/connection'
import { useUiStore } from '@/stores/ui'
import { connectionApi, metadataApi } from '@/api'

const emit = defineEmits<{
  (e: 'open-table', payload: { connectionId: number; database: string; table: TableInfo }): void
  (e: 'view-ddl', payload: { connectionId: number; database: string; table: string }): void
  (e: 'export-table', payload: { connectionId: number; database: string; table: string }): void
  (e: 'edit-connection', conn: Connection): void
  (e: 'new-connection'): void
  (e: 'select-connection', id: number): void
}>()

const store = useConnectionStore()
const ui = useUiStore()

const expanded = reactive(new Set<number>())
const dbExpanded = reactive(new Set<string>())
const databases = reactive<Record<number, string[]>>({})
const tables = reactive<Record<string, TableInfo[]>>({})
const tableFilter = reactive<Record<string, string>>({})
const loadingConn = ref<number | null>(null)
const loadingTables = ref<string | null>(null)
const selectedConnection = ref<number | null>(null)

const ctxMenu = reactive({
  visible: false,
  x: 0,
  y: 0,
  kind: '' as 'connection' | 'table' | '',
  conn: null as Connection | null,
  connectionId: 0,
  database: '',
  table: null as TableInfo | null
})

async function reload() {
  await store.loadAll()
}

async function toggleConn(conn: Connection) {
  selectedConnection.value = conn.id
  emit('select-connection', conn.id)
  if (expanded.has(conn.id)) {
    expanded.delete(conn.id)
    return
  }
  expanded.add(conn.id)
  if (!databases[conn.id]) {
    loadingConn.value = conn.id
    try {
      databases[conn.id] = await metadataApi.databases(conn.id)
    } catch (e: any) {
      ui.error(e.message)
      expanded.delete(conn.id)
    } finally {
      loadingConn.value = null
    }
  }
}

async function toggleDb(connId: number, db: string) {
  const key = connId + ':' + db
  if (dbExpanded.has(key)) {
    dbExpanded.delete(key)
    return
  }
  dbExpanded.add(key)
  if (!tables[key]) {
    loadingTables.value = key
    try {
      tables[key] = await metadataApi.tables(connId, db)
    } catch (e: any) {
      ui.error(e.message)
      dbExpanded.delete(key)
    } finally {
      loadingTables.value = null
    }
  }
}

function filteredTables(connId: number, db: string): TableInfo[] {
  const key = connId + ':' + db
  const list = tables[key] || []
  const f = (tableFilter[key] || '').toLowerCase()
  if (!f) return list
  return list.filter((t) => t.name.toLowerCase().includes(f))
}

function openTable(connId: number, db: string, t: TableInfo) {
  selectedConnection.value = connId
  emit('open-table', { connectionId: connId, database: db, table: t })
}

function openNew() {
  emit('new-connection')
}

// --- context menu handlers ---
function onConnContext(e: MouseEvent, conn: Connection) {
  ctxMenu.visible = true
  ctxMenu.x = e.clientX
  ctxMenu.y = e.clientY
  ctxMenu.kind = 'connection'
  ctxMenu.conn = conn
}

function onTableContext(e: MouseEvent, connId: number, db: string, t: TableInfo) {
  ctxMenu.visible = true
  ctxMenu.x = e.clientX
  ctxMenu.y = e.clientY
  ctxMenu.kind = 'table'
  ctxMenu.connectionId = connId
  ctxMenu.database = db
  ctxMenu.table = t
}

function hideCtx() {
  ctxMenu.visible = false
}

function editConn() {
  if (ctxMenu.conn) emit('edit-connection', ctxMenu.conn)
  hideCtx()
}

async function testConn() {
  if (!ctxMenu.conn) return
  const id = ctxMenu.conn.id
  hideCtx()
  try {
    await connectionApi.testExisting(id)
    ui.success('Connection successful')
  } catch (e: any) {
    ui.error(e.message)
  }
}

async function deleteConn() {
  if (!ctxMenu.conn) return
  const conn = ctxMenu.conn
  hideCtx()
  if (!confirm(`Delete connection "${conn.name}"?`)) return
  try {
    await connectionApi.remove(conn.id)
    ui.success('Connection deleted')
    expanded.delete(conn.id)
    delete databases[conn.id]
    await store.loadAll()
  } catch (e: any) {
    ui.error(e.message)
  }
}

function ctxOpenTable() {
  if (ctxMenu.table) openTable(ctxMenu.connectionId, ctxMenu.database, ctxMenu.table)
  hideCtx()
}

function ctxViewDdl() {
  if (ctxMenu.table) {
    emit('view-ddl', {
      connectionId: ctxMenu.connectionId,
      database: ctxMenu.database,
      table: ctxMenu.table.name
    })
  }
  hideCtx()
}

function ctxExport() {
  if (ctxMenu.table) {
    emit('export-table', {
      connectionId: ctxMenu.connectionId,
      database: ctxMenu.database,
      table: ctxMenu.table.name
    })
  }
  hideCtx()
}

function ctxCopyName() {
  if (ctxMenu.table) {
    navigator.clipboard?.writeText(ctxMenu.table.name)
    ui.info('Table name copied')
  }
  hideCtx()
}

onMounted(() => {
  document.addEventListener('click', hideCtx)
  reload()
})
onBeforeUnmount(() => document.removeEventListener('click', hideCtx))
</script>

<style scoped>
.sidebar {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--bg-alt);
  border-right: 1px solid var(--border);
  overflow: hidden;
}
.sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 10px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.5px;
  color: var(--text-dim);
  border-bottom: 1px solid var(--border);
}
.tree {
  flex: 1;
  overflow-y: auto;
  padding: 4px 0;
}
.node-row {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  cursor: pointer;
  white-space: nowrap;
  font-size: 13px;
}
.node-row:hover {
  background: var(--bg-hover);
}
.node-row.active {
  background: var(--bg-hover);
}
.twisty {
  width: 12px;
  color: var(--text-dim);
  font-size: 10px;
}
.node-icon {
  font-size: 12px;
}
.node-label {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
}
.node-meta {
  font-size: 11px;
  color: var(--text-dim);
}
.children {
  padding-left: 14px;
}
.db-row .node-label { color: var(--yellow); }
.table-row { padding-left: 20px; }
.filter-box {
  padding: 4px 8px 4px 20px;
}
.filter-box input {
  padding: 3px 6px;
  font-size: 12px;
}
.loading-hint, .empty-hint {
  padding: 8px 16px;
  color: var(--text-dim);
  font-size: 12px;
}
.empty-hint { text-align: center; line-height: 1.6; }
.ctx-menu {
  position: fixed;
  background: var(--bg-panel);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.4);
  z-index: 2000;
  min-width: 150px;
  padding: 4px 0;
}
.ctx-item {
  padding: 6px 14px;
  cursor: pointer;
  font-size: 13px;
}
.ctx-item:hover {
  background: var(--bg-hover);
}
.ctx-item.danger {
  color: var(--red);
}
</style>
