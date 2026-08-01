<script setup lang="ts">
import { ref, watch } from 'vue'
import type { ColumnMeta } from '@/types'

const props = defineProps<{
  visible: boolean
  columns: ColumnMeta[]
  primaryKeys: string[]
}>()

const emit = defineEmits<{
  (e: 'update:visible', v: boolean): void
  (e: 'submit', values: Record<string, unknown>): void
}>()

interface FieldState {
  value: string
  isNull: boolean
}

const fields = ref<Record<string, FieldState>>({})
const submitting = ref(false)

function initFields() {
  const init: Record<string, FieldState> = {}
  for (const c of props.columns) {
    init[c.name] = { value: '', isNull: false }
  }
  fields.value = init
}

watch(
  () => props.visible,
  (v) => {
    if (v) initFields()
  },
  { immediate: true },
)

watch(
  () => props.columns,
  () => {
    if (props.visible) initFields()
  },
)

function close() {
  emit('update:visible', false)
}

function isPk(name: string) {
  return props.primaryKeys.includes(name)
}

async function onSubmit() {
  const values: Record<string, unknown> = {}
  for (const [col, f] of Object.entries(fields.value)) {
    if (f.isNull) values[col] = null
    else if (f.value !== '') values[col] = f.value
  }
  submitting.value = true
  try {
    emit('submit', values)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <Teleport to="body">
    <div v-if="visible" class="modal-overlay" @mousedown.self="close">
      <div class="modal" style="width: 480px">
        <div class="modal-header">
          <span>新增行</span>
          <button class="modal-close" @click="close">×</button>
        </div>

        <div class="modal-body newrow-body">
          <div v-for="c in columns" :key="c.name" class="newrow-item">
            <span class="newrow-label" :title="c.type">
              {{ c.name }}
              <i v-if="isPk(c.name)" class="pk-badge">PK</i>
              <em class="newrow-type">{{ c.type }}</em>
            </span>
            <input
              v-model="fields[c.name].value"
              class="input"
              :disabled="fields[c.name].isNull"
              :placeholder="fields[c.name].isNull ? 'NULL' : ''"
            />
            <label class="newrow-null">
              <input type="checkbox" v-model="fields[c.name].isNull" />
              NULL
            </label>
          </div>
          <div v-if="columns.length === 0" class="newrow-empty">列信息加载后可新增</div>
        </div>

        <div class="modal-footer">
          <button class="btn btn-sm" @click="close">取消</button>
          <button class="btn btn-primary btn-sm" :disabled="submitting || columns.length === 0" @click="onSubmit">
            提交
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.newrow-body {
  max-height: 55vh;
  overflow: auto;
}

.newrow-item {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.newrow-label {
  width: 170px;
  flex-shrink: 0;
  color: var(--text-1);
  font-size: 12.5px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pk-badge {
  font-style: normal;
  font-size: 10px;
  color: var(--yellow);
  border: 1px solid var(--yellow);
  border-radius: 3px;
  padding: 0 3px;
  margin-left: 4px;
}

.newrow-type {
  font-style: normal;
  color: var(--text-2);
  font-size: 10.5px;
  margin-left: 4px;
}

.newrow-null {
  display: flex;
  align-items: center;
  gap: 3px;
  color: var(--text-2);
  font-size: 11px;
  cursor: pointer;
  flex-shrink: 0;
}

.newrow-null input {
  accent-color: var(--accent);
  cursor: pointer;
}

.newrow-empty {
  color: var(--text-2);
  text-align: center;
  padding: 12px;
}
</style>
