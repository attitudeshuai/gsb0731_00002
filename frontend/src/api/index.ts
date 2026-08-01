import axios, { type AxiosResponse } from 'axios'
import { streamDownload, type DownloadProgress } from '@/utils/download'
import type {
  ApiResponse, ConnectionConfig, ConnectionGroup, ConnectionRequest,
  TableMeta, ColumnMeta, IndexInfo, TableDataResponse, QueryResult,
  SavedQuery, QueryFolder, QueryHistoryItem, TestConnectionRequest
} from '@/types'

const http = axios.create({
  baseURL: '/api',
  timeout: 120000,
  headers: { 'Content-Type': 'application/json' }
})

http.interceptors.response.use(
  (response) => response,
  (error) => {
    const message = error.response?.data?.message || error.message || 'Request failed'
    return Promise.reject(new Error(message))
  }
)

function unwrap<T>(p: Promise<AxiosResponse<ApiResponse<T>>>): Promise<T> {
  return p.then(res => res.data.data)
}

export const connectionApi = {
  list: (): Promise<ConnectionConfig[]> =>
    unwrap(http.get('/connections')),
  get: (id: number): Promise<ConnectionConfig> =>
    unwrap(http.get(`/connections/${id}`)),
  create: (data: ConnectionRequest): Promise<ConnectionConfig> =>
    unwrap(http.post('/connections', data)),
  update: (id: number, data: ConnectionRequest): Promise<ConnectionConfig> =>
    unwrap(http.put(`/connections/${id}`, data)),
  delete: (id: number): Promise<void> =>
    unwrap(http.delete(`/connections/${id}`)),
  test: (data: TestConnectionRequest): Promise<{ success: boolean }> =>
    unwrap(http.post('/connections/test', data)),
  testExisting: (id: number): Promise<{ success: boolean }> =>
    unwrap(http.post(`/connections/${id}/test`)),
  listGroups: (): Promise<ConnectionGroup[]> =>
    unwrap(http.get('/connections/groups')),
  createGroup: (data: { name: string }): Promise<ConnectionGroup> =>
    unwrap(http.post('/connections/groups', data)),
  updateGroup: (id: number, data: { name: string }): Promise<ConnectionGroup> =>
    unwrap(http.put(`/connections/groups/${id}`, data)),
  deleteGroup: (id: number): Promise<void> =>
    unwrap(http.delete(`/connections/groups/${id}`))
}

export const metadataApi = {
  listDatabases: (connectionId: number): Promise<string[]> =>
    unwrap(http.get(`/metadata/${connectionId}/databases`)),
  listTables: (connectionId: number, database: string, keyword?: string): Promise<TableMeta[]> =>
    unwrap(http.get(`/metadata/${connectionId}/databases/${database}/tables`, { params: { keyword } })),
  describeTable: (connectionId: number, database: string, table: string): Promise<ColumnMeta[]> =>
    unwrap(http.get(`/metadata/${connectionId}/databases/${database}/tables/${table}/columns`)),
  listIndexes: (connectionId: number, database: string, table: string): Promise<IndexInfo[]> =>
    unwrap(http.get(`/metadata/${connectionId}/databases/${database}/tables/${table}/indexes`)),
  showCreateTable: (connectionId: number, database: string, table: string): Promise<{ ddl: string }> =>
    unwrap(http.get(`/metadata/${connectionId}/databases/${database}/tables/${table}/ddl`))
}

