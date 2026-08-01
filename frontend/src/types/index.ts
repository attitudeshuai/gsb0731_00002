export interface ConnectionConfig {
  id: number
  name: string
  host: string
  port: number
  username: string
  hasPassword?: boolean
  databaseName?: string
  groupId?: number | null
  color?: string
  remark?: string
  createdAt?: string
  updatedAt?: string
}

export interface ConnectionRequest {
  name: string
  host: string
  port: number
  username: string
  password?: string
  databaseName?: string
  groupId?: number | null
  color?: string
  remark?: string
}

export interface TestConnectionRequest {
  host: string
  port: number
  username: string
  password?: string
  databaseName?: string
}

export interface ConnectionGroup {
  id: number
  name: string
  parentId?: number | null
  sortOrder?: number
}

export interface ColumnMeta {
  name: string
  type: string
  typeName: string
  precision?: number
  scale?: number
  nullable?: boolean
  primaryKey?: boolean
  defaultValue?: string
  comment?: string
  autoIncrement?: boolean
}

export interface PrimaryKeyColumn {
  columnName: string
  keySeq: number
}

export interface TableMeta {
  name: string
  type: string
  comment?: string
  estimatedRows?: number
}

export interface IndexInfo {
  indexName: string
  columnName: string
  nonUnique: boolean
  indexType?: string
  seqInIndex?: number
}

export interface TableDataResponse {
  columns: ColumnMeta[]
  rows: Record<string, any>[]
  total: number
  page: number
  pageSize: number
  primaryKeys: PrimaryKeyColumn[]
}

export interface QueryResult {
  columns: ColumnMeta[]
  rows: Record<string, any>[]
  affectedRows: number
  total?: number
  elapsedMs: number
  sql: string
  query: boolean
}

export interface FilterCondition {
  column: string
  operator: string
  value: any
}

export interface QueryHistoryItem {
  id: number
  connectionId?: number
  databaseName?: string
  sqlText: string
  status: string
  affectedRows: number
  elapsedMs: number
  errorMessage?: string
  executedAt: string
}

export interface SavedQuery {
  id: number
  title: string
  sqlText: string
  folderId?: number
  connectionId?: number
  databaseName?: string
  tags?: string
  createdAt?: string
  updatedAt?: string
}

export interface QueryFolder {
  id: number
  name: string
  parentId?: number | null
  sortOrder?: number
}

export interface ApiResponse<T> {
  success: boolean
  message?: string
  data: T
}
