<template>
  <div class="table-tab">
    <div class="toolbar">
      <div class="left">
        <strong>{{ database }}.{{ table }}</strong>
        <span class="muted">{{ total }} rows</span>
      </div>
      <div class="right">
        <button class="icon-btn" @click="addFilter" title="Add filter">⧩ Filter</button>
        <button class="icon-btn" @click="addRow" title="Add row">＋ Row</button>
        <button
          class="icon-btn"
          @click="deleteSelectedRows"
          :disabled="!hasPendingChanges && selectedRow === null"
          title="Delete row"
        >🗑 Delete</button>
        <button class="primary" @click="saveChanges" :disabled="!hasPendingChanges">
          Save Changes ({{ pendingCount }})
        </button>
        <button @click="reload" title="Reload">⟳</button>
        <select v-model="exportFormat" class="fmt" title="Export format">
          <option value="csv">CSV</option>
          <option value="json">JSON</option>
          <option value="sql">SQL INSERT</option>
        </select>
        <button @click="doExport" title="Export">⭳ Export</button>
      </div>
    </div>

    <div v-if="filters.length" class="filter-bar">
      <div v-for="(f, i) in filters" :key="i" class="filter-chip">
        <select v-model="f.column">
          <option v-for="c in columns" :key="c.name" :value="c.name">{{ c.name }}</option>
        </select>
        <select v-model="f.operator">
          <option value="eq">=</option>
          <option value="ne">≠</option>
          <option value="gt">&gt;</option>
          <option value="ge">≥</option>
          <option value="lt">&lt;</option>
          <option value="le">≤</option>
          <option value="like">LIKE</option>
          <option value="is_null">IS NULL</option>
          <option value="is_not_null">IS NOT NULL</option>
        </select>
        <input
          v-if="f.operator !== 'is_null' && f.operator !== 'is_not_null'"
          v-model="f.value"
          placeholder="value"
        />
        <button class="icon-btn" @click="removeFilter(i)">✕</button>
      </div>
      <button class="primary sm" @click="reload">Apply</button>
    </div>

    <div class="grid-wrap" @click="captureRowSelect">
      <DataGrid
        :columns="columns"
        :rows="rows"
        :editable="true"
        :sort-column="sortColumn"
        :sort-direction="sortDirection"
        :edited-cells="editedCells"
        :new-row-indices="newRowIndices"
        :deleted-row-indices="deletedRowIndices"
        @sort="onSort"
        @edit-cell="onEditCell"
      />
    </div>

    <div class="pager">
      <button @click="firstPage" :disabled="page <= 1">«</button>
      <button @click="prevPage" :disabled="page <= 1">‹</button>
      <span>Page {{ page }} / {{ totalPages }}</span>
      <button @click="nextPage" :disabled="page >= totalPages">›</button>
      <button @click="lastPage" :disabled="page >= totalPages">»</button>
      <label class="pagesize">
        Per page
        <select v-model.number="pageSize" @change="reload">
          <option :value="50">50</option>
          <option :value="100">100</option>
          <option :value="200">200</option>
          <option :value="500">500</option>
          <option :value="1000">1000</option>
        </select>
      </label>
    </div>

    <SavePreviewModal
      v-if="showPreview"
      :statements="previewStatements"
      :busy="saving"
      @confirm="confirmSave"
      @cancel="cancelPreview"
    />

    <ExportProgressModal
      v-if="exportJob.active.value && exportJob.progress.value"
      :progress="exportJob.progress.value"
      :label="exportJob.label.value"
      :cancelling="exportJob.cancelling.value"
      @cancel="exportJob.cancel"
      @close="exportJob.close"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import DataGrid from './DataGrid.vue'
import SavePreviewModal from './SavePreviewModal.vue'
import ExportProgressModal from './ExportProgressModal.vue'
import type { ColumnMeta, Filter, RowChange, PreviewStatement } from '@/types'
import { queryApi } from '@/api'
import { useExportJob } from '@/composables/useExportJob'
import { useUiStore } from '@/stores/ui'

