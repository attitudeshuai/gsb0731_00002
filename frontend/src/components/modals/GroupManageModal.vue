<script setup lang="ts">
import { ref } from 'vue'
import { useConnectionStore } from '@/stores/connections'
import { confirmDialog } from '@/ui/confirm'
import { toast } from '@/ui/toast'

defineProps<{ visible: boolean }>()
const emit = defineEmits<{ (e: 'update:visible', v: boolean): void }>()

const store = useConnectionStore()

const newName = ref('')
const adding = ref(false)
const editingId = ref<number | null>(null)
const editingName = ref('')

function close() {
  emit('update:visible', false)
}

async function onAdd() {
  const name = newName.value.trim()
  if (!name) {
    toast.info('请输入分组名称')
    return
  }
  adding.value = true
  try {
    await store.addGroup(name)
    newName.value = ''
    toast.success('分组已创建')
  } catch {
    /* 拦截器已提示 */
  } finally {
    adding.value = false
  }
}

function startRename(id: number, name: string) {
  editingId.value = id
  editingName.value = name
}

async function onRename() {
  const name = editingName.value.trim()
  if (!name || editingId.value == null) {
    editingId.value = null
    return
  }
  try {
    await store.renameGroupById(editingId.value, name)
    toast.success('已重命名')
  } catch {
    /* 拦截器已提示 */
  } finally {
    editingId.value = null
  }
}

async function onDelete(id: number, name: string) {
  const count = store.connectionsOfGroup(id).length
  const tip =
    count > 0
      ? `分组「${name}」下有 ${count} 个连接，删除分组可能影响这些连接，确定删除吗？`
      : `确定删除分组「${name}」吗？`
  const ok = await confirmDialog(tip, { title: '删除分组', danger: true, okText: '删除' })
  if (!ok) return
  try {
    await store.removeGroup(id)
    toast.success('分组已删除')
  } catch {
    /* 拦截器已提示 */
  }
}
</script>

<template>
  <Teleport to="body">
    <div v-if="visible" class="modal-overlay" @mousedown.self="close">
      <div class="modal" style="width: 400px">
        <div class="modal-header">
          <span>分组管理</span>
          <button class="modal-close" @click="close">×</button>
        </div>

        <div class="modal-body">
          <div class="group-add">
            <input
              v-model="newName"
              class="input"
              placeholder="新分组名称"
              @keydown.enter="onAdd"
            />
            <button class="btn btn-primary btn-sm" :disabled="adding" @click="onAdd">新建</button>
          </div>

          <ul class="group-list">
            <li v-for="g in store.groups" :key="g.id" class="group-item">
              <template v-if="editingId === g.id">
                <input
                  v-model="editingName"
                  class="input group-edit-input"
                  @keydown.enter="onRename"
                  @keydown.esc="editingId = null"
                />
                <button class="btn btn-primary btn-sm" @click="onRename">确定</button>
                <button class="btn btn-sm" @click="editingId = null">取消</button>
              </template>
              <template v-else>
                <span class="group-name">{{ g.name }}</span>
                <span class="group-count">{{ store.connectionsOfGroup(g.id).length }} 个连接</span>
                <button class="btn btn-sm" @click="startRename(g.id, g.name)">重命名</button>
                <button class="btn btn-danger btn-sm" @click="onDelete(g.id, g.name)">删除</button>
              </template>
            </li>
            <li v-if="store.groups.length === 0" class="group-empty">暂无分组</li>
          </ul>
        </div>

        <div class="modal-footer">
          <button class="btn btn-sm" @click="close">关闭</button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.group-add {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.group-list {
  list-style: none;
  margin: 0;
  padding: 0;
  max-height: 300px;
  overflow: auto;
}

.group-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 4px;
  border-bottom: 1px solid var(--border-soft);
}

.group-name {
  color: var(--text-0);
  font-size: 13px;
}

.group-count {
  flex: 1;
  color: var(--text-2);
  font-size: 11.5px;
}

.group-edit-input {
  flex: 1;
}

.group-empty {
  color: var(--text-2);
  font-size: 12.5px;
  text-align: center;
  padding: 18px 0;
}
</style>
