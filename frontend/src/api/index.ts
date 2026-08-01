import axios from 'axios'
import type {
  Connection, ConnectionRequest, ConnectionGroup, TableInfo, TableStructure,
  ColumnInfo, IndexInfo, QueryResult, PageResponse, RowChange, Filter,
  QueryHistory, QueryFolder, SavedQuery, Page, SaveResult
} from '@/types'

const http = axios.create({
  baseURL: '/api',
  timeout: 60000
})

http.interceptors.response.use(
  (res) => res,
  (err) => {
    const msg = err?.response?.data?.message || err.message || 'Request failed'
    return Promise.reject(new Error(msg))
  }
)

export const connectionApi = {
  list: () => http.get<Connection[]>('/connections').then((r) => r.data),
  get: (id: number) => http.get<Connection>(`/connections/${id}`).then((r) => r.data),
  create: (body: ConnectionRequest) =>
    http.post<Connection>('/connections', body).then((r) => r.data),
  update: (id: number, body: ConnectionRequest) =>
    http.put<Connection>(`/connections/${id}`, body).then((r) => r.data),
  remove: (id: number) => http.delete(`/connections/${id}`).then((r) => r.data),
  testExisting: (id: number) =>
    http.post(`/connections/${id}/test`).then((r) => r.data),
  testAdHoc: (body: ConnectionRequest) =>
    http.post('/connections/test', body).then((r) => r.data)
}

export const groupApi = {
  list: () => http.get<ConnectionGroup[]>('/connection-groups').then((r) => r.data),
  create: (body: { name: string; sortOrder?: number }) =>
    http.post<ConnectionGroup>('/connection-groups', body).then((r) => r.data),
  update: (id: number, body: { name: string; sortOrder?: number }) =>
    http.put<ConnectionGroup>(`/connection-groups/${id}`, body).then((r) => r.data),
  remove: (id: number) => http.delete(`/connection-groups/${id}`).then((r) => r.data)
}

export const metadataApi = {
  databases: (connId: number) =>
    http.get<string[]>(`/connections/${connId}/databases`).then((r) => r.data),
  tables: (connId: number, db: string) =>
    http.get<TableInfo[]>(`/connections/${connId}/databases/${encodeURIComponent(db)}/tables`).then((r) => r.data),
  structure: (connId: number, db: string, table: string) =>
    http.get<TableStructure>(
      `/connections/${connId}/databases/${encodeURIComponent(db)}/tables/${encodeURIComponent(table)}/structure`
    ).then((r) => r.data),
  columns: (connId: number, db: string, table: string) =>
    http.get<ColumnInfo[]>(
      `/connections/${connId}/databases/${encodeURIComponent(db)}/tables/${encodeURIComponent(table)}/columns`
    ).then((r) => r.data),
  indexes: (connId: number, db: string, table: string) =>
    http.get<IndexInfo[]>(
      `/connections/${connId}/databases/${encodeURIComponent(db)}/tables/${encodeURIComponent(table)}/indexes`
    ).then((r) => r.data),
  ddl: (connId: number, db: string, table: string) =>
    http.get<{ ddl: string }>(
      `/connections/${connId}/databases/${encodeURIComponent(db)}/tables/${encodeURIComponent(table)}/ddl`
    ).then((r) => r.data.ddl)
}

export const queryApi = {
  execute: (connId: number, sql: string, queryToken?: string) =>
    http.post<QueryResult>(`/connections/${connId}/execute`, { sql, queryToken }).then((r) => r.data),
  cancel: (connId: number, queryToken: string) =>
    http.post<{ cancelled: boolean }>(`/connections/${connId}/queries/${encodeURIComponent(queryToken)}/cancel`)
      .then((r) => r.data),
  browse: (
    connId: number,
    body: {
      database: string
      table: string
      page: number
      pageSize: number
      sortColumn?: string
      sortDirection?: string
      filters?: Filter[]
    }
  ) => http.post<PageResponse>(`/connections/${connId}/table-data`, body).then((r) => r.data),
  preview: (connId: number, body: { database: string; table: string; changes: RowChange[] }) =>
    http.post<SaveResult>(`/connections/${connId}/table-data/preview`, body).then((r) => r.data),
  save: (connId: number, body: { database: string; table: string; changes: RowChange[] }) =>
    http.post<SaveResult>(`/connections/${connId}/table-data/save`, body).then((r) => r.data)
}

export const historyApi = {
  list: (params: { connectionId?: number; page?: number; size?: number }) =>
    http.get<Page<QueryHistory>>('/query-history', { params }).then((r) => r.data),
  remove: (id: number) => http.delete(`/query-history/${id}`).then((r) => r.data)
}

export const savedQueryApi = {
  list: (folderId?: number) =>
    http.get<SavedQuery[]>('/saved-queries', { params: { folderId } }).then((r) => r.data),
  create: (body: Partial<SavedQuery>) =>
    http.post<SavedQuery>('/saved-queries', body).then((r) => r.data),
  update: (id: number, body: Partial<SavedQuery>) =>
    http.put<SavedQuery>(`/saved-queries/${id}`, body).then((r) => r.data),
  remove: (id: number) => http.delete(`/saved-queries/${id}`).then((r) => r.data),
  folders: () => http.get<QueryFolder[]>('/saved-queries/folders').then((r) => r.data),
  createFolder: (body: { name: string; sortOrder?: number }) =>
    http.post<QueryFolder>('/saved-queries/folders', body).then((r) => r.data),
  removeFolder: (id: number) => http.delete(`/saved-queries/folders/${id}`).then((r) => r.data)
}

export interface ExportProgress {
  jobId: string
  status: 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED'
  rowsWritten: number
  totalRows: number | null
  percent: number | null
  filename: string
  error: string | null
}

export const exportApi = {
  startTable: (connId: number, body: { database: string; table: string; format: string }) =>
    http.post<ExportProgress>(`/connections/${connId}/export/table`, body).then((r) => r.data),
  startQuery: (connId: number, body: { sql: string; format: string }) =>
    http.post<ExportProgress>(`/connections/${connId}/export/query`, body).then((r) => r.data),
  status: (connId: number, jobId: string) =>
    http.get<ExportProgress>(`/connections/${connId}/export/jobs/${jobId}`).then((r) => r.data),
  cancel: (connId: number, jobId: string) =>
    http.post<{ cancelled: boolean }>(`/connections/${connId}/export/jobs/${jobId}/cancel`).then((r) => r.data),
  downloadUrl: (connId: number, jobId: string) =>
    `/api/connections/${connId}/export/jobs/${jobId}/download`
}

export default http
