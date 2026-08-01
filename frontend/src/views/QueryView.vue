<template>
  <div class="query-view">
    <div class="qv-tabs">
      <div
        v-for="tab in queryStore.tabs"
        :key="tab.id"
        class="qv-tab"
        :class="{ active: tab.id === queryStore.activeTabId }"
        @click="queryStore.setActiveTab(tab.id)"
      >
        <span class="qv-tab-title">{{ getTabTitle(tab) }}</span>
        <span v-if="tab.dirty" class="qv-tab-dirty">●</span>
        <button class="qv-tab-close" @click.stop="queryStore.closeTab(tab.id)">✕</button>
      </div>
      <button class="qv-tab-add" @click="addNewTab" title="新建查询">+</button>
    </div>
    <div v-if="activeTab" class="qv-content">
      <div class="qv-toolbar">
        <select
          v-model="activeTab.connectionId"
          class="qv-select"
          @change="onConnectionChange"
        >
          <option :value="null">选择连接</option>
          <option v-for="conn in connectionStore.connections" :key="conn.id" :value="conn.id">
            {{ conn.name }}
          </option>
        </select>
        <select
          v-if="activeTab.connectionId"
          v-model="activeTab.database"
          class="qv-select"
        >
          <option value="">选择数据库</option>
          <option v-for="db in databases" :key="db" :value="db">{{ db }}</option>
        </select>
        <div class="qv-toolbar-divider"></div>
        <button v-if="!activeTab.loading" class="btn btn-primary btn-sm" @click="onExecute">
          ▶ 执行 (Ctrl+Enter)
        </button>
        <button v-if="activeTab.loading" class="btn btn-danger btn-sm" @click="onCancel">
          ⏹ 取消
        </button>
        <button class="btn btn-default btn-sm" :disabled="activeTab.loading" @click="onExecuteSelection">
          执行选中 (Ctrl+Shift+Enter)
        </button>
        <label class="qv-limit-label">
          行数上限
          <select
            class="qv-select qv-limit-select"
            :value="activeTab.maxRows"
            @change="onMaxRowsChange"
          >
            <option :value="100">100</option>
            <option :value="500">500</option>
            <option :value="1000">1000</option>
            <option :value="5000">5000</option>
            <option :value="10000">10000</option>
          </select>
        </label>
        <button class="btn btn-default btn-sm" @click="showSaveDialog = true">💾 保存</button>
        <div class="qv-toolbar-spacer"></div>
        <template v-if="!exporting">
          <button class="btn btn-default btn-sm" @click="onExport" :disabled="!activeTab.result">📥 导出 CSV</button>
        </template>
        <template v-else>
          <span class="qv-export-progress" :title="`已下载 ${formatBytes(exportProgress.loaded)}`">
            {{ formatProgress(exportProgress) }}
          </span>
          <button class="btn btn-danger btn-sm" @click="onCancelExport">⏹ 取消导出</button>
        </template>
      </div>
      <div class="qv-editor-wrap" ref="editorWrapRef">
        <SqlEditor
          v-if="activeTab"
          ref="editorRef"
          :model-value="activeTab.sql"
          @update:model-value="(v: string) => queryStore.updateTabSql(activeTab.id, v)"
          @execute="onExecute"
          @execute-selection="onExecuteSelection"
        />
      </div>
      <div class="qv-result-panel" :style="{ height: resultHeight + 'px' }">
        <div class="qv-result-header">
          <div class="qv-result-tabs">
            <button
              class="qv-result-tab"
              :class="{ active: resultTab === 'result' }"
              @click="resultTab = 'result'"
            >结果</button>
            <button
              class="qv-result-tab"
              :class="{ active: resultTab === 'history' }"
              @click="resultTab = 'history'; loadHistory()"
            >历史</button>
          </div>
          <div class="qv-result-status">
            <template v-if="activeTab.error">
              <span class="status-error">✕ {{ activeTab.error }}</span>
            </template>
            <template v-else-if="activeTab.result">
              <span class="status-item">耗时: {{ activeTab.result.executionTimeMs }}ms</span>
              <span class="status-item" v-if="activeTab.result.rows">
                行数: {{ activeTab.result.rows.length }}
              </span>
              <span class="status-item" v-if="activeTab.result.updateCount != null">
                影响行数: {{ activeTab.result.updateCount }}
              </span>
            </template>
            <template v-else-if="activeTab.loading">
              <span class="status-loading">执行中...</span>
            </template>
          </div>
          <div class="qv-resize-handle" @mousedown="startResize"></div>
        </div>
        <div class="qv-result-body">
          <div v-show="resultTab === 'result'" class="qv-result-content">
            <div v-if="activeTab.result?.truncated" class="qv-truncation-banner">
              ⚠️ 结果已被截断：仅返回前 {{ activeTab.result.rows?.length }} 行（上限 {{ activeTab.result.maxRows }} 行），
              完整结果可能更多。请添加 LIMIT 或 WHERE 条件以缩小查询范围。
            </div>
            <div v-if="activeTab.result" class="qv-table-wrap">
              <VirtualScrollTable
                :columns="resultColumns"
                :rows="resultRows"
                :loading="activeTab.loading"
                row-height="32"
                height="100%"
              />
            </div>
            <div v-else-if="!activeTab.loading" class="qv-empty-result">
              执行 SQL 后显示结果
            </div>
          </div>
          <div v-show="resultTab === 'history'" class="qv-history-list">
            <div
              v-for="h in historyList"
              :key="h.id"
              class="history-item"
              @click="loadHistoryToEditor(h)"
            >
              <div class="history-time">{{ formatTime(h.executedAt) }}</div>
              <div class="history-sql" :title="h.sqlText">{{ h.sqlText }}</div>
              <div class="history-meta">
                <span :class="h.success ? 'text-success' : 'text-error'">
                  {{ h.success ? '成功' : '失败' }}
                </span>
                <span>{{ h.executionTimeMs }}ms</span>
                <span v-if="h.rowCount != null">{{ h.rowCount }} 行</span>
              </div>
            </div>
            <div v-if="historyList.length === 0" class="qv-empty-result">暂无历史记录</div>
          </div>
        </div>
      </div>
    </div>
    <div v-else class="qv-empty">
      <div class="qv-empty-icon">📝</div>
      <p>点击上方 + 新建查询</p>
    </div>

    <transition name="fade">
      <div v-if="showSaveDialog" class="save-dialog-overlay" @click.self="showSaveDialog = false">
        <div class="save-dialog">
          <h3>保存查询</h3>
          <input v-model="saveName" class="form-input" placeholder="查询名称" />
          <div class="save-dialog-footer">
            <button class="btn btn-default" @click="showSaveDialog = false">取消</button>
            <button class="btn btn-primary" @click="onSaveQuery">保存</button>
          </div>
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useQueryStore, type QueryTab } from '@/stores/query'
import { useConnectionStore } from '@/stores/connection'
import { getDatabases } from '@/api/metadata'
import { getHistory, createSavedQuery, exportData, cancelQuery, type ExportHandle } from '@/api/query'
import type { QueryHistory } from '@/types'
import SqlEditor from '@/components/editor/SqlEditor.vue'
import VirtualScrollTable from '@/components/common/VirtualScrollTable.vue'
import { useToast } from '@/composables/useToast'

