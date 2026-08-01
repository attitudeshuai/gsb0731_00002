<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch, type ComponentPublicInstance } from 'vue'
import type { FilterCondition, FilterOperator } from '@/types'

export interface GridColumn {
  key: string
  title?: string
  type?: string
  width?: number
  sortable?: boolean
  filterable?: boolean
  /** 当前排序状态（受控） */
  sort?: 'asc' | 'desc' | null
  /** 当前是否有筛选（受控） */
  filtered?: boolean
}

const props = withDefaults(
  defineProps<{
    columns: GridColumn[]
    rows: unknown[][]
    loading?: boolean
    selectable?: boolean
    editable?: boolean
    selected?: number[]
    /** 行号起始偏移（分页时使用） */
    startIndex?: number
    rowHeight?: number
    /** 变化时滚动条回到顶部 */
    resetScrollKey?: string | number
    emptyText?: string
  }>(),
  {
    loading: false,
    selectable: false,
    editable: false,
    selected: () => [],
    startIndex: 0,
    rowHeight: 28,
    resetScrollKey: '',
    emptyText: '暂无数据',
  },
)

const emit = defineEmits<{
  (e: 'update:selected', v: number[]): void
  (e: 'sort', column: GridColumn): void
  (e: 'filter', column: string, cond: FilterCondition | null): void
  (e: 'save-cell', rowIndex: number, column: string, value: string): void
}>()

const HEADER_H = 32
const CHECK_W = 34
const INDEX_W = 52
const BUFFER = 8
const MIN_COL_W = 56

// ---------- 列宽 ----------
const widths = ref<number[]>([])

function estimateWidth(c: GridColumn): number {
  const label = c.title ?? c.key
  return Math.min(320, Math.max(90, label.length * 9 + 48))
}

watch(
  () => props.columns,
  (cols) => {
    widths.value = cols.map((c) => c.width ?? estimateWidth(c))
  },
  { immediate: true },
)

const totalWidth = computed(
  () =>
    widths.value.reduce((a, b) => a + b, 0) +
    (props.selectable ? CHECK_W : 0) +
    INDEX_W,
)

// ---------- 虚拟滚动 ----------
const container = ref<HTMLElement | null>(null)
const scrollTop = ref(0)
const viewportH = ref(400)

const totalHeight = computed(() => props.rows.length * props.rowHeight + HEADER_H)

const startIndexV = computed(() =>
  Math.max(0, Math.floor(scrollTop.value / props.rowHeight) - BUFFER),
)
const endIndexV = computed(() =>
  Math.min(
    props.rows.length,
    Math.ceil((scrollTop.value + viewportH.value) / props.rowHeight) + BUFFER,
  ),
)

const visibleRows = computed(() => {
  const out: { index: number; row: unknown[] }[] = []
  for (let i = startIndexV.value; i < endIndexV.value; i++) {
    out.push({ index: i, row: props.rows[i] })
  }
  return out
})

const bodyOffsetY = computed(() => HEADER_H + startIndexV.value * props.rowHeight)

function onScroll() {
  const el = container.value
  if (!el) return
  scrollTop.value = el.scrollTop
  closeFilterPopup()
}

let resizeObserver: ResizeObserver | null = null

onMounted(() => {
  if (container.value) {
    viewportH.value = container.value.clientHeight
    resizeObserver = new ResizeObserver(() => {
      if (container.value) viewportH.value = container.value.clientHeight
    })
    resizeObserver.observe(container.value)
  }
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  stopResizeListeners()
})

watch(
  () => props.resetScrollKey,
  () => {
    if (container.value) {
      container.value.scrollTop = 0
      scrollTop.value = 0
    }
  },
)

// ---------- 选择 ----------
const selectedSet = computed(() => new Set(props.selected))

const allSelected = computed(
  () => props.rows.length > 0 && props.selected.length >= props.rows.length,
)

function isSelected(i: number) {
  return selectedSet.value.has(i)
}

function toggleRow(i: number) {
  const next = new Set(selectedSet.value)
  if (next.has(i)) next.delete(i)
  else next.add(i)
  emit('update:selected', [...next].sort((a, b) => a - b))
}

function toggleAll() {
  if (allSelected.value) emit('update:selected', [])
  else emit('update:selected', props.rows.map((_, i) => i))
}

// ---------- 排序 ----------
function onHeaderClick(c: GridColumn) {
  if (c.sortable === false) return
  emit('sort', c)
}

