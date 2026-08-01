<template>
  <div class="table-viewer">
    <div class="table-toolbar">
      <div class="toolbar-left">
        <span class="table-title">{{ tableName }}</span>
        <span class="table-db">{{ databaseName }}</span>
      </div>
      <div class="toolbar-right">
        <button class="btn btn-secondary btn-sm" @click="addRow" title="新增行">+ 新增</button>
        <button class="btn btn-danger btn-sm" @click="deleteSelected" :disabled="!selectedRow" title="删除行">删除</button>
        <div class="toolbar-divider" />
        <button class="btn btn-ghost btn-sm" @click="openFilter" title="筛选">筛选</button>
        <select v-model="pageSize" class="page-size-select" @change="reload">
          <option :value="50">50</option>
          <option :value="100">100</option>
          <option :value="200">200</option>
          <option :value="500">500</option>
          <option :value="1000">1000</option>
        </select>
        <div class="export-group">
          <button class="btn btn-ghost btn-sm" @click="exportData('csv')">CSV</button>
          <button class="btn btn-ghost btn-sm" @click="exportData('json')">JSON</button>
          <button class="btn btn-ghost btn-sm" @click="exportData('sql')">SQL</button>
        </div>
      </div>
    </div>

    <div v-if="showFilter" class="filter-bar">
      <select v-model="filterColumn" class="filter-select">
        <option value="">选择列...</option>
        <option v-for="col in columns" :key="col.name" :value="col.name">{{ col.name }}</option>
      </select>
      <select v-model="filterOperator" class="filter-select">
        <option value="contains">包含</option>
        <option value="eq">等于</option>
        <option value="neq">不等于</option>
        <option value="gt">大于</option>
        <option value="gte">大于等于</option>
        <option value="lt">小于</option>
        <option value="lte">小于等于</option>
        <option value="startswith">开头是</option>
        <option value="endswith">结尾是</option>
      </select>
      <input v-model="filterValue" class="filter-input" placeholder="筛选值" @keyup.enter="reload" />
      <button class="btn btn-primary btn-sm" @click="applyFilter">应用</button>
      <button class="btn btn-ghost btn-sm" @click="clearFilter">清除</button>
    </div>

    <div class="table-wrapper">
      <VirtualTable
        ref="virtualTableRef"
        :columns="columns"
        :rows="rows"
        :loading="loading"
        :sort-column="sortColumn"
        :sort-direction="sortDirection"
        :primary-keys="primaryKeyColumns"
        @sort="onSort"
        @edit="onCellEdit"
        @select="onRowSelect"
      />
    </div>

    <div class="table-footer">
      <div class="footer-info">
        共 {{ total }} 行，第 {{ page }} / {{ totalPages }} 页
      </div>
      <div class="footer-pagination">
        <button class="btn btn-ghost btn-sm" :disabled="page <= 1" @click="goPage(1)">«</button>
        <button class="btn btn-ghost btn-sm" :disabled="page <= 1" @click="goPage(page - 1)">‹</button>
        <span class="page-indicator">{{ page }}</span>
        <button class="btn btn-ghost btn-sm" :disabled="page >= totalPages" @click="goPage(page + 1)">›</button>
        <button class="btn btn-ghost btn-sm" :disabled="page >= totalPages" @click="goPage(totalPages)">»</button>
      </div>
    </div>

    <ExportProgressModal
      :visible="exportProgress.visible"
      :file-name="exportProgress.fileName"
      :progress="exportProgress.progress"
      :error="exportProgress.error"
      :cancelling="exportProgress.cancelling"
      @close="closeExportModal"
      @cancel="cancelExport"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import VirtualTable from './VirtualTable.vue'
import ExportProgressModal from '@/components/common/ExportProgressModal.vue'
import { queryApi, exportApi } from '@/api'
import { useToastStore } from '@/stores/toast'
import { DownloadAbortedError, DownloadIncompleteError } from '@/utils/download'
import type { ColumnMeta, PrimaryKeyColumn, FilterCondition } from '@/types'

