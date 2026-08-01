export interface Connection {
  id: number
  name: string
  host: string
  port: number
  username: string
  password: string
  databaseName: string
  groupId: number | null
  type: string
  color: string
  remark: string
  createdAt: string
  updatedAt: string
}

export interface ConnectionGroup {
  id: number
  name: string
  parentId: number | null
  userId: number | null
  sortOrder: number
  color: string
}

export interface ColumnMeta {
  name: string
  type: string
  nullable: boolean
  defaultValue: string | null
  comment: string
  primaryKey: boolean
  autoIncrement: boolean
}

export interface TableMeta {
  name: string
  type: string
  schema: string
  comment: string
  rowCountEstimate: number
}

export interface QueryResult {
  columns: string[]
  rows: any[][]
  updateCount?: number
  executionTimeMs: number
  sql: string
  truncated?: boolean
  maxRows?: number | null
  cancelled?: boolean
  timeout?: boolean
}

export interface QueryHistory {
  id: number
  connectionId: number
  sqlText: string
  executionTimeMs: number
  rowCount: number
  success: boolean
  errorMessage: string
  executedAt: string
}

export interface SavedQuery {
  id: number
  name: string
  sqlText: string
  folderId: number | null
  connectionId: number | null
  description: string
  tags: string
  createdAt: string
  updatedAt: string
}

export interface QueryFolder {
  id: number
  name: string
  parentId: number | null
  sortOrder: number
  icon: string
  color: string
}

export interface ExportLog {
  id: number
  connectionId: number
  tableName: string
  exportFormat: string
  fileName: string
  fileSize: number
  rowCount: number
  status: string
  completedAt: string
}

export interface ApiResponse<T> {
  success: boolean
  message: string
  data: T
}

export interface PageResult<T> {
  content: T[]
  totalElements: number
  totalPages: number
  page: number
  size: number
}
