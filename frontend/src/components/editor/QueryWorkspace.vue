<template>
  <div class="query-workspace">
    <div class="tab-bar">
      <div
        v-for="tab in workspaceStore.tabs"
        :key="tab.id"
        :class="['tab', { active: tab.id === workspaceStore.activeTabId }]"
        @click="workspaceStore.setActiveTab(tab.id)"
      >
        <span>{{ tab.title }}</span>
        <span class="tab-close" @click.stop="workspaceStore.closeTab(tab.id)">×</span>
      </div>
      <button class="new-tab-btn" @click="newTab" title="新建查询标签">+</button>
    </div>

    <div v-if="currentTab" class="query-content">
      <div class="editor-section">
        <div class="editor-toolbar">
          <select v-model="currentTab.connectionId" class="conn-select">
            <option :value="null">选择连接...</option>
            <option v-for="c in connectionStore.connections" :key="c.id" :value="c.id">
              {{ c.name }}
            </option>
          </select>
          <input
            v-if="currentTab.connectionId"
            v-model="currentTab.databaseName"
            class="db-input"
            placeholder="数据库名"
          />
          <button class="btn btn-primary btn-sm" @click="executeAll" :disabled="tabLoading">
            ▶ 执行全部 (Ctrl+Enter)
          </button>
          <button class="btn btn-secondary btn-sm" @click="executeSelected" :disabled="tabLoading">
            执行选中 (Ctrl+Shift+Enter)
          </button>
          <button v-if="tabLoading" class="btn btn-danger btn-sm" @click="cancelQuery">
            ■ 取消查询
          </button>
          <button class="btn btn-ghost btn-sm" @click="saveQuery" title="收藏查询" :disabled="tabLoading">
            ★ 收藏
          </button>
          <div class="toolbar-spacer" />
          <button class="btn btn-ghost btn-sm" @click="exportResult('csv')">CSV</button>
          <button class="btn btn-ghost btn-sm" @click="exportResult('json')">JSON</button>
          <button class="btn btn-ghost btn-sm" @click="exportResult('sql')">SQL</button>
        </div>
        <div class="editor-container">
          <SqlEditor
            v-model="currentTab.sql"
            @execute-all="executeAll"
            @execute="executeSelected"
          />
        </div>
      </div>

      <div class="result-section">
        <div class="result-tabs">
          <button
            :class="['result-tab', { active: workspaceStore.rightPanelTab === 'result' }]"
            @click="workspaceStore.setRightPanel('result')"
          >结果</button>
          <button
            v-if="workspaceStore.selectedTableForMeta"
            :class="['result-tab', { active: workspaceStore.rightPanelTab === 'structure' }]"
            @click="workspaceStore.setRightPanel('structure')"
          >表结构</button>
          <button
            v-if="workspaceStore.selectedTableForMeta"
            :class="['result-tab', { active: workspaceStore.rightPanelTab === 'ddl' }]"
            @click="workspaceStore.setRightPanel('ddl')"
          >建表语句</button>
        </div>
        <div class="result-content">
          <ResultPanel
            v-if="workspaceStore.rightPanelTab === 'result'"
            :result="currentTab.result"
            :loading="currentTab.loading"
            :error="currentTab.error"
            @export="exportResult"
          />
          <TableStructurePanel
            v-else-if="workspaceStore.rightPanelTab === 'structure' && workspaceStore.selectedTableForMeta"
            v-bind="workspaceStore.selectedTableForMeta"
          />
          <DdlPanel
            v-else-if="workspaceStore.rightPanelTab === 'ddl' && workspaceStore.selectedTableForMeta"
            v-bind="workspaceStore.selectedTableForMeta"
          />
        </div>
      </div>
    </div>
    <div v-else class="no-tab">
      <div class="empty-state">
        <div class="empty-state-icon">📝</div>
        <div>点击 + 新建查询标签</div>
      </div>
    </div>

    <ExportProgressModal
      :visible="exportProgress.visible"
      :file-name="exportProgress.fileName"
      :progress="exportProgress.progress"
      :error="exportProgress.error"
      :cancelling="exportProgress.cancelling"
      @close="closeExportModal"
      @cancel="cancelExport"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import SqlEditor from './SqlEditor.vue'