const props = defineProps<{
  connectionId: number
  databaseName: string
  tableName: string
}>()

const toastStore = useToastStore()

const columns = ref<ColumnMeta[]>([])
const primaryKeys = ref<PrimaryKeyColumn[]>([])
const rows = ref<Record<string, any>[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(100)
const loading = ref(false)
const selectedRow = ref<Record<string, any> | null>(null)
const virtualTableRef = ref<InstanceType<typeof VirtualTable> | null>(null)
const sortColumn = ref('')
const sortDirection = ref<'asc' | 'desc'>('asc')
const showFilter = ref(false)
const filterColumn = ref('')
const filterOperator = ref('contains')
const filterValue = ref('')

const primaryKeyColumns = computed(() => primaryKeys.value.map(pk => pk.columnName))
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))

const filters = ref<FilterCondition[]>([])

async function reload() {
  loading.value = true
  try {
    const body: any = {
      page: page.value,
      pageSize: pageSize.value
    }
    if (sortColumn.value) {
      body.sortColumn = sortColumn.value
      body.sortDirection = sortDirection.value
    }
    if (filters.value.length > 0) {
      body.filters = filters.value
    }
    const data = await queryApi.getTableData(
      props.connectionId, props.databaseName, props.tableName, body
    )
    columns.value = data.columns
    primaryKeys.value = data.primaryKeys
    rows.value = data.rows
    total.value = data.total
    page.value = data.page
  } catch (e: any) {
    toastStore.error(e.message)
  } finally {
    loading.value = false
  }
}

function onSort(column: string) {
  if (sortColumn.value === column) {
    sortDirection.value = sortDirection.value === 'asc' ? 'desc' : 'asc'
  } else {
    sortColumn.value = column
    sortDirection.value = 'asc'
  }
  page.value = 1
  reload()
}

async function onCellEdit(row: Record<string, any>, column: string, value: any) {
  if (primaryKeys.value.length === 0) {
    toastStore.error('该表没有主键，无法编辑')
    return
  }
  const pk: Record<string, any> = {}
  for (const key of primaryKeyColumns.value) {
    pk[key] = row[key]
  }
  try {
    await queryApi.updateRow(props.connectionId, props.databaseName, props.tableName, {
      primaryKey: pk,
      data: { [column]: value }
    })
    row[column] = value
    toastStore.success('更新成功')
  } catch (e: any) {
    toastStore.error(e.message)
    reload()
  }
}

function addRow() {
  const newRow: Record<string, any> = {}
  for (const col of columns.value) {
    newRow[col.name] = col.defaultValue || null
  }
  const saveRow = { ...newRow }
  queryApi.insertRow(props.connectionId, props.databaseName, props.tableName, { newRow: saveRow })
    .then(() => {
      toastStore.success('新增成功')
      reload()
    })
    .catch((e: any) => toastStore.error(e.message))
}

function onRowSelect(row: Record<string, any> | null) {
  selectedRow.value = row
}

function deleteSelected() {
  if (!selectedRow.value) return
  const row = selectedRow.value
  if (!confirm('确定删除该行？')) return
  if (primaryKeys.value.length === 0) {
    toastStore.error('该表没有主键，无法删除')
    return
  }
  const pk: Record<string, any> = {}
  for (const key of primaryKeyColumns.value) {
    pk[key] = row[key]
  }
  queryApi.deleteRow(props.connectionId, props.databaseName, props.tableName, { primaryKey: pk })
    .then(() => {
      toastStore.success('删除成功')
      selectedRow.value = null
      virtualTableRef.value?.clearSelection()
      reload()
    })
    .catch((e: any) => toastStore.error(e.message))
}

function openFilter() {
  showFilter.value = !showFilter.value
}

function clearFilter() {
  filterColumn.value = ''
  filterValue.value = ''
  filters.value = []
  page.value = 1
  reload()
}

