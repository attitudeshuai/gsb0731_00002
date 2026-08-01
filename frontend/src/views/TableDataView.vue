<template>
  <div class="table-data-view">
    <div class="tdv-toolbar">
      <button class="btn btn-default btn-sm" @click="goBack">← 返回</button>
      <div class="tdv-title">
        <span class="tdv-db">{{ dbName }}</span>
        <span class="tdv-sep">.</span>
        <span class="tdv-table">{{ tableName }}</span>
      </div>
      <div class="tdv-actions">
        <button class="btn btn-default btn-sm" @click="loadData" :disabled="loading">↻ 刷新</button>
        <button class="btn btn-primary btn-sm" @click="onAddRow">+ 新增行</button>
        <div class="tdv-export">
          <template v-if="!exporting">
            <button class="btn btn-default btn-sm" @click="toggleExportMenu">
              📥 导出 ▾
            </button>
            <div v-if="exportMenuVisible" class="export-dropdown">
              <div @click="onExport('csv')">导出 CSV</div>
              <div @click="onExport('json')">导出 JSON</div>
              <div @click="onExport('sql')">导出 SQL</div>
            </div>
          </template>
          <template v-else>
            <span class="tdv-export-progress" :title="`已下载 ${formatBytes(exportProgress.loaded)}`">
              {{ formatExportProgress(exportProgress) }}
            </span>
            <button class="btn btn-danger btn-sm" @click="onCancelExport">⏹ 取消导出</button>
          </template>
        </div>
      </div>
    </div>
    <div class="tdv-tabs">
      <button
        class="tdv-tab"
        :class="{ active: activeTab === 'data' }"
        @click="activeTab = 'data'"
      >数据</button>
      <button
        class="tdv-tab"
        :class="{ active: activeTab === 'structure' }"
        @click="switchToStructure"
      >表结构</button>
      <button
        class="tdv-tab"
        :class="{ active: activeTab === 'ddl' }"
        @click="switchToDDL"
      >建表语句</button>
    </div>
    <div class="tdv-content">
      <div v-if="activeTab === 'data'" class="tdv-data-panel">
        <div class="tdv-filter-row" v-if="tableColumns.length">
          <div v-if="selectable" class="tdv-filter-cell tdv-checkbox-cell"></div>
          <div v-if="showIndex" class="tdv-filter-cell tdv-index-cell"></div>
          <div
            v-for="col in tableColumns"
            :key="'f-' + col.key"
            class="tdv-filter-cell"
            :style="{ width: col.width + 'px' }"
          >
            <input
              v-model="filters[col.key]"
              class="tdv-filter-input"
              placeholder="筛选..."
              @input="onFilterChange"
            />
          </div>
          <div v-if="selectable" class="tdv-filter-cell tdv-action-cell"></div>
        </div>
        <div class="tdv-table-wrap">
          <VirtualScrollTable
            :columns="tableColumns"
            :rows="tableRows"
            :loading="loading"
            :selectable="selectable"
            show-index
            show-add-row
            row-height="36"
            height="100%"
            @sort-change="onSortChange"
            @cell-update="onCellUpdate"
            @row-delete="onRowDelete"
            @add-row="onAddRow"
          />
        </div>
        <div class="tdv-pagination">
          <div class="tdv-page-left">
            <span>每页</span>
            <select v-model.number="pageSize" class="tdv-page-select" @change="onPageSizeChange">
              <option v-for="s in pageSizes" :key="s" :value="s">{{ s }}</option>
            </select>
            <span>条</span>
            <label class="tdv-select-label">
              <input type="checkbox" v-model="selectable" /> 行选择
            </label>
          </div>
          <div class="tdv-page-right">
            <span class="tdv-total">共 {{ total }} 条</span>
            <button
              class="btn btn-default btn-sm"
              :disabled="page <= 1"
              @click="goPage(page - 1)"
            >上一页</button>
            <span class="tdv-page-num">{{ page }} / {{ totalPages }}</span>
            <button
              class="btn btn-default btn-sm"
              :disabled="page >= totalPages"
              @click="goPage(page + 1)"
            >下一页</button>
          </div>
        </div>
      </div>
      <div v-else-if="activeTab === 'structure'" class="tdv-structure-panel">
        <h3 class="tdv-section-title">列信息</h3>
        <div class="tdv-info-table-wrap">
          <table class="tdv-info-table">
            <thead>
              <tr>
                <th>列名</th>
                <th>类型</th>
                <th>可空</th>
                <th>默认值</th>
                <th>主键</th>
                <th>自增</th>
                <th>注释</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="col in columns" :key="col.name">
                <td class="col-name">{{ col.name }}</td>
                <td class="col-type">{{ col.type }}</td>
                <td>{{ col.nullable ? '是' : '否' }}</td>
                <td>{{ col.defaultValue ?? '-' }}</td>
                <td>
                  <span v-if="col.primaryKey" class="badge badge-primary">PK</span>
                </td>
                <td>{{ col.autoIncrement ? '是' : '' }}</td>
                <td>{{ col.comment }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <h3 class="tdv-section-title" v-if="indexes.length">索引信息</h3>
        <div class="tdv-info-table-wrap" v-if="indexes.length">
          <table class="tdv-info-table">
            <thead>
              <tr>
                <th>索引名</th>
                <th>列</th>
                <th>唯一</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(idx, i) in indexes" :key="i">
                <td>{{ idx.name }}</td>
                <td>{{ idx.column }}</td>
                <td>{{ idx.unique ? '是' : '否' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-if="structureLoading" class="tdv-loading-inner">加载中...</div>
      </div>
      <div v-else class="tdv-ddl-panel">
        <div class="tdv-ddl-header">
          <span>建表语句</span>
          <button class="btn btn-default btn-sm" @click="copyDDL">📋 复制</button>
        </div>
        <pre class="tdv-ddl-code"><code>{{ ddl || '加载中...' }}</code></pre>
      </div>
    </div>
    <ConfirmDialog
      :visible="confirmVisible"
      type="danger"
      title="删除行"
      message="确定要删除选中的行吗？"
      @confirm="confirmDeleteRows"
      @cancel="confirmVisible = false"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import VirtualScrollTable from '@/components/common/VirtualScrollTable.vue'
import ConfirmDialog from '@/components/common/ConfirmDialog.vue'
import { useToast } from '@/composables/useToast'
import {
  getTableData,
  getTableStructure,
  getTableDDL,
  updateTableRow,
  insertTableRow,
  deleteTableRow
} from '@/api/metadata'
import { exportData, cancelQuery, type ExportHandle } from '@/api/query'
import type { ColumnMeta } from '@/types'

const route = useRoute()
const router = useRouter()
const toast = useToast()

const connectionId = computed(() => Number(route.params.connectionId))
const dbName = computed(() => route.params.dbName as string)
const tableName = computed(() => route.params.tableName as string)

const activeTab = ref<'data' | 'structure' | 'ddl'>('data')
const loading = ref(false)
const structureLoading = ref(false)
const tableRows = ref<Record<string, any>[]>([])
const columns = ref<ColumnMeta[]>([])
const ddl = ref('')
const total = ref(0)
const page = ref(1)
const pageSize = ref(50)
const pageSizes = [20, 50, 100, 500]
const sortKey = ref<string>('')
const sortOrder = ref<'asc' | 'desc' | null>(null)
const filters = ref<Record<string, string>>({})
const selectable = ref(false)
const showIndex = true
const exportMenuVisible = ref(false)
const exporting = ref(false)
const exportProgress = ref({ loaded: 0, total: 0 })
const exportHandle = ref<ExportHandle | null>(null)
const exportExecutionId = ref('')
const confirmVisible = ref(false)
const pendingDeleteRows = ref<Record<string, any>[]>([])

const indexes = ref<{ name: string; column: string; unique: boolean }[]>([])

const tableColumns = computed(() =>
  columns.value.map((c) => ({
    key: c.name,
    title: c.name,
    type: mapColumnType(c.type),
    editable: true,
    sortable: true,
    width: 160
  }))
)

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))

function mapColumnType(dbType: string): 'text' | 'number' | 'date' {
  const t = dbType.toLowerCase()
  if (/(int|decimal|float|double|number|numeric)/.test(t)) return 'number'
  if (/(date|time|timestamp)/.test(t)) return 'date'
  return 'text'
}

async function loadData() {
  loading.value = true
  try {
    const sort = sortKey.value && sortOrder.value ? `${sortKey.value},${sortOrder.value}` : undefined
    const filterStr = Object.entries(filters.value)
      .filter(([, v]) => v.trim())
      .map(([k, v]) => `${k}:${v}`)
      .join(';')
    const result = await getTableData(connectionId.value, dbName.value, tableName.value, {
      page: page.value - 1,
      size: pageSize.value,
      sort,
      filter: filterStr || undefined
    })
    columns.value = result.columns
    tableRows.value = result.rows
    total.value = result.total
  } catch (e: any) {
    toast.error(e.message || '加载数据失败')
  } finally {
    loading.value = false
  }
}

async function loadStructure() {
  structureLoading.value = true
  try {
    const data = await getTableStructure(connectionId.value, dbName.value, tableName.value)
    columns.value = data
    const idxMap: Record<string, { name: string; columns: string[]; unique: boolean }> = {}
    data.forEach((c) => {
      if (c.primaryKey) {
        if (!idxMap['PRIMARY']) idxMap['PRIMARY'] = { name: 'PRIMARY', columns: [], unique: true }
        idxMap['PRIMARY'].columns.push(c.name)
      }
    })
    indexes.value = Object.values(idxMap).map((i) => ({
      name: i.name,
      column: i.columns.join(', '),
      unique: i.unique
    }))
  } catch (e: any) {
    toast.error(e.message || '加载表结构失败')
  } finally {
    structureLoading.value = false
  }
}

async function loadDDL() {
  try {
    ddl.value = await getTableDDL(connectionId.value, dbName.value, tableName.value)
  } catch (e: any) {
    ddl.value = `加载失败: ${e.message}`
  }
}

function switchToStructure() {
  activeTab.value = 'structure'
  if (columns.value.length === 0) loadStructure()
}

function switchToDDL() {
  activeTab.value = 'ddl'
  if (!ddl.value) loadDDL()
}

function onSortChange(payload: { key: string; order: 'asc' | 'desc' | null }) {
  sortKey.value = payload.key
  sortOrder.value = payload.order
  page.value = 1
  loadData()
}

function onFilterChange() {
  page.value = 1
  loadData()
}

async function onCellUpdate(payload: { row: Record<string, any>; key: string; value: any; oldValue: any }) {
  try {
    const pk: Record<string, any> = {}
    columns.value.filter((c) => c.primaryKey).forEach((c) => {
      pk[c.name] = payload.row[c.name]
    })
    if (Object.keys(pk).length === 0) {
      pk[payload.key] = payload.oldValue
    }
    await updateTableRow(connectionId.value, dbName.value, tableName.value, {
      primaryKey: pk,
      row: { [payload.key]: payload.value }
    })
    toast.success('更新成功')
  } catch (e: any) {
    toast.error(e.message || '更新失败')
    payload.row[payload.key] = payload.oldValue
  }
}

function onRowDelete(payload: { row: Record<string, any> }) {
  pendingDeleteRows.value = [payload.row]
  confirmVisible.value = true
}

async function confirmDeleteRows() {
  try {
    for (const row of pendingDeleteRows.value) {
      const pk: Record<string, any> = {}
      columns.value.filter((c) => c.primaryKey).forEach((c) => {
        pk[c.name] = row[c.name]
      })
      if (Object.keys(pk).length === 0) {
        const firstCol = columns.value[0]
        if (firstCol) pk[firstCol.name] = row[firstCol.name]
      }
      await deleteTableRow(connectionId.value, dbName.value, tableName.value, pk)
    }
    toast.success('删除成功')
    confirmVisible.value = false
    pendingDeleteRows.value = []
    loadData()
  } catch (e: any) {
    toast.error(e.message || '删除失败')
  }
}

async function onAddRow() {
  try {
    const emptyRow: Record<string, any> = {}
    columns.value.forEach((c) => {
      emptyRow[c.name] = c.defaultValue ?? null
    })
    await insertTableRow(connectionId.value, dbName.value, tableName.value, emptyRow)
    toast.success('新增成功')
    loadData()
  } catch (e: any) {
    toast.error(e.message || '新增失败')
  }
}

function goPage(p: number) {
  page.value = p
  loadData()
}

function onPageSizeChange() {
  page.value = 1
  loadData()
}

function goBack() {
  router.back()
}

function toggleExportMenu() {
  exportMenuVisible.value = !exportMenuVisible.value
}

async function onExport(format: string) {
  exportMenuVisible.value = false
  const fmt = format === 'sql' ? 'SQL_INSERT' : format.toUpperCase()
  const executionId =
    (crypto as any).randomUUID?.() ||
    `export-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
  exporting.value = true
  exportProgress.value = { loaded: 0, total: 0 }
  exportExecutionId.value = executionId
  try {
    const handle = exportData(
      connectionId.value,
      {
        sql: `SELECT * FROM \`${tableName.value}\``,
        database: dbName.value,
        format: fmt,
        tableName: tableName.value
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

function formatExportProgress(p: { loaded: number; total: number }): string {
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

function copyDDL() {
  navigator.clipboard?.writeText(ddl.value).then(
    () => toast.success('已复制到剪贴板'),
    () => toast.error('复制失败')
  )
}

watch(
  () => route.query.tab,
  (tab) => {
    if (tab === 'ddl') switchToDDL()
    else if (tab === 'structure') switchToStructure()
  }
)

onMounted(() => {
  loadData()
  if (route.query.tab === 'ddl') {
    activeTab.value = 'ddl'
    loadDDL()
  } else if (route.query.tab === 'structure') {
    activeTab.value = 'structure'
    loadStructure()
  }
})
</script>

<style scoped lang="scss">
@use '@/styles/index.scss' as *;

.table-data-view {
  display: flex;
  flex-direction: column;
  height: 100%;
  margin: -20px;
  background: $bg-color;
  overflow: hidden;
}

.tdv-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 16px;
  background: #fff;
  border-bottom: 1px solid $border-color;
  flex-shrink: 0;
}

.tdv-title {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 15px;
  font-weight: 600;
}

.tdv-db {
  color: $text-secondary;
}

.tdv-sep {
  color: $text-light;
}

.tdv-table {
  color: $primary-color;
}

.tdv-actions {
  margin-left: auto;
  display: flex;
  gap: 8px;
  align-items: center;
}

.tdv-export {
  position: relative;
  display: flex;
  align-items: center;
  gap: 8px;
}

.tdv-export-progress {
  font-size: 12px;
  color: $text-secondary;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.export-dropdown {
  position: absolute;
  top: 100%;
  right: 0;
  margin-top: 4px;
  background: #fff;
  border: 1px solid $border-color;
  border-radius: $radius-sm;
  box-shadow: $shadow-md;
  min-width: 140px;
  z-index: 100;

  div {
    padding: 8px 14px;
    font-size: 13px;
    cursor: pointer;

    &:hover {
      background: $primary-light;
      color: $primary-color;
    }
  }
}

.tdv-tabs {
  display: flex;
  background: #fff;
  border-bottom: 1px solid $border-color;
  padding: 0 16px;
  flex-shrink: 0;
}

.tdv-tab {
  padding: 10px 20px;
  background: transparent;
  color: $text-secondary;
  font-size: 13px;
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;

  &.active {
    color: $primary-color;
    border-bottom-color: $primary-color;
  }

  &:hover:not(.active) {
    color: $text-primary;
  }
}

.tdv-content {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.tdv-data-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: 8px;
}

.tdv-filter-row {
  display: flex;
  background: #fff;
  border: 1px solid $border-color;
  border-bottom: none;
  border-radius: $radius-sm $radius-sm 0 0;
  padding: 6px 0;
  flex-shrink: 0;
}

.tdv-filter-cell {
  padding: 0 6px;
  flex-shrink: 0;
}

.tdv-checkbox-cell {
  width: 44px;
  display: flex;
  justify-content: center;
}

.tdv-index-cell {
  width: 56px;
}

.tdv-action-cell {
  width: 60px;
}

.tdv-filter-input {
  width: 100%;
  padding: 4px 8px;
  border: 1px solid $border-color;
  border-radius: 3px;
  font-size: 12px;

  &:focus {
    border-color: $primary-color;
  }
}

.tdv-table-wrap {
  flex: 1;
  overflow: hidden;
  border: 1px solid $border-color;
  border-radius: 0 0 $radius-sm $radius-sm;
}

.tdv-pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 4px;
  flex-shrink: 0;
}

.tdv-page-left,
.tdv-page-right {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: $text-secondary;
}

.tdv-page-select {
  padding: 4px 8px;
  border: 1px solid $border-color;
  border-radius: 3px;
}

.tdv-select-label {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-left: 12px;
  cursor: pointer;
}

.tdv-total {
  color: $text-secondary;
}

.tdv-page-num {
  padding: 0 8px;
  color: $text-secondary;
}

.tdv-structure-panel {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}

.tdv-section-title {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 10px;
  color: $text-primary;

  &:not(:first-child) {
    margin-top: 20px;
  }
}

.tdv-info-table-wrap {
  background: #fff;
  border: 1px solid $border-color;
  border-radius: $radius-sm;
  overflow: auto;
}

.tdv-info-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;

  th {
    background: #f7f8fa;
    padding: 10px 14px;
    text-align: left;
    font-weight: 600;
    color: $text-primary;
    border-bottom: 1px solid $border-color;
    white-space: nowrap;
  }

  td {
    padding: 8px 14px;
    border-bottom: 1px solid #f0f0f0;
    color: $text-secondary;
  }

  tr:hover td {
    background: #f9fafb;
  }
}

.col-name {
  font-weight: 600;
  color: $text-primary;
}

.col-type {
  font-family: Consolas, Monaco, monospace;
  color: $primary-color;
}

.badge {
  display: inline-block;
  padding: 1px 6px;
  border-radius: 3px;
  font-size: 11px;
  font-weight: 600;

  &-primary {
    background: $primary-light;
    color: $primary-color;
  }
}

.tdv-ddl-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 16px;
  overflow: hidden;
}

.tdv-ddl-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
  font-size: 14px;
  font-weight: 600;
}

.tdv-ddl-code {
  flex: 1;
  background: #282c34;
  color: #abb2bf;
  padding: 16px;
  border-radius: $radius-sm;
  overflow: auto;
  font-family: Consolas, Monaco, 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}

.tdv-loading-inner {
  text-align: center;
  padding: 40px;
  color: $text-light;
}
</style>
