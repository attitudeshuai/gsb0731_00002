<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import SqlEditor from './SqlEditor.vue'
import DataGrid, { type GridColumn } from './DataGrid.vue'
import SaveQueryModal from './modals/SaveQueryModal.vue'
import { cancelQueryExecution, executeQuery } from '@/api/query'
import { useConnectionStore } from '@/stores/connections'
import { useQueryStore } from '@/stores/query'
import type { QueryTabData } from '@/stores/tabs'
import { downloadResultSet } from '@/utils/exporters'
import { formatDuration, formatNumber } from '@/utils/format'
import { toast } from '@/ui/toast'
import type { ExportFormat, QueryResultItem } from '@/types'

const props = defineProps<{ tab: QueryTabData }>()

const connStore = useConnectionStore()
const queryStore = useQueryStore()

const editorRef = ref<InstanceType<typeof SqlEditor> | null>(null)

// ---------- 数据库选择 ----------
const connection = computed(() => connStore.connectionById(props.tab.connectionId))
const databases = computed(() => connStore.databases[props.tab.connectionId] ?? [])
const selectedDb = ref(props.tab.database || '')

watch(selectedDb, (db) => {
  props.tab.database = db || undefined
  void loadCompletion()
})

// ---------- 补全数据 ----------
const completionTables = computed(() => {
  if (!selectedDb.value) return []
  return (connStore.tables[`${props.tab.connectionId}/${selectedDb.value}`] ?? []).map((t) => t.name)
})
const completionColumns = computed(() =>
  selectedDb.value ? connStore.columnsOfDatabase(props.tab.connectionId, selectedDb.value) : [],
)

async function loadCompletion() {
  const db = selectedDb.value
  if (!db) return
  await connStore.loadTables(props.tab.connectionId, db)
  const tables = connStore.tables[`${props.tab.connectionId}/${db}`] ?? []
  // 后台预取部分表的列名用于补全
  tables.slice(0, 20).forEach((t) => void connStore.ensureColumns(props.tab.connectionId, db, t.name))
}

onMounted(async () => {
  await connStore.loadDatabases(props.tab.connectionId)
  if (!selectedDb.value) {
    selectedDb.value =
      props.tab.database || connection.value?.defaultDatabase || databases.value[0] || ''
  }
  await loadCompletion()
})

// ---------- 执行 ----------
const executing = ref(false)
const cancelling = ref(false)
const elapsedMs = ref(0)
const results = ref<QueryResultItem[]>([])
const totalDuration = ref(0)
const activeResult = ref(0)
const executedAt = ref('')

let elapsedTimer = 0
let currentExecutionId = ''

const elapsedText = computed(() => `${(elapsedMs.value / 1000).toFixed(1)}s`)

/** 当前连接配置的查询超时（秒），用于超时提示 */
const queryTimeoutSeconds = computed(() => connection.value?.queryTimeoutSeconds ?? 60)

async function run(mode: 'all' | 'selection') {
  let sqlText = mode === 'selection' ? (editorRef.value?.getSelectionText() ?? '').trim() : ''
  if (!sqlText) sqlText = props.tab.sql.trim()
  if (!sqlText) {
    toast.info('请输入要执行的 SQL')
    return
  }
  if (!selectedDb.value) {
    toast.info('请选择数据库')
    return
  }
  executing.value = true
  cancelling.value = false
  const startedAt = Date.now()
  elapsedMs.value = 0
  elapsedTimer = window.setInterval(() => {
    elapsedMs.value = Date.now() - startedAt
  }, 100)
  currentExecutionId = crypto.randomUUID()
  try {
    const resp = await executeQuery(props.tab.connectionId, {
      database: selectedDb.value,
      sql: sqlText,
      executionId: currentExecutionId,
    })
    results.value = resp.results
    totalDuration.value = resp.totalDurationMs
    activeResult.value = 0
    executedAt.value = new Date().toLocaleTimeString()
    if (resp.results.length === 0) toast.info('执行完成，无结果返回')
  } catch {
    /* 拦截器已提示 */
  } finally {
    executing.value = false
    currentExecutionId = ''
    window.clearInterval(elapsedTimer)
    elapsedTimer = 0
  }
}

