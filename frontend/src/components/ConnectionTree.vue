<script setup lang="ts">
import { h, ref, watch } from 'vue'
import { useConnectionStore } from '@/stores/connections'
import { useTabsStore } from '@/stores/tabs'
import { useExportTasksStore } from '@/stores/exportTasks'
import { confirmDialog } from '@/ui/confirm'
import { toast } from '@/ui/toast'
import ContextMenu, { type MenuItem } from './ContextMenu.vue'
import type { ConnectionConfig, ConnectionGroup, ExportFormat, TableInfo } from '@/types'

const emit = defineEmits<{
  (e: 'new-connection', groupId?: number): void
  (e: 'edit-connection', conn: ConnectionConfig): void
  (e: 'manage-groups'): void
}>()

const store = useConnectionStore()
const tabsStore = useTabsStore()
const exportTasksStore = useExportTasksStore()

// ---------- 小图标（内联 SVG） ----------
function svgIcon(children: ReturnType<typeof h>[], cls: string) {
  return () =>
    h(
      'svg',
      { viewBox: '0 0 16 16', width: 13, height: 13, fill: 'currentColor', class: `node-icon ${cls}` },
      children,
    )
}
const IconGroup = svgIcon(
  [h('path', { d: 'M1.2 4A1.7 1.7 0 0 1 2.9 2.3h3l1.6 1.9h5.6A1.7 1.7 0 0 1 14.8 6v5.7a1.7 1.7 0 0 1-1.7 1.7H2.9a1.7 1.7 0 0 1-1.7-1.7V4z' })],
  'ic-group',
)
const IconConn = svgIcon(
  [
    h('rect', { x: 1.5, y: 2, width: 13, height: 5, rx: 1.2 }),
    h('rect', { x: 1.5, y: 9, width: 13, height: 5, rx: 1.2 }),
    h('circle', { cx: 4.2, cy: 4.5, r: 1, fill: 'var(--bg-1)' }),
    h('circle', { cx: 4.2, cy: 11.5, r: 1, fill: 'var(--bg-1)' }),
  ],
  'ic-conn',
)
const IconDb = svgIcon(
  [
    h('ellipse', { cx: 8, cy: 3.4, rx: 6, ry: 2.4 }),
    h('path', { d: 'M2 3.4v9.2c0 1.3 2.7 2.4 6 2.4s6-1.1 6-2.4V3.4c0 1.3-2.7 2.4-6 2.4s-6-1.1-6-2.4zm12 4.6c0 1.3-2.7 2.4-6 2.4s-6-1.1-6-2.4' }),
  ],
  'ic-db',
)
const IconTable = svgIcon(
  [h('path', { d: 'M1.5 2.5h13v11h-13zM1.5 6h13M1.5 9.5h13M6 2.5v11M10.5 2.5v11', fill: 'none', stroke: 'currentColor', 'stroke-width': 1.2 })],
  'ic-table',
)
const IconView = svgIcon(
  [
    h('path', { d: 'M8 3C4.7 3 2.1 5.4 1 8c1.1 2.6 3.7 5 7 5s5.9-2.4 7-5c-1.1-2.6-3.7-5-7-5zm0 8.2A3.2 3.2 0 1 1 8 4.8a3.2 3.2 0 0 1 0 6.4z' }),
    h('circle', { cx: 8, cy: 8, r: 1.5 }),
  ],
  'ic-view',
)

// ---------- 搜索 ----------
const keyword = ref('')
let debounceTimer = 0

watch(keyword, (kw) => {
  window.clearTimeout(debounceTimer)
  debounceTimer = window.setTimeout(() => {
    // 对已展开的库节点做服务端关键字过滤
    for (const key of store.expanded) {
      const m = /^c:(\d+)\/d:(.+)$/.exec(key)
      if (m) {
        void store.loadTables(Number(m[1]), m[2], kw || undefined, true)
      }
    }
  }, 300)
})

// ---------- 展开 / 加载 ----------
function expanded(key: string) {
  return store.expanded.has(key)
}

async function toggleConnection(conn: ConnectionConfig) {
  const key = `c:${conn.id}`
  store.toggleExpanded(key)
  if (expanded(key) && conn.id != null) {
    await store.loadDatabases(conn.id)
  }
}

