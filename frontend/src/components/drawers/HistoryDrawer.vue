<script setup lang="ts">
import { ref, watch } from 'vue'
import { useQueryStore } from '@/stores/query'
import { useConnectionStore } from '@/stores/connections'
import { useTabsStore } from '@/stores/tabs'
import { confirmDialog } from '@/ui/confirm'
import { toast } from '@/ui/toast'
import { formatDateTime, formatDuration, sqlSummary } from '@/utils/format'
import type { QueryHistoryItem } from '@/types'

const props = defineProps<{ visible: boolean }>()
const emit = defineEmits<{ (e: 'update:visible', v: boolean): void }>()

const queryStore = useQueryStore()
const connStore = useConnectionStore()
const tabsStore = useTabsStore()

watch(
  () => props.visible,
  (v) => {
    if (v) void queryStore.fetchHistory(true)
  },
)

function close() {
  emit('update:visible', false)
}

function connName(id?: number) {
  if (id == null) return '-'
  return connStore.connectionById(id)?.name ?? `#${id}`
}

function statusClass(h: QueryHistoryItem) {
  const s = (h.status ?? 'SUCCESS').toUpperCase()
  if (s === 'SUCCESS' || s === 'OK') return 'st-ok'
  if (s === 'TIMEOUT') return 'st-timeout'
  if (s === 'CANCELLED') return 'st-cancelled'
  return 'st-fail'
}

function statusText(h: QueryHistoryItem) {
  const s = (h.status ?? 'SUCCESS').toUpperCase()
  if (s === 'SUCCESS' || s === 'OK') return '成功'
  if (s === 'TIMEOUT') return '超时'
  if (s === 'CANCELLED') return '取消'
  return '失败'
}

function loadIntoEditor(h: QueryHistoryItem) {
  tabsStore.loadSqlIntoEditor(h.sqlText, h.connectionId, h.databaseName)
  toast.success('已载入编辑器')
  close()
}

async function removeItem(h: QueryHistoryItem, ev: MouseEvent) {
  ev.stopPropagation()
  try {
    await queryStore.removeHistoryItem(h.id)
  } catch {
    /* 拦截器已提示 */
  }
}

const clearing = ref(false)

async function clearAll() {
  const scope = queryStore.historyConnectionId
    ? `连接「${connName(queryStore.historyConnectionId)}」的`
    : '全部'
  const ok = await confirmDialog(`确定清空${scope}执行历史吗？`, {
    title: '清空历史',
    danger: true,
    okText: '清空',
  })
  if (!ok) return
  clearing.value = true
  try {
    await queryStore.clearAllHistory()
    toast.success('历史已清空')
  } catch {
    /* 拦截器已提示 */
  } finally {
    clearing.value = false
  }
}
</script>

<template>
  <Teleport to="body">
    <div v-if="visible" class="drawer-mask" @mousedown.self="close">
      <aside class="drawer">
        <div class="drawer-header">
          <span class="drawer-title">执行历史</span>
          <select
            class="input drawer-conn-filter"
            :value="queryStore.historyConnectionId"
            @change="queryStore.setHistoryConnection(Number(($event.target as HTMLSelectElement).value))"
          >
            <option :value="0">全部连接</option>
            <option v-for="c in connStore.connections" :key="c.id" :value="c.id">{{ c.name }}</option>
          </select>
          <button class="btn btn-sm" @click="queryStore.fetchHistory(true)">刷新</button>
          <button class="btn btn-danger btn-sm" :disabled="clearing" @click="clearAll">清空</button>
          <button class="modal-close" @click="close">×</button>
        </div>

        <div class="drawer-body">
          <div v-if="queryStore.historyLoading && queryStore.history.length === 0" class="drawer-hint">
            <span class="spinner"></span>加载中…
          </div>
          <div v-else-if="queryStore.history.length === 0" class="drawer-hint">暂无历史记录</div>

          <div
            v-for="h in queryStore.history"
            :key="h.id"
            class="history-item"
            @click="loadIntoEditor(h)"
            :title="h.sqlText"
          >
            <div class="history-sql">{{ sqlSummary(h.sqlText) }}</div>
            <div class="history-meta">
              <span class="st-badge" :class="statusClass(h)">{{ statusText(h) }}</span>
              <span>{{ connName(h.connectionId) }}</span>
              <span v-if="h.databaseName">· {{ h.databaseName }}</span>
              <span>· {{ formatDuration(h.durationMs) }}</span>
              <span class="spacer"></span>
              <span>{{ formatDateTime(h.executedAt ?? h.createdAt) }}</span>
              <button class="history-del" title="删除" @click="removeItem(h, $event)">×</button>
            </div>
          </div>

          <div v-if="queryStore.historyHasMore" class="drawer-more">
            <button class="btn btn-sm" :disabled="queryStore.historyLoading" @click="queryStore.loadMoreHistory()">
              {{ queryStore.historyLoading ? '加载中…' : `加载更多（已显示 ${queryStore.history.length}/${queryStore.historyTotal}）` }}
            </button>
          </div>
        </div>
      </aside>
    </div>
  </Teleport>
</template>

<style scoped>
.drawer-conn-filter {
  width: 130px;
}

.history-item {
  padding: 8px 14px;
  border-bottom: 1px solid var(--border-soft);
  cursor: pointer;
}

.history-item:hover {
  background: var(--bg-hover);
}

.history-sql {
  color: var(--text-0);
  font-family: var(--font-mono);
  font-size: 12px;
  margin-bottom: 4px;
  word-break: break-all;
}

.history-meta {
  display: flex;
  align-items: center;
  gap: 5px;
  color: var(--text-2);
  font-size: 11px;
}

.st-badge {
  padding: 0 6px;
  border-radius: 8px;
  font-size: 10.5px;
}

.st-ok {
  background: rgba(63, 185, 80, 0.16);
  color: var(--green);
}

.st-fail {
  background: rgba(245, 83, 75, 0.14);
  color: var(--red);
}

.st-timeout {
  background: rgba(210, 153, 34, 0.16);
  color: var(--yellow);
}

.st-cancelled {
  background: rgba(111, 118, 131, 0.18);
  color: var(--text-2);
}

.spacer {
  flex: 1;
}

.history-del {
  background: none;
  border: none;
  color: var(--text-2);
  cursor: pointer;
  font-size: 14px;
  padding: 0 2px;
}

.history-del:hover {
  color: var(--red);
}

.drawer-more {
  padding: 12px;
  text-align: center;
}
</style>
