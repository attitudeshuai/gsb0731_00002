<template>
  <div class="saved-queries-view">
    <div class="page-header">
      <h1 class="page-title">收藏查询</h1>
      <div class="header-actions">
        <input
          v-model="keyword"
          class="search-input"
          placeholder="搜索查询..."
          @input="loadQueries"
        />
        <button class="btn btn-primary" @click="openSaveDialog()">+ 新建收藏</button>
      </div>
    </div>
    <div class="sqv-body">
      <div class="sqv-sidebar">
        <div class="sqv-sidebar-title">文件夹</div>
        <div class="folder-tree">
          <div
            class="folder-item"
            :class="{ active: selectedFolderId === null }"
            @click="selectedFolderId = null; loadQueries()"
          >
            <span class="folder-icon">📁</span>
            <span class="folder-name">全部</span>
          </div>
          <div
            v-for="folder in folders"
            :key="folder.id"
            class="folder-item"
            :class="{ active: selectedFolderId === folder.id }"
            @click="selectedFolderId = folder.id; loadQueries()"
          >
            <span class="folder-icon" :style="{ color: folder.color }">📁</span>
            <span class="folder-name">{{ folder.name }}</span>
            <button class="folder-del" @click.stop="onDeleteFolder(folder.id)" title="删除">✕</button>
          </div>
        </div>
        <button class="btn btn-default btn-sm add-folder-btn" @click="showFolderDialog = true">
          + 新建文件夹
        </button>
      </div>
      <div class="sqv-main">
        <div class="card sqv-table-card">
          <table class="sqv-table" v-if="queries.length">
            <thead>
              <tr>
                <th>名称</th>
                <th>SQL 预览</th>
                <th>连接</th>
                <th>标签</th>
                <th>更新时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="q in queries" :key="q.id" @dblclick="openInEditor(q)">
                <td class="col-name">{{ q.name }}</td>
                <td class="col-sql" :title="q.sqlText">{{ q.sqlText }}</td>
                <td class="col-conn">{{ getConnectionName(q.connectionId) }}</td>
                <td class="col-tags">{{ q.tags || '-' }}</td>
                <td class="col-time">{{ formatTime(q.updatedAt) }}</td>
                <td class="col-actions" @click.stop>
                  <button class="link-btn" @click="openInEditor(q)">打开</button>
                  <button class="link-btn" @click="openSaveDialog(q)">编辑</button>
                  <button class="link-btn link-danger" @click="onDelete(q)">删除</button>
                </td>
              </tr>
            </tbody>
          </table>
          <div v-else class="sqv-empty">
            <div class="sqv-empty-icon">⭐</div>
            <p>暂无收藏查询</p>
            <p class="sqv-empty-hint">新建收藏或在 SQL 查询中保存</p>
          </div>
        </div>
      </div>
    </div>

    <transition name="fade">
      <div v-if="showSaveDialog" class="dialog-overlay" @click.self="showSaveDialog = false">
        <div class="dialog-card-small">
          <h3>{{ editingQuery ? '编辑收藏' : '新建收藏' }}</h3>
          <div class="form-group">
            <label>名称</label>
            <input v-model="form.name" class="form-input" placeholder="查询名称" />
          </div>
          <div class="form-group">
            <label>SQL</label>
            <textarea v-model="form.sqlText" class="form-input form-textarea" rows="5"></textarea>
          </div>
          <div class="form-group">
            <label>文件夹</label>
            <select v-model="form.folderId" class="form-input">
              <option :value="null">无</option>
              <option v-for="f in folders" :key="f.id" :value="f.id">{{ f.name }}</option>
            </select>
          </div>
          <div class="form-group">
            <label>标签</label>
            <input v-model="form.tags" class="form-input" placeholder="逗号分隔" />
          </div>
          <div class="dialog-footer">
            <button class="btn btn-default" @click="showSaveDialog = false">取消</button>
            <button class="btn btn-primary" @click="saveQuery">保存</button>
          </div>
        </div>
      </div>
    </transition>

    <transition name="fade">
      <div v-if="showFolderDialog" class="dialog-overlay" @click.self="showFolderDialog = false">
        <div class="dialog-card-small">
          <h3>新建文件夹</h3>
          <div class="form-group">
            <label>名称</label>
            <input v-model="folderForm.name" class="form-input" placeholder="文件夹名称" />
          </div>
          <div class="form-group">
            <label>颜色</label>
            <input v-model="folderForm.color" type="color" class="color-input" />
          </div>
          <div class="dialog-footer">
            <button class="btn btn-default" @click="showFolderDialog = false">取消</button>
            <button class="btn btn-primary" @click="onCreateFolder">创建</button>
          </div>
        </div>
      </div>
    </transition>

    <ConfirmDialog
      :visible="confirmVisible"
      type="danger"
      title="删除收藏"
      :message="`确定要删除「${deletingQuery?.name}」吗？`"
      @confirm="confirmDelete"
      @cancel="confirmVisible = false"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import {
  getSavedQueries,
  createSavedQuery,
  updateSavedQuery,
  deleteSavedQuery,
  getFolders,
  createFolder,
  deleteFolder
} from '@/api/query'
import { useConnectionStore } from '@/stores/connection'
import { useToast } from '@/composables/useToast'
import ConfirmDialog from '@/components/common/ConfirmDialog.vue'
import type { SavedQuery, QueryFolder } from '@/types'

