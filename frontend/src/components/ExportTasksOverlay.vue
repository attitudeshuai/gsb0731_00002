<script setup lang="ts">
import { computed, ref } from 'vue'
import { useExportTasksStore } from '@/stores/exportTasks'
import { formatNumber } from '@/utils/format'
import type { ExportTask } from '@/types'

const store = useExportTasksStore()

const tasks = computed(() =>
  store.order.map((id) => store.tasks.get(id)).filter((t): t is ExportTask => !!t),
)

function isRunning(t: ExportTask) {
  return t.status === 'PENDING' || t.status === 'RUNNING'
}

function statusText(t: ExportTask) {
  switch (t.status) {
    case 'PENDING':
      return '排队中'
    case 'RUNNING':
      return '导出中'
    case 'COMPLETED':
      return '已完成'
    case 'FAILED':
      return '失败'
    case 'CANCELLED':
      return '已取消'
  }
}

function statusClass(t: ExportTask) {
  switch (t.status) {
    case 'COMPLETED':
      return 'et-ok'
    case 'FAILED':
      return 'et-fail'
    case 'CANCELLED':
      return 'et-cancelled'
    default:
      return 'et-running'
  }
}

/** 进度百分比；estimatedRows 无效时返回 null（不显示进度条） */
function progress(t: ExportTask): number | null {
  if (!t.estimatedRows || t.estimatedRows <= 0) return null
  return Math.min(100, Math.round((t.rowsExported / t.estimatedRows) * 100))
}

const cancellingIds = ref<Set<string>>(new Set())

async function onCancel(t: ExportTask) {
  if (cancellingIds.value.has(t.taskId)) return
  cancellingIds.value = new Set(cancellingIds.value).add(t.taskId)
  try {
    await store.cancel(t.taskId)
  } catch {
    /* 拦截器已提示 */
  } finally {
    const next = new Set(cancellingIds.value)
    next.delete(t.taskId)
    cancellingIds.value = next
  }
}
</script>

<template>
  <Teleport to="body">
    <div v-if="store.overlayVisible && tasks.length > 0" class="et-overlay">
      <div class="et-header">
        <span class="et-title">导出任务（{{ tasks.length }}）</span>
        <span class="spacer"></span>
        <button class="et-x" title="隐藏（任务继续后台运行）" @click="store.hideOverlay()">×</button>
      </div>

      <div class="et-list">
        <div v-for="t in tasks" :key="t.taskId" class="et-item">
          <div class="et-row">
            <span class="et-table" :title="t.tableName">{{ t.tableName }}</span>
            <span class="et-fmt">{{ t.format.toUpperCase() }}</span>
            <span class="et-status" :class="statusClass(t)">{{ statusText(t) }}</span>
            <span class="spacer"></span>
            <button
              v-if="isRunning(t)"
              class="btn btn-danger btn-sm"
              :disabled="cancellingIds.has(t.taskId)"
              @click="onCancel(t)"
            >
              {{ cancellingIds.has(t.taskId) ? '取消中…' : '取消' }}
            </button>
            <button
              v-else-if="t.status === 'COMPLETED'"
              class="btn btn-sm"
              title="重新下载"
              @click="store.download(t.taskId)"
            >
              下载
            </button>
            <button v-if="!isRunning(t)" class="et-x" title="移除" @click="store.dismiss(t.taskId)">×</button>
          </div>

          <div class="et-progress-row">
            <template v-if="isRunning(t)">
              <span class="spinner et-spinner"></span>
              <span class="et-progress-text">
                已导出 {{ formatNumber(t.rowsExported) }} 行
                <template v-if="progress(t) != null">（{{ progress(t) }}%）</template>
              </span>
            </template>
            <span v-else-if="t.status === 'COMPLETED'" class="et-progress-text">
              共 {{ formatNumber(t.rowsExported) }} 行
            </span>
            <span v-else-if="t.status === 'FAILED'" class="et-error-text" :title="t.errorMessage ?? ''">
              {{ t.errorMessage || '导出失败' }}
            </span>
            <span v-else class="et-progress-text">已导出 {{ formatNumber(t.rowsExported) }} 行</span>
          </div>

          <div v-if="isRunning(t) && progress(t) != null" class="et-bar">
            <div class="et-bar-inner" :style="{ width: progress(t) + '%' }"></div>
          </div>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.et-overlay {
  position: fixed;
  right: 16px;
  bottom: 16px;
  width: 320px;
  max-height: 50vh;
  display: flex;
  flex-direction: column;
  background: var(--bg-2);
  border: 1px solid var(--border);
  border-radius: 8px;
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.55);
  z-index: 950;
  overflow: hidden;
}

.et-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: var(--bg-3);
  border-bottom: 1px solid var(--border);
  flex-shrink: 0;
}

.et-title {
  color: var(--text-0);
  font-size: 12.5px;
  font-weight: 600;
}

.et-list {
  overflow-y: auto;
  min-height: 0;
}

.et-item {
  padding: 8px 12px;
  border-bottom: 1px solid var(--border-soft);
}

.et-item:last-child {
  border-bottom: none;
}

.et-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.et-table {
  color: var(--text-0);
  font-size: 12.5px;
  font-family: var(--font-mono);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 120px;
}

.et-fmt {
  background: rgba(79, 140, 255, 0.15);
  color: var(--accent);
  border-radius: 8px;
  padding: 0 7px;
  font-size: 10.5px;
  line-height: 17px;
  flex-shrink: 0;
}

.et-status {
  font-size: 11px;
  flex-shrink: 0;
}

.et-ok {
  color: var(--green);
}

.et-fail {
  color: var(--red);
}

.et-cancelled {
  color: var(--text-2);
}

.et-running {
  color: var(--accent);
}

.et-progress-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 5px;
}

.et-spinner {
  width: 10px;
  height: 10px;
  border-width: 1.5px;
}

.et-progress-text {
  color: var(--text-1);
  font-size: 11.5px;
}

.et-error-text {
  color: var(--red);
  font-size: 11.5px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.et-bar {
  margin-top: 6px;
  height: 4px;
  border-radius: 2px;
  background: var(--bg-0);
  overflow: hidden;
}

.et-bar-inner {
  height: 100%;
  background: var(--accent);
  border-radius: 2px;
  transition: width 0.3s ease;
}

.et-x {
  background: none;
  border: none;
  color: var(--text-2);
  cursor: pointer;
  font-size: 14px;
  padding: 0 2px;
  line-height: 1;
  flex-shrink: 0;
}

.et-x:hover {
  color: var(--text-0);
}

.spacer {
  flex: 1;
}
</style>
