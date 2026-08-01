import { client } from './client'
import type { ConnectionConfig, ConnectionGroup } from '@/types'

// ---------- 连接 ----------

export async function listConnections(): Promise<ConnectionConfig[]> {
  const { data } = await client.get('/connections')
  return Array.isArray(data) ? data : (data?.items ?? data?.content ?? [])
}

export async function createConnection(payload: ConnectionConfig): Promise<ConnectionConfig> {
  const { data } = await client.post('/connections', payload)
  return data
}

export async function updateConnection(id: number, payload: ConnectionConfig): Promise<ConnectionConfig> {
  const { data } = await client.put(`/connections/${id}`, payload)
  return data
}

export async function deleteConnection(id: number): Promise<void> {
  await client.delete(`/connections/${id}`)
}

/** 测试未保存的连接配置 */
export async function testConnectionConfig(payload: ConnectionConfig): Promise<unknown> {
  const { data } = await client.post('/connections/test', payload, { silent: true })
  return data
}

/** 测试已保存的连接 */
export async function testConnectionById(id: number): Promise<unknown> {
  const { data } = await client.post(`/connections/${id}/test`, null, { silent: true })
  return data
}

// ---------- 分组 ----------

export async function listGroups(): Promise<ConnectionGroup[]> {
  const { data } = await client.get('/connection-groups')
  return Array.isArray(data) ? data : (data?.items ?? data?.content ?? [])
}

export async function createGroup(name: string): Promise<ConnectionGroup> {
  const { data } = await client.post('/connection-groups', { name })
  return data
}

export async function renameGroup(id: number, name: string): Promise<ConnectionGroup> {
  const { data } = await client.put(`/connection-groups/${id}`, { name })
  return data
}

export async function deleteGroup(id: number): Promise<void> {
  await client.delete(`/connection-groups/${id}`)
}