const router = useRouter()
const connectionStore = useConnectionStore()
const toast = useToast()

const keyword = ref('')
const selectedFolderId = ref<number | null>(null)
const queries = ref<SavedQuery[]>([])
const folders = ref<QueryFolder[]>([])
const showSaveDialog = ref(false)
const showFolderDialog = ref(false)
const confirmVisible = ref(false)
const editingQuery = ref<SavedQuery | null>(null)
const deletingQuery = ref<SavedQuery | null>(null)

const form = reactive({
  name: '',
  sqlText: '',
  folderId: null as number | null,
  tags: ''
})

const folderForm = reactive({
  name: '',
  color: '#fbbc04'
})

onMounted(async () => {
  await Promise.all([loadQueries(), loadFolders(), connectionStore.fetchConnections()])
})

async function loadQueries() {
  try {
    queries.value = await getSavedQueries({
      folderId: selectedFolderId.value ?? undefined,
      keyword: keyword.value || undefined
    })
  } catch (e: any) {
    toast.error(e.message || '加载失败')
  }
}

async function loadFolders() {
  try {
    folders.value = await getFolders()
  } catch (e: any) {
    toast.error(e.message || '加载文件夹失败')
  }
}

function getConnectionName(id: number | null): string {
  if (!id) return '-'
  const conn = connectionStore.connections.find((c) => c.id === id)
  return conn?.name || `#${id}`
}

function openInEditor(q: SavedQuery) {
  router.push({ path: '/query', query: { sql: q.sqlText, connectionId: q.connectionId || undefined } })
}

function openSaveDialog(q?: SavedQuery) {
  editingQuery.value = q || null
  if (q) {
    form.name = q.name
    form.sqlText = q.sqlText
    form.folderId = q.folderId
    form.tags = q.tags
  } else {
    form.name = ''
    form.sqlText = ''
    form.folderId = selectedFolderId.value
    form.tags = ''
  }
  showSaveDialog.value = true
}

async function saveQuery() {
  if (!form.name.trim() || !form.sqlText.trim()) {
    toast.warning('请填写名称和 SQL')
    return
  }
  try {
    if (editingQuery.value) {
      await updateSavedQuery(editingQuery.value.id, { ...form })
      toast.success('更新成功')
    } else {
      await createSavedQuery({ ...form })
      toast.success('创建成功')
    }
    showSaveDialog.value = false
    loadQueries()
  } catch (e: any) {
    toast.error(e.message || '保存失败')
  }
}

function onDelete(q: SavedQuery) {
  deletingQuery.value = q
  confirmVisible.value = true
}

async function confirmDelete() {
  if (!deletingQuery.value) return
  try {
    await deleteSavedQuery(deletingQuery.value.id)
    toast.success('删除成功')
    loadQueries()
  } catch (e: any) {
    toast.error(e.message || '删除失败')
  } finally {
    confirmVisible.value = false
    deletingQuery.value = null
  }
}

async function onCreateFolder() {
  if (!folderForm.name.trim()) {
    toast.warning('请输入文件夹名称')
    return
  }
  try {
    await createFolder({ ...folderForm, sortOrder: 0 })
    toast.success('创建成功')
    showFolderDialog.value = false
    folderForm.name = ''
    loadFolders()
  } catch (e: any) {
    toast.error(e.message || '创建失败')
  }
}

