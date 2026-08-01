import axios, { AxiosError } from 'axios'
import { toast } from '@/ui/toast'
import type { ApiErrorBody } from '@/types'

export const client = axios.create({
  baseURL: '/api',
  timeout: 120000,
})

declare module 'axios' {
  interface AxiosRequestConfig {
    /** 为 true 时出错不弹 toast，由调用方自行处理 */
    silent?: boolean
  }
}

async function extractMessage(err: AxiosError): Promise<string> {
  const data = err.response?.data
  if (data instanceof Blob) {
    try {
      const text = await data.text()
      const body = JSON.parse(text) as ApiErrorBody
      if (body?.message) return body.message
    } catch {
      /* ignore */
    }
    return err.message || '请求失败'
  }
  const body = data as ApiErrorBody | undefined
  return body?.message || err.message || '请求失败'
}

client.interceptors.response.use(
  (resp) => resp,
  async (err: AxiosError) => {
    const message = await extractMessage(err)
    ;(err as AxiosError & { friendlyMessage?: string }).friendlyMessage = message
    if (!(err.config as { silent?: boolean } | undefined)?.silent) {
      toast.error(message)
    }
    return Promise.reject(err)
  },
)

/** 从响应头 / 约定生成下载文件名并触发浏览器下载 */
export function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}

export function filenameFromDisposition(disposition: string | undefined, fallback: string): string {
  if (!disposition) return fallback
  const star = /filename\*=(?:UTF-8'')?([^;]+)/i.exec(disposition)
  if (star) {
    try {
      return decodeURIComponent(star[1].trim().replace(/^"|"$/g, ''))
    } catch {
      return star[1].trim()
    }
  }
  const plain = /filename=([^;]+)/i.exec(disposition)
  if (plain) return plain[1].trim().replace(/^"|"$/g, '')
  return fallback
}
