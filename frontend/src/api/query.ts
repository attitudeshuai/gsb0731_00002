import request from './index'
import type { QueryResult, QueryHistory, SavedQuery, QueryFolder, PageResult } from '@/types'

export interface ExecuteQueryParams {
  sql: string
  database?: string
  maxRows?: number
  timeoutSeconds?: number
}

export interface HistoryQueryParams {
  page?: number
  size?: number
  connectionId?: number
  keyword?: string
}

export interface ExportParams {
  sql: string
  database?: string
  format: string
  tableName?: string
}

export function executeQuery(
  connectionId: number,
  data: ExecuteQueryParams,
  executionId: string,
  signal?: AbortSignal
) {
  return request.post<any, QueryResult>(`/query/execute`, data, {
    params: { connectionId, executionId },
    signal
  })
}

export function cancelQuery(executionId: string) {
  return request.post<any, void>(`/query/cancel/${executionId}`)
}

export function getHistory(params?: HistoryQueryParams) {
  return request.get<any, PageResult<QueryHistory>>('/query/history', { params })
}

export function clearHistory() {
  return request.delete<any, void>('/query/history')
}

export function getSavedQueries(params?: { folderId?: number; keyword?: string }) {
  return request.get<any, SavedQuery[]>('/query/saved', { params })
}

export function getSavedQuery(id: number) {
  return request.get<any, SavedQuery>(`/query/saved/${id}`)
}

export function createSavedQuery(data: Partial<SavedQuery>) {
  return request.post<any, SavedQuery>('/query/saved', data)
}

export function updateSavedQuery(id: number, data: Partial<SavedQuery>) {
  return request.put<any, SavedQuery>(`/query/saved/${id}`, data)
}

export function deleteSavedQuery(id: number) {
  return request.delete<any, void>(`/query/saved/${id}`)
}

export function getFolders() {
  return request.get<any, QueryFolder[]>('/query/folders')
}

export function createFolder(data: Partial<QueryFolder>) {
  return request.post<any, QueryFolder>('/query/folders', data)
}

export function updateFolder(id: number, data: Partial<QueryFolder>) {
  return request.put<any, QueryFolder>(`/query/folders/${id}`, data)
}

export function deleteFolder(id: number) {
  return request.delete<any, void>(`/query/folders/${id}`)
}

export interface ExportResultInfo {
  rowCount: number
  fileSize: number
  status: string
  fileName: string
  errorMessage?: string
}

export interface ExportHandle {
  executionId: string
  done: Promise<ExportResultInfo>
  abort: () => void
}

export function getExportResult(executionId: string) {
  return request.get<any, ExportResultInfo>(`/export/result/${executionId}`)
}

export function exportData(
  connectionId: number,
  params: ExportParams,
  onProgress?: (loaded: number, total: number) => void,
  executionId?: string
): ExportHandle {
  const execId =
    executionId ||
    (crypto as any).randomUUID?.() ||
    `export-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`

  const xhr = new XMLHttpRequest()
  let resolveFn: (r: ExportResultInfo) => void = () => {}
  let rejectFn: (e: any) => void = () => {}
  const done = new Promise<ExportResultInfo>((resolve, reject) => {
    resolveFn = resolve
    rejectFn = reject
  })

  const url = `/api/export?connectionId=${encodeURIComponent(connectionId)}&executionId=${encodeURIComponent(execId)}`
  xhr.open('POST', url)
  xhr.responseType = 'blob'
  xhr.setRequestHeader('Content-Type', 'application/json')

  xhr.onprogress = (e) => {
    if (onProgress) {
      onProgress(e.loaded, e.lengthComputable ? e.total : 0)
    }
  }

  xhr.onload = async () => {
    if (xhr.status >= 200 && xhr.status < 300) {
      const result = await fetchResultWithRetry(execId)
      if (result && result.status === 'CANCELLED') {
        rejectFn(new Error('EXPORT_ABORTED'))
        return
      }
      if (result && result.status === 'FAILED') {
        rejectFn(new Error(result.errorMessage || '导出失败'))
        return
      }
      const blob = xhr.response as Blob
      const disposition = xhr.getResponseHeader('Content-Disposition') || ''
      const filename = resolveFilename(disposition, params.format)
      triggerDownload(blob, filename)
      resolveFn(
        result || {
          rowCount: 0,
          fileSize: blob.size,
          status: 'SUCCESS',
          fileName: filename
        }
      )
    } else {
      const msg = await readBlobError(xhr.response as Blob)
      rejectFn(new Error(msg || `导出失败 (${xhr.status})`))
    }
  }

  xhr.onerror = async () => {
    const result = await fetchResultWithRetry(execId)
    if (result) {
      if (result.status === 'CANCELLED') {
        rejectFn(new Error('EXPORT_ABORTED'))
      } else if (result.status === 'FAILED') {
        rejectFn(new Error(result.errorMessage || `导出失败（已写入 ${formatBytes(result.fileSize)}）`))
      } else {
        rejectFn(new Error('导出网络错误'))
      }
    } else {
      rejectFn(new Error('导出网络错误'))
    }
  }

  xhr.onabort = () => rejectFn(new Error('EXPORT_ABORTED'))

  xhr.send(JSON.stringify(params))

  return {
    executionId: execId,
    done,
    abort: () => xhr.abort()
  }
}

async function fetchResultWithRetry(executionId: string, attempts = 5): Promise<ExportResultInfo | null> {
  for (let i = 0; i < attempts; i++) {
    try {
      await new Promise((r) => setTimeout(r, 200))
      const result = await getExportResult(executionId)
      if (result) return result
    } catch {
      // retry
    }
  }
  return null
}

function formatBytes(bytes: number): string {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(1024))
  return `${(bytes / Math.pow(1024, i)).toFixed(i === 0 ? 0 : 1)} ${units[i]}`
}

function resolveFilename(disposition: string, format: string): string {
  const match = disposition.match(/filename\*?=(?:UTF-8'')?["']?([^"';\n]+)/i)
  if (match && match[1]) {
    try {
      return decodeURIComponent(match[1])
    } catch {
      return match[1]
    }
  }
  const ext = format.toLowerCase() === 'json' ? 'json' : format.toLowerCase() === 'csv' ? 'csv' : 'sql'
  return `export-${Date.now()}.${ext}`
}

function triggerDownload(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

function readBlobError(blob: Blob): Promise<string> {
  return new Promise((resolve) => {
    if (!blob) {
      resolve('')
      return
    }
    const reader = new FileReader()
    reader.onload = () => {
      const text = String(reader.result || '')
      try {
        const obj = JSON.parse(text)
        resolve(obj.message || obj.error || text)
      } catch {
        resolve(text)
      }
    }
    reader.onerror = () => resolve('')
    reader.readAsText(blob)
  })
}