/** 取消当前执行：调用取消接口后，原执行请求会以 errorKind=cancelled 的错误结果返回 */
async function cancelRun() {
  if (!executing.value || !currentExecutionId || cancelling.value) return
  cancelling.value = true
  try {
    await cancelQueryExecution(currentExecutionId)
  } catch {
    /* 404（不存在或已结束）等情况静默忽略，等待原请求返回 */
  } finally {
    cancelling.value = false
  }
}

const activeResultItem = computed(() => results.value[activeResult.value] ?? null)

const resultGridColumns = computed<GridColumn[]>(() =>
  (activeResultItem.value?.columns ?? []).map((c) => ({
    key: c.name,
    title: c.name,
    type: c.type,
    sortable: false,
    filterable: false,
  })),
)

function resultTitle(r: QueryResultItem, i: number) {
  if (r.type === 'error') return `错误 ${i + 1}`
  return r.type === 'resultSet' ? `结果 ${i + 1}（${formatNumber(r.rowCount)} 行）` : `影响 ${formatNumber(r.rowCount)} 行`
}

// ---------- 错误结果展示 ----------
function errorStateTitle(r: QueryResultItem) {
  if (r.errorKind === 'cancelled') return '已取消'
  if (r.errorKind === 'timeout') return `执行超时（超过 ${queryTimeoutSeconds.value}s）`
  return '执行错误'
}

function errorStateClass(r: QueryResultItem) {
  if (r.errorKind === 'cancelled') return 'qt-err-cancelled'
  if (r.errorKind === 'timeout') return 'qt-err-timeout'
  return 'qt-err-error'
}

// ---------- 导出结果（前端生成） ----------
const exportMenuOpen = ref(false)

function exportActive(fmt: ExportFormat) {
  exportMenuOpen.value = false
  const r = activeResultItem.value
  if (!r || r.type !== 'resultSet') {
    toast.info('当前结果集不可导出')
    return
  }
  downloadResultSet('query_result', r.columns, r.rows, fmt)
  toast.success(`已导出 query_result.${fmt}`)
}

// ---------- 保存收藏 ----------
const saveModalVisible = ref(false)

function openSaveModal() {
  if (!props.tab.sql.trim()) {
    toast.info('编辑器内容为空')
    return
  }
  saveModalVisible.value = true
}

async function onSaveQuery(payload: { name: string; folderId: number | null }) {
  await queryStore.saveQuery({ ...payload, sqlText: props.tab.sql })
  toast.success('已保存到收藏')
}

// ---------- 编辑器高度拖拽 ----------
const editorHeight = ref(240)
let splitDrag: { startY: number; startH: number } | null = null

function startSplit(ev: MouseEvent) {
  splitDrag = { startY: ev.clientY, startH: editorHeight.value }
  document.body.classList.add('dg-col-resizing')
  window.addEventListener('mousemove', onSplit)
  window.addEventListener('mouseup', stopSplit, { once: true })
}

function onSplit(ev: MouseEvent) {
  if (!splitDrag) return
  editorHeight.value = Math.min(640, Math.max(120, splitDrag.startH + ev.clientY - splitDrag.startY))
}

function stopSplit() {
  splitDrag = null
  document.body.classList.remove('dg-col-resizing')
  window.removeEventListener('mousemove', onSplit)
}

onBeforeUnmount(() => {
  window.removeEventListener('mousemove', onSplit)
  if (elapsedTimer) window.clearInterval(elapsedTimer)
})
</script>