const route = useRoute()
const router = useRouter()
const queryStore = useQueryStore()
const connectionStore = useConnectionStore()
const toast = useToast()

const activeTab = computed(() => queryStore.activeTab)
const databases = ref<string[]>([])
const resultTab = ref<'result' | 'history'>('result')
const historyList = ref<QueryHistory[]>([])
const resultHeight = ref(280)
const showSaveDialog = ref(false)
const saveName = ref('')
const editorWrapRef = ref<HTMLElement | null>(null)
const editorRef = ref<InstanceType<typeof SqlEditor> | null>(null)
const exporting = ref(false)
const exportProgress = ref({ loaded: 0, total: 0 })
const exportHandle = ref<ExportHandle | null>(null)
const exportExecutionId = ref('')

const resultColumns = computed(() => {
  if (!activeTab.value?.result?.columns) return []
  return activeTab.value.result.columns.map((c) => ({ key: c, title: c, sortable: true }))
})

const resultRows = computed(() => {
  if (!activeTab.value?.result?.rows) return []
  const cols = activeTab.value.result.columns
  return activeTab.value.result.rows.map((row) => {
    const obj: Record<string, any> = {}
    cols.forEach((c, i) => {
      obj[c] = row[i]
    })
    return obj
  })
})

function getTabTitle(tab: QueryTab): string {
  if (tab.connectionId) {
    const conn = connectionStore.connections.find((c) => c.id === tab.connectionId)
    if (conn) {
      return tab.database ? `${conn.name}/${tab.database}` : conn.name
    }
  }
  return '新建查询'
}

