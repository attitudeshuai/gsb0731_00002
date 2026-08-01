<template>
  <div class="query-tab">
    <div class="editor-toolbar">
      <button class="primary" @click="runAll" :disabled="running">▶ Run (Ctrl+Enter)</button>
      <button @click="runSelection" :disabled="running">▶ Run Selection</button>
      <button class="danger" v-if="running" @click="cancelQuery" :disabled="cancelling">
        {{ cancelling ? 'Cancelling…' : '■ Cancel' }}
      </button>
      <button @click="saveQuery">★ Save</button>
      <button @click="exportResult" :disabled="!result">⭳ Export</button>
      <select v-model="exportFormat" class="fmt">
        <option value="csv">CSV</option>
        <option value="json">JSON</option>
        <option value="sql">SQL INSERT</option>
      </select>
      <span class="spacer"></span>
      <span v-if="running" class="stats running">⏳ Running…</span>
      <span v-else-if="result" class="stats">
        {{ result.rowCount }} rows · {{ result.affectedRows ?? '—' }} affected · {{ result.durationMs }} ms
        <span v-if="result.truncated" class="warn">(truncated)</span>
      </span>
    </div>

    <div class="editor-pane" :style="{ height: editorHeight + 'px' }">
      <SqlEditor
        ref="editorRef"
        v-model="sqlText"
        :schema="schema"
        @run-all="runAll"
        @run-selection="runSelection"
      />
    </div>

    <div class="splitter" @mousedown="startDrag"></div>

    <div class="result-pane">
      <div v-if="error" class="error-box">{{ error }}</div>
      <DataGrid v-else-if="result" :columns="result.columns" :rows="result.rows" />
      <div v-else class="empty">Run a query to see results</div>
    </div>

    <ExportProgressModal
      v-if="exportJob.active.value && exportJob.progress.value"
      :progress="exportJob.progress.value"
      :label="exportJob.label.value"
      :cancelling="exportJob.cancelling.value"
      @cancel="exportJob.cancel"
      @close="exportJob.close"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import SqlEditor from './SqlEditor.vue'
import DataGrid from './DataGrid.vue'
import ExportProgressModal from './ExportProgressModal.vue'
import type { QueryResult } from '@/types'
import { queryApi, metadataApi, savedQueryApi } from '@/api'
import { useExportJob } from '@/composables/useExportJob'
import { useUiStore } from '@/stores/ui'

const props = defineProps<{
  connectionId: number
  database?: string
  initialSql?: string
}>()

const ui = useUiStore()
const exportJob = useExportJob(() => props.connectionId)

const sqlText = ref(props.initialSql ?? 'SELECT 1;')
const result = ref<QueryResult | null>(null)
const error = ref<string | null>(null)
const running = ref(false)
const cancelling = ref(false)
const currentToken = ref<string | null>(null)
const editorRef = ref<InstanceType<typeof SqlEditor> | null>(null)
const schema = ref<Record<string, string[]>>({})
const exportFormat = ref<'csv' | 'json' | 'sql'>('csv')

const editorHeight = ref(240)

async function runAll() {
  await execute(sqlText.value)
}

async function runSelection() {
  const sel = editorRef.value?.getSelection() ?? ''
  await execute(sel || sqlText.value)
}

function makeToken(): string {
  return 'q-' + Date.now() + '-' + Math.random().toString(36).slice(2, 10)
}

async function execute(sql: string) {
  if (!sql.trim()) return
  running.value = true
  cancelling.value = false
  error.value = null
  const token = makeToken()
  currentToken.value = token
  try {
    result.value = await queryApi.execute(props.connectionId, sql.trim(), token)
  } catch (e: any) {
    error.value = e.message
    result.value = null
  } finally {
    running.value = false
    cancelling.value = false
    currentToken.value = null
  }
}

/** Ask the backend to cancel the in-flight statement (real DB-side cancel). */
async function cancelQuery() {
  if (!currentToken.value) return
  cancelling.value = true
  try {
    const res = await queryApi.cancel(props.connectionId, currentToken.value)
    ui.info(res.cancelled ? 'Cancellation sent' : 'Query already finished')
  } catch (e: any) {
    ui.error(e.message)
    cancelling.value = false
  }
  // running/cancelling reset happens when execute()'s finally block runs
}

async function saveQuery() {
  const name = prompt('Save query as:')
  if (!name) return
  try {
    await savedQueryApi.create({
      name,
      connectionId: props.connectionId,
      sqlText: sqlText.value
    })
    ui.success('Query saved')
  } catch (e: any) {
    ui.error(e.message)
  }
}

function exportResult() {
  if (!result.value) return
  exportJob.startQuery(sqlText.value.trim(), exportFormat.value)
}

// --- splitter drag ---
function startDrag(e: MouseEvent) {
  const startY = e.clientY
  const startH = editorHeight.value
  const move = (ev: MouseEvent) => {
    editorHeight.value = Math.max(100, Math.min(600, startH + ev.clientY - startY))
  }
  const up = () => {
    document.removeEventListener('mousemove', move)
    document.removeEventListener('mouseup', up)
  }
  document.addEventListener('mousemove', move)
  document.addEventListener('mouseup', up)
}

async function loadSchema() {
  if (!props.database) return
  try {
    const tables = await metadataApi.tables(props.connectionId, props.database)
    const s: Record<string, string[]> = {}
    // load columns lazily would be heavy; provide table names for completion
    tables.forEach((t) => {
      s[t.name] = []
    })
    schema.value = s
  } catch {
    // completion schema is best-effort
  }
}

onMounted(loadSchema)
</script>

<style scoped>
.query-tab {
  display: flex;
  flex-direction: column;
  height: 100%;
}
.editor-toolbar {
  display: flex;
  gap: 6px;
  align-items: center;
  padding: 6px 10px;
  border-bottom: 1px solid var(--border);
}
.editor-toolbar .fmt { width: auto; }
.spacer { flex: 1; }
.stats {
  font-size: 12px;
  color: var(--text-dim);
}
.warn { color: var(--yellow); }
.stats.running { color: var(--accent); }
.editor-pane {
  min-height: 100px;
  border-bottom: 1px solid var(--border);
}
.splitter {
  height: 5px;
  cursor: row-resize;
  background: var(--bg-alt);
}
.splitter:hover {
  background: var(--accent);
}
.result-pane {
  flex: 1;
  overflow: hidden;
  min-height: 0;
}
.error-box {
  padding: 16px;
  color: var(--red);
  font-family: var(--mono);
  white-space: pre-wrap;
}
.empty {
  padding: 20px;
  color: var(--text-dim);
  text-align: center;
}
</style>
