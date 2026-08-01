<template>
  <transition name="fade">
    <div v-if="visible" class="dialog-overlay" @click.self="onClose">
      <div class="dialog-card">
        <div class="dialog-header">
          <span class="dialog-title">{{ isEdit ? '编辑连接' : '新建连接' }}</span>
          <button class="dialog-close" @click="onClose">✕</button>
        </div>
        <div class="dialog-body">
          <div class="form-row">
            <label class="form-label"><span class="required">*</span> 连接名称</label>
            <input v-model="form.name" class="form-input" placeholder="例如：生产数据库" />
            <span v-if="errors.name" class="form-error">{{ errors.name }}</span>
          </div>
          <div class="form-row-2">
            <div class="form-row">
              <label class="form-label"><span class="required">*</span> 主机</label>
              <input v-model="form.host" class="form-input" placeholder="127.0.0.1" />
              <span v-if="errors.host" class="form-error">{{ errors.host }}</span>
            </div>
            <div class="form-row">
              <label class="form-label"><span class="required">*</span> 端口</label>
              <input v-model.number="form.port" type="number" class="form-input" placeholder="3306" />
              <span v-if="errors.port" class="form-error">{{ errors.port }}</span>
            </div>
          </div>
          <div class="form-row-2">
            <div class="form-row">
              <label class="form-label"><span class="required">*</span> 用户名</label>
              <input v-model="form.username" class="form-input" placeholder="root" />
              <span v-if="errors.username" class="form-error">{{ errors.username }}</span>
            </div>
            <div class="form-row">
              <label class="form-label">密码</label>
              <input
                v-if="!isEdit || changePassword"
                v-model="form.password"
                type="password"
                class="form-input"
                placeholder="请输入密码"
              />
              <div v-else class="password-placeholder">
                <span class="password-dots">••••••</span>
                <label class="change-pwd-label">
                  <input type="checkbox" v-model="changePassword" /> 修改密码
                </label>
              </div>
            </div>
          </div>
          <div class="form-row">
            <label class="form-label">数据库名</label>
            <input v-model="form.databaseName" class="form-input" placeholder="可选，默认数据库" />
          </div>
          <div class="form-row-2">
            <div class="form-row">
              <label class="form-label">分组</label>
              <select v-model="form.groupId" class="form-input">
                <option :value="null">无分组</option>
                <option v-for="g in groups" :key="g.id" :value="g.id">{{ g.name }}</option>
              </select>
            </div>
            <div class="form-row">
              <label class="form-label">颜色</label>
              <div class="color-row">
                <input v-model="form.color" type="color" class="color-picker" />
                <span class="color-value">{{ form.color }}</span>
              </div>
            </div>
          </div>
          <div class="form-row">
            <label class="form-label">备注</label>
            <textarea v-model="form.remark" class="form-input form-textarea" rows="2" placeholder="连接备注信息"></textarea>
          </div>
        </div>
        <div class="dialog-footer">
          <button class="btn btn-default" @click="onTest" :disabled="testing">
            {{ testing ? '测试中...' : '测试连接' }}
          </button>
          <div class="footer-right">
            <button class="btn btn-default" @click="onClose">取消</button>
            <button class="btn btn-primary" @click="onSave">保存</button>
          </div>
        </div>
      </div>
    </div>
  </transition>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import type { Connection, ConnectionGroup } from '@/types'
import { testConnection } from '@/api/connection'
import { useToast } from '@/composables/useToast'

const props = defineProps<{
  visible: boolean
  connection?: Connection | null
  groups: ConnectionGroup[]
}>()

const emit = defineEmits<{
  save: [data: Partial<Connection>]
  test: [data: Partial<Connection>]
  close: []
}>()

const toast = useToast()
const testing = ref(false)
const changePassword = ref(false)

const isEdit = computed(() => !!props.connection?.id)

