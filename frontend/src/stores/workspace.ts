import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { QueryResult, ColumnMeta } from '@/types'

export interface QueryTab {
  id: string
  title: string
  connectionId: number | null
  databaseName: string
  sql: string
  result: QueryResult | null
  loading: boolean
  error: string | null
  selectedText?: string
}

export interface TableViewerState {
  connectionId: number
  databaseName: string
  tableName: string
}

let tabCounter = 0

export const useWorkspaceStore = defineStore('workspace', () => {
  const tabs = ref<QueryTab[]>([])
  const activeTabId = ref<string | null>(null)
  const activeTableViewer = ref<TableViewerState | null>(null)
  const rightPanelTab = ref<'result' | 'structure' | 'ddl' | 'history' | 'saved'>('result')
  const selectedTableForMeta = ref<{ connectionId: number; database: string; table: string } | null>(null)

  const activeTab = computed(() => tabs.value.find(t => t.id === activeTabId.value) || null)

  function createQueryTab(connectionId?: number, databaseName?: string, sql?: string, title?: string): string {
    tabCounter++
    const id = `tab-${Date.now()}-${tabCounter}`
    const tab: QueryTab = {
      id,
      title: title || `Query ${tabCounter}`,
      connectionId: connectionId ?? null,
      databaseName: databaseName || '',
      sql: sql || '',
      result: null,
      loading: false,
      error: null
    }
    tabs.value.push(tab)
    activeTabId.value = id
    rightPanelTab.value = 'result'
    return id
  }

  function closeTab(id: string) {
    const idx = tabs.value.findIndex(t => t.id === id)
    if (idx < 0) return
    tabs.value.splice(idx, 1)
    if (activeTabId.value === id) {
      if (tabs.value.length > 0) {
        activeTabId.value = tabs.value[Math.min(idx, tabs.value.length - 1)].id
      } else {
        activeTabId.value = null
      }
    }
  }

  function setActiveTab(id: string) {
    activeTabId.value = id
  }

  function updateTab(id: string, patch: Partial<QueryTab>) {
    const tab = tabs.value.find(t => t.id === id)
    if (tab) Object.assign(tab, patch)
  }

  function openTableViewer(connectionId: number, databaseName: string, tableName: string) {
    activeTableViewer.value = { connectionId, databaseName, tableName }
    selectedTableForMeta.value = { connectionId, database: databaseName, table: tableName }
    rightPanelTab.value = 'structure'
  }

  function showTableMeta(connectionId: number, database: string, table: string) {
    selectedTableForMeta.value = { connectionId, database, table }
    rightPanelTab.value = 'structure'
  }

  function setRightPanel(tab: 'result' | 'structure' | 'ddl' | 'history' | 'saved') {
    rightPanelTab.value = tab
  }

  return {
    tabs, activeTabId, activeTab,
    activeTableViewer, rightPanelTab, selectedTableForMeta,
    createQueryTab, closeTab, setActiveTab, updateTab,
    openTableViewer, showTableMeta, setRightPanel
  }
})
