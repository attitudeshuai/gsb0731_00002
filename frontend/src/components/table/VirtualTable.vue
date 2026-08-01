<template>
  <div class="v-table" ref="containerRef" @scroll="onScroll">
    <div class="v-table-header" :style="{ width: totalWidth + 'px' }">
      <div
        v-for="col in columns"
        :key="col.name"
        class="v-cell v-header-cell"
        :style="{ width: getColumnWidth(col) + 'px', left: getColumnOffset(col) + 'px' }"
        @click="onSort(col)"
      >
        <span class="header-name" :title="col.name">
          <span v-if="col.primaryKey" class="pk-icon" title="Primary Key">🔑</span>
          {{ col.name }}
        </span>
        <span class="sort-indicator" v-if="sortColumn === col.name">
          {{ sortDirection === 'desc' ? '▼' : '▲' }}
        </span>
      </div>
    </div>

    <div class="v-table-body" :style="{ height: bodyHeight + 'px', width: totalWidth + 'px' }">
      <div class="v-spacer-top" :style="{ height: topSpacer + 'px' }" />
      <div
        v-for="(row, idx) in visibleRows"
        :key="getRowKey(row, idx)"
        class="v-row"
        :class="{ selected: selectedRowIdx === startIndex + idx, 'new-row': isNewRow(row) }"
        :style="{ height: rowHeight + 'px', width: totalWidth + 'px' }"
        @click="selectRow(startIndex + idx, row)"
        @dblclick="() => onRowDblClick(row)"
      >
        <div
          v-for="col in columns"
          :key="col.name"
          class="v-cell"
          :style="{ width: getColumnWidth(col) + 'px', left: getColumnOffset(col) + 'px' }"
          @dblclick.stop="startEdit(startIndex + idx, col.name, row[col.name])"
        >
          <template v-if="editingCell.rowIdx === startIndex + idx && editingCell.column === col.name">
            <input
              ref="editInputRef"
              v-model="editingCell.value"
              class="cell-editor"
              @blur="commitEdit"
              @keydown.enter.prevent="commitEdit"
              @keydown.esc="cancelEdit"
            />
          </template>
          <template v-else>
            <span class="cell-value" :class="{ null: row[col.name] === null }">
              {{ formatCell(row[col.name]) }}
            </span>
          </template>
        </div>
      </div>
      <div class="v-spacer-bottom" :style="{ height: bottomSpacer + 'px' }" />
    </div>

    <div v-if="loading" class="v-table-loading">加载中...</div>
    <div v-if="!loading && rows.length === 0" class="v-table-empty">无数据</div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick, onMounted, reactive } from 'vue'
import type { ColumnMeta } from '@/types'

const props = withDefaults(defineProps<{
  columns: ColumnMeta[]
  rows: Record<string, any>[]
  loading?: boolean
  total?: number
  page?: number
  pageSize?: number
  rowHeight?: number
  overscan?: number
  sortColumn?: string
  sortDirection?: 'asc' | 'desc'
  primaryKeys?: string[]
  editable?: boolean
}>(), {
  loading: false,
  total: 0,
  page: 1,
  pageSize: 100,
  rowHeight: 32,
  overscan: 8,
  sortColumn: '',
  sortDirection: 'asc',
  primaryKeys: () => [],
  editable: true
})

const emit = defineEmits<{
  sort: [column: string]
  edit: [row: Record<string, any>, column: string, value: any]
  rowDblClick: [row: Record<string, any>]
  select: [row: Record<string, any> | null]
}>()

const containerRef = ref<HTMLDivElement>()
const bodyHeight = ref(400)
const scrollTop = ref(0)
const selectedRowIdx = ref(-1)
const editInputRef = ref<HTMLInputElement>()

const editingCell = reactive({
  rowIdx: -1,
  column: '',
  value: '' as any,
  originalRow: null as Record<string, any> | null
})

const columnWidths = computed(() => {
  const widths: Record<string, number> = {}
  for (const col of props.columns) {
    let w = 140
    if (col.type) {
      const t = col.type.toUpperCase()
      if (t.includes('TEXT') || t.includes('BLOB') || t.includes('JSON')) w = 200
      else if (t.includes('INT') || t.includes('BIT') || t.includes('BOOL')) w = 110
      else if (t.includes('DATE') || t.includes('TIME')) w = 170
      else if (t.includes('DECIMAL') || t.includes('FLOAT') || t.includes('DOUBLE')) w = 130
      else if (col.precision && col.precision > 20) w = Math.min(220, 80 + col.precision * 6)
    }
    widths[col.name] = w
  }
  return widths
})

const totalWidth = computed(() =>
  props.columns.reduce((sum, c) => sum + (columnWidths.value[c.name] || 140), 0)
)

function getColumnWidth(col: ColumnMeta) {
  return columnWidths.value[col.name] || 140
}

function getColumnOffset(col: ColumnMeta) {
  let offset = 0
  for (const c of props.columns) {
    if (c.name === col.name) break
    offset += getColumnWidth(c)
  }
  return offset
}