const props = defineProps<{
  connectionId: number
  database: string
  table: string
}>()

const ui = useUiStore()
const exportFormat = ref<'csv' | 'json' | 'sql'>('csv')
const exportJob = useExportJob(() => props.connectionId)

const columns = ref<ColumnMeta[]>([])
const rows = ref<any[][]>([])
const primaryKeys = ref<string[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(100)
const sortColumn = ref<string | undefined>(undefined)
const sortDirection = ref<'ASC' | 'DESC'>('ASC')
const filters = reactive<Filter[]>([])

const selectedRow = ref<number | null>(null)

// pending changes state
const editedCells = reactive(new Map<string, any>())          // "rowIndex:col" -> value
const newRowIndices = reactive(new Set<number>())
const deletedRowIndices = reactive(new Set<number>())
// snapshot of original key values per row (for update/delete WHERE)
const originalRows = ref<any[][]>([])

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))
const pendingCount = computed(() => editedCells.size + newRowIndices.size + deletedRowIndices.size)
const hasPendingChanges = computed(() => pendingCount.value > 0)

// save preview / confirm state
const showPreview = ref(false)
const previewStatements = ref<PreviewStatement[]>([])
const saving = ref(false)
let pendingChanges: RowChange[] | null = null

async function reload() {
  clearPending()
  try {
    const res = await queryApi.browse(props.connectionId, {
      database: props.database,
      table: props.table,
      page: page.value,
      pageSize: pageSize.value,
      sortColumn: sortColumn.value,
      sortDirection: sortDirection.value,
      filters: filters.filter((f) => f.column)
    })
    columns.value = res.columns
    rows.value = res.rows.map((r) => [...r])
    originalRows.value = res.rows.map((r) => [...r])
    primaryKeys.value = res.primaryKeys
    total.value = res.total
  } catch (e: any) {
    ui.error(e.message)
  }
}

function clearPending() {
  editedCells.clear()
  newRowIndices.clear()
  deletedRowIndices.clear()
  selectedRow.value = null
}

function onSort(col: string) {
  if (sortColumn.value === col) {
    sortDirection.value = sortDirection.value === 'ASC' ? 'DESC' : 'ASC'
  } else {
    sortColumn.value = col
    sortDirection.value = 'ASC'
  }
  page.value = 1
  reload()
}

function onEditCell(payload: { rowIndex: number; column: string; value: any }) {
  editedCells.set(payload.rowIndex + ':' + payload.column, payload.value)
}

function addFilter() {
  filters.push({ column: columns.value[0]?.name ?? '', operator: 'eq', value: '' })
}
function removeFilter(i: number) {
  filters.splice(i, 1)
}

function addRow() {
  const empty = columns.value.map(() => null)
  rows.value.push(empty)
  originalRows.value.push([...empty])
  newRowIndices.add(rows.value.length - 1)
}

function captureRowSelect(e: MouseEvent) {
  // best-effort: find row index from clicked cell's row
  const tr = (e.target as HTMLElement).closest('tr')
  if (!tr) return
  const rownum = tr.querySelector('.rownum-col')?.textContent
  if (rownum) {
    const idx = parseInt(rownum, 10) - 1
    if (!isNaN(idx)) selectedRow.value = idx
  }
}

function deleteSelectedRows() {
  if (selectedRow.value === null) {
    ui.info('Click a row to select it first')
    return
  }
  const idx = selectedRow.value
  if (newRowIndices.has(idx)) {
    // a brand new row -> just discard visually by marking deleted
    newRowIndices.delete(idx)
  }
  deletedRowIndices.add(idx)
}

