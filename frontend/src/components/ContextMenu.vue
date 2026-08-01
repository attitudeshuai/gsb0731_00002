<script setup lang="ts">
export interface MenuItem {
  label: string
  danger?: boolean
  action?: () => void
  children?: MenuItem[]
}

const props = defineProps<{
  visible: boolean
  x: number
  y: number
  items: MenuItem[]
}>()

const emit = defineEmits<{ (e: 'close'): void }>()

function onItemClick(item: MenuItem) {
  if (item.children?.length) return
  emit('close')
  item.action?.()
}

// 防止菜单超出视口
function menuStyle() {
  const w = 180
  const h = props.items.length * 30 + 8
  return {
    left: Math.min(props.x, window.innerWidth - w - 8) + 'px',
    top: Math.min(props.y, window.innerHeight - h - 8) + 'px',
  }
}
</script>

<template>
  <Teleport to="body">
    <div v-if="visible" class="ctx-mask" @mousedown="emit('close')" @contextmenu.prevent="emit('close')">
      <ul class="ctx-menu" :style="menuStyle()" @mousedown.stop>
        <li
          v-for="(item, i) in items"
          :key="i"
          class="ctx-item"
          :class="{ danger: item.danger, 'has-children': !!item.children?.length }"
          @click="onItemClick(item)"
        >
          <span>{{ item.label }}</span>
          <span v-if="item.children?.length" class="ctx-arrow">▸</span>
          <ul v-if="item.children?.length" class="ctx-menu ctx-submenu">
            <li
              v-for="(child, j) in item.children"
              :key="j"
              class="ctx-item"
              :class="{ danger: child.danger }"
              @click.stop="onItemClick(child)"
            >
              <span>{{ child.label }}</span>
            </li>
          </ul>
        </li>
      </ul>
    </div>
  </Teleport>
</template>

<style scoped>
.ctx-mask {
  position: fixed;
  inset: 0;
  z-index: 2000;
}

.ctx-menu {
  position: fixed;
  min-width: 160px;
  background: var(--bg-2);
  border: 1px solid var(--border);
  border-radius: 6px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.5);
  padding: 4px;
  margin: 0;
  list-style: none;
  z-index: 2001;
}

.ctx-item {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 12px;
  font-size: 12.5px;
  color: var(--text-0);
  border-radius: 4px;
  cursor: pointer;
  white-space: nowrap;
}

.ctx-item:hover {
  background: var(--accent);
  color: #fff;
}

.ctx-item.danger {
  color: var(--red);
}

.ctx-item.danger:hover {
  background: var(--red);
  color: #fff;
}

.ctx-arrow {
  font-size: 10px;
  margin-left: 16px;
  opacity: 0.8;
}

.ctx-submenu {
  display: none;
  position: absolute;
  left: calc(100% - 2px);
  top: -5px;
}

.ctx-item.has-children:hover > .ctx-submenu {
  display: block;
}
</style>
