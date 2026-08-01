<template>
  <div class="structure-tab">
    <div class="toolbar">
      <strong>{{ database }}.{{ table }}</strong>
      <span class="muted" v-if="structure">~{{ structure.estimatedRows ?? 0 }} rows</span>
      <span class="spacer"></span>
      <button :class="{ primary: view === 'columns' }" @click="view = 'columns'">Columns</button>
      <button :class="{ primary: view === 'indexes' }" @click="view = 'indexes'">Indexes</button>
      <button :class="{ primary: view === 'ddl' }" @click="view = 'ddl'">DDL</button>
    </div>

    <div class="body">
      <template v-if="!structure">Loading…</template>

      <table v-else-if="view === 'columns'" class="info-table">
        <thead>
          <tr>
            <th>Name</th><th>Type</th><th>Null</th><th>Default</th><th>Key</th><th>Extra</th><th>Comment</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="c in structure.columns" :key="c.name">
            <td>{{ c.name }}</td>
            <td>{{ c.dataType }}</td>
            <td>{{ c.nullable ? 'YES' : 'NO' }}</td>
            <td>{{ c.defaultValue ?? '—' }}</td>
            <td>{{ c.columnKey || '' }}</td>
            <td>{{ c.extra || '' }}</td>
            <td>{{ c.comment || '' }}</td>
          </tr>
        </tbody>
      </table>

      <table v-else-if="view === 'indexes'" class="info-table">
        <thead>
          <tr><th>Name</th><th>Unique</th><th>Type</th><th>Columns</th></tr>
        </thead>
        <tbody>
          <tr v-for="ix in structure.indexes" :key="ix.name">
            <td>{{ ix.name }}</td>
            <td>{{ ix.unique ? 'YES' : 'NO' }}</td>
            <td>{{ ix.type }}</td>
            <td>{{ ix.columns.join(', ') }}</td>
          </tr>
        </tbody>
      </table>

      <pre v-else class="ddl">{{ structure.ddl }}</pre>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import type { TableStructure } from '@/types'
import { metadataApi } from '@/api'
import { useUiStore } from '@/stores/ui'

const props = defineProps<{
  connectionId: number
  database: string
  table: string
  initialView?: 'columns' | 'indexes' | 'ddl'
}>()

const ui = useUiStore()
const structure = ref<TableStructure | null>(null)
const view = ref<'columns' | 'indexes' | 'ddl'>(props.initialView ?? 'columns')

onMounted(async () => {
  try {
    structure.value = await metadataApi.structure(props.connectionId, props.database, props.table)
  } catch (e: any) {
    ui.error(e.message)
  }
})
</script>

<style scoped>
.structure-tab {
  display: flex;
  flex-direction: column;
  height: 100%;
}
.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 10px;
  border-bottom: 1px solid var(--border);
}
.spacer { flex: 1; }
.body {
  flex: 1;
  overflow: auto;
  padding: 10px;
}
.info-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}
.info-table th, .info-table td {
  border: 1px solid var(--border);
  padding: 5px 8px;
  text-align: left;
}
.info-table th {
  background: var(--bg-alt);
}
.ddl {
  font-family: var(--mono);
  font-size: 12px;
  white-space: pre;
  padding: 12px;
  background: var(--bg-alt);
  border-radius: var(--radius);
  overflow: auto;
}
</style>