<template>
  <div class="query-tab">
    <!-- 工具栏 -->
    <div class="qt-toolbar">
      <button v-if="!executing" class="btn btn-primary btn-sm" @click="run('all')">
        执行 <span class="kbd">Ctrl+Enter</span>
      </button>
      <button v-else class="btn btn-danger btn-sm" :disabled="cancelling" @click="cancelRun">
        {{ cancelling ? '取消中…' : '取消' }}
      </button>
      <span v-if="executing" class="qt-elapsed" title="已耗时">{{ elapsedText }}</span>
      <button class="btn btn-sm" :disabled="executing" @click="run('selection')">
        执行选中 <span class="kbd">Ctrl+Shift+Enter</span>
      </button>
      <span class="toolbar-sep"></span>
      <select v-model="selectedDb" class="input qt-db-select" title="目标数据库">
        <option value="" disabled>选择数据库</option>
        <option v-for="db in databases" :key="db" :value="db">{{ db }}</option>
      </select>
      <span class="qt-conn-name" :title="connection ? `${connection.host}:${connection.port}` : ''">
        {{ connection?.name ?? '连接已删除' }}
      </span>
      <span class="spacer"></span>
      <button class="btn btn-sm" @click="openSaveModal">收藏</button>
      <div class="qt-export">
        <button class="btn btn-sm" @click="exportMenuOpen = !exportMenuOpen">导出结果 ▾</button>
        <div v-if="exportMenuOpen" class="qt-export-mask" @click="exportMenuOpen = false"></div>
        <div v-if="exportMenuOpen" class="qt-export-menu">
          <div class="qt-export-item" @click="exportActive('csv')">CSV</div>
          <div class="qt-export-item" @click="exportActive('json')">JSON</div>
          <div class="qt-export-item" @click="exportActive('sql')">SQL</div>
        </div>
      </div>
    </div>

    <!-- 编辑器 -->
    <div class="qt-editor" :style="{ height: editorHeight + 'px' }">
      <SqlEditor
        ref="editorRef"
        v-model="tab.sql"
        :tables="completionTables"
        :columns="completionColumns"
        @execute="run"
      />
    </div>
    <div class="qt-splitter" @mousedown="startSplit"></div>

    <!-- 结果区 -->
    <div class="qt-results">
      <div v-if="results.length > 1" class="qt-result-tabs">
        <button
          v-for="(r, i) in results"
          :key="i"
          class="qt-result-tab"
          :class="{ active: i === activeResult }"
          @click="activeResult = i"
        >
          {{ resultTitle(r, i) }}
        </button>
      </div>
      <div class="qt-result-body">
        <div v-if="activeResultItem && activeResultItem.type === 'resultSet'" class="qt-resultset">
          <div v-if="activeResultItem.truncated" class="qt-truncated-banner">
            结果已截断，仅显示前 1000 行（共返回行数受限制），请添加 LIMIT 或筛选条件
          </div>
          <div class="qt-resultset-grid">
            <DataGrid
              :columns="resultGridColumns"
              :rows="activeResultItem.rows"
              :loading="executing"
              empty-text="结果集为空"
            />
          </div>
        </div>
        <div
          v-else-if="activeResultItem && activeResultItem.type === 'error'"
          class="qt-error-state"
          :class="errorStateClass(activeResultItem)"
        >
          <div class="qt-error-title">{{ errorStateTitle(activeResultItem) }}</div>
          <div v-if="activeResultItem.message && activeResultItem.errorKind !== 'cancelled'" class="qt-error-message">
            {{ activeResultItem.message }}
          </div>
          <div class="qt-error-hint">耗时 {{ formatDuration(activeResultItem.durationMs) }}</div>
        </div>
        <div v-else-if="activeResultItem" class="qt-update-count">
          <div class="qt-update-num">{{ formatNumber(activeResultItem.rowCount) }}</div>
          <div class="qt-update-label">影响行数 · 耗时 {{ formatDuration(activeResultItem.durationMs) }}</div>
        </div>
        <div v-else class="qt-no-result">{{ executing ? '执行中…' : '执行 SQL 后在此显示结果' }}</div>
      </div>
      <div class="qt-statusbar">
        <template v-if="executing">
          <span class="spinner"></span><span>执行中… {{ elapsedText }}</span>
        </template>
        <template v-else-if="results.length">
          <span>总耗时 {{ formatDuration(totalDuration) }}</span>
          <span>·</span>
          <span>{{ results.length }} 个结果</span>
          <template v-if="activeResultItem && activeResultItem.type === 'resultSet'">
            <span>·</span>
            <span>返回 {{ formatNumber(activeResultItem.rowCount) }} 行</span>
          </template>
          <span>·</span>
          <span>{{ executedAt }}</span>
        </template>
        <span v-else>就绪</span>
      </div>
    </div>

    <SaveQueryModal v-model:visible="saveModalVisible" @save="onSaveQuery" />
  </div>
</template>

<style scoped>
.query-tab {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  background: var(--bg-1);
}

.qt-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  background: var(--bg-2);
  border-bottom: 1px solid var(--border-soft);
  flex-shrink: 0;
}

