<template>
  <div class="structure-panel">
    <div v-if="loading" class="loading">加载中...</div>
    <template v-else>
      <div class="section">
        <h4 class="section-title">列信息 ({{ columns.length }})</h4>
        <table class="meta-table">
          <thead>
            <tr>
              <th>名称</th>
              <th>类型</th>
              <th>可空</th>
              <th>主键</th>
              <th>自增</th>
              <th>默认值</th>
              <th>注释</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="col in columns" :key="col.name">
              <td class="col-name">{{ col.name }}</td>
              <td class="col-type">{{ formatType(col) }}</td>
              <td>{{ col.nullable ? '是' : '否' }}</td>
              <td>
                <span v-if="col.primaryKey" class="pk-badge">🔑</span>
              </td>
              <td>{{ col.autoIncrement ? '是' : '' }}</td>
              <td class="col-default">{{ col.defaultValue || '' }}</td>
              <td class="col-comment">{{ col.comment || '' }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="section">
        <h4 class="section-title">索引 ({{ indexes.length }})</h4>
        <table class="meta-table">
          <thead>
            <tr>
              <th>索引名</th>
              <th>列</th>
              <th>唯一</th>
              <th>类型</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(idx, i) in indexes" :key="i">
              <td>{{ idx.indexName }}</td>
              <td>{{ idx.columnName }}</td>
              <td>{{ idx.nonUnique ? '否' : '是' }}</td>
              <td>{{ idx.indexType || '' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { metadataApi } from '@/api'
import type { ColumnMeta, IndexInfo } from '@/types'

const props = defineProps<{
  connectionId: number
  database: string
  table: string
}>()

const columns = ref<ColumnMeta[]>([])
const indexes = ref<IndexInfo[]>([])
const loading = ref(false)

function formatType(col: ColumnMeta) {
  let t = col.type || ''
  if (col.precision && col.scale !== null && col.scale !== undefined && col.scale > 0) {
    t += `(${col.precision},${col.scale})`
  } else if (col.precision) {
    t += `(${col.precision})`
  }
  return t
}

async function load() {
  loading.value = true
  try {
    const [cols, idxs] = await Promise.all([
      metadataApi.describeTable(props.connectionId, props.database, props.table),
      metadataApi.listIndexes(props.connectionId, props.database, props.table)
    ])
    columns.value = cols
    indexes.value = idxs
  } finally {
    loading.value = false
  }
}

watch(() => [props.connectionId, props.database, props.table], load, { immediate: true })
</script>

<style scoped>
.structure-panel {
  height: 100%;
  overflow: auto;
  padding: 12px;
}
.section { margin-bottom: 20px; }
.section-title {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 8px;
  color: var(--text-secondary);
}
.meta-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}
.meta-table th {
  background: var(--bg-secondary);
  color: var(--text-secondary);
  padding: 6px 10px;
  text-align: left;
  border-bottom: 1px solid var(--border-color);
  font-weight: 600;
  white-space: nowrap;
}
.meta-table td {
  padding: 5px 10px;
  border-bottom: 1px solid rgba(58,58,82,0.3);
  color: var(--text-primary);
}
.meta-table tr:hover td { background: var(--bg-hover); }
.col-name { font-weight: 500; color: var(--accent); }
.col-type { font-family: var(--font-mono); color: var(--info); }
.col-default { color: var(--text-muted); }
.col-comment { color: var(--text-secondary); }
.pk-badge { font-size: 12px; }
.loading {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--text-muted);
}
</style>
