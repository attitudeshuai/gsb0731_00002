<template>
  <teleport to="body">
    <div v-if="visible" class="modal-overlay export-overlay">
      <div class="export-modal">
        <h3 class="export-title">
          <span class="export-icon" :class="{ spinning: !done }">⬇</span>
          {{ done ? '导出完成' : '正在导出...' }}
        </h3>
        <div class="export-info">
          <span class="filename">{{ fileName }}</span>
          <span class="filesize">{{ formatBytes(receivedBytes) }}{{ totalBytes ? ' / ' + formatBytes(totalBytes) : '' }}</span>
        </div>
        <div class="progress-bar">
          <div class="progress-fill" :style="{ width: barWidth + '%' }" />
          <span v-if="totalBytes" class="progress-text">{{ percentage }}%</span>
          <span v-else class="progress-text indeterminate">接收中...</span>
        </div>
        <div v-if="error" class="export-error">{{ error }}</div>
        <div v-if="done" class="export-footer">
          <button class="btn btn-primary btn-sm" @click="$emit('close')">关闭</button>
        </div>
        <div v-else-if="!error" class="export-footer">
          <button class="btn btn-danger btn-sm" @click="$emit('cancel')" :disabled="cancelling">
            {{ cancelling ? '正在取消...' : '取消导出' }}
          </button>
        </div>
        <div v-else class="export-footer">
          <button class="btn btn-secondary btn-sm" @click="$emit('close')">关闭</button>
        </div>
      </div>
    </div>
  </teleport>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { formatBytes, type DownloadProgress } from '@/utils/download'

const props = defineProps<{
  visible: boolean
  fileName: string
  progress: DownloadProgress | null
  error?: string | null
  cancelling?: boolean
}>()

defineEmits<{ close: []; cancel: [] }>()

const receivedBytes = computed(() => props.progress?.receivedBytes || 0)
const totalBytes = computed(() => props.progress?.totalBytes || null)
const percentage = computed(() => props.progress?.percentage ? Math.max(0, props.progress.percentage) : 0)
const done = computed(() => props.progress?.done || false)
const barWidth = computed(() => totalBytes.value ? percentage.value : (done.value ? 100 : 40))
</script>

<style scoped>
.export-overlay { z-index: 2500; }
.export-modal {
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  width: 440px;
  padding: 20px;
  box-shadow: 0 8px 32px rgba(0,0,0,0.5);
}
.export-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 14px;
}
.export-icon { font-size: 18px; color: var(--accent); }
.export-icon.spinning { animation: spin 1.2s linear infinite; display: inline-block; }
@keyframes spin { from { transform: rotate(0); } to { transform: rotate(360deg); } }
.export-info {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: var(--text-secondary);
  margin-bottom: 10px;
}
.filename {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-right: 10px;
}
.progress-bar {
  position: relative;
  height: 22px;
  background: var(--bg-tertiary);
  border-radius: 4px;
  overflow: hidden;
}
.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, var(--accent), var(--accent-hover));
  transition: width 0.2s ease;
}
.progress-text {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  font-size: 11px;
  color: #fff;
  text-shadow: 0 0 3px rgba(0,0,0,0.6);
}
.progress-text.indeterminate { color: var(--text-secondary); text-shadow: none; }
.export-error {
  margin-top: 12px;
  padding: 8px 10px;
  background: rgba(224,85,85,0.15);
  border-left: 3px solid var(--danger);
  color: var(--danger);
  font-size: 12px;
  border-radius: 3px;
}
.export-footer {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