import ResultPanel from './ResultPanel.vue'
import TableStructurePanel from '../table/TableStructurePanel.vue'
import DdlPanel from '../table/DdlPanel.vue'
import ExportProgressModal from '@/components/common/ExportProgressModal.vue'
import { useWorkspaceStore } from '@/stores/workspace'
import { useConnectionStore } from '@/stores/connection'
import { useToastStore } from '@/stores/toast'
import { queryApi, savedQueryApi, exportApi } from '@/api'
import { DownloadAbortedError, DownloadIncompleteError } from '@/utils/download'

const workspaceStore = useWorkspaceStore()
const connectionStore = useConnectionStore()
const toastStore = useToastStore()

const currentTab = computed(() => workspaceStore.activeTab)
const tabLoading = computed(() => currentTab.value?.loading)

const activeAbortController = ref<AbortController | null>(null)
const activeRequestId = ref<string | null>(null)
let queryCancelling = false

function genRequestId(prefix: string): string {
  return prefix + '-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 10)
}

function newTab() {
  workspaceStore.createQueryTab()
}

async function executeAll() {
  if (!currentTab.value) return
  if (!currentTab.value.connectionId) {
    toastStore.error('请先选择连接')
    return
  }
  const tab = currentTab.value
  const requestId = genRequestId('q')
  const controller = new AbortController()
  activeAbortController.value = controller
  activeRequestId.value = requestId
  queryCancelling = false
  tab.loading = true
  tab.error = null
  try {
    const result = await queryApi.execute({
      connectionId: tab.connectionId,
      databaseName: tab.databaseName || undefined,
      sql: tab.sql,
      page: 1,
      pageSize: 500
    }, requestId, controller.signal)
    tab.result = result
    workspaceStore.setRightPanel('result')
    if (!result.query) {
      toastStore.success(`执行成功，影响 ${result.affectedRows} 行`)
    }
  } catch (e: any) {
    if (queryCancelling) {
      tab.error = '查询已取消'
      toastStore.info('查询已取消')
    } else {
      tab.error = e.message
      tab.result = null
      toastStore.error(e.message)
    }
  } finally {
    tab.loading = false
    activeAbortController.value = null
    activeRequestId.value = null
    queryCancelling = false
  }
}

async function executeSelected(selectedSql?: string | Event) {
  if (!currentTab.value) return
  if (!currentTab.value.connectionId) {
    toastStore.error('请先选择连接')
    return
  }
  const tab = currentTab.value
  let sql: string
  if (typeof selectedSql === 'string') {
    sql = selectedSql
  } else {
    sql = tab.sql
  }
  if (!sql.trim()) {
    toastStore.error('没有选中的 SQL')
    return
  }
  const requestId = genRequestId('q')
  const controller = new AbortController()
  activeAbortController.value = controller
  activeRequestId.value = requestId
  queryCancelling = false
  tab.loading = true
  tab.error = null
  try {
    const result = await queryApi.executeSelected(
      tab.connectionId!,
      tab.databaseName || undefined,
      sql,
      requestId,
      controller.signal
    )
    tab.result = result
    workspaceStore.setRightPanel('result')
  } catch (e: any) {
    if (queryCancelling) {
      tab.error = '查询已取消'
      toastStore.info('查询已取消')
    } else {
      tab.error = e.message
      tab.result = null
      toastStore.error(e.message)
    }
  } finally {
    tab.loading = false
    activeAbortController.value = null
    activeRequestId.value = null
    queryCancelling = false
  }
}

async function cancelQuery() {
  const requestId = activeRequestId.value
  const controller = activeAbortController.value
  if (!requestId || !controller) return
  queryCancelling = true
  try {
    await queryApi.cancel(requestId)
  } catch {
    // ignore cancel endpoint errors; still abort local
  }
  controller.abort()
}

