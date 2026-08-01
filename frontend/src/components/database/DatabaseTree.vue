<template>
  <div class="db-tree">
    <div class="db-tree-search">
      <input
        v-model="searchKeyword"
        class="db-tree-input"
        placeholder="搜索表名..."
      />
    </div>
    <div class="db-tree-body" @click="closeContextMenu">
      <template v-for="group in rootGroups" :key="'g-' + group.id">
        <div
          class="tree-node group-node"
          :style="{ paddingLeft: 8 + 'px' }"
          @click="toggleGroup(group.id)"
        >
          <span class="tree-arrow">{{ expandedGroups.has(group.id) ? '▼' : '▶' }}</span>
          <span class="tree-icon" :style="{ color: group.color || '#fbbc04' }">📁</span>
          <span class="tree-label">{{ group.name }}</span>
        </div>
        <template v-if="expandedGroups.has(group.id)">
          <template v-for="conn in connectionsByGroup(group.id)" :key="'c-' + conn.id">
            <div
              class="tree-node conn-node"
              :style="{ paddingLeft: 24 + 'px' }"
              @click="toggleConnection(conn)"
            >
              <span class="tree-arrow">{{ expandedConnections.has(conn.id) ? '▼' : '▶' }}</span>
              <span class="tree-icon" :style="{ color: conn.color || '#1a73e8' }">🗄</span>
              <span class="tree-label" :title="conn.name">{{ conn.name }}</span>
              <button
                class="tree-action-btn"
                title="刷新"
                @click.stop="refreshConnection(conn.id)"
              >↻</button>
              <button
                class="tree-action-btn"
                title="编辑"
                @click.stop="$emit('edit-connection', conn)"
              >✎</button>
              <button
                class="tree-action-btn tree-action-danger"
                title="删除"
                @click.stop="$emit('delete-connection', conn)"
              >✕</button>
            </div>
            <template v-if="expandedConnections.has(conn.id)">
              <div v-if="connectionLoading.has(conn.id)" class="tree-loading" :style="{ paddingLeft: 44 + 'px' }">
                <span class="mini-spinner"></span> 加载中...
              </div>
              <template v-else>
                <template v-for="db in databasesFor(conn.id)" :key="'db-' + conn.id + '-' + db">
                  <div
                    v-if="matchDatabase(conn.id, db)"
                    class="tree-node db-node"
                    :style="{ paddingLeft: 40 + 'px' }"
                    @click="toggleDatabase(conn.id, db)"
                  >
                    <span class="tree-arrow">{{ isDbExpanded(conn.id, db) ? '▼' : '▶' }}</span>
                    <span class="tree-icon">📊</span>
                    <span class="tree-label">{{ db }}</span>
                  </div>
                  <template v-if="isDbExpanded(conn.id, db)">
                    <template v-for="folder in tableFolders" :key="'f-' + conn.id + '-' + db + '-' + folder.type">
                      <div
                        v-if="hasTablesInFolder(conn.id, db, folder.type)"
                        class="tree-node folder-node"
                        :style="{ paddingLeft: 56 + 'px' }"
                        @click="toggleFolder(conn.id, db, folder.type)"
                      >
                        <span class="tree-arrow">{{ isFolderExpanded(conn.id, db, folder.type) ? '▼' : '▶' }}</span>
                        <span class="tree-icon">{{ folder.icon }}</span>
                        <span class="tree-label">{{ folder.label }}</span>
                      </div>
                      <template v-if="isFolderExpanded(conn.id, db, folder.type)">
                        <div
                          v-for="table in filteredTables(conn.id, db, folder.type)"
                          :key="'t-' + conn.id + '-' + db + '-' + table.name"
                          class="tree-node table-node"
                          :style="{ paddingLeft: 72 + 'px' }"
                          @click="onTableClick(conn.id, db, table, folder.type)"
                          @contextmenu.prevent="onTableContextMenu($event, conn.id, db, table, folder.type)"
                        >
                          <span class="tree-icon">{{ table.type === 'VIEW' ? '👁' : '📋' }}</span>
                          <span class="tree-label" :title="table.name">{{ table.name }}</span>
                        </div>
                      </template>
                    </template>
                  </template>
                </template>
              </template>
            </template>
          </template>
        </template>
      </template>
      <template v-for="conn in ungroupedConnections" :key="'uc-' + conn.id">
        <div
          class="tree-node conn-node"
          :style="{ paddingLeft: 8 + 'px' }"
          @click="toggleConnection(conn)"
        >
          <span class="tree-arrow">{{ expandedConnections.has(conn.id) ? '▼' : '▶' }}</span>
          <span class="tree-icon" :style="{ color: conn.color || '#1a73e8' }">🗄</span>
          <span class="tree-label" :title="conn.name">{{ conn.name }}</span>
          <button class="tree-action-btn" title="刷新" @click.stop="refreshConnection(conn.id)">↻</button>
          <button class="tree-action-btn" title="编辑" @click.stop="$emit('edit-connection', conn)">✎</button>
          <button
            class="tree-action-btn tree-action-danger"
            title="删除"
            @click.stop="$emit('delete-connection', conn)"
          >✕</button>
        </div>
        <template v-if="expandedConnections.has(conn.id)">
          <div v-if="connectionLoading.has(conn.id)" class="tree-loading" :style="{ paddingLeft: 28 + 'px' }">
            <span class="mini-spinner"></span> 加载中...
          </div>
          <template v-else>
            <template v-for="db in databasesFor(conn.id)" :key="'udb-' + conn.id + '-' + db">
              <div
                v-if="matchDatabase(conn.id, db)"
                class="tree-node db-node"
                :style="{ paddingLeft: 24 + 'px' }"
                @click="toggleDatabase(conn.id, db)"
              >
                <span class="tree-arrow">{{ isDbExpanded(conn.id, db) ? '▼' : '▶' }}</span>
                <span class="tree-icon">📊</span>
                <span class="tree-label">{{ db }}</span>
              </div>
              <template v-if="isDbExpanded(conn.id, db)">
                <template v-for="folder in tableFolders" :key="'uf-' + conn.id + '-' + db + '-' + folder.type">
                  <div
                    v-if="hasTablesInFolder(conn.id, db, folder.type)"
                    class="tree-node folder-node"
                    :style="{ paddingLeft: 40 + 'px' }"
                    @click="toggleFolder(conn.id, db, folder.type)"
                  >
                    <span class="tree-arrow">{{ isFolderExpanded(conn.id, db, folder.type) ? '▼' : '▶' }}</span>
                    <span class="tree-icon">{{ folder.icon }}</span>
                    <span class="tree-label">{{ folder.label }}</span>
                  </div>
                  <template v-if="isFolderExpanded(conn.id, db, folder.type)">
                    <div
                      v-for="table in filteredTables(conn.id, db, folder.type)"
                      :key="'ut-' + conn.id + '-' + db + '-' + table.name"
                      class="tree-node table-node"
                      :style="{ paddingLeft: 56 + 'px' }"
                      @click="onTableClick(conn.id, db, table, folder.type)"
                      @contextmenu.prevent="onTableContextMenu($event, conn.id, db, table, folder.type)"
                    >
                      <span class="tree-icon">{{ table.type === 'VIEW' ? '👁' : '📋' }}</span>
                      <span class="tree-label" :title="table.name">{{ table.name }}</span>
                    </div>
                  </template>
                </template>
              </template>
            </template>
          </template>
        </template>
      </template>
      <div v-if="!rootGroups.length && !ungroupedConnections.length" class="tree-empty">
        暂无连接，请先新建连接
      </div>
    </div>
    <ContextMenu
      :visible="contextMenu.visible"
      :x="contextMenu.x"
      :y="contextMenu.y"
      :items="contextMenuItems"
      @select="onContextMenuSelect"
      @close="closeContextMenu"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, reactive } from 'vue'
