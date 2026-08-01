<template>
  <div class="ddl-panel">
    <div v-if="loading" class="loading">加载中...</div>
    <pre v-else class="ddl-content">{{ ddl }}</pre>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { metadataApi } from '@/api'

const props = defineProps<{
  connectionId: number
  database: string
  table: string
}>()

const ddl = ref('')
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await metadataApi.showCreateTable(props.connectionId, props.database, props.table)
    ddl.value = res.ddl
  } finally {
    loading.value = false
  }
}

watch(() => [props.connectionId, props.database, props.table], load, { immediate: true })
</script>

<style scoped>
.ddl-panel {
  height: 100%;
  overflow: auto;
  padding: 12px;
}
.ddl-content {
  font-family: var(--font-mono);
  font-size: 13px;
  color: var(--text-primary);
  background: var(--bg-secondary);
  padding: 14px;
  border-radius: 6px;
  border: 1px solid var(--border-color);
  white-space: pre-wrap;
  word-break: break-all;
  line-height: 1.6;
}
.loading {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--text-muted);
}
</style>
