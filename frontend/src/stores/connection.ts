import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Connection, ConnectionGroup } from '@/types'
import * as connectionApi from '@/api/connection'

export const useConnectionStore = defineStore('connection', () => {
  const connections = ref<Connection[]>([])
  const groups = ref<ConnectionGroup[]>([])
  const currentConnection = ref<Connection | null>(null)
  const loading = ref(false)

  async function fetchConnections(params?: connectionApi.ConnectionQueryParams) {
    loading.value = true
    try {
      const result = await connectionApi.getConnections(params)
      connections.value = result.content
      return result
    } finally {
      loading.value = false
    }
  }

  async function fetchGroups() {
    groups.value = await connectionApi.getGroups()
  }

  async function saveConnection(data: Partial<Connection>) {
    if (data.id) {
      const updated = await connectionApi.updateConnection(data.id, data)
      const index = connections.value.findIndex((c) => c.id === data.id)
      if (index !== -1) {
        connections.value[index] = updated
      }
      return updated
    } else {
      const created = await connectionApi.createConnection(data)
      connections.value.push(created)
      return created
    }
  }

  async function removeConnection(id: number) {
    await connectionApi.deleteConnection(id)
    connections.value = connections.value.filter((c) => c.id !== id)
    if (currentConnection.value?.id === id) {
      currentConnection.value = null
    }
  }

  async function testConnection(data: Partial<Connection>) {
    return connectionApi.testConnection(data)
  }

  function setCurrentConnection(connection: Connection | null) {
    currentConnection.value = connection
  }

  return {
    connections,
    groups,
    currentConnection,
    loading,
    fetchConnections,
    fetchGroups,
    saveConnection,
    removeConnection,
    testConnection,
    setCurrentConnection
  }
})