async function toggleDatabase(conn: ConnectionConfig, db: string) {
  const key = `c:${conn.id}/d:${db}`
  store.toggleExpanded(key)
  if (expanded(key) && conn.id != null) {
    await store.loadTables(conn.id, db, keyword.value || undefined)
  }
}

function toggleGroup(groupId: number) {
  store.toggleExpanded(`g:${groupId}`)
}

function tablesOf(connId: number | undefined, db: string): TableInfo[] {
  if (connId == null) return []
  return store.tables[`${connId}/${db}`] ?? []
}

function tablesLoading(connId: number | undefined, db: string): boolean {
  if (connId == null) return false
  return !!store.tablesLoading[`${connId}/${db}`]
}

function onTableClick(conn: ConnectionConfig, db: string, t: TableInfo) {
  if (conn.id == null) return
  tabsStore.openTableDataTab(conn.id, db, t.name)
}

function isView(t: TableInfo) {
  return t.type?.toUpperCase() === 'VIEW'
}

// ---------- 右键菜单 ----------
const menuVisible = ref(false)
const menuX = ref(0)
const menuY = ref(0)
const menuItems = ref<MenuItem[]>([])

function openMenu(ev: MouseEvent, items: MenuItem[]) {
  menuItems.value = items
  menuX.value = ev.clientX
  menuY.value = ev.clientY
  menuVisible.value = true
}

type TreeNode =
  | { kind: 'group'; group: ConnectionGroup }
  | { kind: 'connection'; conn: ConnectionConfig }
  | { kind: 'database'; conn: ConnectionConfig; db: string }
  | { kind: 'table'; conn: ConnectionConfig; db: string; table: TableInfo }

function onContextMenu(ev: MouseEvent, node: TreeNode) {
  ev.preventDefault()
  ev.stopPropagation()

  if (node.kind === 'group') {
    openMenu(ev, [
      { label: '新建连接', action: () => emit('new-connection', node.group.id) },
      { label: '分组管理', action: () => emit('manage-groups') },
    ])
    return
  }

  if (node.kind === 'connection') {
    const conn = node.conn
    openMenu(ev, [
      {
        label: '新建查询',
        action: () => conn.id != null && tabsStore.openQueryTab(conn.id, conn.defaultDatabase || undefined),
      },
      {
        label: '刷新',
        action: () => conn.id != null && store.loadDatabases(conn.id, true),
      },
      { label: '编辑连接', action: () => emit('edit-connection', conn) },
      {
        label: '删除连接',
        danger: true,
        action: () => void removeConnection(conn),
      },
    ])
    return
  }

  if (node.kind === 'database') {
    const { conn, db } = node
    openMenu(ev, [
      {
        label: '新建查询',
        action: () => conn.id != null && tabsStore.openQueryTab(conn.id, db),
      },
      {
        label: '刷新表列表',
        action: () => conn.id != null && store.loadTables(conn.id, db, keyword.value || undefined, true),
      },
    ])
    return
  }

  const { conn, db, table } = node
  openMenu(ev, [
    {
      label: '打开表',
      action: () => conn.id != null && tabsStore.openTableDataTab(conn.id, db, table.name),
    },
    {
      label: '查看建表语句',
      action: () => conn.id != null && tabsStore.openStructureTab(conn.id, db, table.name),
    },
    {
      label: '导出表',
      children: (['csv', 'json', 'sql'] as ExportFormat[]).map((fmt) => ({
        label: fmt.toUpperCase(),
        action: () => void doExport(conn, db, table.name, fmt),
      })),
    },
    {
      label: '复制表名',
      action: () => void copyText(table.name, '已复制表名'),
    },
  ])
}

async function removeConnection(conn: ConnectionConfig) {
  if (conn.id == null) return
  const ok = await confirmDialog(`确定删除连接「${conn.name}」吗？该操作不可恢复。`, {
    title: '删除连接',
    danger: true,
    okText: '删除',
  })
  if (!ok) return
  await store.removeConnection(conn.id)
  tabsStore.closeTabsByConnection(conn.id)
  toast.success('连接已删除')
}

