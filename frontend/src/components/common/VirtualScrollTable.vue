<template>
  <div class="vst-wrapper" :style="{ height: typeof height === 'number' ? height + 'px' : height }">
    <div class="vst-header" ref="headerRef">
      <div class="vst-header-row" :style="{ width: totalWidth + 'px' }">
        <div v-if="selectable" class="vst-cell vst-header-cell vst-checkbox-cell">
          <input
            type="checkbox"
            :checked="allSelected"
            :indeterminate.prop="someSelected && !allSelected"
            @change="toggleSelectAll"
          />
        </div>
        <div v-if="showIndex" class="vst-cell vst-header-cell vst-index-cell">#</div>
        <div
          v-for="col in columns"
          :key="col.key"
          class="vst-cell vst-header-cell"
          :class="{ sortable: col.sortable }"
          :style="{ width: getColWidth(col) + 'px' }"
          @click="col.sortable ? onSort(col) : undefined"
        >
          <span class="vst-header-title">{{ col.title }}</span>
          <span v-if="col.sortable" class="vst-sort-arrow">{{ sortArrow(col.key) }}</span>
          <div
            v-if="resizable"
            class="vst-col-resizer"
            @mousedown.stop="onResizeStart($event, col)"
          ></div>
        </div>
        <div v-if="selectable" class="vst-cell vst-header-cell vst-action-cell">操作</div>
      </div>
    </div>
    <div class="vst-body" ref="bodyRef" @scroll="onScroll">
      <div v-if="loading" class="vst-loading">
        <div class="vst-spinner"></div>
        <span>加载中...</span>
      </div>
      <div class="vst-phantom" :style="{ height: totalHeight + 'px', width: totalWidth + 'px' }">
        <div
          class="vst-visible"
          :style="{ transform: `translateY(${startOffset}px)` }"
        >
          <div
            v-for="(row, vIdx) in visibleRows"
            :key="getRowKey(row, startIndex + vIdx)"
            class="vst-row"
            :class="{
              'vst-row-selected': isSelected(row),
              'vst-row-even': (startIndex + vIdx) % 2 === 1
            }"
            :style="{ height: rowHeight + 'px', width: totalWidth + 'px' }"
            @click="onRowClick(row, startIndex + vIdx)"
          >
            <div v-if="selectable" class="vst-cell vst-checkbox-cell" @click.stop>
              <input
                type="checkbox"
                :checked="isSelected(row)"
                @change="toggleSelectRow(row)"
              />
            </div>
            <div v-if="showIndex" class="vst-cell vst-index-cell">{{ startIndex + vIdx + 1 }}</div>
            <div
              v-for="col in columns"
              :key="col.key"
              class="vst-cell vst-data-cell"
              :class="[`vst-cell-${col.type || 'text'}`, { 'vst-cell-editable': col.editable }]"
              :style="{ width: getColWidth(col) + 'px' }"
              :title="String(row[col.key] ?? '')"
              @dblclick="col.editable ? startEdit(row, col.key) : undefined"
            >
              <input
                v-if="isEditing(row, col.key)"
                :ref="(el: any) => focusInput(el)"
                v-model="editValue"
                class="vst-edit-input"
                type="text"
                @click.stop
                @keydown.enter="saveEdit(row)"
                @keydown.esc="cancelEdit"
                @blur="saveEdit(row)"
              />
              <template v-else>{{ formatCell(row[col.key], col.type) }}</template>
            </div>
            <div v-if="selectable" class="vst-cell vst-action-cell" @click.stop>
              <button class="vst-del-btn" title="删除" @click="onDeleteRow(row)">✕</button>
            </div>
          </div>
        </div>
      </div>
      <div v-if="!loading && rows.length === 0" class="vst-empty">暂无数据</div>
    </div>
    <div v-if="showAddRow" class="vst-footer">
      <button class="btn btn-primary btn-sm" @click="$emit('add-row')">+ 新增行</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onBeforeUnmount, type ComponentPublicInstance } from 'vue'

