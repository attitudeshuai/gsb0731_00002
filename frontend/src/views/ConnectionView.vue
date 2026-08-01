<template>
  <div class="connection-view">
    <div class="cv-sidebar">
      <div class="cv-toolbar">
        <button class="btn btn-primary btn-sm" @click="openConnectionDialog()">+ 新建连接</button>
        <button class="btn btn-default btn-sm" @click="openGroupDialog()">+ 分组</button>
      </div>
      <DatabaseTree
        :connections="connectionStore.connections"
        :groups="connectionStore.groups"
        @open-table="onOpenTable"
        @view-ddl="onViewDDL"
        @export-table="onExportTable"
        @refresh-connection="onRefreshConnection"
        @edit-connection="openConnectionDialog"
        @delete-connection="onDeleteConnection"
      />
    </div>
    <div class="cv-main">
      <div class="cv-welcome">
        <div class="welcome-icon">🗄</div>
        <h2 class="welcome-title">数据库管理</h2>
        <p class="welcome-hint">选择左侧连接和表开始浏览数据</p>
        <p class="welcome-tip">提示：右键表节点可查看建表语句、导出数据</p>
      </div>
    </div>
    <ConnectionDialog
      :visible="connDialogVisible"
      :connection="editingConnection"
      :groups="connectionStore.groups"
      @save="onSaveConnection"
      @close="connDialogVisible = false"
    />
    <GroupDialog
      :visible="groupDialogVisible"
      :groups="connectionStore.groups"
      @save="onSaveGroup"
      @close="groupDialogVisible = false"
    />
    <ConfirmDialog
      :visible="confirmVisible"
      type="danger"
      title="删除连接"
      :message="`确定要删除连接「${deletingConnection?.name}」吗？此操作不可恢复。`"
      @confirm="confirmDelete"
      @cancel="confirmVisible = false"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useConnectionStore } from '@/stores/connection'
import DatabaseTree from '@/components/database/DatabaseTree.vue'
import ConnectionDialog from '@/components/connection/ConnectionDialog.vue'
import GroupDialog from '@/components/connection/GroupDialog.vue'
import ConfirmDialog from '@/components/common/ConfirmDialog.vue'
import { useToast } from '@/composables/useToast'
import { createGroup } from '@/api/connection'
import type { Connection } from '@/types'

const router = useRouter()
const connectionStore = useConnectionStore()
const toast = useToast()

const connDialogVisible = ref(false)
const groupDialogVisible = ref(false)
const editingConnection = ref<Connection | null>(null)
const deletingConnection = ref<Connection | null>(null)
const confirmVisible = ref(false)

onMounted(async () => {
  try {
    await Promise.all([
      connectionStore.fetchConnections(),
      connectionStore.fetchGroups()
    ])
  } catch (e: any) {
    toast.error(e.message || '加载数据失败')
  }
})

function openConnectionDialog(conn?: Connection) {
  editingConnection.value = conn || null
  connDialogVisible.value = true
}

function openGroupDialog() {
  groupDialogVisible.value = true
}

async function onSaveConnection(data: Partial<Connection>) {
  try {
    await connectionStore.saveConnection(data)
    toast.success('连接保存成功')
    connDialogVisible.value = false
  } catch (e: any) {
    toast.error(e.message || '保存失败')
  }
}

async function onSaveGroup(data: any) {
  try {
    await createGroup(data)
    await connectionStore.fetchGroups()
    toast.success('分组保存成功')
    groupDialogVisible.value = false
  } catch (e: any) {
    toast.error(e.message || '保存失败')
  }
}

function onOpenTable(payload: { connectionId: number; database: string; table: string; type: string }) {
  router.push({
    name: 'TableData',
    params: {
      connectionId: payload.connectionId,
      dbName: payload.database,
      tableName: payload.table
    }
  })
}

function onViewDDL(payload: { connectionId: number; database: string; table: string }) {
  router.push({
    name: 'TableData',
    params: {
      connectionId: payload.connectionId,
      dbName: payload.database,
      tableName: payload.table
    },
    query: { tab: 'ddl' }
  })
}

function onExportTable(payload: { connectionId: number; database: string; table: string }) {
  toast.info(`导出功能：${payload.table}`)
}

async function onRefreshConnection(connectionId: number) {
  try {
    await connectionStore.fetchConnections()
    toast.success('连接已刷新')
  } catch (e: any) {
    toast.error(e.message || '刷新失败')
  }
}

function onDeleteConnection(conn: Connection) {
  deletingConnection.value = conn
  confirmVisible.value = true
}

async function confirmDelete() {
  if (!deletingConnection.value) return
  try {
    await connectionStore.removeConnection(deletingConnection.value.id)
    toast.success('连接已删除')
  } catch (e: any) {
    toast.error(e.message || '删除失败')
  } finally {
    confirmVisible.value = false
    deletingConnection.value = null
  }
}
</script>

<style scoped lang="scss">
@use '@/styles/index.scss' as *;

.connection-view {
  display: flex;
  height: 100%;
  margin: -20px;
  overflow: hidden;
}

.cv-sidebar {
  width: 280px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  background: $sidebar-bg;
  border-right: 1px solid rgba(255, 255, 255, 0.06);
}

.cv-toolbar {
  display: flex;
  gap: 8px;
  padding: 10px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}

.cv-main {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  background: $bg-color;
  overflow: auto;
}

.cv-welcome {
  text-align: center;
  color: $text-secondary;
}

.welcome-icon {
  font-size: 64px;
  margin-bottom: 20px;
  opacity: 0.6;
}

.welcome-title {
  font-size: 22px;
  font-weight: 600;
  color: $text-primary;
  margin-bottom: 10px;
}

.welcome-hint {
  font-size: 14px;
  margin-bottom: 8px;
}

.welcome-tip {
  font-size: 12px;
  color: $text-light;
}
</style>
