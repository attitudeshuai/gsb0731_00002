<template>
  <teleport to="body">
    <div
      v-if="visible"
      class="context-menu"
      :style="{ top: adjustedY + 'px', left: adjustedX + 'px' }"
      @click.stop
    >
      <template v-for="(item, idx) in items" :key="idx">
        <div v-if="item.divider" class="context-menu-divider"></div>
        <div
          v-else
          class="context-menu-item"
          :class="{ disabled: item.disabled }"
          @click="onItemClick(item)"
        >
          <span v-if="item.icon" class="context-menu-icon">{{ item.icon }}</span>
          <span class="context-menu-label">{{ item.label }}</span>
        </div>
      </template>
    </div>
  </teleport>
</template>

<script setup lang="ts">
import { ref, watch, nextTick, onBeforeUnmount } from 'vue'

export interface ContextMenuItem {
  label: string
  icon?: string
  action: string
  divider?: boolean
  disabled?: boolean
}

const props = defineProps<{
  visible: boolean
  x: number
  y: number
  items: ContextMenuItem[]
}>()

const emit = defineEmits<{
  select: [action: string]
  close: []
}>()

const adjustedX = ref(0)
const adjustedY = ref(0)

function adjustPosition() {
  nextTick(() => {
    const menu = document.querySelector('.context-menu') as HTMLElement | null
    if (!menu) return
    const menuWidth = menu.offsetWidth || 180
    const menuHeight = menu.offsetHeight || 100
    const vw = window.innerWidth
    const vh = window.innerHeight
    let nx = props.x
    let ny = props.y
    if (nx + menuWidth > vw - 4) nx = vw - menuWidth - 4
    if (ny + menuHeight > vh - 4) ny = vh - menuHeight - 4
    if (nx < 4) nx = 4
    if (ny < 4) ny = 4
    adjustedX.value = nx
    adjustedY.value = ny
  })
}

watch(
  () => props.visible,
  (v) => {
    if (v) {
      adjustedX.value = props.x
      adjustedY.value = props.y
      adjustPosition()
      document.addEventListener('click', onDocumentClick, true)
      document.addEventListener('contextmenu', onDocumentClick, true)
      window.addEventListener('resize', onWindowResize)
    } else {
      document.removeEventListener('click', onDocumentClick, true)
      document.removeEventListener('contextmenu', onDocumentClick, true)
      window.removeEventListener('resize', onWindowResize)
    }
  }
)

function onDocumentClick() {
  emit('close')
}

function onWindowResize() {
  emit('close')
}

function onItemClick(item: ContextMenuItem) {
  if (item.disabled) return
  emit('select', item.action)
  emit('close')
}

onBeforeUnmount(() => {
  document.removeEventListener('click', onDocumentClick, true)
  document.removeEventListener('contextmenu', onDocumentClick, true)
  window.removeEventListener('resize', onWindowResize)
})
</script>

<style scoped lang="scss">
@use '@/styles/index.scss' as *;

.context-menu {
  position: fixed;
  z-index: 9999;
  min-width: 180px;
  background: #fff;
  border-radius: $radius-sm;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.18);
  border: 1px solid $border-color;
  padding: 4px 0;
  user-select: none;
}

.context-menu-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 16px;
  font-size: 13px;
  color: $text-primary;
  cursor: pointer;
  transition: background 0.15s;

  &:hover:not(.disabled) {
    background: $primary-light;
    color: $primary-color;
  }

  &.disabled {
    color: $text-light;
    cursor: not-allowed;
  }
}

.context-menu-icon {
  font-size: 14px;
  width: 18px;
  text-align: center;
}

.context-menu-label {
  flex: 1;
}

.context-menu-divider {
  height: 1px;
  background: $border-color;
  margin: 4px 0;
}
</style>