function addNewTab() {
  queryStore.addTab()
}

async function onConnectionChange() {
  if (!activeTab.value?.connectionId) {
    databases.value = []
    return
  }
  try {
    databases.value = await getDatabases(activeTab.value.connectionId)
  } catch (e: any) {
    toast.error(e.message || '加载数据库列表失败')
  }
}

async function onExecute() {
  if (!activeTab.value) return
  await queryStore.executeQuery(activeTab.value.id)
  resultTab.value = 'result'
}

async function onExecuteSelection() {
  if (!activeTab.value) return
  const selected = editorRef.value?.getSelection() || ''
  if (selected.trim()) {
    await queryStore.executeSelection(activeTab.value.id, selected)
  } else {
    await queryStore.executeQuery(activeTab.value.id)
  }
  resultTab.value = 'result'
}

async function onCancel() {
  if (!activeTab.value) return
  await queryStore.cancelQuery(activeTab.value.id)
  toast.warning('正在取消查询...')
}

function onMaxRowsChange(e: Event) {
  if (!activeTab.value) return
  const val = Number((e.target as HTMLSelectElement).value)
  queryStore.setMaxRows(activeTab.value.id, val)
}

async function loadHistory() {
  try {
    const result = await getHistory({ size: 50 })
    historyList.value = result.content
  } catch (e: any) {
    toast.error(e.message || '加载历史失败')
  }
}

function loadHistoryToEditor(h: QueryHistory) {
  if (!activeTab.value) {
    const id = queryStore.addTab(h.connectionId || undefined)
    queryStore.updateTabSql(id, h.sqlText)
  } else {
    if (h.connectionId) activeTab.value.connectionId = h.connectionId
    queryStore.updateTabSql(activeTab.value.id, h.sqlText)
  }
  resultTab.value = 'result'
  toast.info('已加载到编辑器')
}

async function onSaveQuery() {
  if (!activeTab.value || !saveName.value.trim()) {
    toast.warning('请输入查询名称')
    return
  }
  try {
    await createSavedQuery({
      name: saveName.value.trim(),
      sqlText: activeTab.value.sql,
      connectionId: activeTab.value.connectionId
    })
    toast.success('保存成功')
    showSaveDialog.value = false
    saveName.value = ''
  } catch (e: any) {
    toast.error(e.message || '保存失败')
  }
}

