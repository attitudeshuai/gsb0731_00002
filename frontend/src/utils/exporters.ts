import type { ColumnMeta, ExportFormat } from '@/types'
import { downloadBlob } from '@/api/client'

function csvEscape(v: unknown): string {
  if (v === null || v === undefined) return ''
  const s = typeof v === 'object' ? JSON.stringify(v) : String(v)
  return /[",\n\r]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s
}

export function rowsToCsv(columns: ColumnMeta[], rows: unknown[][]): string {
  const header = columns.map((c) => csvEscape(c.name)).join(',')
  const body = rows.map((r) => r.map(csvEscape).join(',')).join('\n')
  return header + (body ? '\n' + body : '')
}

export function rowsToJson(columns: ColumnMeta[], rows: unknown[][]): string {
  const list = rows.map((r) => {
    const obj: Record<string, unknown> = {}
    columns.forEach((c, i) => {
      obj[c.name] = r[i]
    })
    return obj
  })
  return JSON.stringify(list, null, 2)
}

function sqlLiteral(v: unknown): string {
  if (v === null || v === undefined) return 'NULL'
  if (typeof v === 'number') return String(v)
  if (typeof v === 'boolean') return v ? '1' : '0'
  const s = typeof v === 'object' ? JSON.stringify(v) : String(v)
  return `'${s.replace(/'/g, "''")}'`
}

export function rowsToSql(table: string, columns: ColumnMeta[], rows: unknown[][]): string {
  const cols = columns.map((c) => `\`${c.name}\``).join(', ')
  return rows
    .map((r) => `INSERT INTO \`${table}\` (${cols}) VALUES (${r.map(sqlLiteral).join(', ')});`)
    .join('\n')
}

/** 前端生成结果集导出文件并下载 */
export function downloadResultSet(
  name: string,
  columns: ColumnMeta[],
  rows: unknown[][],
  format: ExportFormat,
) {
  let content: string
  let mime: string
  if (format === 'csv') {
    content = '\uFEFF' + rowsToCsv(columns, rows) // BOM 便于 Excel 打开
    mime = 'text/csv;charset=utf-8'
  } else if (format === 'json') {
    content = rowsToJson(columns, rows)
    mime = 'application/json;charset=utf-8'
  } else {
    content = rowsToSql(name, columns, rows)
    mime = 'text/plain;charset=utf-8'
  }
  const blob = new Blob([content], { type: mime })
  downloadBlob(blob, `${name}.${format}`)
}
