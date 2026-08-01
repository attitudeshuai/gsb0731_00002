<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import DataGrid, { type GridColumn } from './DataGrid.vue'
import NewRowModal from './modals/NewRowModal.vue'
import {
  deleteRows,
  getTableData,
  insertRow,
  updateRow,
} from '@/api/metadata'
import { useExportTasksStore } from '@/stores/exportTasks'
import type { TableDataTabData } from '@/stores/tabs'
import { confirmDialog } from '@/ui/confirm'
import { toast } from '@/ui/toast'
import { formatNumber } from '@/utils/format'
import type { ColumnMeta, ExportFormat, FilterCondition } from '@/types'

const props = defineProps<{ tab: TableDataTabData }>()

const columns = ref<ColumnMeta[]>([])
const primaryKeys = ref<string[]>([])
const rows = ref<unknown[][]>([])
const total = ref(0)
const page = ref(0)
const size = ref(100)
const sort = ref('')
const order = ref<'asc' | 'desc'>('asc')
const filterMap = ref<Record<string, FilterCondition>>({})
const loading = ref(false)
const selected = ref<number[]>([])

const activeFilters = computed(() => Object.values(filterMap.value))
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size.value)))

const gridColumns = computed<GridColumn[]>(() =>
  columns.value.map((c) => ({
    key: c.name,
    title: c.name,
    type: c.type,
    sort: sort.value === c.name ? order.value : null,
    filtered: !!filterMap.value[c.name],
  })),
)

const resetScrollKey = computed(
  () => `${page.value}|${size.value}|${sort.value}|${order.value}|${JSON.stringify(activeFilters.value)}`,
)

async function load() {
  loading.value = true
  try {
    const resp = await getTableData(props.tab.connectionId, props.tab.database, props.tab.table, {
      page: page.value,
      size: size.value,
      sort: sort.value || undefined,
      order: order.value,
      filters: activeFilters.value,
    })
    columns.value = resp.columns
    primaryKeys.value = resp.primaryKeys
    rows.value = resp.rows.map((r) => (Array.isArray(r) ? [...r] : [r]))
    total.value = resp.total
    selected.value = []
    // 页码越界回退（例如删除后）
    if (resp.total > 0 && page.value >= Math.ceil(resp.total / size.value)) {
      page.value = Math.max(0, Math.ceil(resp.total / size.value) - 1)
      void load()
    }
  } catch {
    /* 拦截器已提示 */
  } finally {
    loading.value = false
  }
}

onMounted(load)

// ---------- 排序 / 筛选 ----------
function onSort(c: GridColumn) {
  if (sort.value === c.key) {
    order.value = order.value === 'asc' ? 'desc' : 'asc'
  } else {
    sort.value = c.key
    order.value = 'asc'
  }
  void load()
}

function onFilter(column: string, cond: FilterCondition | null) {
  const next = { ...filterMap.value }
  if (cond) next[column] = cond
  else delete next[column]
  filterMap.value = next
  page.value = 0
  void load()
}

function removeFilter(column: string) {
  onFilter(column, null)
}

// ---------- 分页 ----------
function gotoPage(p: number) {
  const target = Math.min(totalPages.value - 1, Math.max(0, p))
  if (target === page.value) return
  page.value = target
  void load()
}

function onSizeChange() {
  page.value = 0
  void load()
}

// ---------- 行编辑 ----------
function pkIndex(name: string) {
  return columns.value.findIndex((c) => c.name === name)
}

function rowKeys(row: unknown[]): Record<string, unknown> {
  const keys: Record<string, unknown> = {}
  for (const pk of primaryKeys.value) keys[pk] = row[pkIndex(pk)]
  return keys
}

async function onSaveCell(rowIndex: number, column: string, value: string) {
  if (primaryKeys.value.length === 0) {
    toast.error('该表无主键，不支持编辑')
    return
  }
  const row = rows.value[rowIndex]
  if (!row) return
  try {
    await updateRow(props.tab.connectionId, props.tab.database, props.tab.table, rowKeys(row), {
      [column]: value,
    })
    const ci = pkIndex(column)
    if (ci >= 0) rows.value[rowIndex][ci] = value
    toast.success('已保存')
  } catch {
    /* 拦截器已提示（无主键表后端 400 也会在此提示） */
  }
}

// ---------- 新增行 ----------
const newRowVisible = ref(false)

async function onCreateRow(values: Record<string, unknown>) {
  await insertRow(props.tab.connectionId, props.tab.database, props.tab.table, values)
  toast.success('已新增一行')
  newRowVisible.value = false
  void load()
}

// ---------- 删除行 ----------
async function onDeleteSelected() {
  if (selected.value.length === 0) {
    toast.info('请先勾选要删除的行')
    return
  }
  if (primaryKeys.value.length === 0) {
    toast.error('该表无主键，不支持删除')
    return
  }
  const ok = await confirmDialog(
    `确定删除选中的 ${selected.value.length} 行吗？删除后不可恢复。`,
    { title: '删除行', danger: true, okText: '删除' },
  )
  if (!ok) return
  try {
    const keys = selected.value.map((i) => rowKeys(rows.value[i]))
    await deleteRows(props.tab.connectionId, props.tab.database, props.tab.table, keys)
    toast.success(`已删除 ${keys.length} 行`)
    void load()
  } catch {
    /* 拦截器已提示 */
  }
}

