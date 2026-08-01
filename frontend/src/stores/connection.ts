import { defineStore } from 'pinia'
import { ref } from 'vue'
import { connectionApi } from '@/api'
import type { ConnectionConfig, ConnectionGroup, ConnectionRequest } from '@/types'

export const useConnectionStore = defineStore('connections', () => {
  const connections = ref<ConnectionConfig[]>([])
  const groups = ref<ConnectionGroup[]>([])
  const loading = ref(false)

  async function loadAll() {
    loading.value = true
    try {
      const [conns, grps] = await Promise.all([
        connectionApi.list(),
        connectionApi.listGroups()
      ])
      connections.value = conns
      groups.value = grps
    } finally {
      loading.value = false
    }
  }

  async function createConnection(data: ConnectionRequest) {
    const conn = await connectionApi.create(data)
    connections.value.push(conn)
    return conn
  }

  async function updateConnection(id: number, data: ConnectionRequest) {
    const conn = await connectionApi.update(id, data)
    const idx = connections.value.findIndex(c => c.id === id)
    if (idx >= 0) connections.value[idx] = conn
    return conn
  }

  async function deleteConnection(id: number) {
    await connectionApi.delete(id)
    connections.value = connections.value.filter(c => c.id !== id)
  }

  async function createGroup(name: string) {
    const g = await connectionApi.createGroup({ name })
    groups.value.push(g)
    return g
  }

  async function deleteGroup(id: number) {
    await connectionApi.deleteGroup(id)
    groups.value = groups.value.filter(g => g.id !== id)
  }

  return {
    connections, groups, loading,
    loadAll, createConnection, updateConnection, deleteConnection,
    createGroup, deleteGroup
  }
})
