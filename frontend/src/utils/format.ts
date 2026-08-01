/** 耗时格式化：1234 -> "1.23 s"，56 -> "56 ms" */
export function formatDuration(ms: number | null | undefined): string {
  if (ms == null) return '-'
  if (ms < 1000) return `${Math.round(ms)} ms`
  return `${(ms / 1000).toFixed(2)} s`
}

/** 日期时间格式化 */
export function formatDateTime(v: string | number | Date | null | undefined): string {
  if (!v) return '-'
  const d = v instanceof Date ? v : new Date(v)
  if (Number.isNaN(d.getTime())) return String(v)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

/** 数字千分位 */
export function formatNumber(n: number | null | undefined): string {
  if (n == null) return '-'
  return n.toLocaleString('en-US')
}

/** SQL 摘要（单行截断） */
export function sqlSummary(sql: string, max = 120): string {
  const oneLine = sql.replace(/\s+/g, ' ').trim()
  return oneLine.length > max ? oneLine.slice(0, max) + '…' : oneLine
}