export const queryApi = {
  execute: (data: Record<string, any>, requestId?: string, signal?: AbortSignal): Promise<QueryResult> =>
    unwrap(http.post('/query/execute', data, {
      headers: requestId ? { 'X-Request-Id': requestId } : {},
      signal
    })),
  executeSelected: (connectionId: number, databaseName: string | undefined, sql: string, requestId?: string, signal?: AbortSignal): Promise<QueryResult> =>
    unwrap(http.post('/query/execute-selected', { connectionId, databaseName, sql }, {
      headers: requestId ? { 'X-Request-Id': requestId } : {},
      signal
    })),
  cancel: (requestId: string): Promise<{ cancelled: boolean }> =>
    unwrap(http.post('/query/cancel', { requestId })),
  getTableData: (connectionId: number, database: string, table: string, data: Record<string, any>): Promise<TableDataResponse> =>
    unwrap(http.post(`/query/${connectionId}/databases/${database}/tables/${table}/data`, data)),
  updateRow: (connectionId: number, database: string, table: string, data: Record<string, any>): Promise<{ affectedRows: number }> =>
    unwrap(http.put(`/query/${connectionId}/databases/${database}/tables/${table}/rows`, data)),
  insertRow: (connectionId: number, database: string, table: string, data: Record<string, any>): Promise<{ affectedRows: number }> =>
    unwrap(http.post(`/query/${connectionId}/databases/${database}/tables/${table}/rows`, data)),
  deleteRow: (connectionId: number, database: string, table: string, data: Record<string, any>): Promise<{ affectedRows: number }> =>
    unwrap(http.delete(`/query/${connectionId}/databases/${database}/tables/${table}/rows`, { data }))
}

export const savedQueryApi = {
  list: (folderId?: number): Promise<SavedQuery[]> =>
    unwrap(http.get('/saved-queries', { params: { folderId } })),
  get: (id: number): Promise<SavedQuery> =>
    unwrap(http.get(`/saved-queries/${id}`)),
  create: (data: Record<string, any>): Promise<SavedQuery> =>
    unwrap(http.post('/saved-queries', data)),
  update: (id: number, data: Record<string, any>): Promise<SavedQuery> =>
    unwrap(http.put(`/saved-queries/${id}`, data)),
  delete: (id: number): Promise<void> =>
    unwrap(http.delete(`/saved-queries/${id}`)),
  listFolders: (): Promise<QueryFolder[]> =>
    unwrap(http.get('/saved-queries/folders')),
  createFolder: (data: Record<string, any>): Promise<QueryFolder> =>
    unwrap(http.post('/saved-queries/folders', data)),
  updateFolder: (id: number, data: Record<string, any>): Promise<QueryFolder> =>
    unwrap(http.put(`/saved-queries/folders/${id}`, data)),
  deleteFolder: (id: number): Promise<void> =>
    unwrap(http.delete(`/saved-queries/folders/${id}`))
}

export const historyApi = {
  list: (params: Record<string, any>): Promise<{ content: QueryHistoryItem[]; totalElements: number }> =>
    unwrap(http.get('/query-history', { params })),
  delete: (id: number): Promise<void> =>
    unwrap(http.delete(`/query-history/${id}`)),
  clear: (): Promise<void> =>
    unwrap(http.delete('/query-history'))
}

export const exportApi = {
  exportTableUrl: (connectionId: number, database: string, table: string, format: string) =>
    `/api/export/${connectionId}/databases/${database}/tables/${table}?format=${format}`,
  streamExportTable: (
    connectionId: number,
    database: string,
    table: string,
    format: string,
    onProgress?: (p: DownloadProgress) => void,
    signal?: AbortSignal,
    onRequestId?: (requestId: string) => void
  ): Promise<{ requestId: string }> => {
    const url = `/api/export/${connectionId}/databases/${database}/tables/${table}?format=${format}`
    return streamDownload(url, { method: 'GET' }, `${table}.${format}`, onProgress, signal, onRequestId)
  },
  streamExportQuery: (
    connectionId: number,
    databaseName: string,
    sql: string,
    format: string,
    fileName: string,
    onProgress?: (p: DownloadProgress) => void,
    signal?: AbortSignal,
    onRequestId?: (requestId: string) => void
  ): Promise<{ requestId: string }> => {
    return streamDownload(
      '/api/export/query',
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ connectionId, databaseName, sql, format, fileName })
      },
      `${fileName}.${format}`,
      onProgress,
      signal,
      onRequestId
    )
  }
}