import type { Connection, ConnectionGroup, TableMeta } from '@/types'
import { getDatabases, getTables } from '@/api/metadata'
import ContextMenu, { type ContextMenuItem } from '@/components/common/ContextMenu.vue'

const props = defineProps<{
  connections: Connection[]
  groups: ConnectionGroup[]
}>()

const emit = defineEmits<{
  'open-table': [payload: { connectionId: number; database: string; table: string; type: string }]
  'view-ddl': [payload: { connectionId: number; database: string; table: string }]
  'export-table': [payload: { connectionId: number; database: string; table: string }]
  'refresh-connection': [connectionId: number]
  'edit-connection': [connection: Connection]
  'delete-connection': [connection: Connection]
}>()

const searchKeyword = ref('')

const expandedGroups = ref<Set<number>>(new Set())
const expandedConnections = ref<Set<number>>(new Set())
const connectionLoading = ref<Set<number>>(new Set())
const dbExpanded = ref<Set<string>>(new Set())
const folderExpanded = ref<Set<string>>(new Set())

const databasesCache = reactive<Record<number, string[]>>({})
const tablesCache = reactive<Record<string, TableMeta[]>>({})

const tableFolders = [
  { type: 'TABLE', label: '表', icon: '📋' },
  { type: 'VIEW', label: '视图', icon: '👁' }
]

