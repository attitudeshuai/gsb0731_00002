<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useQueryStore } from '@/stores/query'
import { toast } from '@/ui/toast'

const props = defineProps<{ visible: boolean }>()
const emit = defineEmits<{
  (e: 'update:visible', v: boolean): void
  (e: 'save', payload: { name: string; folderId: number | null }): void
}>()

const queryStore = useQueryStore()

const name = ref('')
const folderId = ref<number | null>(null)
const newFolderName = ref('')
const saving = ref(false)

onMounted(() => void queryStore.fetchFolders())

watch(
  () => props.visible,
  (v) => {
    if (v) {
      name.value = ''
      folderId.value = null
      newFolderName.value = ''
      void queryStore.fetchFolders()
    }
  },
)

function close() {
  emit('update:visible', false)
}

async function addFolder() {
  const n = newFolderName.value.trim()
  if (!n) return
  try {
    await queryStore.addFolder(n)
    newFolderName.value = ''
    const created = queryStore.folders.find((f) => f.name === n)
    if (created) folderId.value = created.id
  } catch {
    /* 拦截器已提示 */
  }
}

async function onSave() {
  if (!name.value.trim()) {
    toast.info('请输入名称')
    return
  }
  saving.value = true
  try {
    emit('save', { name: name.value.trim(), folderId: folderId.value })
    close()
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <Teleport to="body">
    <div v-if="visible" class="modal-overlay" @mousedown.self="close">
      <div class="modal" style="width: 380px">
        <div class="modal-header">
          <span>保存到收藏</span>
          <button class="modal-close" @click="close">×</button>
        </div>

        <div class="modal-body">
          <label class="form-item">
            <span class="form-label">名称 <i class="req">*</i></span>
            <input v-model="name" class="input" placeholder="例如：查询近 7 天订单" @keydown.enter="onSave" />
          </label>

          <label class="form-item">
            <span class="form-label">文件夹</span>
            <select v-model="folderId" class="input">
              <option :value="null">（无文件夹）</option>
              <option v-for="f in queryStore.folders" :key="f.id" :value="f.id">{{ f.name }}</option>
            </select>
          </label>

          <div class="folder-add">
            <input v-model="newFolderName" class="input" placeholder="新建文件夹…" @keydown.enter="addFolder" />
            <button class="btn btn-sm" @click="addFolder">创建</button>
          </div>
        </div>

        <div class="modal-footer">
          <button class="btn btn-sm" @click="close">取消</button>
          <button class="btn btn-primary btn-sm" :disabled="saving" @click="onSave">保存</button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.folder-add {
  display: flex;
  gap: 8px;
}
</style>