// ---------- 筛选弹窗 ----------
const OPERATORS: FilterOperator[] = ['=', '!=', '>', '>=', '<', '<=', 'LIKE', 'IS NULL', 'IS NOT NULL']

const filterPopup = ref<{ column: string; x: number; y: number } | null>(null)
const filterOperator = ref<FilterOperator>('=')
const filterValue = ref('')

const noValueOperator = computed(
  () => filterOperator.value === 'IS NULL' || filterOperator.value === 'IS NOT NULL',
)

function openFilterPopup(c: GridColumn, ev: MouseEvent) {
  ev.stopPropagation()
  if (filterPopup.value?.column === c.key) {
    closeFilterPopup()
    return
  }
  const btn = (ev.currentTarget as HTMLElement).getBoundingClientRect()
  filterPopup.value = {
    column: c.key,
    x: Math.min(btn.left, window.innerWidth - 260),
    y: btn.bottom + 4,
  }
  filterOperator.value = '='
  filterValue.value = ''
}

function closeFilterPopup() {
  filterPopup.value = null
}

function applyFilter() {
  if (!filterPopup.value) return
  const cond: FilterCondition = {
    column: filterPopup.value.column,
    operator: filterOperator.value,
    value: noValueOperator.value ? undefined : filterValue.value,
  }
  emit('filter', cond.column, cond)
  closeFilterPopup()
}

function clearFilter() {
  if (!filterPopup.value) return
  emit('filter', filterPopup.value.column, null)
  closeFilterPopup()
}

// ---------- 行内编辑 ----------
const editing = ref<{ r: number; c: number } | null>(null)
const editValue = ref('')
const editInputEl = ref<HTMLInputElement | null>(null)
let editCancelled = false

// v-for 内的 ref 会被收集为数组，这里用函数 ref 取唯一编辑框
function setEditInput(el: Element | ComponentPublicInstance | null) {
  editInputEl.value = (el as HTMLInputElement | null) ?? null
}

function isEditing(r: number, c: number) {
  return editing.value?.r === r && editing.value?.c === c
}

function beginEdit(r: number, c: number) {
  if (!props.editable) return
  const raw = props.rows[r]?.[c]
  editCancelled = false
  editing.value = { r, c }
  editValue.value = raw == null ? '' : String(raw)
  void nextTick(() => {
    editInputEl.value?.focus()
    editInputEl.value?.select()
  })
}

function commitEdit() {
  const cur = editing.value
  if (!cur) return
  const raw = props.rows[cur.r]?.[cur.c]
  const oldStr = raw == null ? '' : String(raw)
  const col = props.columns[cur.c]
  editing.value = null
  if (editValue.value === oldStr || !col) return
  emit('save-cell', cur.r, col.key, editValue.value)
}

function cancelEdit() {
  editCancelled = true
  editing.value = null
}

function onEditBlur() {
  if (editCancelled) {
    editCancelled = false
    return
  }
  commitEdit()
}

// ---------- 列宽拖拽 ----------
let resizing: { i: number; startX: number; startW: number } | null = null

function startResize(i: number, ev: MouseEvent) {
  ev.preventDefault()
  ev.stopPropagation()
  resizing = { i, startX: ev.clientX, startW: widths.value[i] ?? 100 }
  document.body.classList.add('dg-col-resizing')
  window.addEventListener('mousemove', onResizing)
  window.addEventListener('mouseup', stopResize, { once: true })
}

function onResizing(ev: MouseEvent) {
  if (!resizing) return
  const w = Math.max(MIN_COL_W, resizing.startW + ev.clientX - resizing.startX)
  widths.value[resizing.i] = w
}

function stopResize() {
  resizing = null
  document.body.classList.remove('dg-col-resizing')
  stopResizeListeners()
}

function stopResizeListeners() {
  window.removeEventListener('mousemove', onResizing)
}

// ---------- 渲染辅助 ----------
function formatCell(v: unknown): string {
  if (v === null || v === undefined) return ''
  if (typeof v === 'object') return JSON.stringify(v)
  return String(v)
}

function isNull(v: unknown) {
  return v === null || v === undefined
}
</script>

