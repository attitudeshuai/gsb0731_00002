import { defineStore } from 'pinia'
import {
  createConnection,
  createGroup,
  deleteConnection,
  deleteGroup,
  listConnections,
  listGroups,
  renameGroup,
  updateConnection,
} from '@/api/connections'
import { listDatabases, listTables, getTableStructure } from '@/api/metadata'
import type { ConnectionConfig, ConnectionGroup, TableInfo } from '@/types'

function dbKey(connId: number, db: string) {
  return `${connId}/${db}`
}

export const useConnectionStore = defineStore('connections', {
  state: () => ({
    groups: [] as ConnectionGroup[],
    connections: [] as ConnectionConfig[],
    loading: false,
    /** 树节点展开状态，key: g:{id} / c:{id} / c:{id}/d:{db} */
    expanded: new Set<string>(),
    /** connId -> 数据库列表 */
    databases: {} as Record<number, string[]>,
    databasesLoading: {} as Record<number, boolean>,
    /** `${connId}/${db}` -> 表列表 */
    tables: {} as Record<string, TableInfo[]>,
    tablesLoading: {} as Record<string, boolean>,
    /** 列名缓存（用于 SQL 补全），key: `${connId}/${db}/${table}` */
    columnCache: {} as Record<string, string[]>,
  }),

  getters: {
    connectionById: (s) => (id: number | null | undefined) =>
      s.connections.find((c) => c.id === id) ?? null,
    connectionsOfGroup: (s) => (groupId: number) =>
      s.connections.filter((c) => c.groupId === groupId),
    ungroupedConnections: (s) =>
      s.connections.filter((c) => c.groupId == null || !s.groups.some((g) => g.id === c.groupId)),
  },

  actions: {
    async fetchAll() {
      this.loading = true
      try {
        const [groups, connections] = await Promise.all([listGroups(), listConnections()])
        this.groups = groups
        this.connections = connections
      } finally {
        this.loading = false
      }
    },

    async saveConnection(payload: ConnectionConfig): Promise<ConnectionConfig> {
      const saved = payload.id
        ? await updateConnection(payload.id, payload)
        : await createConnection(payload)
      await this.fetchAll()
      return saved
    },

    async removeConnection(id: number) {
      await deleteConnection(id)
      delete this.databases[id]
      delete this.databasesLoading[id]
      for (const key of Object.keys(this.tables)) {
        if (key.startsWith(`${id}/`)) delete this.tables[key]
      }
      for (const key of Object.keys(this.columnCache)) {
        if (key.startsWith(`${id}/`)) delete this.columnCache[key]
      }
      const next = new Set(this.expanded)
      for (const key of [...next]) {
        if (key.startsWith(`c:${id}`)) next.delete(key)
      }
      this.expanded = next
      await this.fetchAll()
    },

    async addGroup(name: string) {
      await createGroup(name)
      await this.fetchAll()
    },

    async renameGroupById(id: number, name: string) {
      await renameGroup(id, name)
      await this.fetchAll()
    },

    async removeGroup(id: number) {
      await deleteGroup(id)
      await this.fetchAll()
    },

    toggleExpanded(key: string) {
      const next = new Set(this.expanded)
      if (next.has(key)) next.delete(key)
      else next.add(key)
      this.expanded = next
    },

    isExpanded(key: string) {
      return this.expanded.has(key)
    },

    async loadDatabases(connId: number, force = false) {
      if (!force && this.databases[connId]) return
      this.databasesLoading[connId] = true
      try {
        this.databases[connId] = await listDatabases(connId)
      } finally {
        this.databasesLoading[connId] = false
      }
    },

    async loadTables(connId: number, db: string, keyword?: string, force = false) {
      const key = dbKey(connId, db)
      if (!force && !keyword && this.tables[key]) return
      this.tablesLoading[key] = true
      try {
        this.tables[key] = await listTables(connId, db, keyword)
      } finally {
        this.tablesLoading[key] = false
      }
    },

    /** 懒加载列名缓存（供 SQL 编辑器补全使用），失败静默 */
    async ensureColumns(connId: number, db: string, table: string) {
      const key = `${connId}/${db}/${table}`
      if (this.columnCache[key]) return
      try {
        const structure = await getTableStructure(connId, db, table)
        this.columnCache[key] = structure.columns.map((c) => c.name)
      } catch {
        /* 补全数据加载失败可忽略 */
      }
    },

    /** 指定库下已缓存的全部列名 */
    columnsOfDatabase(connId: number, db: string): string[] {
      const prefix = `${connId}/${db}/`
      const set = new Set<string>()
      for (const [key, cols] of Object.entries(this.columnCache)) {
        if (key.startsWith(prefix)) cols.forEach((c) => set.add(c))
      }
      return [...set]
    },
  },
})
