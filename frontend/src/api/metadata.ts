import { client, downloadBlob, filenameFromDisposition } from './client'
import type {
  ExportFormat,
  ExportTask,
  FilterCondition,
  TableDataResponse,
  TableInfo,
  TableStructure,
} from '@/types'

const enc = encodeURIComponent

function tableBase(connId: number, db: string, table: string) {
  return `/connections/${connId}/databases/${enc(db)}/tables/${enc(table)}`
}

// ---------- 数据库 / 表 ----------

export async function listDatabases(connId: number): Promise<string[]> {
  const { data } = await client.get(`/connections/${connId}/databases`)
  const arr = Array.isArray(data) ? data : (data?.items ?? [])
  return arr.map((d: unknown) => (typeof d === 'string' ? d : ((d as { name?: string })?.name ?? String(d))))
}

export async function listTables(connId: number, db: string, keyword?: string): Promise<TableInfo[]> {
  const { data } = await client.get(`/connections/${connId}/databases/${enc(db)}/tables`, {
    params: keyword ? { keyword } : {},
  })
  const arr = Array.isArray(data) ? data : (data?.items ?? [])
  return arr.map((t: unknown) =>
    typeof t === 'string' ? { name: t, type: 'TABLE' } : (t as TableInfo),
  )
}

// ---------- 表结构 ----------

export async function getTableStructure(connId: number, db: string, table: string): Promise<TableStructure> {
  const { data } = await client.get(`${tableBase(connId, db, table)}/structure`)
  return {
    ddl: data?.ddl ?? '',
    columns: data?.columns ?? [],
    indexes: data?.indexes ?? [],
    estimatedRows: data?.estimatedRows ?? 0,
  }
}

// ---------- 表数据 ----------

export interface TableDataParams {
  page: number
  size: number
  sort?: string
  order?: 'asc' | 'desc'
  filters?: FilterCondition[]
}

export async function getTableData(
  connId: number,
  db: string,
  table: string,
  params: TableDataParams,
): Promise<TableDataResponse> {
  const { data } = await client.get(`${tableBase(connId, db, table)}/data`, {
    params: {
      page: params.page,
      size: params.size,
      sort: params.sort || undefined,
      order: params.sort ? params.order : undefined,
      filters:
        params.filters && params.filters.length > 0
          ? JSON.stringify(params.filters)
          : undefined,
    },
  })
  return {
    columns: data?.columns ?? [],
    primaryKeys: data?.primaryKeys ?? [],
    rows: data?.rows ?? [],
    total: data?.total ?? 0,
    page: data?.page ?? params.page,
    size: data?.size ?? params.size,
  }
}

// ---------- 行编辑 ----------

export async function insertRow(
  connId: number,
  db: string,
  table: string,
  values: Record<string, unknown>,
): Promise<void> {
  await client.post(`${tableBase(connId, db, table)}/rows`, { values })
}

export async function updateRow(
  connId: number,
  db: string,
  table: string,
  keys: Record<string, unknown>,
  values: Record<string, unknown>,
): Promise<void> {
  await client.put(`${tableBase(connId, db, table)}/rows`, { keys, values })
}

export async function deleteRows(
  connId: number,
  db: string,
  table: string,
  keys: Record<string, unknown>[],
): Promise<void> {
  await client.delete(`${tableBase(connId, db, table)}/rows`, { data: { keys } })
}

// ---------- 异步导出任务 ----------

function mapExportTask(data: Record<string, unknown>): ExportTask {
  return {
    taskId: String(data?.taskId ?? ''),
    status: (data?.status ?? 'PENDING') as ExportTask['status'],
    rowsExported: Number(data?.rowsExported ?? 0),
    estimatedRows: data?.estimatedRows != null ? Number(data.estimatedRows) : undefined,
    format: (data?.format ?? 'csv') as ExportTask['format'],
    tableName: String(data?.tableName ?? ''),
    errorMessage: (data?.errorMessage ?? null) as string | null,
    createdAt: data?.createdAt as string | undefined,
    finishedAt: (data?.finishedAt ?? null) as string | null,
  }
}

/** 创建导出任务（202） */
export async function createExportTask(
  connId: number,
  db: string,
  table: string,
  format: ExportFormat,
  filters?: FilterCondition[],
): Promise<ExportTask> {
  const { data } = await client.post(`${tableBase(connId, db, table)}/export-tasks`, null, {
    params: {
      format,
      filters: filters && filters.length > 0 ? JSON.stringify(filters) : undefined,
    },
  })
  return mapExportTask(data ?? {})
}

export async function getExportTask(taskId: string): Promise<ExportTask> {
  // 轮询场景静默处理，避免拦截器反复弹 toast
  const { data } = await client.get(`/export-tasks/${encodeURIComponent(taskId)}`, { silent: true })
  return mapExportTask(data ?? {})
}

export async function cancelExportTask(taskId: string): Promise<{ success: boolean; message?: string }> {
  const { data } = await client.post(`/export-tasks/${encodeURIComponent(taskId)}/cancel`, null)
  return { success: !!data?.success, message: data?.message as string | undefined }
}

/** 导出任务结果下载地址（仅 COMPLETED 可用） */
export function exportTaskDownloadUrl(taskId: string): string {
  return `/api/export-tasks/${encodeURIComponent(taskId)}/download`
}

/** 下载已完成的导出任务文件（解析 Content-Disposition 文件名） */
export async function downloadExportTask(taskId: string, fallbackName: string): Promise<void> {
  const resp = await client.get(`/export-tasks/${encodeURIComponent(taskId)}/download`, {
    responseType: 'blob',
  })
  const filename = filenameFromDisposition(
    (resp.headers['content-disposition'] as string | undefined) ?? undefined,
    fallbackName,
  )
  downloadBlob(resp.data as Blob, filename)
}
