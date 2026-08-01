export interface DownloadProgress {
  receivedBytes: number
  totalBytes: number | null
  percentage: number
  done: boolean
}

export class DownloadAbortedError extends Error {
  constructor() {
    super('下载已取消')
    this.name = 'DownloadAbortedError'
  }
}

export class DownloadIncompleteError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'DownloadIncompleteError'
  }
}

function generateRequestId(): string {
  return 'dl-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 10)
}

export async function streamDownload(
  url: string,
  options: RequestInit,
  fileName: string,
  onProgress?: (p: DownloadProgress) => void,
  signal?: AbortSignal,
  onRequestId?: (requestId: string) => void
): Promise<{ requestId: string }> {
  const requestId = generateRequestId()
  onRequestId?.(requestId)
  const headers = new Headers(options.headers || {})
  headers.set('X-Request-Id', requestId)

  if (signal?.aborted) {
    throw new DownloadAbortedError()
  }

  let response: Response
  try {
    response = await fetch(url, { ...options, headers, signal })
  } catch (e: any) {
    if (e?.name === 'AbortError') throw new DownloadAbortedError()
    throw e
  }

  if (!response.ok) {
    let msg = `下载失败 (HTTP ${response.status})`
    try {
      const ct = response.headers.get('content-type') || ''
      if (ct.includes('application/json')) {
        const errData = await response.json()
        if (errData?.message) msg = errData.message
      } else {
        const t = await response.text()
        if (t) msg = t
      }
    } catch {
      // ignore body parse error
    }
    throw new Error(msg)
  }

  const contentLength = response.headers.get('Content-Length')
  const totalBytes = contentLength ? parseInt(contentLength, 10) : null

  if (!response.body) {
    throw new Error('当前浏览器不支持流式读取响应')
  }

  const reader = response.body.getReader()
  const chunks: Uint8Array[] = []
  let receivedBytes = 0

  try {
    while (true) {
      if (signal?.aborted) {
        await reader.cancel('aborted by user')
        throw new DownloadAbortedError()
      }
      const { done, value } = await reader.read()
      if (done) break
      if (value) {
        chunks.push(value)
        receivedBytes += value.length
        onProgress?.({
          receivedBytes,
          totalBytes,
          percentage: totalBytes ? Math.round((receivedBytes / totalBytes) * 100) : -1,
          done: false
        })
      }
    }
  } catch (e: any) {
    if (e instanceof DownloadAbortedError) throw e
    if (e?.name === 'AbortError') throw new DownloadAbortedError()
    throw new DownloadIncompleteError(
      '下载过程中连接中断，文件可能不完整：' + (e?.message || '未知错误')
    )
  }

  if (totalBytes !== null && receivedBytes !== totalBytes) {
    throw new DownloadIncompleteError(
      `下载不完整：已接收 ${receivedBytes} 字节，预期 ${totalBytes} 字节，文件已丢弃`
    )
  }

  const blob = new Blob(chunks as BlobPart[])
  const objectUrl = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = objectUrl
  a.download = fileName
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(objectUrl)

  onProgress?.({ receivedBytes, totalBytes, percentage: 100, done: true })
  return { requestId }
}

export function formatBytes(bytes: number): string {
  if (bytes === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(1024))
  return (bytes / Math.pow(1024, i)).toFixed(i === 0 ? 0 : 1) + ' ' + units[i]
}
