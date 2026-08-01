import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { QueryResult } from '@/types'
import { executeQuery as apiExecuteQuery, cancelQuery as apiCancelQuery } from '@/api/query'

export interface QueryTab {
  id: string
  connectionId: number | null
  database?: string
  sql: string
  result: QueryResult | null
  loading: boolean
  error: string | null
  dirty: boolean
  maxRows: number
  executionId?: string
  abortController?: AbortController | null
}

export const useQueryStore = defineStore('query', () => {
  const tabs = ref<QueryTab[]>([])
  const activeTabId = ref<string | null>(null)
  const results = ref<Record<string, QueryResult>>({})

  const activeTab = computed(() =>
    tabs.value.find((t) => t.id === activeTabId.value) || null
  )

  function addTab(connectionId?: number, database?: string, sql?: string): string {
    const id = `tab-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
    const newTab: QueryTab = {
      id,
      connectionId: connectionId ?? null,
      database,
      sql: sql ?? '',
      result: null,
      loading: false,
      error: null,
      dirty: false,
      maxRows: 1000,
      abortController: null
    }
    tabs.value.push(newTab)
    activeTabId.value = id
    return id
  }

  function closeTab(id: string) {
    const tab = tabs.value.find((t) => t.id === id)
    if (tab?.loading && tab.abortController) {
      tab.abortController.abort()
    }
    const index = tabs.value.findIndex((t) => t.id === id)
    if (index === -1) return
    tabs.value.splice(index, 1)
    if (activeTabId.value === id) {
      if (tabs.value.length > 0) {
        activeTabId.value = tabs.value[Math.max(0, index - 1)].id
      } else {
        activeTabId.value = null
      }
    }
  }

  function setActiveTab(id: string) {
    activeTabId.value = id
  }

  function updateTabSql(id: string, sql: string) {
    const tab = tabs.value.find((t) => t.id === id)
    if (tab) {
      tab.sql = sql
      tab.dirty = true
    }
  }

  function setMaxRows(id: string, maxRows: number) {
    const tab = tabs.value.find((t) => t.id === id)
    if (tab) {
      tab.maxRows = maxRows
    }
  }

  async function executeQuery(tabId: string, sqlText?: string) {
    const tab = tabs.value.find((t) => t.id === tabId)
    if (!tab) return
    if (!tab.connectionId) {
      tab.error = '请先选择数据库连接'
      return
    }
    const sql = sqlText ?? tab.sql
    if (!sql.trim()) {
      tab.error = 'SQL 不能为空'
      return
    }

    const executionId =
      (crypto as any).randomUUID?.() ??
      `exec-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`
    const controller = new AbortController()
    tab.executionId = executionId
    tab.abortController = controller
    tab.loading = true
    tab.error = null

    try {
      const result = await apiExecuteQuery(
        tab.connectionId,
        {
          sql,
          database: tab.database,
          maxRows: tab.maxRows
        },
        executionId,
        controller.signal
      )
      tab.result = result
      results.value[tabId] = result
      tab.dirty = false
      if (result.cancelled) {
        tab.error = '查询已取消'
      }
    } catch (e: any) {
      if (controller.signal.aborted) {
        tab.error = '查询已取消'
        tab.result = null
      } else {
        tab.error = e.message || '查询执行失败'
      }
    } finally {
      tab.loading = false
      tab.abortController = null
      tab.executionId = undefined
    }
  }

  async function executeSelection(tabId: string, selectedSql: string) {
    return executeQuery(tabId, selectedSql)
  }

  async function cancelQuery(tabId: string) {
    const tab = tabs.value.find((t) => t.id === tabId)
    if (!tab) return
    const executionId = tab.executionId
    if (executionId) {
      try {
        await apiCancelQuery(executionId)
      } catch {
        // ignore cancel request errors; the statement may already be finished
      }
    }
    tab.abortController?.abort()
  }

  return {
    tabs,
    activeTabId,
    activeTab,
    results,
    addTab,
    closeTab,
    setActiveTab,
    updateTabSql,
    setMaxRows,
    executeQuery,
    executeSelection,
    cancelQuery
  }
})
