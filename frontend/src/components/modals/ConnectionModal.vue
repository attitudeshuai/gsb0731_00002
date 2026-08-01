<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { testConnectionConfig } from '@/api/connections'
import { useConnectionStore } from '@/stores/connections'
import { toast } from '@/ui/toast'
import type { ConnectionConfig } from '@/types'

const props = defineProps<{
  visible: boolean
  /** 编辑时传入已有连接；新建为 null */
  connection: ConnectionConfig | null
  /** 新建时预选分组 */
  presetGroupId?: number | null
}>()

const emit = defineEmits<{
  (e: 'update:visible', v: boolean): void
  (e: 'saved'): void
}>()

const store = useConnectionStore()

const form = reactive<ConnectionConfig>({
  name: '',
  groupId: null,
  host: '127.0.0.1',
  port: 3306,
  username: 'root',
  password: '',
  defaultDatabase: '',
  queryTimeoutSeconds: 60,
})

const saving = ref(false)
const testing = ref(false)
const testResult = ref<{ ok: boolean; message: string } | null>(null)

watch(
  () => props.visible,
  (v) => {
    if (!v) return
    testResult.value = null
    if (props.connection) {
      Object.assign(form, {
        id: props.connection.id,
        name: props.connection.name,
        groupId: props.connection.groupId ?? null,
        host: props.connection.host,
        port: props.connection.port,
        username: props.connection.username,
        password: props.connection.password ?? '',
        defaultDatabase: props.connection.defaultDatabase ?? '',
        queryTimeoutSeconds: props.connection.queryTimeoutSeconds ?? 60,
      })
    } else {
      Object.assign(form, {
        id: undefined,
        name: '',
        groupId: props.presetGroupId ?? null,
        host: '127.0.0.1',
        port: 3306,
        username: 'root',
        password: '',
        defaultDatabase: '',
        queryTimeoutSeconds: 60,
      })
    }
  },
  { immediate: true },
)

function close() {
  emit('update:visible', false)
}

function validate(): string | null {
  if (!form.name.trim()) return '请输入连接名称'
  if (!form.host.trim()) return '请输入 Host'
  if (!form.port || form.port <= 0 || form.port > 65535) return '请输入有效端口'
  if (!form.username.trim()) return '请输入用户名'
  const timeout = Number(form.queryTimeoutSeconds)
  if (!Number.isInteger(timeout) || timeout < 1 || timeout > 3600) {
    return '查询超时需为 1-3600 之间的整数秒'
  }
  return null
}

function buildPayload(): ConnectionConfig {
  return {
    id: form.id,
    name: form.name.trim(),
    groupId: form.groupId ?? null,
    host: form.host.trim(),
    port: Number(form.port),
    username: form.username.trim(),
    password: form.password,
    defaultDatabase: form.defaultDatabase?.trim() || null,
    queryTimeoutSeconds: Number(form.queryTimeoutSeconds) || 60,
  }
}

async function onTest() {
  const err = validate()
  if (err) {
    toast.info(err)
    return
  }
  testing.value = true
  testResult.value = null
  try {
    await testConnectionConfig(buildPayload())
    testResult.value = { ok: true, message: '连接成功' }
  } catch (e) {
    const msg = (e as { friendlyMessage?: string })?.friendlyMessage ?? '连接失败'
    testResult.value = { ok: false, message: msg }
  } finally {
    testing.value = false
  }
}

async function onSave() {
  const err = validate()
  if (err) {
    toast.info(err)
    return
  }
  saving.value = true
  try {
    await store.saveConnection(buildPayload())
    toast.success(form.id ? '连接已更新' : '连接已创建')
    emit('saved')
    close()
  } catch {
    /* 拦截器已提示 */
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <Teleport to="body">
    <div v-if="visible" class="modal-overlay" @mousedown.self="close">
      <div class="modal" style="width: 460px">
        <div class="modal-header">
          <span>{{ form.id ? '编辑连接' : '新建连接' }}</span>
          <button class="modal-close" @click="close">×</button>
        </div>

        <div class="modal-body">
          <label class="form-item">
            <span class="form-label">名称 <i class="req">*</i></span>
            <input v-model="form.name" class="input" placeholder="例如：本地 MySQL" />
          </label>

          <label class="form-item">
            <span class="form-label">分组</span>
            <select v-model="form.groupId" class="input">
              <option :value="null">（无分组）</option>
              <option v-for="g in store.groups" :key="g.id" :value="g.id">{{ g.name }}</option>
            </select>
          </label>

          <div class="form-row">
            <label class="form-item" style="flex: 2">
              <span class="form-label">Host <i class="req">*</i></span>
              <input v-model="form.host" class="input" placeholder="127.0.0.1" />
            </label>
            <label class="form-item" style="flex: 1">
              <span class="form-label">Port <i class="req">*</i></span>
              <input v-model.number="form.port" class="input" type="number" placeholder="3306" />
            </label>
          </div>

          <div class="form-row">
            <label class="form-item" style="flex: 1">
              <span class="form-label">用户名 <i class="req">*</i></span>
              <input v-model="form.username" class="input" autocomplete="off" />
            </label>
            <label class="form-item" style="flex: 1">
              <span class="form-label">密码</span>
              <input v-model="form.password" class="input" type="password" autocomplete="new-password" />
            </label>
          </div>

          <label class="form-item">
            <span class="form-label">默认数据库</span>
            <input v-model="form.defaultDatabase" class="input" placeholder="（可选）" />
          </label>

          <label class="form-item">
            <span class="form-label">查询超时（秒）</span>
            <input
              v-model.number="form.queryTimeoutSeconds"
              class="input"
              type="number"
              min="1"
              max="3600"
              placeholder="60"
            />
          </label>

          <div v-if="testResult" class="test-result" :class="{ ok: testResult.ok }">
            {{ testResult.ok ? '✓ ' : '✕ ' }}{{ testResult.message }}
          </div>
        </div>

        <div class="modal-footer">
          <button class="btn btn-sm" :disabled="testing" @click="onTest">
            {{ testing ? '测试中…' : '测试连接' }}
          </button>
          <span class="spacer"></span>
          <button class="btn btn-sm" @click="close">取消</button>
          <button class="btn btn-primary btn-sm" :disabled="saving" @click="onSave">
            {{ saving ? '保存中…' : '保存' }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.form-row {
  display: flex;
  gap: 10px;
}

.test-result {
  margin-top: 4px;
  padding: 7px 10px;
  border-radius: 5px;
  font-size: 12px;
  background: rgba(245, 83, 75, 0.12);
  color: var(--red);
  border: 1px solid rgba(245, 83, 75, 0.3);
  word-break: break-all;
}

.test-result.ok {
  background: rgba(63, 185, 80, 0.12);
  color: var(--green);
  border-color: rgba(63, 185, 80, 0.3);
}

.spacer {
  flex: 1;
}
</style>
