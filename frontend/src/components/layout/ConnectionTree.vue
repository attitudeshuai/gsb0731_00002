<template>
  <div class="tree-container" @click="closeContextMenu">
    <div
      v-for="conn in filteredConnections"
      :key="conn.id"
      class="tree-node connection-node"
    >
      <div
        class="tree-item"
        :class="{ active: expandedConn === conn.id }"
        @click="toggleConnection(conn)"
        @contextmenu.prevent="onContextMenu($event, { type: 'connection', connection: conn })"
      >
        <span class="tree-arrow" :class="{ expanded: expandedConn === conn.id }">▶</span>
        <span class="tree-icon" :style="{ color: conn.color || 'var(--accent)' }">🗄</span>
        <span class="tree-label">{{ conn.name }}</span>
      </div>

      <div v-if="expandedConn === conn.id" class="tree-children">
        <div v-if="connLoading" class="tree-loading">加载中...</div>
        <template v-else>
          <div
            v-for="db in databases"
            :key="db"
            class="tree-node"
          >
            <div
              class="tree-item"
              :class="{ active: expandedDb === db }"
              @click="toggleDatabase(conn, db)"
            >
              <span class="tree-arrow" :class="{ expanded: expandedDb === db }">▶</span>
              <span class="tree-icon">📁</span>
              <span class="tree-label">{{ db }}</span>
            </div>

            <div v-if="expandedDb === db" class="tree-children">
              <div class="table-search">
                <input
                  v-model="tableSearch[conn.id + ':' + db]"
                  type="text"
                  placeholder="过滤表..."
                  class="search-input"
                />
              </div>
              <div
                v-for="tbl in filteredTables(conn.id, db)"
                :key="tbl.name"
                class="tree-item table-item"
                @click="openTable(conn, db, tbl)"
                @dblclick="openTableData(conn, db, tbl)"
                @contextmenu.prevent="onContextMenu($event, { type: 'table', connection: conn, database: db, table: tbl })"
              >
                <span class="tree-icon">
                  <span v-if="tbl.type === 'VIEW'" class="badge badge-view">V</span>
                  <span v-else class="badge badge-table">T</span>
                </span>
                <span class="tree-label" :title="tbl.comment || tbl.name">{{ tbl.name }}</span>
              </div>
            </div>
          </div>
        </template>
      </div>
    </div>

    <div v-if="filteredConnections.length === 0" class="empty-hint">
      暂无连接，点击 + 新建
    </div>

    <ContextMenu
      v-if="contextMenu.visible"
      :x="contextMenu.x"
      :y="contextMenu.y"
      :items="contextMenuItems"
      @close="closeContextMenu"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, reactive } from 'vue'
import { metadataApi, connectionApi } from '@/api'
import { useConnectionStore } from '@/stores/connection'
import { useWorkspaceStore } from '@/stores/workspace'
import { useUiStore } from '@/stores/ui'
import { useToastStore } from '@/stores/toast'
import ContextMenu from '@/components/common/ContextMenu.vue'
import type { ConnectionConfig, TableMeta } from '@/types'

const props = defineProps<{ search?: string }>()

const connectionStore = useConnectionStore()
const workspaceStore = useWorkspaceStore()
const uiStore = useUiStore()
const toastStore = useToastStore()

const expandedConn = ref<number | null>(null)
const expandedDb = ref<string | null>(null)
const connLoading = ref(false)
const databases = ref<string[]>([])
const tablesMap = reactive<Record<string, TableMeta[]>>({})
const tableSearch = reactive<Record<string, string>>({})

const contextMenu = reactive({ visible: false, x: 0, y: 0, target: null as any })

const filteredConnections = computed(() => {
  if (!props.search) return connectionStore.connections
  const q = props.search.toLowerCase()
  return connectionStore.connections.filter(c =>
    c.name.toLowerCase().includes(q) ||
    c.host.toLowerCase().includes(q) ||
    (c.databaseName || '').toLowerCase().includes(q)
  )
})

function filteredTables(connId: number, db: string): TableMeta[] {
  const key = `${connId}:${db}`
  const list = tablesMap[key] || []
  const q = tableSearch[key]
  if (!q) return list
  const lower = q.toLowerCase()
  return list.filter(t => t.name.toLowerCase().includes(lower))
}

