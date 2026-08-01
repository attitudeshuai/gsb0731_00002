<template>
  <div class="modal-backdrop">
    <div class="modal">
      <div class="modal-header">
        <h3>Exporting {{ label }}</h3>
      </div>
      <div class="modal-body">
        <div class="status-line">
          <span class="status-tag" :class="progress.status.toLowerCase()">{{ progress.status }}</span>
          <span class="rows">{{ formatRows(progress.rowsWritten) }} rows</span>
          <span v-if="progress.totalRows" class="muted">
            / ~{{ formatRows(progress.totalRows) }}
          </span>
        </div>

        <div class="bar" v-if="progress.status === 'RUNNING'">
          <div
            class="bar-fill"
            :class="{ indeterminate: progress.percent === null }"
            :style="progress.percent !== null ? { width: progress.percent + '%' } : {}"
          ></div>
        </div>
        <div class="percent" v-if="progress.percent !== null && progress.status === 'RUNNING'">
          {{ progress.percent }}%
        </div>

        <p v-if="progress.status === 'COMPLETED'" class="done">
          ✓ Done — {{ formatRows(progress.rowsWritten) }} rows exported. Download starting…
        </p>
        <p v-if="progress.status === 'CANCELLED'" class="cancelled">■ Export cancelled.</p>
        <p v-if="progress.status === 'FAILED'" class="failed">✕ {{ progress.error || 'Export failed' }}</p>
      </div>
      <div class="modal-footer">
        <button
          v-if="progress.status === 'RUNNING'"
          class="danger"
          @click="$emit('cancel')"
          :disabled="cancelling"
        >
          {{ cancelling ? 'Cancelling…' : '■ Cancel Export' }}
        </button>
        <button v-else @click="$emit('close')">Close</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { ExportProgress } from '@/api'

defineProps<{
  progress: ExportProgress
  label: string
  cancelling?: boolean
}>()

defineEmits<{
  (e: 'cancel'): void
  (e: 'close'): void
}>()

function formatRows(n: number | null): string {
  if (n === null || n === undefined) return '0'
  return n.toLocaleString()
}
</script>

<style scoped>
.modal-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1100;
}
.modal {
  width: 420px;
  background: var(--bg-panel);
  border: 1px solid var(--border);
  border-radius: 8px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.5);
}
.modal-header {
  padding: 14px 16px;
  border-bottom: 1px solid var(--border);
}
.modal-header h3 { font-size: 15px; }
.modal-body {
  padding: 16px;
}
.status-line {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 10px;
}
.status-tag {
  font-size: 10px;
  font-weight: 700;
  padding: 1px 7px;
  border-radius: 3px;
  color: #11111b;
  background: var(--accent);
}
.status-tag.completed { background: var(--green); }
.status-tag.cancelled { background: var(--text-dim); }
.status-tag.failed { background: var(--red); }
.rows { font-family: var(--mono); }
.bar {
  height: 8px;
  background: var(--bg);
  border-radius: 4px;
  overflow: hidden;
  border: 1px solid var(--border);
}
.bar-fill {
  height: 100%;
  background: var(--accent);
  transition: width 0.2s;
}
.bar-fill.indeterminate {
  width: 40%;
  animation: slide 1.2s infinite ease-in-out;
}
@keyframes slide {
  0% { margin-left: -40%; }
  100% { margin-left: 100%; }
}
.percent {
  text-align: right;
  font-size: 11px;
  color: var(--text-dim);
  margin-top: 4px;
}
.done { color: var(--green); margin-top: 8px; }
.cancelled { color: var(--text-dim); margin-top: 8px; }
.failed { color: var(--red); margin-top: 8px; white-space: pre-wrap; }
.modal-footer {
  display: flex;
  justify-content: flex-end;
  padding: 14px 16px;
  border-top: 1px solid var(--border);
}
</style>