/** Assembles the pending row changes into the backend RowChange payload. */
function collectChanges(): RowChange[] {
  const changes: RowChange[] = []

  // inserts
  for (const idx of newRowIndices) {
    if (deletedRowIndices.has(idx)) continue
    const values: Record<string, any> = {}
    columns.value.forEach((c, ci) => {
      let v = rows.value[idx][ci]
      const editKey = idx + ':' + c.name
      if (editedCells.has(editKey)) v = editedCells.get(editKey)
      if (v !== null && v !== undefined && v !== '') values[c.name] = v
    })
    changes.push({ type: 'insert', values })
  }

  // updates (edited cells on existing rows)
  const editedRowIdx = new Set<number>()
  for (const key of editedCells.keys()) {
    const idx = parseInt(key.split(':')[0], 10)
    if (!newRowIndices.has(idx) && !deletedRowIndices.has(idx)) editedRowIdx.add(idx)
  }
  for (const idx of editedRowIdx) {
    const values: Record<string, any> = {}
    columns.value.forEach((c) => {
      const editKey = idx + ':' + c.name
      if (editedCells.has(editKey)) values[c.name] = editedCells.get(editKey)
    })
    changes.push({ type: 'update', values, keys: buildKeys(idx) })
  }

  // deletes (existing rows)
  for (const idx of deletedRowIndices) {
    if (newRowIndices.has(idx)) continue
    changes.push({ type: 'delete', keys: buildKeys(idx) })
  }

  return changes
}

/** Step 1: ask the backend to render the exact statements, then show them for review. */
async function saveChanges() {
  const changes = collectChanges()
  if (changes.length === 0) {
    ui.info('No changes to save')
    return
  }
  pendingChanges = changes
  try {
    const res = await queryApi.preview(props.connectionId, {
      database: props.database,
      table: props.table,
      changes
    })
    previewStatements.value = res.preview || []
    showPreview.value = true
  } catch (e: any) {
    ui.error(e.message)
  }
}

/** Step 2: user confirmed — execute the batch in a single transaction. */
async function confirmSave() {
  if (!pendingChanges) return
  saving.value = true
  try {
    const res = await queryApi.save(props.connectionId, {
      database: props.database,
      table: props.table,
      changes: pendingChanges
    })
    ui.success(
      `Saved: ${res.inserted} inserted, ${res.updated} updated, ${res.deleted} deleted`
    )
    showPreview.value = false
    pendingChanges = null
    await reload()
  } catch (e: any) {
    ui.error(e.message)
  } finally {
    saving.value = false
  }
}

function cancelPreview() {
  showPreview.value = false
  pendingChanges = null
}

/** Builds the primary-key (or full-row fallback) WHERE identifiers from original values. */
function buildKeys(rowIndex: number): Record<string, any> {
  const keys: Record<string, any> = {}
  const keyCols = primaryKeys.value.length ? primaryKeys.value : columns.value.map((c) => c.name)
  keyCols.forEach((name) => {
    const ci = columns.value.findIndex((c) => c.name === name)
    keys[name] = originalRows.value[rowIndex][ci]
  })
  return keys
}

function doExport() {
  exportJob.startTable(props.database, props.table, exportFormat.value)
}

function firstPage() { page.value = 1; reload() }
function prevPage() { if (page.value > 1) { page.value--; reload() } }
function nextPage() { if (page.value < totalPages.value) { page.value++; reload() } }
function lastPage() { page.value = totalPages.value; reload() }

onMounted(reload)
</script>

<style scoped>
.table-tab {
  display: flex;
  flex-direction: column;
  height: 100%;
}
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 10px;
  border-bottom: 1px solid var(--border);
  gap: 10px;
}
.toolbar .left { display: flex; gap: 10px; align-items: baseline; }
.toolbar .right { display: flex; gap: 6px; align-items: center; }
.filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 6px 10px;
  border-bottom: 1px solid var(--border);
  background: var(--bg-alt);
  align-items: center;
}
.filter-chip {
  display: flex;
  gap: 4px;
  align-items: center;
}
.filter-chip select, .filter-chip input {
  width: auto;
  min-width: 90px;
}
button.sm { padding: 3px 10px; }
.grid-wrap {
  flex: 1;
  overflow: hidden;
  min-height: 0;
}
.pager {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  border-top: 1px solid var(--border);
  font-size: 12px;
}
.pager .pagesize {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 6px;
}
.pager .pagesize select { width: auto; }
</style>