async function saveQuery() {
  if (!currentTab.value || !currentTab.value.sql) {
    toastStore.error('没有可收藏的 SQL')
    return
  }
  const title = prompt('请输入收藏名称：', currentTab.value.title)
  if (!title) return
  try {
    await savedQueryApi.create({
      title,
      sqlText: currentTab.value.sql,
      connectionId: currentTab.value.connectionId,
      databaseName: currentTab.value.databaseName
    })
    toastStore.success('收藏成功')
  } catch (e: any) {
    toastStore.error(e.message)
  }
}

const exportProgress = ref<{ visible: boolean; progress: any; error: string | null; fileName: string; cancelling: boolean }>({
  visible: false,
  progress: null,
  error: null,
  fileName: '',
  cancelling: false
})
let exportAbortController: AbortController | null = null
let exportRequestId: string | null = null
let exportAborted = false

async function exportResult(format: string) {
  if (!currentTab.value?.connectionId) {
    toastStore.error('请先选择连接')
    return
  }
  exportAbortController = new AbortController()
  exportRequestId = null
  exportAborted = false
  exportProgress.value = {
    visible: true,
    progress: { receivedBytes: 0, totalBytes: null, percentage: 0, done: false },
    error: null,
    fileName: `${currentTab.value.title}.${format}`,
    cancelling: false
  }
  try {
    const res = await exportApi.streamExportQuery(
      currentTab.value.connectionId,
      currentTab.value.databaseName,
      currentTab.value.sql,
      format,
      currentTab.value.title,
      (p) => { exportProgress.value.progress = p },
      exportAbortController.signal,
      (rid) => { exportRequestId = rid }
    )
    void res
    toastStore.success('导出完成')
  } catch (e: any) {
    if (e instanceof DownloadAbortedError || exportAborted) {
      exportProgress.value.error = '导出已取消'
      toastStore.info('导出已取消')
    } else if (e instanceof DownloadIncompleteError) {
      exportProgress.value.error = e.message
      toastStore.error('导出失败：连接中断，文件不完整，未保存')
    } else {
      exportProgress.value.error = e.message
      toastStore.error(e.message)
    }
  } finally {
    exportAbortController = null
    exportRequestId = null
    exportProgress.value.cancelling = false
  }
}

async function cancelExport() {
  if (!exportAbortController) return
  exportProgress.value.cancelling = true
  exportAborted = true
  if (exportRequestId) {
    try {
      await queryApi.cancel(exportRequestId)
    } catch {
      // ignore
    }
  }
  exportAbortController.abort()
}

function closeExportModal() {
  if (exportAbortController) {
    cancelExport()
    return
  }
  exportProgress.value.visible = false
}
</script>

<style scoped>
.query-workspace {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}
.tab-bar {
  display: flex;
  background: var(--bg-secondary);
  border-bottom: 1px solid var(--border-color);
  min-height: 36px;
  overflow-x: auto;
}
.new-tab-btn {
  padding: 0 12px;
  font-size: 18px;
  color: var(--text-secondary);
  background: transparent;
}
.new-tab-btn:hover { color: var(--accent); background: var(--bg-hover); }
.query-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.editor-section {
  height: 45%;
  min-height: 120px;
  display: flex;
  flex-direction: column;
  border-bottom: 1px solid var(--border-color);
}
.editor-toolbar {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  background: var(--bg-secondary);
  border-bottom: 1px solid var(--border-color);
}
.conn-select, .db-input {
  padding: 4px 8px;
  font-size: 12px;
  max-width: 200px;
}
.db-input { max-width: 140px; }
.toolbar-spacer { flex: 1; }
.editor-container {
  flex: 1;
  overflow: hidden;
}
.result-section {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.result-tabs {
  display: flex;
  background: var(--bg-secondary);
  border-bottom: 1px solid var(--border-color);
}
.result-tab {
  padding: 6px 16px;
  font-size: 12px;
  color: var(--text-secondary);
  background: transparent;
  border-bottom: 2px solid transparent;
}
.result-tab:hover { color: var(--text-primary); }
.result-tab.active {
  color: var(--accent);
  border-bottom-color: var(--accent);
}
.result-content {
  flex: 1;
  overflow: hidden;
}
.no-tab {
  flex: 1;
}
</style>
