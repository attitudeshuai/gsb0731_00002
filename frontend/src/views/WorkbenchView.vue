<script setup lang="ts">
import { h, onBeforeUnmount, onMounted, ref } from 'vue'
import TopToolbar from '@/components/TopToolbar.vue'
import ConnectionTree from '@/components/ConnectionTree.vue'
import QueryTab from '@/components/QueryTab.vue'
import TableDataTab from '@/components/TableDataTab.vue'
import TableStructureTab from '@/components/TableStructureTab.vue'
import ConnectionModal from '@/components/modals/ConnectionModal.vue'
import GroupManageModal from '@/components/modals/GroupManageModal.vue'
import HistoryDrawer from '@/components/drawers/HistoryDrawer.vue'
import SavedQueriesDrawer from '@/components/drawers/SavedQueriesDrawer.vue'
import ExportLogsDrawer from '@/components/drawers/ExportLogsDrawer.vue'
import ExportTasksOverlay from '@/components/ExportTasksOverlay.vue'
import { useConnectionStore } from '@/stores/connections'
import { useTabsStore, type WorkTab } from '@/stores/tabs'
import { toast } from '@/ui/toast'
import type { ConnectionConfig } from '@/types'

const connStore = useConnectionStore()
const tabsStore = useTabsStore()

onMounted(() => {
  void connStore.fetchAll()
})

// ---------- Tab 图标 ----------
function svgIcon(children: ReturnType<typeof h>[], cls: string) {
  return () =>
    h('svg', { viewBox: '0 0 16 16', width: 12, height: 12, fill: 'none', stroke: 'currentColor', 'stroke-width': 1.6, 'stroke-linecap': 'round', 'stroke-linejoin': 'round', class: `tab-icon ${cls}` }, children)
}
const IconQuery = svgIcon([h('path', { d: 'M5.5 4L2 8l3.5 4M10.5 4L14 8l-3.5 4' })], 'ti-query')
const IconData = svgIcon(
  [h('rect', { x: 2, y: 2.5, width: 12, height: 11, rx: 1 }), h('path', { d: 'M2 6.2h12M2 9.8h12M6.7 2.5v11M10 2.5v11' })],
  'ti-data',
)
const IconStruct = svgIcon([h('path', { d: 'M2.5 4h11M2.5 8h11M2.5 12h6' })], 'ti-struct')

// ---------- 弹窗 / 抽屉状态 ----------
const connModalVisible = ref(false)
const editingConnection = ref<ConnectionConfig | null>(null)
const presetGroupId = ref<number | null>(null)
const groupModalVisible = ref(false)
const activeDrawer = ref<'' | 'history' | 'saved' | 'exports'>('')

function openNewConnection(groupId?: number) {
  editingConnection.value = null
  presetGroupId.value = groupId ?? null
  connModalVisible.value = true
}

function openEditConnection(conn: ConnectionConfig) {
  editingConnection.value = conn
  presetGroupId.value = null
  connModalVisible.value = true
}

function openDrawer(which: 'history' | 'saved' | 'exports') {
  activeDrawer.value = which
}

// ---------- Tab 操作 ----------
function newQueryTab() {
  const connId = tabsStore.lastConnectionId ?? connStore.connections[0]?.id ?? null
  if (connId == null) {
    toast.info('请先创建连接')
    openNewConnection()
    return
  }
  const conn = connStore.connectionById(connId)
  tabsStore.openQueryTab(connId, tabsStore.lastDatabase || conn?.defaultDatabase || undefined)
}

function closeTab(tab: WorkTab, ev?: MouseEvent) {
  ev?.stopPropagation()
  tabsStore.closeTab(tab.id)
}

function onTabMiddleClick(tab: WorkTab, ev: MouseEvent) {
  if (ev.button === 1) {
    ev.preventDefault()
    tabsStore.closeTab(tab.id)
  }
}

// ---------- 侧栏宽度拖拽 ----------
const sidebarWidth = ref(264)
let sideDrag: { startX: number; startW: number } | null = null

function startSideDrag(ev: MouseEvent) {
  sideDrag = { startX: ev.clientX, startW: sidebarWidth.value }
  document.body.classList.add('dg-col-resizing')
  window.addEventListener('mousemove', onSideDrag)
  window.addEventListener('mouseup', stopSideDrag, { once: true })
}

function onSideDrag(ev: MouseEvent) {
  if (!sideDrag) return
  sidebarWidth.value = Math.min(480, Math.max(180, sideDrag.startW + ev.clientX - sideDrag.startX))
}

function stopSideDrag() {
  sideDrag = null
  document.body.classList.remove('dg-col-resizing')
  window.removeEventListener('mousemove', onSideDrag)
}

onBeforeUnmount(() => {
  window.removeEventListener('mousemove', onSideDrag)
})
</script>

