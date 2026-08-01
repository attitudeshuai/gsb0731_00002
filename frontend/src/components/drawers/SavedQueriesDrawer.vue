<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useQueryStore } from '@/stores/query'
import { useTabsStore } from '@/stores/tabs'
import { confirmDialog } from '@/ui/confirm'
import { toast } from '@/ui/toast'
import { sqlSummary } from '@/utils/format'
import type { QueryFolder, SavedQuery } from '@/types'

const props = defineProps<{ visible: boolean }>()
const emit = defineEmits<{ (e: 'update:visible', v: boolean): void }>()

const queryStore = useQueryStore()
const tabsStore = useTabsStore()

const collapsedFolders = ref<Set<number>>(new Set())
const newFolderName = ref('')

watch(
  () => props.visible,
  (v) => {
    if (v) {
      void queryStore.fetchFolders()
      void queryStore.fetchSavedQueries()
    }
  },
)

function close() {
  emit('update:visible', false)
}

function queriesOf(folderId: number | null): SavedQuery[] {
  return queryStore.savedQueries.filter((q) => (q.folderId ?? null) === folderId)
}

const unfiled = computed(() => queriesOf(null))

function toggleFolder(id: number) {
  const next = new Set(collapsedFolders.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  collapsedFolders.value = next
}

function isCollapsed(id: number) {
  return collapsedFolders.value.has(id)
}

function loadQuery(q: SavedQuery) {
  tabsStore.loadSqlIntoEditor(q.sqlText)
  toast.success('已载入编辑器')
  close()
}

async function addFolder() {
  const n = newFolderName.value.trim()
  if (!n) return
  try {
    await queryStore.addFolder(n)
    newFolderName.value = ''
  } catch {
    /* 拦截器已提示 */
  }
}

async function renameFolder(f: QueryFolder) {
  const name = window.prompt('重命名文件夹', f.name)
  if (!name || !name.trim() || name.trim() === f.name) return
  try {
    await queryStore.renameFolder(f.id, name.trim())
  } catch {
    /* 拦截器已提示 */
  }
}

async function removeFolder(f: QueryFolder) {
  const ok = await confirmDialog(
    `确定删除文件夹「${f.name}」吗？其中的收藏查询可能被一并删除。`,
    { title: '删除文件夹', danger: true, okText: '删除' },
  )
  if (!ok) return
  try {
    await queryStore.removeFolder(f.id)
  } catch {
    /* 拦截器已提示 */
  }
}

async function removeQuery(q: SavedQuery, ev: MouseEvent) {
  ev.stopPropagation()
  const ok = await confirmDialog(`确定删除收藏「${q.name}」吗？`, {
    title: '删除收藏',
    danger: true,
    okText: '删除',
  })
  if (!ok) return
  try {
    await queryStore.removeSavedQuery(q.id)
  } catch {
    /* 拦截器已提示 */
  }
}
</script>

<template>
  <Teleport to="body">
    <div v-if="visible" class="drawer-mask" @mousedown.self="close">
      <aside class="drawer">
        <div class="drawer-header">
          <span class="drawer-title">收藏的查询</span>
          <span class="spacer"></span>
          <button class="modal-close" @click="close">×</button>
        </div>

        <div class="folder-toolbar">
          <input v-model="newFolderName" class="input" placeholder="新建文件夹…" @keydown.enter="addFolder" />
          <button class="btn btn-sm" @click="addFolder">创建</button>
        </div>

        <div class="drawer-body">
          <div v-if="queryStore.savedLoading && queryStore.savedQueries.length === 0" class="drawer-hint">
            <span class="spinner"></span>加载中…
          </div>

          <template v-else>
            <!-- 文件夹分组 -->
            <div v-for="f in queryStore.folders" :key="f.id" class="folder-block">
              <div class="folder-head" @click="toggleFolder(f.id)">
                <span class="twisty">{{ isCollapsed(f.id) ? '▸' : '▾' }}</span>
                <span class="folder-name">{{ f.name }}</span>
                <span class="folder-count">{{ queriesOf(f.id).length }}</span>
                <button class="mini-btn" title="重命名" @click.stop="renameFolder(f)">✎</button>
                <button class="mini-btn danger" title="删除" @click.stop="removeFolder(f)">×</button>
              </div>
              <div v-if="!isCollapsed(f.id)">
                <div
                  v-for="q in queriesOf(f.id)"
                  :key="q.id"
                  class="saved-item"
                  :title="q.sqlText"
                  @click="loadQuery(q)"
                >
                  <div class="saved-name">{{ q.name }}</div>
                  <div class="saved-sql">{{ sqlSummary(q.sqlText, 80) }}</div>
                  <button class="saved-del" title="删除" @click="removeQuery(q, $event)">×</button>
                </div>
                <div v-if="queriesOf(f.id).length === 0" class="drawer-hint small">空文件夹</div>
              </div>
            </div>

            <!-- 未归档 -->
            <div class="folder-block">
              <div class="folder-head static">
                <span class="twisty">▾</span>
                <span class="folder-name">未归档</span>
                <span class="folder-count">{{ unfiled.length }}</span>
              </div>
              <div
                v-for="q in unfiled"
                :key="q.id"
                class="saved-item"
                :title="q.sqlText"
                @click="loadQuery(q)"
              >
                <div class="saved-name">{{ q.name }}</div>
                <div class="saved-sql">{{ sqlSummary(q.sqlText, 80) }}</div>
                <button class="saved-del" title="删除" @click="removeQuery(q, $event)">×</button>
              </div>
            </div>

            <div v-if="queryStore.savedQueries.length === 0" class="drawer-hint">
              暂无收藏，可在查询编辑器工具栏点击「收藏」保存
            </div>
          </template>
        </div>
      </aside>
    </div>
  </Teleport>
</template>

<style scoped>
.folder-toolbar {
  display: flex;
  gap: 8px;
  padding: 8px 12px;
  border-bottom: 1px solid var(--border-soft);
}

.folder-block {
  border-bottom: 1px solid var(--border-soft);
}

.folder-head {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  cursor: pointer;
  color: var(--text-1);
  user-select: none;
}

.folder-head:hover {
  background: var(--bg-hover);
  color: var(--text-0);
}

.folder-head.static {
  cursor: default;
}

.twisty {
  font-size: 9px;
  color: var(--text-2);
  width: 10px;
}

.folder-name {
  font-size: 12.5px;
  font-weight: 600;
}

.folder-count {
  flex: 1;
  color: var(--text-2);
  font-size: 11px;
}

.mini-btn {
  background: none;
  border: none;
  color: var(--text-2);
  cursor: pointer;
  font-size: 12px;
  padding: 0 3px;
}

.mini-btn:hover {
  color: var(--text-0);
}

.mini-btn.danger:hover {
  color: var(--red);
}

.saved-item {
  position: relative;
  padding: 7px 34px 7px 34px;
  cursor: pointer;
  border-top: 1px solid var(--border-soft);
}

.saved-item:hover {
  background: var(--bg-hover);
}

.saved-name {
  color: var(--text-0);
  font-size: 12.5px;
  margin-bottom: 2px;
}

.saved-sql {
  color: var(--text-2);
  font-family: var(--font-mono);
  font-size: 11px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.saved-del {
  position: absolute;
  right: 10px;
  top: 8px;
  background: none;
  border: none;
  color: var(--text-2);
  cursor: pointer;
  font-size: 14px;
}

.saved-del:hover {
  color: var(--red);
}

.drawer-hint.small {
  padding: 8px 34px;
  font-size: 11.5px;
}

.spacer {
  flex: 1;
}
</style>
