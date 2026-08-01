<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getTableStructure } from '@/api/metadata'
import type { TableStructureTabData } from '@/stores/tabs'
import { toast } from '@/ui/toast'
import { formatNumber } from '@/utils/format'
import type { TableStructure } from '@/types'

const props = defineProps<{ tab: TableStructureTabData }>()

const structure = ref<TableStructure | null>(null)
const loading = ref(true)

onMounted(async () => {
  loading.value = true
  try {
    structure.value = await getTableStructure(props.tab.connectionId, props.tab.database, props.tab.table)
  } catch {
    /* 拦截器已提示 */
  } finally {
    loading.value = false
  }
})

async function copyDdl() {
  if (!structure.value?.ddl) return
  try {
    await navigator.clipboard.writeText(structure.value.ddl)
    toast.success('建表语句已复制')
  } catch {
    const ta = document.createElement('textarea')
    ta.value = structure.value.ddl
    document.body.appendChild(ta)
    ta.select()
    document.execCommand('copy')
    document.body.removeChild(ta)
    toast.success('建表语句已复制')
  }
}

function indexColumns(columns: string[] | string): string {
  return Array.isArray(columns) ? columns.join(', ') : String(columns ?? '')
}
</script>

<template>
  <div class="struct-tab">
    <div v-if="loading" class="struct-loading"><span class="spinner"></span>加载中…</div>

    <template v-else-if="structure">
      <!-- 建表语句 -->
      <section class="struct-section">
        <div class="struct-head">
          <h3>建表语句（DDL）</h3>
          <div class="struct-head-right">
            <span class="struct-rows-est">估算行数：{{ formatNumber(structure.estimatedRows) }}</span>
            <button class="btn btn-sm" @click="copyDdl">复制</button>
          </div>
        </div>
        <pre class="struct-ddl">{{ structure.ddl || '（无 DDL）' }}</pre>
      </section>

      <!-- 列信息 -->
      <section class="struct-section">
        <div class="struct-head"><h3>列（{{ structure.columns.length }}）</h3></div>
        <div class="struct-table-wrap">
          <table class="struct-table">
            <thead>
              <tr>
                <th>名称</th>
                <th>类型</th>
                <th>可空</th>
                <th>键</th>
                <th>默认值</th>
                <th>额外</th>
                <th>注释</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="c in structure.columns" :key="c.name">
                <td class="col-name">{{ c.name }}</td>
                <td class="col-type">{{ c.type }}</td>
                <td>
                  <span :class="c.nullable ? 'tag tag-yes' : 'tag tag-no'">{{ c.nullable ? 'YES' : 'NO' }}</span>
                </td>
                <td>{{ c.key || '-' }}</td>
                <td>{{ c.defaultValue ?? 'NULL' }}</td>
                <td>{{ c.extra || '-' }}</td>
                <td class="col-comment">{{ c.comment || '-' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <!-- 索引 -->
      <section class="struct-section">
        <div class="struct-head"><h3>索引（{{ structure.indexes.length }}）</h3></div>
        <div class="struct-table-wrap">
          <table class="struct-table">
            <thead>
              <tr>
                <th>索引名</th>
                <th>类型</th>
                <th>列</th>
                <th>唯一</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="idx in structure.indexes" :key="idx.name">
                <td class="col-name">{{ idx.name }}</td>
                <td>{{ idx.type || '-' }}</td>
                <td class="col-type">{{ indexColumns(idx.columns) }}</td>
                <td>
                  <span :class="idx.unique ? 'tag tag-yes' : 'tag tag-no'">{{ idx.unique ? 'YES' : 'NO' }}</span>
                </td>
              </tr>
              <tr v-if="structure.indexes.length === 0">
                <td colspan="4" class="struct-empty">无索引</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </template>

    <div v-else class="struct-loading">加载失败</div>
  </div>
</template>

<style scoped>
.struct-tab {
  height: 100%;
  overflow: auto;
  padding: 14px 16px 32px;
  background: var(--bg-1);
}

.struct-loading {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text-1);
  padding: 24px;
}

.struct-section {
  margin-bottom: 22px;
}

.struct-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.struct-head h3 {
  margin: 0;
  font-size: 13px;
  color: var(--text-0);
  font-weight: 600;
}

.struct-head-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.struct-rows-est {
  color: var(--text-2);
  font-size: 12px;
}

.struct-ddl {
  margin: 0;
  padding: 12px 14px;
  background: var(--bg-0);
  border: 1px solid var(--border-soft);
  border-radius: 6px;
  color: var(--text-0);
  font-family: var(--font-mono);
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
  user-select: text;
}

.struct-table-wrap {
  border: 1px solid var(--border-soft);
  border-radius: 6px;
  overflow: auto;
}

.struct-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12.5px;
}

.struct-table th {
  background: var(--bg-2);
  color: var(--text-1);
  text-align: left;
  padding: 7px 12px;
  font-weight: 600;
  border-bottom: 1px solid var(--border);
  white-space: nowrap;
  position: sticky;
  top: 0;
}

.struct-table td {
  padding: 6px 12px;
  border-bottom: 1px solid var(--border-soft);
  color: var(--text-0);
  white-space: nowrap;
}

.struct-table tr:hover td {
  background: var(--bg-hover);
}

.col-name {
  font-family: var(--font-mono);
  color: var(--accent) !important;
}

.col-type {
  font-family: var(--font-mono);
}

.col-comment {
  color: var(--text-1) !important;
}

.tag {
  display: inline-block;
  padding: 0 7px;
  border-radius: 8px;
  font-size: 11px;
  line-height: 17px;
}

.tag-yes {
  background: rgba(63, 185, 80, 0.16);
  color: var(--green);
}

.tag-no {
  background: rgba(245, 83, 75, 0.14);
  color: var(--red);
}

.struct-empty {
  color: var(--text-2) !important;
  text-align: center;
}
</style>