interface TableColumn {
  key: string
  title: string
  width?: number
  type?: 'text' | 'number' | 'date'
  editable?: boolean
  sortable?: boolean
}

const props = withDefaults(
  defineProps<{
    columns: TableColumn[]
    rows: Record<string, any>[]
    rowHeight?: number
    height?: string | number
    loading?: boolean
    selectable?: boolean
    showIndex?: boolean
    showAddRow?: boolean
    resizable?: boolean
  }>(),
  {
    rowHeight: 36,
    height: '100%',
    loading: false,
    selectable: false,
    showIndex: false,
    showAddRow: false,
    resizable: true
  }
)

const emit = defineEmits<{
  'sort-change': [payload: { key: string; order: 'asc' | 'desc' | null }]
  'cell-update': [payload: { row: Record<string, any>; key: string; value: any; oldValue: any }]
  'row-click': [payload: { row: Record<string, any>; index: number }]
  'row-delete': [payload: { row: Record<string, any> }]
  'add-row': []
}>()

const BUFFER = 5
const DEFAULT_COL_WIDTH = 150
const CHECKBOX_COL_WIDTH = 44
const INDEX_COL_WIDTH = 56
const ACTION_COL_WIDTH = 60

const bodyRef = ref<HTMLElement | null>(null)
const headerRef = ref<HTMLElement | null>(null)
const scrollTop = ref(0)
const viewportHeight = ref(400)

const colWidths = ref<Record<string, number>>({})

const sortKey = ref<string | null>(null)
const sortOrder = ref<'asc' | 'desc' | null>(null)

const selectedRows = ref<Set<Record<string, any>>>(new Set())
const editingCell = ref<{ row: Record<string, any>; key: string } | null>(null)
const editValue = ref<any>(null)

const totalHeight = computed(() => props.rows.length * props.rowHeight)

const totalWidth = computed(() => {
  let w = 0
  if (props.selectable) w += CHECKBOX_COL_WIDTH
  if (props.showIndex) w += INDEX_COL_WIDTH
  props.columns.forEach((c) => (w += getColWidth(c)))
  if (props.selectable) w += ACTION_COL_WIDTH
  return w
})

const startIndex = computed(() => {
  const i = Math.floor(scrollTop.value / props.rowHeight) - BUFFER
  return Math.max(0, i)
})

const endIndex = computed(() => {
  const visibleCount = Math.ceil(viewportHeight.value / props.rowHeight)
  const i = startIndex.value + visibleCount + BUFFER * 2
  return Math.min(props.rows.length, i)
})

const visibleRows = computed(() => props.rows.slice(startIndex.value, endIndex.value))
const startOffset = computed(() => startIndex.value * props.rowHeight)

const allSelected = computed(
  () => props.rows.length > 0 && selectedRows.value.size === props.rows.length
)
const someSelected = computed(() => selectedRows.value.size > 0 && !allSelected.value)

function getColWidth(col: TableColumn): number {
  return colWidths.value[col.key] ?? col.width ?? DEFAULT_COL_WIDTH
}

function formatCell(value: any, type?: string): string {
  if (value === null || value === undefined) return ''
  if (type === 'date' && value) {
    try {
      const d = new Date(value)
      if (!isNaN(d.getTime())) return d.toLocaleString('zh-CN')
    } catch {
      /* ignore */
    }
  }
  return String(value)
}

function getRowKey(row: Record<string, any>, index: number): string | number {
  return row.id ?? row._id ?? index
}

function onScroll() {
  if (!bodyRef.value) return
  scrollTop.value = bodyRef.value.scrollTop
}

