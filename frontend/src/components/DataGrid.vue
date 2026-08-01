<template>
  <div class="vgrid" ref="container" @scroll="onScroll">
    <div class="vgrid-sizer" :style="{ height: totalHeight + 'px' }">
      <table class="vgrid-table" :style="{ transform: `translateY(${offsetY}px)` }">
        <thead>
          <tr>
            <th class="rownum-col">#</th>
            <th
              v-for="(col, ci) in columns"
              :key="ci"
              :style="{ width: colWidth + 'px' }"
              @click="onHeaderClick(col.name)"
            >
              <span class="col-name">{{ col.name }}</span>
              <span v-if="sortColumn === col.name" class="sort-ind">
                {{ sortDirection === 'ASC' ? '▲' : '▼' }}
              </span>
              <span class="col-type">{{ col.typeName }}</span>
            </th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="(row, ri) in visibleRows"
            :key="startIndex + ri"
            :style="{ height: rowHeight + 'px' }"
            :class="{ 'row-new': isNewRow(startIndex + ri), 'row-deleted': isDeletedRow(startIndex + ri) }"
          >
            <td class="rownum-col">{{ startIndex + ri + 1 }}</td>
            <td
              v-for="(col, ci) in columns"
              :key="ci"
              :style="{ width: colWidth + 'px' }"
              :class="{ edited: isEdited(startIndex + ri, col.name), editable }"
              @dblclick="editable && beginEdit(startIndex + ri, ci, col.name)"
            >
              <template v-if="isEditing(startIndex + ri, ci)">
                <input
                  ref="editInput"
                  v-model="editValue"
                  class="cell-input"
                  @keydown.enter.prevent="commitEdit"
                  @keydown.esc.prevent="cancelEdit"
                  @blur="commitEdit"
                />
              </template>
              <template v-else>
                <span :class="{ 'null-val': cellValue(startIndex + ri, col.name) === null }">
                  {{ display(cellValue(startIndex + ri, col.name)) }}
                </span>
              </template>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <div v-if="rows.length === 0" class="vgrid-empty">No data</div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, watch, onMounted } from 'vue'
import type { ColumnMeta } from '@/types'

const props = defineProps<{
  columns: ColumnMeta[]
  rows: any[][]
  editable?: boolean
  sortColumn?: string
  sortDirection?: string
  /** row indices that are pending-new / pending-delete for visual feedback */
  newRowIndices?: Set<number>
  deletedRowIndices?: Set<number>
  /** map "rowIndex:col" -> edited value */
  editedCells?: Map<string, any>
}>()

const emit = defineEmits<{
  (e: 'sort', column: string): void
  (e: 'edit-cell', payload: { rowIndex: number; column: string; value: any }): void
}>()

const rowHeight = 28
const headerHeight = 34
const overscan = 8
const colWidth = 160

const container = ref<HTMLElement | null>(null)
const scrollTop = ref(0)
const viewportHeight = ref(400)

const totalHeight = computed(() => props.rows.length * rowHeight + headerHeight)

const startIndex = computed(() => {
  const idx = Math.floor(scrollTop.value / rowHeight) - overscan
  return Math.max(0, idx)
})

const visibleCount = computed(() => Math.ceil(viewportHeight.value / rowHeight) + overscan * 2)

const endIndex = computed(() => Math.min(props.rows.length, startIndex.value + visibleCount.value))

const visibleRows = computed(() => props.rows.slice(startIndex.value, endIndex.value))

// vertical offset so the rendered slice sits at the correct position (account for sticky header)
const offsetY = computed(() => startIndex.value * rowHeight)

function onScroll() {
  if (container.value) {
    scrollTop.value = container.value.scrollTop
  }
}

function measure() {
  if (container.value) {
    viewportHeight.value = container.value.clientHeight
  }
}

onMounted(() => {
  measure()
  window.addEventListener('resize', measure)
})

watch(() => props.rows, () => {
  scrollTop.value = 0
  if (container.value) container.value.scrollTop = 0
})

// --- header / sort ---
function onHeaderClick(name: string) {
  emit('sort', name)
}

// --- cell display ---
function cellValue(rowIndex: number, column: string) {
  const key = rowIndex + ':' + column
  if (props.editedCells && props.editedCells.has(key)) {
    return props.editedCells.get(key)
  }
  const colIdx = props.columns.findIndex((c) => c.name === column)
  return props.rows[rowIndex]?.[colIdx]
}

