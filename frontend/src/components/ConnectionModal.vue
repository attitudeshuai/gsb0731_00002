<template>
  <div class="modal-backdrop" @click.self="$emit('close')">
    <div class="modal">
      <div class="modal-header">
        <h3>{{ editing ? 'Edit Connection' : 'New Connection' }}</h3>
        <button class="icon-btn" @click="$emit('close')">✕</button>
      </div>
      <div class="modal-body">
        <div class="field">
          <label>Name</label>
          <input v-model="form.name" placeholder="My Database" />
        </div>
        <div class="field">
          <label>Group</label>
          <select v-model="form.groupId">
            <option :value="null">— No group —</option>
            <option v-for="g in groups" :key="g.id" :value="g.id">{{ g.name }}</option>
          </select>
        </div>
        <div class="row">
          <div class="field flex3">
            <label>Host</label>
            <input v-model="form.host" placeholder="127.0.0.1" />
          </div>
          <div class="field flex1">
            <label>Port</label>
            <input v-model.number="form.port" type="number" />
          </div>
        </div>
        <div class="field">
          <label>Username</label>
          <input v-model="form.username" placeholder="root" />
        </div>
        <div class="field">
          <label>Password {{ editing ? '(leave blank to keep)' : '' }}</label>
          <input v-model="form.password" type="password" placeholder="••••••" />
        </div>
        <div class="field">
          <label>Database (optional)</label>
          <input v-model="form.databaseName" placeholder="mydb" />
        </div>
      </div>
      <div class="modal-footer">
        <button @click="test" :disabled="busy">Test Connection</button>
        <div class="spacer"></div>
        <button @click="$emit('close')">Cancel</button>
        <button class="primary" @click="save" :disabled="busy">Save</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import type { Connection, ConnectionGroup, ConnectionRequest } from '@/types'
import { connectionApi } from '@/api'
import { useUiStore } from '@/stores/ui'

const props = defineProps<{
  editing: Connection | null
  groups: ConnectionGroup[]
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'saved'): void
}>()

const ui = useUiStore()
const busy = ref(false)

const form = reactive<ConnectionRequest>({
  name: props.editing?.name ?? '',
  groupId: props.editing?.groupId ?? null,
  host: props.editing?.host ?? '127.0.0.1',
  port: props.editing?.port ?? 3306,
  username: props.editing?.username ?? 'root',
  password: '',
  databaseName: props.editing?.databaseName ?? ''
})

async function test() {
  busy.value = true
  try {
    await connectionApi.testAdHoc({ ...form })
    ui.success('Connection successful')
  } catch (e: any) {
    ui.error(e.message)
  } finally {
    busy.value = false
  }
}

async function save() {
  if (!form.name || !form.host || !form.username) {
    ui.error('Name, host and username are required')
    return
  }
  busy.value = true
  try {
    const body: ConnectionRequest = { ...form }
    if (props.editing && !body.password) {
      delete body.password
    }
    if (props.editing) {
      await connectionApi.update(props.editing.id, body)
      ui.success('Connection updated')
    } else {
      await connectionApi.create(body)
      ui.success('Connection created')
    }
    emit('saved')
  } catch (e: any) {
    ui.error(e.message)
  } finally {
    busy.value = false
  }
}
</script>

<style scoped>
.modal-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}
.modal {
  width: 440px;
  background: var(--bg-panel);
  border: 1px solid var(--border);
  border-radius: 8px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.5);
}
.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid var(--border);
}
.modal-header h3 {
  font-size: 15px;
}
.modal-body {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.field.flex3 { flex: 3; }
.field.flex1 { flex: 1; }
.row {
  display: flex;
  gap: 12px;
}
.modal-footer {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 16px;
  border-top: 1px solid var(--border);
}
.spacer { flex: 1; }
</style>