const contextMenu = reactive({
  visible: false,
  x: 0,
  y: 0,
  connectionId: 0,
  database: '',
  table: ''
})

const contextMenuItems = computed<ContextMenuItem[]>(() => [
  { label: '打开表数据', icon: '📂', action: 'open' },
  { label: '查看建表语句', icon: '📝', action: 'ddl' },
  { label: '导出表数据', icon: '📥', action: 'export' },
  { divider: true, label: '', action: '' },
  { label: '复制表名', icon: '📋', action: 'copy' }
])

const rootGroups = computed(() => props.groups.filter((g) => g.parentId === null || g.parentId === undefined))
const ungroupedConnections = computed(() =>
  props.connections.filter((c) => c.groupId === null || c.groupId === undefined)
)

function connectionsByGroup(groupId: number) {
  return props.connections.filter((c) => c.groupId === groupId)
}

function toggleGroup(id: number) {
  if (expandedGroups.value.has(id)) expandedGroups.value.delete(id)
  else expandedGroups.value.add(id)
  expandedGroups.value = new Set(expandedGroups.value)
}

async function toggleConnection(conn: Connection) {
  const id = conn.id
  if (expandedConnections.value.has(id)) {
    expandedConnections.value.delete(id)
    expandedConnections.value = new Set(expandedConnections.value)
    return
  }
  expandedConnections.value.add(id)
  expandedConnections.value = new Set(expandedConnections.value)
  if (!databasesCache[id]) {
    connectionLoading.value.add(id)
    connectionLoading.value = new Set(connectionLoading.value)
    try {
      const dbs = await getDatabases(id)
      databasesCache[id] = dbs
    } catch {
      databasesCache[id] = []
    } finally {
      connectionLoading.value.delete(id)
      connectionLoading.value = new Set(connectionLoading.value)
    }
  }
}

function refreshConnection(id: number) {
  delete databasesCache[id]
  Object.keys(tablesCache).forEach((k) => {
    if (k.startsWith(`${id}:`)) delete tablesCache[k]
  })
  emit('refresh-connection', id)
  if (expandedConnections.value.has(id)) {
    expandedConnections.value.delete(id)
    expandedConnections.value = new Set(expandedConnections.value)
    const conn = props.connections.find((c) => c.id === id)
    if (conn) toggleConnection(conn)
  }
}

function databasesFor(connectionId: number): string[] {
  return databasesCache[connectionId] || []
}

function dbKey(connectionId: number, db: string) {
  return `${connectionId}:${db}`
}

function isDbExpanded(connectionId: number, db: string) {
  return dbExpanded.value.has(dbKey(connectionId, db))
}

async function toggleDatabase(connectionId: number, db: string) {
  const key = dbKey(connectionId, db)
  if (dbExpanded.value.has(key)) {
    dbExpanded.value.delete(key)
    dbExpanded.value = new Set(dbExpanded.value)
    return
  }
  dbExpanded.value.add(key)
  dbExpanded.value = new Set(dbExpanded.value)
  if (!tablesCache[key]) {
    try {
      const tables = await getTables(connectionId, db)
      tablesCache[key] = tables
    } catch {
      tablesCache[key] = []
    }
  }
  if (searchKeyword.value) {
    folderExpanded.value.add(`${key}:TABLE`)
    folderExpanded.value.add(`${key}:VIEW`)
    folderExpanded.value = new Set(folderExpanded.value)
  }
}

function folderKey(connectionId: number, db: string, type: string) {
  return `${dbKey(connectionId, db)}:${type}`
}

function isFolderExpanded(connectionId: number, db: string, type: string) {
  return folderExpanded.value.has(folderKey(connectionId, db, type))
}

function toggleFolder(connectionId: number, db: string, type: string) {
  const key = folderKey(connectionId, db, type)
  if (folderExpanded.value.has(key)) folderExpanded.value.delete(key)
  else folderExpanded.value.add(key)
  folderExpanded.value = new Set(folderExpanded.value)
}

function tablesFor(connectionId: number, db: string): TableMeta[] {
  return tablesCache[dbKey(connectionId, db)] || []
}

