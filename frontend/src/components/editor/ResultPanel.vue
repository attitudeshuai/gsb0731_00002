<template>
  <div class="result-panel">
    <div v-if="loading" class="result-loading">执行中...</div>
    <div v-else-if="error" class="result-error">
      <div class="error-title">执行错误</div>
      <pre class="error-msg">{{ error }}</pre>
    </div>
    <template v-else-if="result">
      <div class="result-status">
        <span v-if="result.query">
          返回 {{ result.rows.length }} 行
          <template v-if="result.total !== undefined && result.total !== null">
            （共 {{ result.total }} 行）
          </template>
        </span>
        <span v-else>影响行数：{{ result.affectedRows }}</span>
        <span class="elapsed">耗时 {{ result.elapsedMs }} ms</span>
        <div class="result-actions">
          <button class="btn btn-ghost btn-sm" @click="$emit('export', 'csv')">导出 CSV</button>
          <button class="btn btn-ghost btn-sm" @click="$emit('export', 'json')">导出 JSON</button>
          <button class="btn btn-ghost btn-sm" @click="$emit('export', 'sql')">导出 SQL</button>
        </div>
      </div>
      <div v-if="result.query && result.rows.length > 0" class="result-table-wrapper">
        <VirtualTable
          :columns="result.columns"
          :rows="result.rows"
          :editable="false"
        />
      </div>
      <div v-else-if="result.query" class="result-empty">查询成功，无返回数据</div>
      <div v-else class="result-success">
        <span class="success-icon">✓</span> 执行成功，影响 {{ result.affectedRows }} 行
      </div>
    </template>
    <div v-else class="result-placeholder">
      按 Ctrl+Enter 执行全部，Ctrl+Shift+Enter 执行选中
    </div>
  </div>
</template>

<script setup lang="ts">
import VirtualTable from '@/components/table/VirtualTable.vue'
import type { QueryResult } from '@/types'

defineProps<{
  result: QueryResult | null
  loading: boolean
  error: string | null
}>()

defineEmits<{
  export: [format: string]
}>()
</script>

<style scoped>
.result-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
  background: var(--bg-primary);
}
.result-status {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 6px 12px;
  background: var(--bg-secondary);
  border-bottom: 1px solid var(--border-color);
  font-size: 12px;
  color: var(--text-secondary);
  flex-shrink: 0;
}
.elapsed { color: var(--text-muted); }
.result-actions {
  margin-left: auto;
  display: flex;
  gap: 4px;
}
.result-table-wrapper {
  flex: 1;
  overflow: hidden;
}
.result-loading, .result-placeholder, .result-empty, .result-success {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--text-muted);
  font-size: 13px;
}
.result-success {
  color: var(--success);
  flex-direction: column;
  gap: 8px;
}
.success-icon {
  font-size: 32px;
}
.result-error {
  padding: 16px;
  overflow: auto;
}
.error-title {
  color: var(--danger);
  font-weight: 600;
  margin-bottom: 8px;
}
.error-msg {
  color: var(--text-primary);
  font-family: var(--font-mono);
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
  background: var(--bg-secondary);
  padding: 10px;
  border-radius: 4px;
  border-left: 3px solid var(--danger);
}
</style>