// ---------- 导出（异步任务，进度见右下角浮层） ----------
const exportMenuOpen = ref(false)
const exportTasksStore = useExportTasksStore()

async function onExport(fmt: ExportFormat) {
  exportMenuOpen.value = false
  try {
    await exportTasksStore.startExport(
      props.tab.connectionId,
      props.tab.database,
      props.tab.table,
      fmt,
      activeFilters.value,
    )
  } catch {
    /* 拦截器已提示 */
  }
}
</script>

<template>
  <div class="table-data-tab">
    <!-- 工具栏 -->
    <div class="td-toolbar">
      <button class="btn btn-sm" :disabled="loading" @click="load">刷新</button>
      <button class="btn btn-primary btn-sm" @click="newRowVisible = true">＋ 新增行</button>
      <button
        class="btn btn-danger btn-sm"
        :disabled="selected.length === 0"
        :title="primaryKeys.length === 0 ? '无主键表不支持删除' : ''"
        @click="onDeleteSelected"
      >
        删除行{{ selected.length ? `（${selected.length}）` : '' }}
      </button>
      <div class="qt-export">
        <button class="btn btn-sm" @click="exportMenuOpen = !exportMenuOpen">导出 ▾</button>
        <div v-if="exportMenuOpen" class="qt-export-mask" @click="exportMenuOpen = false"></div>
        <div v-if="exportMenuOpen" class="qt-export-menu">
          <div class="qt-export-item" @click="onExport('csv')">CSV</div>
          <div class="qt-export-item" @click="onExport('json')">JSON</div>
          <div class="qt-export-item" @click="onExport('sql')">SQL</div>
        </div>
      </div>
      <span v-if="primaryKeys.length === 0" class="td-nopk">无主键 · 只读</span>
      <span class="spacer"></span>

      <!-- 分页控件 -->
      <div class="td-pager">
        <button class="btn btn-sm" :disabled="page <= 0 || loading" @click="gotoPage(page - 1)">‹ 上一页</button>
        <span class="td-page-info">
          第
          <input
            class="input td-page-input"
            type="number"
            :value="page + 1"
            :min="1"
            :max="totalPages"
            @change="gotoPage(Number(($event.target as HTMLInputElement).value) - 1)"
          />
          / {{ totalPages }} 页
        </span>
        <button class="btn btn-sm" :disabled="page >= totalPages - 1 || loading" @click="gotoPage(page + 1)">下一页 ›</button>
        <select v-model.number="size" class="input td-size" @change="onSizeChange">
          <option :value="50">50 / 页</option>
          <option :value="100">100 / 页</option>
          <option :value="200">200 / 页</option>
          <option :value="500">500 / 页</option>
        </select>
        <span class="td-total">共 {{ formatNumber(total) }} 行</span>
      </div>
    </div>

    <!-- 筛选条件 chips -->
    <div v-if="activeFilters.length" class="td-filter-chips">
      <span class="td-chip-label">筛选：</span>
      <span v-for="f in activeFilters" :key="f.column" class="td-chip">
        {{ f.column }} {{ f.operator }}<template v-if="f.value !== undefined">「{{ f.value }}」</template>
        <button class="td-chip-x" @click="removeFilter(f.column)">×</button>
      </span>
    </div>

    <!-- 数据网格 -->
    <div class="td-grid">
      <DataGrid
        :columns="gridColumns"
        :rows="rows"
        :loading="loading"
        selectable
        editable
        v-model:selected="selected"
        :start-index="page * size"
        :reset-scroll-key="resetScrollKey"
        @sort="onSort"
        @filter="onFilter"
        @save-cell="onSaveCell"
      />
    </div>

    <NewRowModal
      v-model:visible="newRowVisible"
      :columns="columns"
      :primary-keys="primaryKeys"
      @submit="onCreateRow"
    />
  </div>
</template>

<style scoped>
.table-data-tab {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  background: var(--bg-1);
}

.td-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  background: var(--bg-2);
  border-bottom: 1px solid var(--border-soft);
  flex-shrink: 0;
  flex-wrap: wrap;
}

.td-nopk {
  color: var(--yellow);
  font-size: 11.5px;
}

.spacer {
  flex: 1;
}

.td-pager {
  display: flex;
  align-items: center;
  gap: 8px;
}

.td-page-info {
  color: var(--text-1);
  font-size: 12px;
  display: flex;
  align-items: center;
  gap: 4px;
}

.td-page-input {
  width: 52px;
  text-align: center;
  padding: 3px 4px;
}

.td-size {
  width: 86px;
}

.td-total {
  color: var(--text-2);
  font-size: 12px;
}

.td-filter-chips {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  padding: 5px 10px;
  background: var(--bg-2);
  border-bottom: 1px solid var(--border-soft);
  flex-shrink: 0;
}

.td-chip-label {
  color: var(--text-2);
  font-size: 11.5px;
}

.td-chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  background: rgba(79, 140, 255, 0.15);
  color: var(--accent);
  border: 1px solid rgba(79, 140, 255, 0.35);
  border-radius: 10px;
  padding: 1px 8px;
  font-size: 11.5px;
}

.td-chip-x {
  background: none;
  border: none;
  color: inherit;
  cursor: pointer;
  font-size: 13px;
  padding: 0;
  line-height: 1;
}

.td-chip-x:hover {
  color: #fff;
}

.td-grid {
  flex: 1;
  min-height: 0;
}

/* 与 QueryTab 共用导出下拉样式 */
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
</style>
