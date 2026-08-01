<template>
  <div class="history-view">
    <div class="page-header">
      <h1 class="page-title">查询历史</h1>
      <div class="header-actions">
        <input
          v-model="keyword"
          class="search-input"
          placeholder="搜索 SQL..."
          @input="debounceSearch"
        />
        <select v-model="filterConnection" class="filter-select" @change="reload">
          <option :value="null">全部连接</option>
          <option v-for="conn in connections" :key="conn.id" :value="conn.id">
            {{ conn.name }}
          </option>
        </select>
        <button class="btn btn-default" @click="onClearHistory" :disabled="loading">清空历史</button>
      </div>
    </div>
    <div class="card hv-table-card">
      <table class="hv-table" v-if="historyList.length">
        <thead>
          <tr>
            <th style="width: 160px">时间</th>
            <th style="width: 120px">连接</th>
            <th>SQL 预览</th>
            <th style="width: 80px">耗时</th>
            <th style="width: 80px">行数</th>
            <th style="width: 80px">状态</th>
            <th style="width: 80px">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="h in historyList" :key="h.id" @dblclick="reopenQuery(h)">
            <td class="col-time">{{ formatTime(h.executedAt) }}</td>
            <td class="col-conn">{{ getConnectionName(h.connectionId) }}</td>
            <td class="col-sql" :title="h.sqlText">{{ h.sqlText }}</td>
            <td class="col-duration">{{ h.executionTimeMs }}ms</td>
            <td class="col-rows">{{ h.rowCount ?? '-' }}</td>
            <td class="col-status">
              <span class="status-badge" :class="h.success ? 'status-success' : 'status-fail'">
                {{ h.success ? '成功' : '失败' }}
              </span>
            </td>
            <td class="col-actions" @click.stop>
              <button class="link-btn" @click="reopenQuery(h)">打开</button>
            </td>
          </tr>
        </tbody>
      </table>
      <div v-else-if="!loading" class="hv-empty">
        <div class="hv-empty-icon">🕐</div>
        <p>暂无查询历史</p>
        <p class="hv-empty-hint">执行 SQL 查询后历史记录将显示在此处</p>
      </div>
      <div v-if="loading" class="hv-loading">加载中...</div>
    </div>
    <div class="hv-pagination" v-if="totalPages > 1">
      <button class="btn btn-default btn-sm" :disabled="page <= 1" @click="goPage(page - 1)">
        上一页
      </button>
      <span class="hv-page-info">{{ page }} / {{ totalPages }}</span>
      <button
        class="btn btn-default btn-sm"
        :disabled="page >= totalPages"
        @click="goPage(page + 1)"
      >下一页</button>
    </div>
    <ConfirmDialog
      :visible="confirmVisible"
      type="danger"
      title="清空历史"
      message="确定要清空所有查询历史吗？此操作不可恢复。"
      @confirm="confirmClear"
      @cancel="confirmVisible = false"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getHistory, clearHistory } from '@/api/query'
import { useConnectionStore } from '@/stores/connection'
import { useToast } from '@/composables/useToast'
import ConfirmDialog from '@/components/common/ConfirmDialog.vue'
import type { QueryHistory } from '@/types'

const router = useRouter()
const connectionStore = useConnectionStore()
const toast = useToast()

const keyword = ref('')
const filterConnection = ref<number | null>(null)
const historyList = ref<QueryHistory[]>([])
const loading = ref(false)
const page = ref(1)
const pageSize = ref(20)
const totalElements = ref(0)
const confirmVisible = ref(false)
let searchTimer: ReturnType<typeof setTimeout> | null = null

const connections = computed(() => connectionStore.connections)
const totalPages = computed(() => Math.max(1, Math.ceil(totalElements.value / pageSize.value)))

onMounted(async () => {
  await connectionStore.fetchConnections()
  reload()
})

async function reload() {
  loading.value = true
  try {
    const result = await getHistory({
      page: page.value - 1,
      size: pageSize.value,
      connectionId: filterConnection.value ?? undefined,
      keyword: keyword.value || undefined
    })
    historyList.value = result.content
    totalElements.value = result.totalElements
  } catch (e: any) {
    toast.error(e.message || '加载历史失败')
  } finally {
    loading.value = false
  }
}

function debounceSearch() {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    page.value = 1
    reload()
  }, 300)
}

function goPage(p: number) {
  page.value = p
  reload()
}

function getConnectionName(id: number): string {
  const conn = connections.value.find((c) => c.id === id)
  return conn?.name || `#${id}`
}

function formatTime(iso: string): string {
  if (!iso) return '-'
  try {
    return new Date(iso).toLocaleString('zh-CN')
  } catch {
    return iso
  }
}

function reopenQuery(h: QueryHistory) {
  router.push({
    path: '/query',
    query: {
      connectionId: String(h.connectionId),
      sql: h.sqlText
    }
  })
}

function onClearHistory() {
  confirmVisible.value = true
}

async function confirmClear() {
  try {
    await clearHistory()
    toast.success('历史已清空')
    confirmVisible.value = false
    reload()
  } catch (e: any) {
    toast.error(e.message || '清空失败')
  }
}
</script>

<style scoped lang="scss">
@use '@/styles/index.scss' as *;

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.header-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}

.search-input {
  padding: 8px 14px;
  border: 1px solid $border-color;
  border-radius: $radius-sm;
  font-size: 14px;
  width: 240px;

  &:focus {
    border-color: $primary-color;
  }
}

.filter-select {
  padding: 8px 12px;
  border: 1px solid $border-color;
  border-radius: $radius-sm;
  font-size: 14px;
  background: #fff;
  min-width: 140px;
}

.hv-table-card {
  overflow: auto;
  padding: 0;
  position: relative;
  min-height: 400px;
}

.hv-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;

  th {
    background: #f7f8fa;
    padding: 12px 14px;
    text-align: left;
    font-weight: 600;
    color: $text-primary;
    border-bottom: 1px solid $border-color;
    position: sticky;
    top: 0;
  }

  td {
    padding: 10px 14px;
    border-bottom: 1px solid #f0f0f0;
    color: $text-secondary;
    vertical-align: top;
  }

  tr {
    cursor: pointer;

    &:hover td {
      background: #f9fafb;
    }
  }
}

.col-time {
  white-space: nowrap;
  color: $text-light;
  font-size: 12px;
}

.col-sql {
  max-width: 400px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
}

.col-duration,
.col-rows {
  font-variant-numeric: tabular-nums;
}

.status-badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 11px;
  font-weight: 600;
}

.status-success {
  background: rgba(52, 168, 83, 0.1);
  color: $success-color;
}

.status-fail {
  background: rgba(234, 67, 53, 0.1);
  color: $error-color;
}

.col-actions {
  white-space: nowrap;
}

.link-btn {
  background: transparent;
  color: $primary-color;
  padding: 2px 6px;
  font-size: 12px;

  &:hover {
    text-decoration: underline;
  }
}

.hv-empty {
  text-align: center;
  padding: 80px 20px;
  color: $text-light;
}

.hv-empty-icon {
  font-size: 48px;
  margin-bottom: 16px;
  opacity: 0.5;
}

.hv-empty-hint {
  font-size: 12px;
  margin-top: 6px;
}

.hv-loading {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  color: $text-light;
}

.hv-pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin-top: 16px;
  font-size: 13px;
  color: $text-secondary;
}
</style>
