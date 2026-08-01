import { client } from './client'
import type {
  ExportLog,
  PagedResult,
  QueryErrorKind,
  QueryExecuteResponse,
  QueryFolder,
  QueryHistoryItem,
  QueryHistoryStatus,
  SavedQuery,
} from '@/types'

// ---------- 查询执行 ----------

export async function executeQuery(
  connId: number,
  payload: { database: string; sql: string; executionId?: string },
): Promise<QueryExecuteResponse> {
  const { data } = await client.post(`/connections/${connId}/query`, payload)
  return {
    executionId: data?.executionId as string | undefined,
    results: (data?.results ?? []).map((r: Partial<QueryExecuteResponse['results'][number]>) => ({
      type: r?.type === 'updateCount' ? 'updateCount' : r?.type === 'error' ? 'error' : 'resultSet',
      columns: r?.columns ?? [],
      rows: r?.rows ?? [],
      rowCount: r?.rowCount ?? (r?.rows?.length ?? 0),
      durationMs: r?.durationMs ?? 0,
      truncated: r?.truncated ?? false,
      errorKind: r?.errorKind as QueryErrorKind | undefined,
      message: r?.message as string | undefined,
    })),
    totalDurationMs: data?.totalDurationMs ?? 0,
  }
}

/** 取消正在执行的查询（不存在或已结束时后端返回 404，调用方静默处理） */
export async function cancelQueryExecution(executionId: string): Promise<{ success: boolean; message?: string }> {
  const { data } = await client.post(`/query-executions/${encodeURIComponent(executionId)}/cancel`, null, {
    silent: true,
  })
  return { success: !!data?.success, message: data?.message as string | undefined }
}

// ---------- 执行历史 ----------

function normalizePage<T>(
  data: unknown,
  page: number,
  size: number,
  mapItem: (raw: Record<string, unknown>) => T,
): PagedResult<T> {
  // 兼容：数组 / Spring Page {content,totalElements} / {items|records|list,total}
  let items: unknown[] = []
  let total = 0
  if (Array.isArray(data)) {
    items = data
    total = data.length
  } else if (data && typeof data === 'object') {
    const d = data as Record<string, unknown>
    const list = (d.content ?? d.items ?? d.records ?? d.list ?? []) as unknown[]
    items = Array.isArray(list) ? list : []
    total = Number(d.totalElements ?? d.total ?? items.length) || 0
  }
  const mapped = items.map((it) => mapItem((it ?? {}) as Record<string, unknown>))
  return {
    items: mapped,
    total,
    page,
    size,
    hasMore: (page + 1) * size < total,
  }
}

function mapHistory(raw: Record<string, unknown>): QueryHistoryItem {
  return {
    id: Number(raw.id),
    connectionId: raw.connectionId as number | undefined,
    connectionName: raw.connectionName as string | undefined,
    databaseName: (raw.databaseName ?? raw.database ?? raw.dbName) as string | undefined,
    sqlText: String(raw.sqlText ?? raw.sql ?? ''),
    status: raw.status as QueryHistoryStatus | undefined,
    durationMs: raw.durationMs as number | undefined,
    executedAt: (raw.executedAt ?? raw.createdAt) as string | undefined,
    createdAt: raw.createdAt as string | undefined,
  }
}

export async function getQueryHistory(params: {
  connectionId?: number
  page: number
  size: number
}): Promise<PagedResult<QueryHistoryItem>> {
  const { data } = await client.get('/query-history', {
    params: {
      connectionId: params.connectionId || undefined,
      page: params.page,
      size: params.size,
    },
  })
  return normalizePage(data, params.page, params.size, mapHistory)
}

export async function deleteHistoryItem(id: number): Promise<void> {
  await client.delete(`/query-history/${id}`)
}

export async function clearHistory(connectionId?: number): Promise<void> {
  await client.delete('/query-history', {
    params: connectionId ? { connectionId } : {},
  })
}

// ---------- 收藏文件夹 ----------

export async function listQueryFolders(): Promise<QueryFolder[]> {
  const { data } = await client.get('/query-folders')
  return Array.isArray(data) ? data : (data?.items ?? data?.content ?? [])
}

export async function createQueryFolder(name: string): Promise<QueryFolder> {
  const { data } = await client.post('/query-folders', { name })
  return data
}

export async function renameQueryFolder(id: number, name: string): Promise<QueryFolder> {
  const { data } = await client.put(`/query-folders/${id}`, { name })
  return data
}

export async function deleteQueryFolder(id: number): Promise<void> {
  await client.delete(`/query-folders/${id}`)
}

// ---------- 收藏查询 ----------

export async function listSavedQueries(folderId?: number): Promise<SavedQuery[]> {
  const { data } = await client.get('/saved-queries', {
    params: folderId ? { folderId } : {},
  })
  const arr = Array.isArray(data) ? data : (data?.items ?? data?.content ?? [])
  return arr.map((q: Record<string, unknown>) => ({
    id: Number(q.id),
    folderId: (q.folderId ?? null) as number | null,
    name: String(q.name ?? ''),
    sqlText: String(q.sqlText ?? q.sql ?? ''),
    createdAt: q.createdAt as string | undefined,
    updatedAt: q.updatedAt as string | undefined,
  }))
}

export async function createSavedQuery(payload: {
  name: string
  sqlText: string
  folderId?: number | null
}): Promise<SavedQuery> {
  const { data } = await client.post('/saved-queries', payload)
  return data
}

export async function updateSavedQuery(
  id: number,
  payload: { name?: string; sqlText?: string; folderId?: number | null },
): Promise<SavedQuery> {
  const { data } = await client.put(`/saved-queries/${id}`, payload)
  return data
}

export async function deleteSavedQuery(id: number): Promise<void> {
  await client.delete(`/saved-queries/${id}`)
}

// ---------- 导出记录 ----------

export async function getExportLogs(): Promise<ExportLog[]> {
  const { data } = await client.get('/export-logs')
  const arr = Array.isArray(data) ? data : (data?.items ?? data?.content ?? [])
  return arr.map((l: Record<string, unknown>) => ({
    id: Number(l.id),
    connectionId: l.connectionId as number | undefined,
    connectionName: l.connectionName as string | undefined,
    databaseName: (l.databaseName ?? l.database) as string | undefined,
    tableName: (l.tableName ?? l.table) as string | undefined,
    format: l.format as string | undefined,
    fileName: (l.fileName ?? l.filename) as string | undefined,
    createdAt: (l.createdAt ?? l.exportedAt) as string | undefined,
  }))
}
