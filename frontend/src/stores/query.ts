import { defineStore } from 'pinia'
import {
  clearHistory,
  createQueryFolder,
  createSavedQuery,
  deleteHistoryItem,
  deleteQueryFolder,
  deleteSavedQuery,
  getExportLogs,
  getQueryHistory,
  listQueryFolders,
  listSavedQueries,
  renameQueryFolder,
} from '@/api/query'
import type { ExportLog, QueryFolder, QueryHistoryItem, SavedQuery } from '@/types'

const HISTORY_PAGE_SIZE = 30

export const useQueryStore = defineStore('query', {
  state: () => ({
    // 历史
    history: [] as QueryHistoryItem[],
    historyTotal: 0,
    historyPage: 0,
    historyHasMore: false,
    historyLoading: false,
    historyConnectionId: 0, // 0 = 全部
    // 收藏
    folders: [] as QueryFolder[],
    savedQueries: [] as SavedQuery[],
    savedLoading: false,
    // 导出记录
    exportLogs: [] as ExportLog[],
    exportLogsLoading: false,
  }),

  actions: {
    // ---------- 历史 ----------
    async fetchHistory(reset = true) {
      if (this.historyLoading) return
      this.historyLoading = true
      try {
        const page = reset ? 0 : this.historyPage + 1
        const resp = await getQueryHistory({
          connectionId: this.historyConnectionId || undefined,
          page,
          size: HISTORY_PAGE_SIZE,
        })
        if (reset) {
          this.history = resp.items
        } else {
          this.history = [...this.history, ...resp.items]
        }
        this.historyTotal = resp.total
        this.historyPage = page
        this.historyHasMore = resp.hasMore
      } finally {
        this.historyLoading = false
      }
    },

    async loadMoreHistory() {
      if (!this.historyHasMore) return
      await this.fetchHistory(false)
    },

    setHistoryConnection(connId: number) {
      this.historyConnectionId = connId
      void this.fetchHistory(true)
    },

    async removeHistoryItem(id: number) {
      await deleteHistoryItem(id)
      this.history = this.history.filter((h) => h.id !== id)
      this.historyTotal = Math.max(0, this.historyTotal - 1)
    },

    async clearAllHistory() {
      await clearHistory(this.historyConnectionId || undefined)
      await this.fetchHistory(true)
    },

    // ---------- 收藏 ----------
    async fetchFolders() {
      this.folders = await listQueryFolders()
    },

    async fetchSavedQueries() {
      this.savedLoading = true
      try {
        this.savedQueries = await listSavedQueries()
      } finally {
        this.savedLoading = false
      }
    },

    async addFolder(name: string) {
      await createQueryFolder(name)
      await this.fetchFolders()
    },

    async renameFolder(id: number, name: string) {
      await renameQueryFolder(id, name)
      await this.fetchFolders()
    },

    async removeFolder(id: number) {
      await deleteQueryFolder(id)
      await Promise.all([this.fetchFolders(), this.fetchSavedQueries()])
    },

    async saveQuery(payload: { name: string; sqlText: string; folderId?: number | null }) {
      await createSavedQuery(payload)
      await this.fetchSavedQueries()
    },

    async removeSavedQuery(id: number) {
      await deleteSavedQuery(id)
      this.savedQueries = this.savedQueries.filter((q) => q.id !== id)
    },

    // ---------- 导出记录 ----------
    async fetchExportLogs() {
      this.exportLogsLoading = true
      try {
        this.exportLogs = await getExportLogs()
      } finally {
        this.exportLogsLoading = false
      }
    },
  },
})