async function doExport(conn: ConnectionConfig, db: string, table: string, format: ExportFormat) {
  if (conn.id == null) return
  try {
    await exportTasksStore.startExport(conn.id, db, table, format)
  } catch {
    /* 拦截器已提示 */
  }
}

async function copyText(text: string, tip: string) {
  try {
    await navigator.clipboard.writeText(text)
    toast.success(tip)
  } catch {
    // 剪贴板不可用的降级
    const ta = document.createElement('textarea')
    ta.value = text
    document.body.appendChild(ta)
    ta.select()
    document.execCommand('copy')
    document.body.removeChild(ta)
    toast.success(tip)
  }
}
</script>

<template>
  <div class="conn-tree">
    <div class="tree-search">
      <input v-model="keyword" class="input" placeholder="搜索表名…" spellcheck="false" />
    </div>

    <div class="tree-body">
      <!-- 分组 -->
      <template v-for="g in store.groups" :key="'g' + g.id">
        <div
          class="tree-node"
          :style="{ paddingLeft: '10px' }"
          @click="toggleGroup(g.id)"
          @contextmenu="onContextMenu($event, { kind: 'group', group: g })"
        >
          <span class="twisty">{{ expanded('g:' + g.id) ? '▾' : '▸' }}</span>
          <IconGroup />
          <span class="node-label">{{ g.name }}</span>
        </div>

        <template v-if="expanded('g:' + g.id)">
          <div
            v-for="conn in store.connectionsOfGroup(g.id)"
            :key="'c' + conn.id"
          >
            <div
              class="tree-node"
              :style="{ paddingLeft: '26px' }"
              @click="toggleConnection(conn)"
              @contextmenu="onContextMenu($event, { kind: 'connection', conn })"
            >
              <span class="twisty">{{ expanded('c:' + conn.id) ? '▾' : '▸' }}</span>
              <IconConn />
              <span class="node-label">{{ conn.name }}</span>
            </div>

            <template v-if="expanded('c:' + conn.id)">
              <div v-if="store.databasesLoading[conn.id ?? -1]" class="tree-hint" :style="{ paddingLeft: '46px' }">加载中…</div>
              <template v-else>
                <div v-for="db in store.databases[conn.id ?? -1] ?? []" :key="db">
                  <div
                    class="tree-node"
                    :style="{ paddingLeft: '42px' }"
                    @click="toggleDatabase(conn, db)"
                    @contextmenu="onContextMenu($event, { kind: 'database', conn, db })"
                  >
                    <span class="twisty">{{ expanded(`c:${conn.id}/d:${db}`) ? '▾' : '▸' }}</span>
                    <IconDb />
                    <span class="node-label">{{ db }}</span>
                  </div>

                  <template v-if="expanded(`c:${conn.id}/d:${db}`)">
                    <div v-if="tablesLoading(conn.id, db)" class="tree-hint" :style="{ paddingLeft: '62px' }">加载中…</div>
                    <template v-else>
                      <div
                        v-for="t in tablesOf(conn.id, db)"
                        :key="t.name"
                        class="tree-node leaf"
                        :style="{ paddingLeft: '58px' }"
                        @click="onTableClick(conn, db, t)"
                        @contextmenu="onContextMenu($event, { kind: 'table', conn, db, table: t })"
                      >
                        <IconView v-if="isView(t)" />
                        <IconTable v-else />
                        <span class="node-label">{{ t.name }}</span>
                      </div>
                      <div v-if="tablesOf(conn.id, db).length === 0" class="tree-hint" :style="{ paddingLeft: '62px' }">
                        {{ keyword ? '无匹配表' : '无表' }}
                      </div>
                    </template>
                  </template>
                </div>
                <div v-if="(store.databases[conn.id ?? -1] ?? []).length === 0" class="tree-hint" :style="{ paddingLeft: '46px' }">无数据库</div>
              </template>
            </template>
          </div>
          <div v-if="store.connectionsOfGroup(g.id).length === 0" class="tree-hint" :style="{ paddingLeft: '30px' }">空分组</div>
        </template>
      </template>

      <!-- 未分组连接 -->
      <div v-for="conn in store.ungroupedConnections" :key="'uc' + conn.id">
        <div
          class="tree-node"
          :style="{ paddingLeft: '10px' }"
          @click="toggleConnection(conn)"
          @contextmenu="onContextMenu($event, { kind: 'connection', conn })"
        >
          <span class="twisty">{{ expanded('c:' + conn.id) ? '▾' : '▸' }}</span>
          <IconConn />
          <span class="node-label">{{ conn.name }}</span>
        </div>

        <template v-if="expanded('c:' + conn.id)">
          <div v-if="store.databasesLoading[conn.id ?? -1]" class="tree-hint" :style="{ paddingLeft: '30px' }">加载中…</div>
          <template v-else>
            <div v-for="db in store.databases[conn.id ?? -1] ?? []" :key="db">
              <div
                class="tree-node"
                :style="{ paddingLeft: '26px' }"
                @click="toggleDatabase(conn, db)"
                @contextmenu="onContextMenu($event, { kind: 'database', conn, db })"
              >
                <span class="twisty">{{ expanded(`c:${conn.id}/d:${db}`) ? '▾' : '▸' }}</span>
                <IconDb />
                <span class="node-label">{{ db }}</span>
              </div>

              <template v-if="expanded(`c:${conn.id}/d:${db}`)">
                <div v-if="tablesLoading(conn.id, db)" class="tree-hint" :style="{ paddingLeft: '46px' }">加载中…</div>
                <template v-else>
                  <div
                    v-for="t in tablesOf(conn.id, db)"
                    :key="t.name"
                    class="tree-node leaf"
                    :style="{ paddingLeft: '42px' }"
                    @click="onTableClick(conn, db, t)"
                    @contextmenu="onContextMenu($event, { kind: 'table', conn, db, table: t })"
                  >
                    <IconView v-if="isView(t)" />
                    <IconTable v-else />
                    <span class="node-label">{{ t.name }}</span>
                  </div>
                  <div v-if="tablesOf(conn.id, db).length === 0" class="tree-hint" :style="{ paddingLeft: '46px' }">
                    {{ keyword ? '无匹配表' : '无表' }}
                  </div>
                </template>
              </template>
            </div>
            <div v-if="(store.databases[conn.id ?? -1] ?? []).length === 0" class="tree-hint" :style="{ paddingLeft: '30px' }">无数据库</div>
          </template>
        </template>
      </div>

      <div v-if="!store.loading && store.connections.length === 0" class="tree-empty">
        <p>暂无连接</p>
        <button class="btn btn-primary btn-sm" @click="emit('new-connection')">新建连接</button>
      </div>
      <div v-if="store.loading" class="tree-hint" style="padding: 12px">加载中…</div>
    </div>

    <ContextMenu :visible="menuVisible" :x="menuX" :y="menuY" :items="menuItems" @close="menuVisible = false" />
  </div>