const startIndex = computed(() => {
  return Math.max(0, Math.floor(scrollTop.value / props.rowHeight) - props.overscan)
})

const visibleCount = computed(() => {
  const visible = Math.ceil(bodyHeight.value / props.rowHeight) + props.overscan * 2
  return Math.min(visible, props.rows.length - startIndex.value)
})

const visibleRows = computed(() => {
  const start = startIndex.value
  const end = Math.min(start + visibleCount.value, props.rows.length)
  return props.rows.slice(start, end)
})

const topSpacer = computed(() => startIndex.value * props.rowHeight)
const bottomSpacer = computed(() =>
  Math.max(0, (props.rows.length - startIndex.value - visibleRows.value.length) * props.rowHeight)
)

function onScroll(e: Event) {
  scrollTop.value = (e.target as HTMLDivElement).scrollTop
}

function onSort(col: ColumnMeta) {
  emit('sort', col.name)
}

function selectRow(idx: number, row: Record<string, any>) {
  selectedRowIdx.value = idx
  emit('select', row)
}

function formatCell(val: any): string {
  if (val === null || val === undefined) return 'NULL'
  if (typeof val === 'object') return JSON.stringify(val)
  return String(val)
}

function isNewRow(_row: Record<string, any>) {
  return false
}

function getRowKey(row: Record<string, any>, idx: number) {
  if (props.primaryKeys.length > 0) {
    const pk = props.primaryKeys.map(k => row[k]).join('::')
    if (pk) return pk
  }
  return startIndex.value + idx
}

function startEdit(rowIdx: number, column: string, value: any) {
  if (!props.editable) return
  editingCell.rowIdx = rowIdx
  editingCell.column = column
  editingCell.value = value === null ? '' : value
  editingCell.originalRow = { ...props.rows[rowIdx] }
  nextTick(() => editInputRef.value?.focus())
}

function commitEdit() {
  if (editingCell.rowIdx < 0) return
  const row = props.rows[editingCell.rowIdx]
  const col = editingCell.column
  let value: any = editingCell.value
  if (value === '' && row[col] !== null) value = null
  if (value !== row[col]) {
    emit('edit', row, col, value)
  }
  cancelEdit()
}

function cancelEdit() {
  editingCell.rowIdx = -1
  editingCell.column = ''
  editingCell.value = ''
  editingCell.originalRow = null
}

function onRowDblClick(row: Record<string, any>) {
  emit('rowDblClick', row)
}

function updateHeight() {
  if (containerRef.value) {
    bodyHeight.value = containerRef.value.clientHeight - props.rowHeight
  }
}

onMounted(() => {
  updateHeight()
  window.addEventListener('resize', updateHeight)
})

watch(() => props.rows, () => {
  selectedRowIdx.value = -1
  emit('select', null)
  nextTick(updateHeight)
})

defineExpose({
  clearSelection: () => {
    selectedRowIdx.value = -1
    emit('select', null)
  }
})
</script>

<style scoped>
.v-table {
  position: relative;
  height: 100%;
  overflow: auto;
  background: var(--bg-primary);
  font-size: 12px;
}
.v-table-header {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  background: var(--bg-secondary);
  border-bottom: 1px solid var(--border-color);
  height: 32px;
}
.v-header-cell {
  cursor: pointer;
  user-select: none;
  font-weight: 600;
  color: var(--text-secondary);
  position: sticky;
  top: 0;
  background: var(--bg-secondary);
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 0 8px;
  border-right: 1px solid var(--border-color);
  border-bottom: 1px solid var(--border-color);
  overflow: hidden;
}
.v-header-cell:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
}
.header-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}
.pk-icon {
  font-size: 10px;
}
.sort-indicator {
  font-size: 9px;
  color: var(--accent);
}
.v-table-body {
  position: relative;
}
.v-row {
  display: flex;
  position: relative;
  border-bottom: 1px solid rgba(58, 58, 82, 0.4);
}
.v-row:hover {
  background: var(--bg-hover);
}
.v-row.selected {
  background: rgba(91, 138, 245, 0.15);
}
.v-row.new-row {
  background: rgba(91, 184, 216, 0.08);
}
.v-cell {
  position: absolute;
  top: 0;
  height: 100%;
  display: flex;
  align-items: center;
  padding: 0 8px;
  border-right: 1px solid rgba(58, 58, 82, 0.3);
  overflow: hidden;
  white-space: nowrap;
}
.cell-value {
  overflow: hidden;
  text-overflow: ellipsis;
}
.cell-value.null {
  color: var(--text-muted);
  font-style: italic;
}
.cell-editor {
  width: 100%;
  height: 100%;
  border: 1px solid var(--accent);
  border-radius: 0;
  padding: 0 6px;
  background: var(--bg-primary);
  font-size: 12px;
}
.v-spacer-top, .v-spacer-bottom {
  width: 100%;
}
.v-table-loading, .v-table-empty {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  color: var(--text-muted);
  font-size: 13px;
}
</style>
