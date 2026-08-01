<template>
  <transition name="fade">
    <div v-if="visible" class="dialog-overlay" @click.self="onClose">
      <div class="dialog-card">
        <div class="dialog-header">
          <span class="dialog-title">{{ isEdit ? '编辑分组' : '新建分组' }}</span>
          <button class="dialog-close" @click="onClose">✕</button>
        </div>
        <div class="dialog-body">
          <div class="form-row">
            <label class="form-label"><span class="required">*</span> 分组名称</label>
            <input v-model="form.name" class="form-input" placeholder="请输入分组名称" />
            <span v-if="errors.name" class="form-error">{{ errors.name }}</span>
          </div>
          <div class="form-row">
            <label class="form-label">父级分组</label>
            <select v-model="form.parentId" class="form-input">
              <option :value="null">无（顶级分组）</option>
              <option
                v-for="g in parentGroups"
                :key="g.id"
                :value="g.id"
              >{{ g.name }}</option>
            </select>
          </div>
          <div class="form-row-2">
            <div class="form-row">
              <label class="form-label">颜色</label>
              <div class="color-row">
                <input v-model="form.color" type="color" class="color-picker" />
                <span class="color-value">{{ form.color }}</span>
              </div>
            </div>
            <div class="form-row">
              <label class="form-label">排序</label>
              <input v-model.number="form.sortOrder" type="number" class="form-input" placeholder="0" />
            </div>
          </div>
        </div>
        <div class="dialog-footer">
          <span></span>
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
import type { ConnectionGroup } from '@/types'

const props = defineProps<{
  visible: boolean
  group?: ConnectionGroup | null
  groups: ConnectionGroup[]
}>()

const emit = defineEmits<{
  save: [data: Partial<ConnectionGroup>]
  close: []
}>()

const isEdit = computed(() => !!props.group?.id)

const defaultForm = (): Partial<ConnectionGroup> => ({
  name: '',
  parentId: null,
  color: '#fbbc04',
  sortOrder: 0
})

const form = reactive<Partial<ConnectionGroup>>(defaultForm())
const errors = reactive<Record<string, string>>({})

const parentGroups = computed(() =>
  props.groups.filter((g) => g.id !== props.group?.id)
)

watch(
  () => props.visible,
  (v) => {
    if (v) {
      Object.assign(form, defaultForm())
      Object.keys(errors).forEach((k) => delete errors[k])
      if (props.group) {
        Object.assign(form, props.group)
      }
    }
  }
)

function onSave() {
  Object.keys(errors).forEach((k) => delete errors[k])
  if (!form.name?.trim()) {
    errors.name = '请输入分组名称'
    return
  }
  emit('save', { ...form })
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
  width: 440px;
  max-width: 92vw;
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

  &:focus {
    border-color: $primary-color;
  }
}

.form-error {
  color: $error-color;
  font-size: 12px;
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