function applyFilter() {
  if (!filterColumn.value || filterValue.value === '') {
    clearFilter()
    return
  }
  filters.value = [{
    column: filterColumn.value,
    operator: filterOperator.value,
    value: filterValue.value
  }]
  page.value = 1
  reload()
}

function goPage(p: number) {
  page.value = p
  reload()
}

const exportProgress = ref<{ visible: boolean; progress: any; error: string | null; fileName: string; cancelling: boolean }>({
  visible: false,
  progress: null,
  error: null,
  fileName: '',
  cancelling: false
})
let exportAbortController: AbortController | null = null
let exportRequestId: string | null = null
let exportAborted = false

async function exportData(format: string) {
  exportAbortController = new AbortController()
  exportRequestId = null
  exportAborted = false
  exportProgress.value = {
    visible: true,
    progress: { receivedBytes: 0, totalBytes: null, percentage: 0, done: false },
    error: null,
    fileName: `${props.tableName}.${format}`,
    cancelling: false
  }
  try {
    await exportApi.streamExportTable(
      props.connectionId,
      props.databaseName,
      props.tableName,
      format,
      (p) => {
        exportProgress.value.progress = p
      },
      exportAbortController.signal,
      (rid) => { exportRequestId = rid }
    )
    toastStore.success('导出完成')
  } catch (e: any) {
    if (e instanceof DownloadAbortedError || exportAborted) {
      exportProgress.value.error = '导出已取消'
      toastStore.info('导出已取消')
    } else if (e instanceof DownloadIncompleteError) {
      exportProgress.value.error = e.message
      toastStore.error('导出失败：连接中断，文件不完整，未保存')
    } else {
      exportProgress.value.error = e.message
      toastStore.error(e.message)
    }
  } finally {
    exportAbortController = null
    exportRequestId = null
    exportProgress.value.cancelling = false
  }
}

async function cancelExport() {
  if (!exportAbortController) return
  exportProgress.value.cancelling = true
  exportAborted = true
  if (exportRequestId) {
    try {
      await queryApi.cancel(exportRequestId)
    } catch {
      // ignore
    }
  }
  exportAbortController.abort()
}

function closeExportModal() {
  if (exportAbortController) {
    cancelExport()
    return
  }
  exportProgress.value.visible = false
}

watch(() => [props.connectionId, props.databaseName, props.tableName], () => {
  page.value = 1
  sortColumn.value = ''
  filters.value = []
  reload()
})

onMounted(reload)
</script>

<style scoped>
.table-viewer {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}
.table-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 12px;
  background: var(--bg-secondary);
  border-bottom: 1px solid var(--border-color);
  flex-shrink: 0;
}
.toolbar-left { display: flex; align-items: center; gap: 10px; }
.table-title { font-weight: 600; font-size: 13px; }
.table-db { font-size: 11px; color: var(--text-muted); }
.toolbar-right { display: flex; align-items: center; gap: 6px; }
.toolbar-divider { width: 1px; height: 20px; background: var(--border-color); margin: 0 4px; }
.page-size-select { padding: 3px 6px; font-size: 12px; }
.export-group { display: flex; gap: 2px; margin-left: 6px; }
.filter-bar {
  display: flex;
  gap: 6px;
  padding: 6px 12px;
  background: var(--bg-tertiary);
  border-bottom: 1px solid var(--border-color);
  align-items: center;
  flex-shrink: 0;
}
.filter-select, .filter-input { padding: 3px 8px; font-size: 12px; }
.filter-input { flex: 1; max-width: 240px; }
.table-wrapper { flex: 1; overflow: hidden; }
.table-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 12px;
  background: var(--bg-secondary);
  border-top: 1px solid var(--border-color);
  flex-shrink: 0;
  font-size: 12px;
  color: var(--text-secondary);
}
.footer-pagination { display: flex; align-items: center; gap: 4px; }
.page-indicator { padding: 0 8px; color: var(--text-primary); }
</style>