<template>
  <div class="dg-wrap">
    <div ref="container" class="dg-container" @scroll="onScroll">
      <div class="dg-phantom" :style="{ height: totalHeight + 'px', width: totalWidth + 'px' }">
      <!-- 表头（sticky） -->
      <div class="dg-head-wrap">
        <table class="dg-table dg-head-table" :style="{ width: totalWidth + 'px' }">
          <colgroup>
            <col v-if="selectable" :style="{ width: CHECK_W + 'px' }" />
            <col :style="{ width: INDEX_W + 'px' }" />
            <col v-for="(c, i) in columns" :key="c.key" :style="{ width: (widths[i] ?? 100) + 'px' }" />
          </colgroup>
          <thead>
            <tr :style="{ height: HEADER_H + 'px' }">
              <th v-if="selectable" class="dg-check">
                <input type="checkbox" :checked="allSelected" @change="toggleAll" />
              </th>
              <th class="dg-index">#</th>
              <th
                v-for="c in columns"
                :key="c.key"
                class="dg-th"
                :class="{ sortable: c.sortable !== false }"
                @click="onHeaderClick(c)"
              >
                <span class="dg-th-label" :title="c.title ?? c.key">{{ c.title ?? c.key }}</span>
                <span v-if="c.sort" class="dg-sort-arrow">{{ c.sort === 'asc' ? '▲' : '▼' }}</span>
                <button
                  v-if="c.filterable !== false"
                  class="dg-filter-btn"
                  :class="{ active: c.filtered }"
                  title="筛选"
                  @click="openFilterPopup(c, $event)"
                >
                  <svg viewBox="0 0 16 16" width="11" height="11" fill="currentColor">
                    <path d="M1.5 2.5h13l-5 6v4.5l-3 1.5v-6l-5-6z" />
                  </svg>
                </button>
                <span class="dg-resizer" @mousedown="startResize(columns.indexOf(c), $event)" @click.stop></span>
              </th>
            </tr>
          </thead>
        </table>
      </div>

      <!-- 数据区（只渲染可视行） -->
      <table
        class="dg-table dg-body-table"
        :style="{ width: totalWidth + 'px', transform: `translateY(${bodyOffsetY}px)` }"
      >
        <colgroup>
          <col v-if="selectable" :style="{ width: CHECK_W + 'px' }" />
          <col :style="{ width: INDEX_W + 'px' }" />
          <col v-for="(c, i) in columns" :key="c.key" :style="{ width: (widths[i] ?? 100) + 'px' }" />
        </colgroup>
        <tbody>
          <tr
            v-for="vr in visibleRows"
            :key="startIndex + vr.index"
            :class="{ 'dg-row-selected': isSelected(vr.index) }"
            :style="{ height: rowHeight + 'px' }"
          >
            <td v-if="selectable" class="dg-check">
              <input type="checkbox" :checked="isSelected(vr.index)" @change="toggleRow(vr.index)" />
            </td>
            <td class="dg-index">{{ startIndex + vr.index + 1 }}</td>
            <td
              v-for="(c, ci) in columns"
              :key="c.key"
              class="dg-td"
              :class="{
                'dg-null': isNull(vr.row[ci]),
                'dg-editing': isEditing(vr.index, ci),
                'dg-editable': editable,
              }"
              :title="isEditing(vr.index, ci) ? undefined : formatCell(vr.row[ci])"
              @dblclick="beginEdit(vr.index, ci)"
            >
              <input
                v-if="isEditing(vr.index, ci)"
                :ref="setEditInput"
                v-model="editValue"
                class="dg-edit-input"
                @keydown.enter.prevent="commitEdit"
                @keydown.esc.prevent="cancelEdit"
                @blur="onEditBlur"
              />
              <template v-else>{{ isNull(vr.row[ci]) ? 'NULL' : formatCell(vr.row[ci]) }}</template>
            </td>
          </tr>
        </tbody>
      </table>
      </div>
    </div>

    <!-- loading / 空态（在滚动容器外，始终居中可见） -->
    <div v-if="loading" class="dg-overlay">
      <span class="spinner"></span>加载中…
    </div>
    <div v-else-if="rows.length === 0" class="dg-overlay dg-empty">{{ emptyText }}</div>

    <!-- 筛选弹窗 -->
    <Teleport to="body">
      <div v-if="filterPopup" class="dg-filter-mask" @mousedown.self="closeFilterPopup">
        <div class="dg-filter-popup" :style="{ left: filterPopup.x + 'px', top: filterPopup.y + 'px' }">
          <div class="dg-filter-title">
            筛选：<b>{{ filterPopup.column }}</b>
          </div>
          <select v-model="filterOperator" class="input dg-filter-op">
            <option v-for="op in OPERATORS" :key="op" :value="op">{{ op }}</option>
          </select>
          <input
            v-if="!noValueOperator"
            v-model="filterValue"
            class="input"
            placeholder="值（LIKE 可用 % 通配）"
            @keydown.enter="applyFilter"
          />
          <div class="dg-filter-actions">
            <button class="btn btn-primary btn-sm" @click="applyFilter">应用</button>
            <button class="btn btn-sm" @click="clearFilter">清除</button>
            <button class="btn btn-sm" @click="closeFilterPopup">取消</button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.dg-wrap {
  position: relative;
  width: 100%;
  height: 100%;
  overflow: hidden;
}