async function toggleConnection(conn: ConnectionConfig) {
  if (expandedConn.value === conn.id) {
    expandedConn.value = null
    expandedDb.value = null
    return
  }
  expandedConn.value = conn.id
  expandedDb.value = null
  connLoading.value = true
  try {
    databases.value = await metadataApi.listDatabases(conn.id)
    if (conn.databaseName && databases.value.includes(conn.databaseName)) {
      await toggleDatabase(conn, conn.databaseName)
    }
  } catch (e: any) {
    toastStore.error(e.message)
  } finally {
    connLoading.value = false
  }
}

async function toggleDatabase(conn: ConnectionConfig, db: string) {
  if (expandedDb.value === db) {
    expandedDb.value = null
    return
  }
  expandedDb.value = db
  const key = `${conn.id}:${db}`
  if (!tablesMap[key]) {
    try {
      tablesMap[key] = await metadataApi.listTables(conn.id, db)
    } catch (e: any) {
      toastStore.error(e.message)
      tablesMap[key] = []
    }
  }
}

function openTable(conn: ConnectionConfig, db: string, tbl: TableMeta) {
  workspaceStore.openTableViewer(conn.id, db, tbl.name)
}

function openTableData(conn: ConnectionConfig, db: string, tbl: TableMeta) {
  workspaceStore.openTableViewer(conn.id, db, tbl.name)
}

const contextMenuItems = computed(() => {
  const t = contextMenu.target
  if (!t) return []
  if (t.type === 'connection') {
    return [
      { label: '新建查询', action: () => workspaceStore.createQueryTab(t.connection.id, t.connection.databaseName) },
      { label: '编辑连接', action: () => uiStore.openEditConnection(t.connection) },
      { label: '测试连接', action: async () => {
          try {
            await connectionApi.testExisting(t.connection.id)
            toastStore.success('连接成功')
          } catch (e: any) { toastStore.error(e.message) }
        }
      },
      { divider: true },
      { label: '删除连接', danger: true, action: async () => {
          if (confirm(`确定删除连接 "${t.connection.name}" ?`)) {
            try {
              await connectionStore.deleteConnection(t.connection.id)
              toastStore.success('连接已删除')
            } catch (e: any) { toastStore.error(e.message) }
          }
        }
      }
    ]
  }
  if (t.type === 'table') {
    return [
      { label: '打开表数据', action: () => workspaceStore.openTableViewer(t.connection.id, t.database, t.table.name) },
      { label: '查看建表语句', action: () => {
          workspaceStore.showTableMeta(t.connection.id, t.database, t.table.name)
          workspaceStore.setRightPanel('ddl')
        }
      },
      { label: '查看表结构', action: () => {
          workspaceStore.showTableMeta(t.connection.id, t.database, t.table.name)
          workspaceStore.setRightPanel('structure')
        }
      },
      { divider: true },
      { label: '复制表名', action: () => {
          navigator.clipboard.writeText(t.table.name)
          toastStore.success('表名已复制')
        }
      },
      { label: 'SELECT * 查询', action: () => {
          workspaceStore.createQueryTab(
            t.connection.id, t.database,
            `SELECT * FROM \`${t.table.name}\` LIMIT 100;`,
            t.table.name
          )
        }
      }
    ]
  }
  return []
})

function onContextMenu(e: MouseEvent, target: any) {
  contextMenu.visible = true
  contextMenu.x = e.clientX
  contextMenu.y = e.clientY
  contextMenu.target = target
}

function closeContextMenu() {
  contextMenu.visible = false
}
</script>

<style scoped>
.tree-container {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 4px 0;
  position: relative;
}
.tree-item {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  cursor: pointer;
  font-size: 13px;
  white-space: nowrap;
  color: var(--text-primary);
}
.tree-item:hover { background: var(--bg-hover); }
.tree-item.active { background: var(--bg-active); }
.tree-arrow {
  display: inline-block;
  width: 12px;
  font-size: 8px;
  color: var(--text-muted);
  transition: transform 0.15s;
  transform: rotate(0deg);
}
.tree-arrow.expanded { transform: rotate(90deg); }
.tree-icon {
  display: inline-flex;
  align-items: center;
  width: 18px;
  font-size: 13px;
}
.tree-label {
  overflow: hidden;
  text-overflow: ellipsis;
}
.tree-children {
  padding-left: 14px;
}
.table-search {
  padding: 4px 8px 4px 0;
}
.table-search .search-input {
  width: 100%;
  font-size: 11px;
  padding: 3px 6px;
}
.table-item {
  padding-left: 24px;
}
.empty-hint {
  padding: 20px;
  text-align: center;
  color: var(--text-muted);
  font-size: 12px;
}
.tree-loading {
  padding: 8px;
  font-size: 12px;
  color: var(--text-muted);
}
</style>