const defaultForm = (): Partial<Connection> => ({
  name: '',
  host: '127.0.0.1',
  port: 3306,
  username: 'root',
  password: '',
  databaseName: '',
  groupId: null,
  color: '#1a73e8',
  remark: '',
  type: 'mysql'
})

const form = reactive<Partial<Connection>>(defaultForm())
const errors = reactive<Record<string, string>>({})

watch(
  () => props.visible,
  (v) => {
    if (v) {
      Object.assign(form, defaultForm())
      Object.keys(errors).forEach((k) => delete errors[k])
      changePassword.value = false
      if (props.connection) {
        Object.assign(form, {
          ...props.connection,
          password: ''
        })
      }
    }
  }
)

function validate(): boolean {
  Object.keys(errors).forEach((k) => delete errors[k])
  if (!form.name?.trim()) errors.name = '请输入连接名称'
  if (!form.host?.trim()) errors.host = '请输入主机地址'
  if (!form.port || form.port <= 0) errors.port = '请输入有效端口'
  if (!form.username?.trim()) errors.username = '请输入用户名'
  return Object.keys(errors).length === 0
}

function buildPayload(): Partial<Connection> {
  const payload: Partial<Connection> = { ...form }
  if (isEdit.value && !changePassword.value) {
    delete payload.password
  }
  return payload
}

async function onTest() {
  if (!validate()) return
  testing.value = true
  try {
    const result = await testConnection(buildPayload())
    if (result.success) {
      toast.success(result.message || '连接成功')
    } else {
      toast.error(result.message || '连接失败')
    }
  } catch (e: any) {
    toast.error(e.message || '连接失败')
  } finally {
    testing.value = false
  }
}

function onSave() {
  if (!validate()) return
  emit('save', buildPayload())
}

function onClose() {
  emit('close')
}
</script>

<style scoped lang="scss">
@use '@/styles/index.scss' as *;

.dialog-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9990;
}

.dialog-card {
  width: 560px;
  max-width: 92vw;
  max-height: 88vh;
  background: #fff;
  border-radius: $radius-md;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.2);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.dialog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid $border-color;
}

.dialog-title {
  font-size: 16px;
  font-weight: 600;
}

.dialog-close {
  background: transparent;
  color: $text-light;
  font-size: 16px;
  padding: 4px 8px;
  border-radius: 4px;

  &:hover {
    background: #f0f0f0;
    color: $text-primary;
  }
}

.dialog-body {
  padding: 20px;
  overflow-y: auto;
  flex: 1;
}

.form-row {
  margin-bottom: 14px;
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.form-row-2 {
  display: flex;
  gap: 14px;

  .form-row {
    flex: 1;
  }
}

.form-label {
  font-size: 13px;
  color: $text-secondary;
  font-weight: 500;
}

.required {
  color: $error-color;
  margin-right: 2px;
}

.form-input {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid $border-color;
  border-radius: $radius-sm;
  font-size: 14px;
  transition: border-color 0.2s;

  &:focus {
    border-color: $primary-color;
  }
}

.form-textarea {
  resize: vertical;
  min-height: 56px;
}

.form-error {
  color: $error-color;
  font-size: 12px;
}

.password-placeholder {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border: 1px solid $border-color;
  border-radius: $radius-sm;
  background: #fafafa;
}

.password-dots {
  color: $text-light;
  letter-spacing: 2px;
}

.change-pwd-label {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: $primary-color;
  cursor: pointer;
}

.color-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.color-picker {
  width: 40px;
  height: 34px;
  border: 1px solid $border-color;
  border-radius: $radius-sm;
  padding: 2px;
  cursor: pointer;
  background: #fff;
}

.color-value {
  font-size: 12px;
  color: $text-secondary;
  font-family: monospace;
}

.dialog-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
  border-top: 1px solid $border-color;
  background: #fafafa;
}

.footer-right {
  display: flex;
  gap: 10px;
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
