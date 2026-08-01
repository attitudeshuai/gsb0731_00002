import { defineStore } from 'pinia'

export type TabType = 'query' | 'table-data' | 'table-structure'

export interface TabBase {
  id: string
  type: TabType
  title: string
  connectionId: number
  database?: string
}

export interface QueryTabData extends TabBase {
  type: 'query'
  sql: string
}

export interface TableDataTabData extends TabBase {
  type: 'table-data'
  table: string
  database: string
}

export interface TableStructureTabData extends TabBase {
  type: 'table-structure'
  table: string
  database: string
}

export type WorkTab = QueryTabData | TableDataTabData | TableStructureTabData

let seq = 1

export const useTabsStore = defineStore('tabs', {
  state: () => ({
    tabs: [] as WorkTab[],
    activeTabId: '' as string,
    lastConnectionId: null as number | null,
    lastDatabase: '' as string,
  }),

  getters: {
    activeTab: (s): WorkTab | null => s.tabs.find((t) => t.id === s.activeTabId) ?? null,
  },

  actions: {
    setActive(id: string) {
      if (this.tabs.some((t) => t.id === id)) {
        this.activeTabId = id
        const tab = this.tabs.find((t) => t.id === id)!
        this.lastConnectionId = tab.connectionId
        if (tab.database) this.lastDatabase = tab.database
      }
    },

    addTab(tab: WorkTab) {
      this.tabs.push(tab)
      this.setActive(tab.id)
    },

    openQueryTab(connectionId: number, database?: string, sql = '') {
      const connTag = `c${connectionId}`
      const tab: QueryTabData = {
        id: `query-${Date.now()}-${seq++}`,
        type: 'query',
        title: `查询 ${seq - 1}`,
        connectionId,
        database: database || undefined,
        sql,
      }
      tab.title = `查询-${connTag}-${seq - 1}`
      this.addTab(tab)
      return tab
    },

    openTableDataTab(connectionId: number, database: string, table: string) {
      const id = `data-${connectionId}-${database}-${table}`
      const existing = this.tabs.find((t) => t.id === id)
      if (existing) {
        this.setActive(id)
        return existing
      }
      const tab: TableDataTabData = {
        id,
        type: 'table-data',
        title: table,
        connectionId,
        database,
        table,
      }
      this.addTab(tab)
      return tab
    },

    openStructureTab(connectionId: number, database: string, table: string) {
      const id = `struct-${connectionId}-${database}-${table}`
      const existing = this.tabs.find((t) => t.id === id)
      if (existing) {
        this.setActive(id)
        return existing
      }
      const tab: TableStructureTabData = {
        id,
        type: 'table-structure',
        title: `${table} 结构`,
        connectionId,
        database,
        table,
      }
      this.addTab(tab)
      return tab
    },

    closeTab(id: string) {
      const idx = this.tabs.findIndex((t) => t.id === id)
      if (idx < 0) return
      this.tabs.splice(idx, 1)
      if (this.activeTabId === id) {
        const next = this.tabs[idx] ?? this.tabs[idx - 1] ?? null
        this.activeTabId = next ? next.id : ''
      }
    },

    closeTabsByConnection(connectionId: number) {
      const ids = this.tabs.filter((t) => t.connectionId === connectionId).map((t) => t.id)
      ids.forEach((id) => this.closeTab(id))
    },

    /** 把 SQL 载入当前查询 Tab；当前不是查询 Tab 时新建一个 */
    loadSqlIntoEditor(sql: string, connectionId?: number, database?: string) {
      const active = this.activeTab
      if (active && active.type === 'query') {
        active.sql = sql
        if (connectionId) active.connectionId = connectionId
        if (database) active.database = database
        return
      }
      const connId = connectionId ?? this.lastConnectionId
      if (connId) {
        this.openQueryTab(connId, database ?? this.lastDatabase, sql)
      }
    },
  },
})