.dg-container {
  position: absolute;
  inset: 0;
  overflow: auto;
  background: var(--bg-1);
  font-family: var(--font-mono);
  font-size: 12px;
}

.dg-phantom {
  position: relative;
}

.dg-head-wrap {
  position: sticky;
  top: 0;
  z-index: 6;
  background: var(--bg-2);
}

.dg-table {
  table-layout: fixed;
  border-collapse: collapse;
}

.dg-body-table {
  position: absolute;
  top: 0;
  left: 0;
  will-change: transform;
}

.dg-head-table th {
  border-bottom: 1px solid var(--border);
  border-right: 1px solid var(--border);
  color: var(--text-1);
  font-weight: 600;
  text-align: left;
  padding: 0 4px 0 8px;
  position: relative;
  user-select: none;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  background: var(--bg-2);
}

.dg-th.sortable {
  cursor: pointer;
}

.dg-th.sortable:hover {
  background: var(--bg-3);
  color: var(--text-0);
}

.dg-th-label {
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: calc(100% - 30px);
  display: inline-block;
  vertical-align: middle;
}

.dg-sort-arrow {
  color: var(--accent);
  font-size: 9px;
  margin-left: 3px;
  vertical-align: middle;
}

.dg-filter-btn {
  background: none;
  border: none;
  color: var(--text-2);
  cursor: pointer;
  padding: 2px 3px;
  border-radius: 3px;
  vertical-align: middle;
  opacity: 0.55;
}

.dg-th:hover .dg-filter-btn {
  opacity: 1;
}

.dg-filter-btn:hover {
  color: var(--text-0);
  background: var(--bg-1);
}

.dg-filter-btn.active {
  color: var(--accent);
  opacity: 1;
}

.dg-resizer {
  position: absolute;
  right: -3px;
  top: 0;
  width: 7px;
  height: 100%;
  cursor: col-resize;
  z-index: 2;
}

.dg-resizer:hover {
  background: var(--accent);
  opacity: 0.5;
}

.dg-body-table td {
  border-bottom: 1px solid var(--border-soft);
  border-right: 1px solid var(--border-soft);
  padding: 0 8px;
  color: var(--text-0);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.dg-body-table tr:hover td {
  background: var(--bg-hover);
}

.dg-row-selected td {
  background: var(--bg-selected) !important;
}

.dg-check {
  text-align: center;
  padding: 0 !important;
}

.dg-check input {
  cursor: pointer;
  accent-color: var(--accent);
}

.dg-index {
  color: var(--text-2);
  text-align: right;
  padding-right: 10px !important;
  background: var(--bg-2);
  user-select: none;
}

.dg-null {
  color: var(--text-2);
  font-style: italic;
}

.dg-td.dg-editable {
  cursor: cell;
}

.dg-td.dg-editing {
  padding: 0 !important;
  overflow: visible;
}

.dg-edit-input {
  width: 100%;
  height: 100%;
  box-sizing: border-box;
  background: var(--bg-0);
  color: var(--text-0);
  border: 1px solid var(--accent);
  outline: none;
  font: inherit;
  padding: 0 6px;
}

.dg-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: var(--text-1);
  font-family: var(--font-ui);
  pointer-events: none;
  z-index: 5;
  background: rgba(28, 31, 38, 0.35);
}

.dg-empty {
  color: var(--text-2);
}

.dg-filter-mask {
  position: fixed;
  inset: 0;
  z-index: 1000;
}

.dg-filter-popup {
  position: fixed;
  width: 240px;
  background: var(--bg-2);
  border: 1px solid var(--border);
  border-radius: 6px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.5);
  padding: 10px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  font-family: var(--font-ui);
}

.dg-filter-title {
  color: var(--text-1);
  font-size: 12px;
}

.dg-filter-title b {
  color: var(--text-0);
}

.dg-filter-actions {
  display: flex;
  gap: 6px;
  justify-content: flex-end;
}
</style>

<style>
body.dg-col-resizing {
  cursor: col-resize;
  user-select: none;
}
</style>
