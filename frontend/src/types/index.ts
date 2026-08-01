/** 连接配置 */
export interface ConnectionConfig {
  id?: number
  name: string
  groupId?: number | null
  host: string
  port: number
  username: string
  password?: string
  defaultDatabase?: string | null
  /** 查询超时（秒），默认 60，范围 1-3600 */
  queryTimeoutSeconds?: number
}

/** 连接分组 */
export interface ConnectionGroup {
  id: number
  name: string
}

/** 表 / 视图信息 */
export interface TableInfo {
  name: string
  type: string // 'TABLE' | 'VIEW'
}

/** 列元信息（数据浏览 / 查询结果） */
export interface ColumnMeta {
  name: string
  type?: string
}

/** 表结构列信息 */
export interface ColumnInfo {
  name: string
  type: string
  nullable: boolean
  defaultValue: string | null
  comment: string
  key: string
  extra: string
}

/** 索引信息 */
export interface IndexInfo {
  name: string
  type: string
  columns: string[] | string
  unique: boolean
}

/** 表结构 */
export interface TableStructure {
  ddl: string
  columns: ColumnInfo[]
  indexes: IndexInfo[]
  estimatedRows: number
}

/** 筛选操作符 */
export type FilterOperator =
  | '='
  | '!='
  | '>'
  | '>='
  | '<'
  | '<='
  | 'LIKE'
  | 'IS NULL'
  | 'IS NOT NULL'

/** 列筛选条件 */
export interface FilterCondition {
  column: string
  operator: FilterOperator
  value?: string
}

/** 表数据分页响应 */
export interface TableDataResponse {
  columns: ColumnMeta[]
  primaryKeys: string[]
  rows: unknown[][]
  total: number
  page: number
  size: number
}

/** 行数据请求体 */
export interface RowPayload {
  keys?: Record<string, unknown>
  values: Record<string, unknown>
}

/** 查询错误类别 */
export type QueryErrorKind = 'timeout' | 'cancelled' | 'error'

/** 查询结果项 */
export interface QueryResultItem {
  type: 'resultSet' | 'updateCount' | 'error'
  columns: ColumnMeta[]
  rows: unknown[][]
  rowCount: number
  durationMs: number
  /** resultSet：结果是否被截断（仅返回前 N 行） */
  truncated?: boolean
  /** error：错误类别 */
  errorKind?: QueryErrorKind
  /** error：错误信息 */
  message?: string
}

/** 查询执行响应 */
export interface QueryExecuteResponse {
  executionId?: string
  results: QueryResultItem[]
  totalDurationMs: number
}

/** 执行历史状态 */
export type QueryHistoryStatus = 'SUCCESS' | 'ERROR' | 'TIMEOUT' | 'CANCELLED'

/** 执行历史 */
export interface QueryHistoryItem {
  id: number
  connectionId?: number
  connectionName?: string
  databaseName?: string
  sqlText: string
  status?: QueryHistoryStatus | (string & {})
  durationMs?: number
  executedAt?: string
  createdAt?: string
}

/** 收藏查询文件夹 */
export interface QueryFolder {
  id: number
  name: string
}

/** 收藏的查询 */
export interface SavedQuery {
  id: number
  folderId?: number | null
  name: string
  sqlText: string
  createdAt?: string
  updatedAt?: string
}

/** 导出记录 */
export interface ExportLog {
  id: number
  connectionId?: number
  connectionName?: string
  databaseName?: string
  tableName?: string
  format?: string
  fileName?: string
  createdAt?: string
}

/** 后端错误响应 */
export interface ApiErrorBody {
  timestamp?: string
  status?: number
  error?: string
  message?: string
  path?: string
}

/** 通用分页结果（前端归一化后） */
export interface PagedResult<T> {
  items: T[]
  total: number
  page: number
  size: number
  hasMore: boolean
}

/** 导出格式 */
export type ExportFormat = 'csv' | 'json' | 'sql'

/** 导出任务状态 */
export type ExportTaskStatus = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED'

/** 异步导出任务 */
export interface ExportTask {
  taskId: string
  status: ExportTaskStatus
  rowsExported: number
  estimatedRows?: number
  format: ExportFormat
  tableName: string
  errorMessage?: string | null
  createdAt?: string
  finishedAt?: string | null
}
