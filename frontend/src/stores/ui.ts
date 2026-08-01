import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useUiStore = defineStore('ui', () => {
  const showConnectionDialog = ref(false)
  const editingConnection = ref<any>(null)

  function openNewConnection() {
    editingConnection.value = null
    showConnectionDialog.value = true
  }

  function openEditConnection(conn: any) {
    editingConnection.value = conn
    showConnectionDialog.value = true
  }

  function closeConnectionDialog() {
    showConnectionDialog.value = false
    editingConnection.value = null
  }

  return {
    showConnectionDialog, editingConnection,
    openNewConnection, openEditConnection, closeConnectionDialog
  }
})