<template>
  <div class="workbench">
    <TopToolbar
      @new-connection="openNewConnection()"
      @manage-groups="groupModalVisible = true"
      @open-drawer="openDrawer"
    />

    <div class="wb-body">
      <!-- 左侧连接树 -->
      <aside class="wb-sidebar" :style="{ width: sidebarWidth + 'px' }">
        <ConnectionTree
          @new-connection="openNewConnection"
          @edit-connection="openEditConnection"
          @manage-groups="groupModalVisible = true"
        />
      </aside>
      <div class="wb-sidebar-resizer" @mousedown="startSideDrag"></div>

      <!-- 右侧工作区 -->
      <main class="wb-main">
        <!-- Tab 栏 -->
        <div class="wb-tabbar">
          <div
            v-for="tab in tabsStore.tabs"
            :key="tab.id"
            class="wb-tab"
            :class="{ active: tab.id === tabsStore.activeTabId }"
            @click="tabsStore.setActive(tab.id)"
            @auxclick="onTabMiddleClick(tab, $event)"
            :title="tab.title"
          >
            <IconQuery v-if="tab.type === 'query'" />
            <IconData v-else-if="tab.type === 'table-data'" />
            <IconStruct v-else />
            <span class="wb-tab-title">{{ tab.title }}</span>
            <button class="wb-tab-close" @click="closeTab(tab, $event)">×</button>
          </div>
          <button class="wb-tab-add" title="新建查询" @click="newQueryTab">＋</button>
        </div>

        <!-- Tab 内容 -->
        <div class="wb-content">
          <template v-for="tab in tabsStore.tabs" :key="tab.id">
            <div v-show="tab.id === tabsStore.activeTabId" class="wb-tab-pane">
              <QueryTab v-if="tab.type === 'query'" :tab="tab" />
              <TableDataTab v-else-if="tab.type === 'table-data'" :tab="tab" />
              <TableStructureTab v-else :tab="tab" />
            </div>
          </template>

          <div v-if="tabsStore.tabs.length === 0" class="wb-welcome">
            <div class="wb-welcome-title">DBManager 工作台</div>
            <ul class="wb-welcome-list">
              <li>左侧展开连接，单击表名打开数据浏览</li>
              <li>右键节点：新建查询 / 查看建表语句 / 导出</li>
              <li>查询编辑器：<span class="kbd-w">Ctrl+Enter</span> 执行，<span class="kbd-w">Ctrl+Shift+Enter</span> 执行选中</li>
              <li>数据表格：双击单元格行内编辑，单击列头排序，列头漏斗图标筛选</li>
            </ul>
            <button class="btn btn-primary" @click="newQueryTab">新建查询</button>
          </div>
        </div>
      </main>
    </div>

    <!-- 弹窗 -->
    <ConnectionModal
      v-model:visible="connModalVisible"
      :connection="editingConnection"
      :preset-group-id="presetGroupId"
    />
    <GroupManageModal v-model:visible="groupModalVisible" />

    <!-- 抽屉 -->
    <HistoryDrawer :visible="activeDrawer === 'history'" @update:visible="activeDrawer = ''" />
    <SavedQueriesDrawer :visible="activeDrawer === 'saved'" @update:visible="activeDrawer = ''" />
    <ExportLogsDrawer :visible="activeDrawer === 'exports'" @update:visible="activeDrawer = ''" />

    <!-- 导出任务进度浮层 -->
    <ExportTasksOverlay />
  </div>
</template>

<style scoped>
.workbench {
  display: flex;
  flex-direction: column;
  height: 100vh;
  overflow: hidden;
}

.wb-body {
  flex: 1;
  display: flex;
  min-height: 0;
}

.wb-sidebar {
  flex-shrink: 0;
  background: var(--bg-2);
  border-right: 1px solid var(--border);
  min-height: 0;
}

.wb-sidebar-resizer {
  width: 4px;
  cursor: col-resize;
  flex-shrink: 0;
  background: transparent;
  margin-left: -2px;
  z-index: 5;
}

.wb-sidebar-resizer:hover {
  background: var(--accent);
  opacity: 0.4;
}

.wb-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
}

.wb-tabbar {
  display: flex;
  align-items: flex-end;
  background: var(--bg-2);
  border-bottom: 1px solid var(--border);
  padding: 6px 8px 0;
  gap: 2px;
  overflow-x: auto;
  flex-shrink: 0;
  min-height: 36px;
}

.wb-tab {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 0 8px 0 10px;
  height: 29px;
  background: var(--bg-1);
  border: 1px solid var(--border);
  border-bottom: none;
  border-radius: 6px 6px 0 0;
  color: var(--text-1);
  font-size: 12px;
  cursor: pointer;
  white-space: nowrap;
  max-width: 200px;
}

.wb-tab:hover {
  color: var(--text-0);
}

.wb-tab.active {
  background: var(--bg-1);
  color: var(--text-0);
  border-color: var(--accent);
  box-shadow: inset 0 2px 0 var(--accent);
}

.wb-tab-title {
  overflow: hidden;
  text-overflow: ellipsis;
}

.wb-tab-close {
  background: none;
  border: none;
  color: var(--text-2);
  cursor: pointer;
  font-size: 13px;
  padding: 0 2px;
  border-radius: 3px;
  line-height: 1;
}

.wb-tab-close:hover {
  color: #fff;
  background: var(--red);
}

.wb-tab-add {
  background: none;
  border: none;
  color: var(--text-2);
  cursor: pointer;
  font-size: 16px;
  padding: 2px 8px;
  margin-bottom: 3px;
  border-radius: 4px;
}

.wb-tab-add:hover {
  color: var(--text-0);
  background: var(--bg-hover);
}

.wb-content {
  flex: 1;
  min-height: 0;
  position: relative;
}

.wb-tab-pane {
  height: 100%;
  min-height: 0;
}

.wb-welcome {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 18px;
  color: var(--text-1);
}

.wb-welcome-title {
  font-size: 20px;
  font-weight: 700;
  color: var(--text-0);
}

.wb-welcome-list {
  margin: 0;
  padding-left: 20px;
  line-height: 2;
  font-size: 13px;
}

.kbd-w {
  font-family: var(--font-mono);
  font-size: 11px;
  background: var(--bg-3);
  border: 1px solid var(--border);
  border-radius: 4px;
  padding: 1px 5px;
}

:deep(.tab-icon) {
  flex-shrink: 0;
}

:deep(.ti-query) {
  color: var(--green);
}

:deep(.ti-data) {
  color: var(--accent);
}

:deep(.ti-struct) {
  color: var(--yellow);
}
</style>