</template>

<style scoped>
.conn-tree {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.tree-search {
  padding: 8px;
  border-bottom: 1px solid var(--border-soft);
}

.tree-body {
  flex: 1;
  overflow: auto;
  padding: 4px 0 12px;
}

.tree-node {
  display: flex;
  align-items: center;
  gap: 5px;
  height: 26px;
  padding-right: 8px;
  cursor: pointer;
  color: var(--text-1);
  font-size: 12.5px;
  user-select: none;
  white-space: nowrap;
}

.tree-node:hover {
  background: var(--bg-hover);
  color: var(--text-0);
}

.tree-node.leaf {
  color: var(--text-0);
}

.twisty {
  width: 12px;
  font-size: 9px;
  color: var(--text-2);
  text-align: center;
  flex-shrink: 0;
}

.node-label {
  overflow: hidden;
  text-overflow: ellipsis;
}

.tree-hint {
  color: var(--text-2);
  font-size: 12px;
  height: 24px;
  line-height: 24px;
}

.tree-empty {
  padding: 24px 12px;
  text-align: center;
  color: var(--text-2);
  font-size: 12.5px;
}

.tree-empty p {
  margin: 0 0 10px;
}

:deep(.node-icon) {
  flex-shrink: 0;
}

:deep(.ic-group) {
  color: var(--yellow);
}

:deep(.ic-conn) {
  color: var(--green);
}

:deep(.ic-db) {
  color: var(--accent);
}

:deep(.ic-table) {
  color: var(--text-2);
}

:deep(.ic-view) {
  color: #b57edc;
}
</style>
