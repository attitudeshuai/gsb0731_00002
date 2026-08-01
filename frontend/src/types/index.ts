export interface ConnectionGroup {
  id: number
  name: string
  sortOrder: number
  createdAt: string
}

export interface Connection {
  id: number
  name: string
  groupId: number | null
  host: string
  port: number
  username: string
  databaseName: string | null
  dbType: string
  hasPassword: boolean
  createdAt: string
  updatedAt: string
}

export interface ConnectionRequest {
  name: string
  groupId: number | null
  host: string
  port: number
  username: string
  password?: string
  databaseName?: string | null
  dbType?: string
}

export interface TableInfo {
  name: string
  type: string
  estimatedRows: number | null
  comment: string | null
}

export interface ColumnInfo {
  name: string
  dataType: string
  nullable: boolean
  defaultValue: string | null
  comment: string | null
  primaryKey: boolean
  columnKey: string
  extra: string
  ordinalPosition: number
}

export interface IndexInfo {
  name: string
  unique: boolean
  type: string
  columns: string[]
}

export interface TableStructure {
  table: string
  columns: ColumnInfo[]
  indexes: IndexInfo[]
  estimatedRows: number | null
  ddl: string
}

export interface ColumnMeta {
  name: string
  typeName: string
  jdbcType: number
  nullable: boolean
}

export interface QueryResult {
  columns: ColumnMeta[]
  rows: any[][]
  rowCount: number
  durationMs: number
  affectedRows: number | null
  truncated: boolean
}

export interface Filter {
  column: string
  operator: string
  value: any
}

export interface PageResponse {
  columns: ColumnMeta[]
  rows: any[][]
  total: number
  page: number
  pageSize: number
  primaryKeys: string[]
}

export interface RowChange {
  type: 'insert' | 'update' | 'delete'
  values?: Record<string, any>
  keys?: Record<string, any>
}

export interface PreviewStatement {
  type: 'insert' | 'update' | 'delete'
  sql: string
  params: any[]
  rendered: string
}

export interface SaveResult {
  inserted: number
  updated: number
  deleted: number
  statements: string[]
  preview: PreviewStatement[]
  executed: boolean
}

export interface QueryHistory {
  id: number
  connectionId: number | null
  sqlText: string
  success: boolean
  errorMessage: string | null
  rowsAffected: number | null
  durationMs: number | null
  executedAt: string
}

export interface QueryFolder {
  id: number
  name: string
  sortOrder: number
  createdAt: string
}

export interface SavedQuery {
  id: number
  name: string
  folderId: number | null
  connectionId: number | null
  sqlText: string
  description: string | null
  createdAt: string
  updatedAt: string
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}