async function onDeleteFolder(id: number) {
  try {
    await deleteFolder(id)
    toast.success('文件夹已删除')
    if (selectedFolderId.value === id) selectedFolderId.value = null
    loadFolders()
    loadQueries()
  } catch (e: any) {
    toast.error(e.message || '删除失败')
  }
}

function formatTime(iso: string): string {
  if (!iso) return '-'
  try {
    return new Date(iso).toLocaleString('zh-CN')
  } catch {
    return iso
  }
}
</script>

<style scoped lang="scss">
@use '@/styles/index.scss' as *;

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.header-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}

.search-input {
  padding: 8px 14px;
  border: 1px solid $border-color;
  border-radius: $radius-sm;
  font-size: 14px;
  width: 240px;

  &:focus {
    border-color: $primary-color;
  }
}

.sqv-body {
  display: flex;
  gap: 16px;
  height: calc(100vh - 180px);
}

.sqv-sidebar {
  width: 240px;
  flex-shrink: 0;
  background: #fff;
  border-radius: $radius-md;
  box-shadow: $shadow-sm;
  padding: 14px;
  display: flex;
  flex-direction: column;
}

.sqv-sidebar-title {
  font-size: 13px;
  font-weight: 600;
  color: $text-secondary;
  margin-bottom: 10px;
  text-transform: uppercase;
}

.folder-tree {
  flex: 1;
  overflow-y: auto;
}

.folder-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: $radius-sm;
  cursor: pointer;
  font-size: 13px;
  transition: background 0.15s;

  &:hover {
    background: #f5f6fa;

    .folder-del {
      opacity: 1;
    }
  }

  &.active {
    background: $primary-light;
    color: $primary-color;
  }
}

.folder-icon {
  font-size: 14px;
}

.folder-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.folder-del {
  background: transparent;
  color: $text-light;
  font-size: 11px;
  opacity: 0;
  padding: 0 4px;

  &:hover {
    color: $error-color;
  }
}

.add-folder-btn {
  margin-top: 10px;
  width: 100%;
}

.sqv-main {
  flex: 1;
  overflow: hidden;
}

.sqv-table-card {
  height: 100%;
  overflow: auto;
  padding: 0;
}

.sqv-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;

  th {
    background: #f7f8fa;
    padding: 12px 14px;
    text-align: left;
    font-weight: 600;
    color: $text-primary;
    border-bottom: 1px solid $border-color;
    position: sticky;
    top: 0;
  }

  td {
    padding: 10px 14px;
    border-bottom: 1px solid #f0f0f0;
    color: $text-secondary;
  }

  tr:hover td {
    background: #f9fafb;
  }
}

.col-name {
  font-weight: 600;
  color: $text-primary;
}

.col-sql {
  max-width: 320px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
}

.col-time {
  white-space: nowrap;
  color: $text-light;
  font-size: 12px;
}

.col-actions {
  white-space: nowrap;
}

.link-btn {
  background: transparent;
  color: $primary-color;
  padding: 2px 6px;
  font-size: 12px;

  &:hover {
    text-decoration: underline;
  }
}

.link-danger {
  color: $error-color;
}

.sqv-empty {
  text-align: center;
  padding: 80px 20px;
  color: $text-light;
}

.sqv-empty-icon {
  font-size: 48px;
  margin-bottom: 16px;
  opacity: 0.5;
}

.sqv-empty-hint {
  font-size: 12px;
  margin-top: 6px;
}

.dialog-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9990;
}

.dialog-card-small {
  width: 480px;
  max-width: 92vw;
  background: #fff;
  border-radius: $radius-md;
  padding: 20px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.2);

  h3 {
    margin-bottom: 16px;
    font-size: 16px;
  }
}

.form-group {
  margin-bottom: 14px;

  label {
    display: block;
    font-size: 13px;
    color: $text-secondary;
    margin-bottom: 5px;
  }
}

.form-input {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid $border-color;
  border-radius: $radius-sm;
  font-size: 14px;

  &:focus {
    border-color: $primary-color;
  }
}

.form-textarea {
  resize: vertical;
  font-family: Consolas, Monaco, monospace;
  font-size: 13px;
}

.color-input {
  width: 50px;
  height: 34px;
  border: 1px solid $border-color;
  border-radius: $radius-sm;
  cursor: pointer;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 16px;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
