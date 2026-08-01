<template>
  <teleport to="body">
    <div v-if="uiStore.showConnectionDialog" class="modal-overlay" @click.self="close">
      <div class="modal connection-modal">
        <div class="modal-header">
          <h3>{{ isEdit ? '编辑连接' : '新建连接' }}</h3>
          <button class="btn btn-ghost btn-sm" @click="close">×</button>
        </div>
        <div class="modal-body">
          <div class="form-group">
            <label>连接名称 *</label>
            <input v-model="form.name" placeholder="例如：生产数据库" />
          </div>
          <div class="form-row">
            <div class="form-group flex-2">
              <label>主机 *</label>
              <input v-model="form.host" placeholder="localhost" />
            </div>
            <div class="form-group flex-1">
              <label>端口 *</label>
              <input v-model.number="form.port" type="number" placeholder="3306" />
            </div>
          </div>
          <div class="form-group">
            <label>数据库名</label>
            <input v-model="form.databaseName" placeholder="可选" />
          </div>
          <div class="form-row">
            <div class="form-group flex-1">
              <label>用户名 *</label>
              <input v-model="form.username" placeholder="root" />
            </div>
            <div class="form-group flex-1">
              <label>密码</label>
              <input v-model="form.password" type="password" :placeholder="isEdit ? '留空不修改' : '密码'" />
            </div>
          </div>
          <div class="form-row">
            <div class="form-group flex-1">
              <label>分组</label>
              <select v-model="form.groupId">
                <option :value="null">无分组</option>
                <option v-for="g in connectionStore.groups" :key="g.id" :value="g.id">
                  {{ g.name }}
                </option>
              </select>
            </div>
            <div class="form-group flex-1">
              <label>颜色标记</label>
              <input v-model="form.color" placeholder="如 #5b8af5" />
            </div>
          </div>
          <div class="form-group">
            <label>备注</label>
            <textarea v-model="form.remark" rows="2" />
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn btn-secondary" @click="testConnection" :disabled="testing">
            {{ testing ? '测试中...' : '测试连接' }}
          </button>
          <button class="btn btn-ghost" @click="close">取消</button>
          <button class="btn btn-primary" @click="save" :disabled="saving">
            {{ saving ? '保存中...' : '保存' }}
          </button>
        </div>
      </div>
    </div>
  </teleport>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { useUiStore } from '@/stores/ui'
import { useConnectionStore } from '@/stores/connection'
import { useToastStore } from '@/stores/toast'
import { connectionApi } from '@/api'

const uiStore = useUiStore()
const connectionStore = useConnectionStore()
const toastStore = useToastStore()

const isEdit = computed(() => !!uiStore.editingConnection)
const testing = ref(false)
const saving = ref(false)

const form = reactive({
  name: '',
  host: 'localhost',
  port: 3306,
  username: 'root',
  password: '',
  databaseName: '',
  groupId: null as number | null,
  color: '',
  remark: ''
})

watch(() => uiStore.showConnectionDialog, (visible) => {
  if (visible) {
    const editing = uiStore.editingConnection
    if (editing) {
      form.name = editing.name || ''
      form.host = editing.host || 'localhost'
      form.port = editing.port || 3306
      form.username = editing.username || 'root'
      form.password = ''
      form.databaseName = editing.databaseName || ''
      form.groupId = editing.groupId ?? null
      form.color = editing.color || ''
      form.remark = editing.remark || ''
    } else {
      form.name = ''
      form.host = 'localhost'
      form.port = 3306
      form.username = 'root'
      form.password = ''
      form.databaseName = ''
      form.groupId = null
      form.color = ''
      form.remark = ''
    }
  }
})

async function testConnection() {
  if (!form.host || !form.username) {
    toastStore.error('请填写主机和用户名')
    return
  }
  testing.value = true
  try {
    await connectionApi.test({
      host: form.host,
      port: form.port,
      username: form.username,
      password: form.password,
      databaseName: form.databaseName
    })
    toastStore.success('连接成功')
  } catch (e: any) {
    toastStore.error(e.message)
  } finally {
    testing.value = false
  }
}

async function save() {
  if (!form.name || !form.host || !form.username) {
    toastStore.error('请填写必填字段')
    return
  }
  saving.value = true
  try {
    const payload = { ...form }
    if (isEdit.value) {
      if (!payload.password) delete (payload as any).password
      await connectionStore.updateConnection(uiStore.editingConnection.id, payload)
      toastStore.success('连接已更新')
    } else {
      await connectionStore.createConnection(payload)
      toastStore.success('连接已创建')
    }
    close()
  } catch (e: any) {
    toastStore.error(e.message)
  } finally {
    saving.value = false
  }
}

function close() {
  uiStore.closeConnectionDialog()
}
</script>

<style scoped>
.connection-modal {
  min-width: 500px;
}
.form-row {
  display: flex;
  gap: 12px;
}
.flex-1 { flex: 1; }
.flex-2 { flex: 2; }
textarea {
  resize: vertical;
}
</style>