async function onExport() {
  if (!activeTab.value?.result || !activeTab.value.connectionId) {
    toast.warning('没有可导出的结果')
    return
  }
  const executionId =
    (crypto as any).randomUUID?.() ||
    `export-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
  exporting.value = true
  exportProgress.value = { loaded: 0, total: 0 }
  exportExecutionId.value = executionId
  try {
    const handle = exportData(
      activeTab.value.connectionId,
      {
        sql: activeTab.value.sql,
        database: activeTab.value.database,
        format: 'csv'
      },
      (loaded, total) => {
        exportProgress.value = { loaded, total }
      },
      executionId
    )
    exportHandle.value = handle
    const result = await handle.done
    toast.success(`导出完成：${result.rowCount} 行，文件 ${formatBytes(result.fileSize)}`)
  } catch (e: any) {
    if (e.message === 'EXPORT_ABORTED') {
      toast.warning('导出已取消')
    } else {
      toast.error(e.message || '导出失败')
    }
  } finally {
    exporting.value = false
    exportHandle.value = null
    exportExecutionId.value = ''
    exportProgress.value = { loaded: 0, total: 0 }
  }
}

async function onCancelExport() {
  if (exportExecutionId.value) {
    try {
      await cancelQuery(exportExecutionId.value)
    } catch {
      // ignore cancel request errors
    }
  }
  exportHandle.value?.abort()
}

function formatProgress(p: { loaded: number; total: number }): string {
  if (p.total > 0) {
    return `${formatBytes(p.loaded)} / ${formatBytes(p.total)}`
  }
  return `${formatBytes(p.loaded)}`
}

function formatBytes(bytes: number): string {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(1024))
  return `${(bytes / Math.pow(1024, i)).toFixed(i === 0 ? 0 : 1)} ${units[i]}`
}

function formatTime(iso: string): string {
  if (!iso) return ''
  try {
    return new Date(iso).toLocaleString('zh-CN')
  } catch {
    return iso
  }
}

let resizing = false
let resizeStartY = 0
let resizeStartH = 0

function startResize(e: MouseEvent) {
  resizing = true
  resizeStartY = e.clientY
  resizeStartH = resultHeight.value
  document.addEventListener('mousemove', onResizing)
  document.addEventListener('mouseup', stopResize)
  document.body.style.cursor = 'ns-resize'
  document.body.style.userSelect = 'none'
}

function onResizing(e: MouseEvent) {
  if (!resizing) return
  const delta = resizeStartY - e.clientY
  resultHeight.value = Math.max(120, Math.min(600, resizeStartH + delta))
}

function stopResize() {
  resizing = false
  document.removeEventListener('mousemove', onResizing)
  document.removeEventListener('mouseup', stopResize)
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
}

watch(
  () => route.params.connectionId,
  (id) => {
    if (id && queryStore.tabs.length === 0) {
      const connId = Number(id)
      queryStore.addTab(connId)
      nextTick(() => onConnectionChange())
    }
  },
  { immediate: true }
)

onMounted(async () => {
  try {
    await connectionStore.fetchConnections()
    if (queryStore.tabs.length === 0 && !route.params.connectionId) {
      queryStore.addTab()
    }
    if (activeTab.value?.connectionId) {
      onConnectionChange()
    }
  } catch (e: any) {
    toast.error(e.message || '初始化失败')
  }
})
</script>

<style scoped lang="scss">
@use '@/styles/index.scss' as *;

.query-view {
  display: flex;
  flex-direction: column;
  height: 100%;
  margin: -20px;
  background: $bg-color;
  overflow: hidden;
}

.qv-tabs {
  display: flex;
  align-items: center;
  background: #2d2d3d;
  padding: 4px 8px 0;
  gap: 2px;
  overflow-x: auto;
  flex-shrink: 0;
}

.qv-tab {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  background: #3a3a4d;
  color: #a0a0b8;
  border-radius: 6px 6px 0 0;
  cursor: pointer;
  font-size: 13px;
  white-space: nowrap;
  transition: all 0.15s;

  &:hover {
    background: #454560;
    color: #fff;
  }

  &.active {
    background: $bg-color;
    color: $text-primary;
  }
}

.qv-tab-dirty {
  color: $warning-color;
  font-size: 10px;
}

.qv-tab-close {
  background: transparent;
  color: inherit;
  font-size: 11px;
  padding: 0 2px;
  border-radius: 3px;

  &:hover {
    background: rgba(0, 0, 0, 0.2);
  }
}

.qv-tab-add {
  background: transparent;
  color: #a0a0b8;
  font-size: 18px;
  padding: 4px 12px;
  border-radius: 4px;

  &:hover {
    background: #3a3a4d;
    color: #fff;
  }
}

.qv-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.qv-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  background: #fff;
  border-bottom: 1px solid $border-color;
  flex-shrink: 0;
}

.qv-select {
  padding: 6px 10px;
  border: 1px solid $border-color;
  border-radius: $radius-sm;
  font-size: 13px;
  background: #fff;
  min-width: 140px;
}

.qv-toolbar-divider {
  width: 1px;
  height: 24px;
  background: $border-color;
  margin: 0 4px;
}

.qv-toolbar-spacer {
  flex: 1;
}

.qv-limit-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: $text-secondary;
  white-space: nowrap;
}

.qv-limit-select {
  min-width: 80px;
  padding: 4px 8px;
  font-size: 12px;
}

.qv-truncation-banner {
  padding: 8px 14px;
  background: #fff8e1;
  border-bottom: 1px solid #ffe082;
  color: #8d6e00;
  font-size: 12px;
  line-height: 1.5;
  flex-shrink: 0;
}

.qv-export-progress {
  font-size: 12px;
  color: $text-secondary;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.qv-editor-wrap {
  flex: 1;
  min-height: 200px;
  display: flex;
  padding: 8px;
  background: $bg-color;
  overflow: hidden;
}

.qv-result-panel {
  background: #fff;
  border-top: 1px solid $border-color;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  position: relative;
}

.qv-result-header {
  display: flex;
  align-items: center;
  border-bottom: 1px solid $border-color;
  background: #f7f8fa;
  position: relative;
  flex-shrink: 0;
}

.qv-result-tabs {
  display: flex;
}

.qv-result-tab {
  padding: 8px 18px;
  background: transparent;
  color: $text-secondary;
  font-size: 13px;
  border-bottom: 2px solid transparent;

  &.active {
    color: $primary-color;
    border-bottom-color: $primary-color;
  }

  &:hover:not(.active) {
    background: rgba(0, 0, 0, 0.03);
  }
}

.qv-result-status {
  flex: 1;
  display: flex;
  gap: 16px;
  padding: 0 14px;
  font-size: 12px;
  color: $text-secondary;
}

.status-error {
  color: $error-color;
}

.status-loading {
  color: $primary-color;
}

.qv-resize-handle {
  position: absolute;
  top: -3px;
  left: 0;
  right: 0;
  height: 6px;
  cursor: ns-resize;
  background: transparent;

  &:hover {
    background: rgba(26, 115, 232, 0.2);
  }
}

.qv-result-body {
  flex: 1;
  overflow: hidden;
  position: relative;
}

.qv-result-content {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.qv-table-wrap {
  flex: 1;
  min-height: 0;
}

.qv-empty-result {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: $text-light;
  font-size: 13px;
}

.qv-history-list {
  height: 100%;
  overflow-y: auto;
  padding: 8px;
}

.history-item {
  padding: 10px 12px;
  border-bottom: 1px solid #f0f0f0;
  cursor: pointer;
  border-radius: 4px;

  &:hover {
    background: #f5f8ff;
  }
}

.history-time {
  font-size: 11px;
  color: $text-light;
  margin-bottom: 4px;
}

.history-sql {
  font-size: 12px;
  font-family: Consolas, Monaco, monospace;
  color: $text-primary;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-bottom: 4px;
}

.history-meta {
  display: flex;
  gap: 12px;
  font-size: 11px;
  color: $text-light;
}

.text-success {
  color: $success-color;
}

.text-error {
  color: $error-color;
}

.qv-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: $text-light;
  gap: 10px;
}

.qv-empty-icon {
  font-size: 48px;
  opacity: 0.5;
}

.save-dialog-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9995;
}

.save-dialog {
  width: 380px;
  background: #fff;
  border-radius: $radius-md;
  padding: 20px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.2);

  h3 {
    margin-bottom: 14px;
    font-size: 16px;
  }
}

.save-dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 16px;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