function onSort(col: TableColumn) {
  if (sortKey.value === col.key) {
    if (sortOrder.value === 'asc') sortOrder.value = 'desc'
    else if (sortOrder.value === 'desc') {
      sortOrder.value = null
      sortKey.value = null
    } else sortOrder.value = 'asc'
  } else {
    sortKey.value = col.key
    sortOrder.value = 'asc'
  }
  emit('sort-change', { key: col.key, order: sortOrder.value })
}

function sortArrow(key: string): string {
  if (sortKey.value !== key) return '↕'
  if (sortOrder.value === 'asc') return '↑'
  if (sortOrder.value === 'desc') return '↓'
  return '↕'
}

function isSelected(row: Record<string, any>): boolean {
  return selectedRows.value.has(row)
}

function toggleSelectRow(row: Record<string, any>) {
  if (selectedRows.value.has(row)) selectedRows.value.delete(row)
  else selectedRows.value.add(row)
  selectedRows.value = new Set(selectedRows.value)
}

function toggleSelectAll() {
  if (allSelected.value) selectedRows.value.clear()
  else selectedRows.value = new Set(props.rows)
}

function onRowClick(row: Record<string, any>, index: number) {
  emit('row-click', { row, index })
}

function onDeleteRow(row: Record<string, any>) {
  emit('row-delete', { row })
}

function startEdit(row: Record<string, any>, key: string) {
  editingCell.value = { row, key }
  editValue.value = row[key]
}

function isEditing(row: Record<string, any>, key: string): boolean {
  return editingCell.value?.row === row && editingCell.value?.key === key
}

function focusInput(el: Element | ComponentPublicInstance | null) {
  if (el instanceof HTMLInputElement) {
    el.focus()
    el.select()
  }
}

function saveEdit(row: Record<string, any>) {
  if (!editingCell.value) return
  const { key } = editingCell.value
  const oldValue = row[key]
  if (editValue.value !== oldValue) {
    row[key] = editValue.value
    emit('cell-update', { row, key, value: editValue.value, oldValue })
  }
  editingCell.value = null
}

function cancelEdit() {
  editingCell.value = null
}

let resizingCol: TableColumn | null = null
let resizeStartX = 0
let resizeStartWidth = 0

function onResizeStart(e: MouseEvent, col: TableColumn) {
  resizingCol = col
  resizeStartX = e.clientX
  resizeStartWidth = getColWidth(col)
  document.addEventListener('mousemove', onResizing)
  document.addEventListener('mouseup', onResizeEnd)
  document.body.style.cursor = 'col-resize'
  document.body.style.userSelect = 'none'
}

function onResizing(e: MouseEvent) {
  if (!resizingCol) return
  const delta = e.clientX - resizeStartX
  const newWidth = Math.max(50, resizeStartWidth + delta)
  colWidths.value[resizingCol.key] = newWidth
}

function onResizeEnd() {
  resizingCol = null
  document.removeEventListener('mousemove', onResizing)
  document.removeEventListener('mouseup', onResizeEnd)
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
}

function syncHeaderScroll() {
  if (headerRef.value && bodyRef.value) {
    headerRef.value.scrollLeft = bodyRef.value.scrollLeft
  }
}

function updateViewport() {
  if (bodyRef.value) {
    viewportHeight.value = bodyRef.value.clientHeight
  }
}

let ro: ResizeObserver | null = null
watch(
  bodyRef,
  (el) => {
    if (el) {
      el.addEventListener('scroll', syncHeaderScroll)
      updateViewport()
      if (typeof ResizeObserver !== 'undefined') {
        ro = new ResizeObserver(() => updateViewport())
        ro.observe(el)
      } else {
        window.addEventListener('resize', updateViewport)
      }
    }
  },
  { immediate: true }
)

watch(
  () => props.rows,
  () => {
    selectedRows.value = new Set()
  }
)

onBeforeUnmount(() => {
  if (ro) ro.disconnect()
  window.removeEventListener('resize', updateViewport)
  document.removeEventListener('mousemove', onResizing)
  document.removeEventListener('mouseup', onResizeEnd)
  if (bodyRef.value) bodyRef.value.removeEventListener('scroll', syncHeaderScroll)
})

