import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Connection, ConnectionGroup } from '@/types'
import { connectionApi, groupApi } from '@/api'

export const useConnectionStore = defineStore('connection', () => {
  const connections = ref<Connection[]>([])
  const groups = ref<ConnectionGroup[]>([])
  const loading = ref(false)

  async function loadAll() {
    loading.value = true
    try {
      const [c, g] = await Promise.all([connectionApi.list(), groupApi.list()])
      connections.value = c
      groups.value = g
    } finally {
      loading.value = false
    }
  }

  function getConnection(id: number): Connection | undefined {
    return connections.value.find((c) => c.id === id)
  }

  return { connections, groups, loading, loadAll, getConnection }
})
