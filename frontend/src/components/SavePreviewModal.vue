<template>
  <div class="modal-backdrop" @click.self="$emit('cancel')">
    <div class="modal">
      <div class="modal-header">
        <h3>Review changes before saving</h3>
        <button class="icon-btn" @click="$emit('cancel')">✕</button>
      </div>
      <div class="modal-body">
        <p class="summary">
          {{ counts.insert }} INSERT · {{ counts.update }} UPDATE · {{ counts.delete }} DELETE
          — these statements will run in a single transaction (all-or-nothing).
        </p>
        <ol class="stmt-list">
          <li v-for="(s, i) in statements" :key="i" :class="s.type">
            <span class="tag">{{ s.type.toUpperCase() }}</span>
            <code>{{ s.rendered }}</code>
          </li>
        </ol>
      </div>
      <div class="modal-footer">
        <button @click="$emit('cancel')">Cancel</button>
        <button class="primary" @click="$emit('confirm')" :disabled="busy">
          {{ busy ? 'Saving…' : 'Confirm & Execute' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { PreviewStatement } from '@/types'

const props = defineProps<{
  statements: PreviewStatement[]
  busy?: boolean
}>()

defineEmits<{
  (e: 'confirm'): void
  (e: 'cancel'): void
}>()

const counts = computed(() => {
  const c = { insert: 0, update: 0, delete: 0 }
  props.statements.forEach((s) => {
    c[s.type]++
  })
  return c
})
</script>

<style scoped>
.modal-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}
.modal {
  width: 640px;
  max-width: 90vw;
  max-height: 80vh;
  display: flex;
  flex-direction: column;
  background: var(--bg-panel);
  border: 1px solid var(--border);
  border-radius: 8px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.5);
}
.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid var(--border);
}
.modal-header h3 { font-size: 15px; }
.modal-body {
  padding: 12px 16px;
  overflow-y: auto;
}
.summary {
  font-size: 12px;
  color: var(--text-dim);
  margin-bottom: 10px;
}
.stmt-list {
  margin: 0;
  padding-left: 20px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.stmt-list li {
  font-size: 12px;
}
.stmt-list code {
  font-family: var(--mono);
  white-space: pre-wrap;
  word-break: break-all;
}
.tag {
  display: inline-block;
  font-size: 10px;
  font-weight: 700;
  padding: 1px 6px;
  border-radius: 3px;
  margin-right: 6px;
  color: #11111b;
}
li.insert .tag { background: var(--green); }
li.update .tag { background: var(--yellow); }
li.delete .tag { background: var(--red); }
.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 14px 16px;
  border-top: 1px solid var(--border);
}
</style>