defineExpose({
  clearSelection: () => {
    selectedRows.value.clear()
  },
  getSelectedRows: () => Array.from(selectedRows.value)
})
</script>

<style scoped lang="scss">
@use '@/styles/index.scss' as *;

.vst-wrapper {
  display: flex;
  flex-direction: column;
  border: 1px solid $border-color;
  border-radius: $radius-sm;
  overflow: hidden;
  background: #fff;
  position: relative;
}

.vst-header {
  overflow: hidden;
  background: #f7f8fa;
  border-bottom: 1px solid $border-color;
  flex-shrink: 0;
}

.vst-header-row {
  display: flex;
  height: 38px;
}

.vst-body {
  flex: 1;
  overflow: auto;
  position: relative;
}

.vst-phantom {
  position: relative;
}

.vst-visible {
  position: absolute;
  top: 0;
  left: 0;
  will-change: transform;
}

.vst-row {
  display: flex;
  border-bottom: 1px solid #f0f0f0;

  &:hover {
    background: #f5f8ff;
  }

  &.vst-row-selected {
    background: #e8f0fe;
  }

  &.vst-row-even {
    background: #fafbfc;

    &.vst-row-selected {
      background: #e8f0fe;
    }
  }
}

.vst-cell {
  padding: 0 10px;
  display: flex;
  align-items: center;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 13px;
  flex-shrink: 0;
  position: relative;
}

.vst-header-cell {
  font-weight: 600;
  color: $text-primary;
  background: #f7f8fa;
  border-right: 1px solid #eee;
  user-select: none;

  &.sortable {
    cursor: pointer;

    &:hover {
      background: #eef1f5;
    }
  }
}

.vst-header-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.vst-sort-arrow {
  margin-left: 4px;
  color: $text-light;
  font-size: 12px;
}

.vst-checkbox-cell {
  width: 44px;
  justify-content: center;
  flex-shrink: 0;
}

.vst-index-cell {
  width: 56px;
  justify-content: center;
  color: $text-light;
  flex-shrink: 0;
}

.vst-action-cell {
  width: 60px;
  justify-content: center;
  flex-shrink: 0;
}

.vst-data-cell {
  border-right: 1px solid #f5f5f5;

  &.vst-cell-number {
    justify-content: flex-end;
    font-variant-numeric: tabular-nums;
  }

  &.vst-cell-editable {
    cursor: cell;
  }
}

.vst-col-resizer {
  position: absolute;
  top: 0;
  right: -3px;
  width: 6px;
  height: 100%;
  cursor: col-resize;
  z-index: 2;

  &:hover {
    background: rgba(26, 115, 232, 0.3);
  }
}

.vst-edit-input {
  width: 100%;
  height: 100%;
  border: 2px solid $primary-color;
  padding: 0 6px;
  font-size: 13px;
  border-radius: 2px;
}

.vst-del-btn {
  background: transparent;
  color: $error-color;
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 3px;

  &:hover {
    background: rgba(234, 67, 53, 0.1);
  }
}

.vst-empty {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  color: $text-light;
  font-size: 14px;
  pointer-events: none;
}

.vst-loading {
  position: absolute;
  inset: 0;
  background: rgba(255, 255, 255, 0.75);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  z-index: 10;
  color: $text-secondary;
  font-size: 13px;
}

.vst-spinner {
  width: 18px;
  height: 18px;
  border: 2px solid #ddd;
  border-top-color: $primary-color;
  border-radius: 50%;
  animation: vst-spin 0.8s linear infinite;
}

@keyframes vst-spin {
  to {
    transform: rotate(360deg);
  }
}

.vst-footer {
  padding: 8px 12px;
  border-top: 1px solid $border-color;
  background: #fafbfc;
  flex-shrink: 0;
}
</style>
