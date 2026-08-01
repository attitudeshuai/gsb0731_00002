import request from './index'
import type { Connection, ConnectionGroup, PageResult } from '@/types'

export interface ConnectionQueryParams {
  page?: number
  size?: number
  keyword?: string
  groupId?: number
  type?: string
}

export function getConnections(params?: ConnectionQueryParams) {
  return request.get<any, PageResult<Connection>>('/connections', { params })
}

export function getConnection(id: number) {
  return request.get<any, Connection>(`/connections/${id}`)
}

export function createConnection(data: Partial<Connection>) {
  return request.post<any, Connection>('/connections', data)
}

export function updateConnection(id: number, data: Partial<Connection>) {
  return request.put<any, Connection>(`/connections/${id}`, data)
}

export function deleteConnection(id: number) {
  return request.delete<any, void>(`/connections/${id}`)
}

export function testConnection(data: Partial<Connection>) {
  return request.post<any, { success: boolean; message: string }>('/connections/test', data)
}

export function getGroups() {
  return request.get<any, ConnectionGroup[]>('/connections/groups')
}

export function createGroup(data: Partial<ConnectionGroup>) {
  return request.post<any, ConnectionGroup>('/connections/groups', data)
}

export function updateGroup(id: number, data: Partial<ConnectionGroup>) {
  return request.put<any, ConnectionGroup>(`/connections/groups/${id}`, data)
}

export function deleteGroup(id: number) {
  return request.delete<any, void>(`/connections/groups/${id}`)
}