function display(v: any): string {
  if (v === null || v === undefined) return 'NULL'
  return String(v)
}

function isEdited(rowIndex: number, column: string): boolean {
  return props.editedCells?.has(rowIndex + ':' + column) ?? false
}

function isNewRow(rowIndex: number): boolean {
  return props.newRowIndices?.has(rowIndex) ?? false
}

function isDeletedRow(rowIndex: number): boolean {
  return props.deletedRowIndices?.has(rowIndex) ?? false
}

// --- inline editing ---
const editingRow = ref<number | null>(null)
const editingCol = ref<number | null>(null)
const editValue = ref<string>('')
const editColName = ref<string>('')
const editInput = ref<HTMLInputElement[] | HTMLInputElement | null>(null)

function isEditing(rowIndex: number, colIndex: number): boolean {
  return editingRow.value === rowIndex && editingCol.value === colIndex
}

async function beginEdit(rowIndex: number, colIndex: number, column: string) {
  editingRow.value = rowIndex
  editingCol.value = colIndex
  editColName.value = column
  const val = cellValue(rowIndex, column)
  editValue.value = val === null || val === undefined ? '' : String(val)
  await nextTick()
  const el = Array.isArray(editInput.value) ? editInput.value[0] : editInput.value
  el?.focus()
  el?.select()
}

function commitEdit() {
  if (editingRow.value === null) return
  const rowIndex = editingRow.value
  const column = editColName.value
  const original = cellValue(rowIndex, column)
  const newVal = editValue.value === '' ? null : editValue.value
  if (String(original ?? '') !== String(newVal ?? '')) {
    emit('edit-cell', { rowIndex, column, value: newVal })
  }
  cancelEdit()
}

function cancelEdit() {
  editingRow.value = null
  editingCol.value = null
  editColName.value = ''
  editValue.value = ''
}
</script>

<style scoped>
.vgrid {
  position: relative;
  overflow: auto;
  height: 100%;
  width: 100%;
  background: var(--bg);
}
.vgrid-sizer {
  position: relative;
  width: 100%;
}
.vgrid-table {
  border-collapse: collapse;
  table-layout: fixed;
  width: max-content;
  min-width: 100%;
}
thead th {
  position: sticky;
  top: 0;
  z-index: 2;
  height: 34px;
  background: var(--bg-alt);
  border-right: 1px solid var(--border);
  border-bottom: 1px solid var(--border);
  text-align: left;
  padding: 0 8px;
  cursor: pointer;
  white-space: nowrap;
  user-select: none;
}
thead th:hover {
  background: var(--bg-hover);
}
.col-name {
  font-weight: 600;
  color: var(--text);
}
.col-type {
  display: block;
  font-size: 10px;
  color: var(--text-dim);
  font-weight: 400;
}
.sort-ind {
  margin-left: 4px;
  color: var(--accent);
  font-size: 10px;
}
tbody td {
  height: 28px;
  border-right: 1px solid var(--border);
  border-bottom: 1px solid var(--border);
  padding: 0 8px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  font-family: var(--mono);
  font-size: 12px;
  max-width: 160px;
}
tbody td.editable {
  cursor: text;
}
tbody td.edited {
  background: rgba(249, 226, 175, 0.12);
  color: var(--yellow);
}
.rownum-col {
  width: 52px;
  min-width: 52px;
  text-align: right;
  color: var(--text-dim);
  background: var(--bg-alt);
  position: sticky;
  left: 0;
  z-index: 1;
}
.null-val {
  color: var(--text-dim);
  font-style: italic;
}
.row-new td {
  background: rgba(166, 227, 161, 0.12);
}
.row-deleted td {
  background: rgba(243, 139, 168, 0.12);
  text-decoration: line-through;
  color: var(--text-dim);
}
.cell-input {
  width: 100%;
  height: 24px;
  padding: 0 4px;
  font-family: var(--mono);
  font-size: 12px;
  border: 1px solid var(--accent);
}
.vgrid-empty {
  position: absolute;
  top: 60px;
  left: 0;
  right: 0;
  text-align: center;
  color: var(--text-dim);
  padding: 20px;
}
</style>