.toolbar-sep {
  width: 1px;
  height: 18px;
  background: var(--border);
  margin: 0 2px;
}

.kbd {
  font-size: 10px;
  opacity: 0.6;
  margin-left: 4px;
}

.qt-db-select {
  width: 160px;
}

.qt-conn-name {
  color: var(--text-2);
  font-size: 12px;
}

.spacer {
  flex: 1;
}

.qt-export {
  position: relative;
}

.qt-export-mask {
  position: fixed;
  inset: 0;
  z-index: 900;
}

.qt-export-menu {
  position: absolute;
  right: 0;
  top: calc(100% + 4px);
  background: var(--bg-2);
  border: 1px solid var(--border);
  border-radius: 6px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.5);
  padding: 4px;
  z-index: 901;
  min-width: 96px;
}

.qt-export-item {
  padding: 6px 12px;
  font-size: 12.5px;
  color: var(--text-0);
  border-radius: 4px;
  cursor: pointer;
}

.qt-export-item:hover {
  background: var(--accent);
  color: #fff;
}

.qt-editor {
  flex-shrink: 0;
  border-bottom: 1px solid var(--border);
}

.qt-splitter {
  height: 4px;
  cursor: row-resize;
  background: transparent;
  flex-shrink: 0;
}

.qt-splitter:hover {
  background: var(--accent);
  opacity: 0.4;
}

.qt-results {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.qt-result-tabs {
  display: flex;
  gap: 2px;
  padding: 4px 8px 0;
  background: var(--bg-2);
  border-bottom: 1px solid var(--border-soft);
  flex-shrink: 0;
  overflow-x: auto;
}

.qt-result-tab {
  background: none;
  border: none;
  border-bottom: 2px solid transparent;
  color: var(--text-1);
  font-size: 12px;
  padding: 5px 10px;
  cursor: pointer;
  white-space: nowrap;
}

.qt-result-tab:hover {
  color: var(--text-0);
}

.qt-result-tab.active {
  color: var(--accent);
  border-bottom-color: var(--accent);
}

.qt-result-body {
  flex: 1;
  min-height: 0;
}

.qt-resultset {
  height: 100%;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.qt-resultset-grid {
  flex: 1;
  min-height: 0;
}

.qt-truncated-banner {
  flex-shrink: 0;
  padding: 6px 12px;
  font-size: 12px;
  color: var(--yellow);
  background: rgba(210, 153, 34, 0.12);
  border-bottom: 1px solid rgba(210, 153, 34, 0.35);
}

.qt-error-state {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 16px;
}

.qt-error-title {
  font-size: 15px;
  font-weight: 600;
}

.qt-err-error .qt-error-title {
  color: var(--red);
}

.qt-err-timeout .qt-error-title {
  color: var(--yellow);
}

.qt-err-cancelled .qt-error-title {
  color: var(--text-1);
}

.qt-error-message {
  max-width: 640px;
  max-height: 40%;
  overflow: auto;
  padding: 8px 12px;
  border-radius: 5px;
  font-size: 12px;
  font-family: var(--font-mono);
  color: var(--red);
  background: rgba(245, 83, 75, 0.1);
  border: 1px solid rgba(245, 83, 75, 0.3);
  word-break: break-all;
  white-space: pre-wrap;
}

.qt-err-timeout .qt-error-message {
  color: var(--yellow);
  background: rgba(210, 153, 34, 0.1);
  border-color: rgba(210, 153, 34, 0.3);
}

.qt-error-hint {
  color: var(--text-2);
  font-size: 11.5px;
}

.qt-elapsed {
  color: var(--text-1);
  font-size: 12px;
  font-family: var(--font-mono);
  min-width: 40px;
}

.qt-update-count {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

.qt-update-num {
  font-size: 40px;
  font-weight: 700;
  color: var(--green);
  font-family: var(--font-mono);
}

.qt-update-label {
  color: var(--text-1);
  font-size: 12.5px;
}

.qt-no-result {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-2);
  font-size: 12.5px;
}

.qt-statusbar {
  height: 24px;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 0 10px;
  background: var(--bg-2);
  border-top: 1px solid var(--border-soft);
  color: var(--text-1);
  font-size: 11.5px;
  flex-shrink: 0;
}
</style>