function hasTablesInFolder(connectionId: number, db: string, type: string): boolean {
  return tablesFor(connectionId, db).some((t) => {
    if (type === 'VIEW') return t.type === 'VIEW'
    return t.type !== 'VIEW'
  })
}

function filteredTables(connectionId: number, db: string, type: string): TableMeta[] {
  const all = tablesFor(connectionId, db).filter((t) => {
    if (type === 'VIEW') return t.type === 'VIEW'
    return t.type !== 'VIEW'
  })
  if (!searchKeyword.value) return all
  const kw = searchKeyword.value.toLowerCase()
  return all.filter((t) => t.name.toLowerCase().includes(kw))
}

function matchDatabase(connectionId: number, db: string): boolean {
  if (!searchKeyword.value) return true
  const kw = searchKeyword.value.toLowerCase()
  if (db.toLowerCase().includes(kw)) return true
  return tablesFor(connectionId, db).some((t) => t.name.toLowerCase().includes(kw))
}

function onTableClick(connectionId: number, db: string, table: TableMeta, type: string) {
  emit('open-table', { connectionId, database: db, table: table.name, type: table.type || type })
}

function onTableContextMenu(
  e: MouseEvent,
  connectionId: number,
  db: string,
  table: TableMeta,
  type: string
) {
  contextMenu.visible = true
  contextMenu.x = e.clientX
  contextMenu.y = e.clientY
  contextMenu.connectionId = connectionId
  contextMenu.database = db
  contextMenu.table = table.name
}

function closeContextMenu() {
  contextMenu.visible = false
}

function onContextMenuSelect(action: string) {
  const { connectionId, database, table } = contextMenu
  if (action === 'open') {
    emit('open-table', { connectionId, database, table, type: 'TABLE' })
  } else if (action === 'ddl') {
    emit('view-ddl', { connectionId, database, table })
  } else if (action === 'export') {
    emit('export-table', { connectionId, database, table })
  } else if (action === 'copy') {
    navigator.clipboard?.writeText(table).catch(() => {})
  }
}
</script>

<style scoped lang="scss">
@use '@/styles/index.scss' as *;

.db-tree {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: $sidebar-bg;
  color: #cdd6f4;
}

.db-tree-search {
  padding: 10px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}

.db-tree-input {
  width: 100%;
  padding: 6px 10px;
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 4px;
  color: #cdd6f4;
  font-size: 13px;

  &::placeholder {
    color: rgba(205, 214, 244, 0.4);
  }

  &:focus {
    border-color: $primary-color;
    background: rgba(255, 255, 255, 0.08);
  }
}

.db-tree-body {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 4px 0;
}

.tree-node {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 5px 8px;
  cursor: pointer;
  font-size: 13px;
  white-space: nowrap;
  user-select: none;
  transition: background 0.15s;

  &:hover {
    background: $sidebar-hover;

    .tree-action-btn {
      opacity: 1;
    }
  }
}

.tree-arrow {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 14px;
  font-size: 9px;
  color: rgba(205, 214, 244, 0.5);
  flex-shrink: 0;
}

.tree-icon {
  font-size: 14px;
  flex-shrink: 0;
}

.tree-label {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
}

.tree-action-btn {
  background: transparent;
  color: rgba(205, 214, 244, 0.5);
  font-size: 13px;
  padding: 0 4px;
  border-radius: 3px;
  opacity: 0;
  transition: all 0.15s;

  &:hover {
    color: #fff;
    background: rgba(255, 255, 255, 0.12);
  }
}

.tree-action-danger:hover {
  color: #ff6b6b !important;
}

.table-node {
  .tree-icon {
    font-size: 13px;
  }
}

.conn-node {
  font-weight: 500;
}

.group-node {
  color: #e0e0e0;
}

.tree-loading {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 0;
  font-size: 12px;
  color: rgba(205, 214, 244, 0.6);
}

.mini-spinner {
  width: 12px;
  height: 12px;
  border: 2px solid rgba(255, 255, 255, 0.15);
  border-top-color: $primary-color;
  border-radius: 50%;
  animation: tree-spin 0.7s linear infinite;
}

@keyframes tree-spin {
  to {
    transform: rotate(360deg);
  }
}

.tree-empty {
  padding: 30px 16px;
  text-align: center;
  color: rgba(205, 214, 244, 0.4);
  font-size: 13px;
}
</style>
