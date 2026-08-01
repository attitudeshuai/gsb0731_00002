import request from './index'
import type { ColumnMeta, TableMeta, PageResult } from '@/types'

export interface TableDataParams {
  page?: number
  size?: number
  sort?: string
  filter?: string
}

export interface TableDataResult {
  columns: ColumnMeta[]
  rows: Record<string, any>[]
  total: number
}

export function getDatabases(connectionId: number) {
  return request.get<any, string[]>(`/connections/${connectionId}/databases`)
}

export function getTables(connectionId: number, database: string) {
  return request.get<any, TableMeta[]>(`/connections/${connectionId}/tables`, {
    params: { database }
  })
}

export function getTableStructure(
  connectionId: number,
  database: string,
  table: string
) {
  return request.get<any, ColumnMeta[]>(
    `/connections/${connectionId}/tables/${table}/structure`,
    { params: { database } }
  )
}

export function getTableDDL(
  connectionId: number,
  database: string,
  table: string
) {
  return request.get<any, string>(
    `/connections/${connectionId}/tables/${table}/ddl`,
    { params: { database } }
  )
}

export function getTableData(
  connectionId: number,
  database: string,
  table: string,
  params: TableDataParams
) {
  return request.get<any, TableDataResult>(
    `/connections/${connectionId}/tables/${table}/data`,
    { params: { database, ...params } }
  )
}

export function updateTableRow(
  connectionId: number,
  database: string,
  table: string,
  data: { primaryKey: Record<string, any>; row: Record<string, any> }
) {
  return request.put<any, void>(
    `/connections/${connectionId}/tables/${table}/data`,
    data,
    { params: { database } }
  )
}

export function insertTableRow(
  connectionId: number,
  database: string,
  table: string,
  data: Record<string, any>
) {
  return request.post<any, void>(
    `/connections/${connectionId}/tables/${table}/data`,
    data,
    { params: { database } }
  )
}

export function deleteTableRow(
  connectionId: number,
  database: string,
  table: string,
  primaryKey: Record<string, any>
) {
  return request.delete<any, void>(
    `/connections/${connectionId}/tables/${table}/data`,
    { params: { database }, data: primaryKey }
  )
}
