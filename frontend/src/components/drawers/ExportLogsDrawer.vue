<script setup lang="ts">
import { watch } from 'vue'
import { useQueryStore } from '@/stores/query'
import { formatDateTime } from '@/utils/format'

const props = defineProps<{ visible: boolean }>()
const emit = defineEmits<{ (e: 'update:visible', v: boolean): void }>()

const queryStore = useQueryStore()

watch(
  () => props.visible,
  (v) => {
    if (v) void queryStore.fetchExportLogs()
  },
)

function close() {
  emit('update:visible', false)
}
</script>

<template>
  <Teleport to="body">
    <div v-if="visible" class="drawer-mask" @mousedown.self="close">
      <aside class="drawer">
        <div class="drawer-header">
          <span class="drawer-title">导出记录</span>
          <span class="spacer"></span>
          <button class="btn btn-sm" @click="queryStore.fetchExportLogs()">刷新</button>
          <button class="modal-close" @click="close">×</button>
        </div>

        <div class="drawer-body">
          <div v-if="queryStore.exportLogsLoading && queryStore.exportLogs.length === 0" class="drawer-hint">
            <span class="spinner"></span>加载中…
          </div>
          <div v-else-if="queryStore.exportLogs.length === 0" class="drawer-hint">暂无导出记录</div>

          <table v-else class="export-table">
            <thead>
              <tr>
                <th>时间</th>
                <th>连接</th>
                <th>库 / 表</th>
                <th>格式</th>
                <th>文件名</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="log in queryStore.exportLogs" :key="log.id">
                <td class="nowrap">{{ formatDateTime(log.createdAt) }}</td>
                <td>{{ log.connectionName ?? (log.connectionId != null ? `#${log.connectionId}` : '-') }}</td>
                <td class="mono">{{ [log.databaseName, log.tableName].filter(Boolean).join(' / ') || '-' }}</td>
                <td>
                  <span class="fmt-badge">{{ (log.format ?? '-').toUpperCase() }}</span>
                </td>
                <td class="mono">{{ log.fileName ?? '-' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </aside>
    </div>
  </Teleport>
</template>

<style scoped>
.export-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}

.export-table th {
  position: sticky;
  top: 0;
  background: var(--bg-2);
  color: var(--text-1);
  text-align: left;
  padding: 8px 12px;
  border-bottom: 1px solid var(--border);
  white-space: nowrap;
}

.export-table td {
  padding: 7px 12px;
  border-bottom: 1px solid var(--border-soft);
  color: var(--text-0);
}

.nowrap {
  white-space: nowrap;
}

.mono {
  font-family: var(--font-mono);
  font-size: 11.5px;
}

.fmt-badge {
  background: rgba(79, 140, 255, 0.15);
  color: var(--accent);
  border-radius: 8px;
  padding: 0 8px;
  font-size: 10.5px;
  line-height: 18px;
  display: inline-block;
}

.spacer {
  flex: 1;
}
</style>
