<template>
  <teleport to="body">
    <div
      class="context-menu"
      :style="{ left: x + 'px', top: y + 'px' }"
      @click.stop
    >
      <template v-for="(item, idx) in items" :key="idx">
        <div v-if="item.divider" class="context-menu-divider" />
        <div
          v-else
          :class="['context-menu-item', { danger: item.danger, disabled: item.disabled }]"
          @click="handleClick(item)"
        >
          {{ item.label }}
        </div>
      </template>
    </div>
  </teleport>
</template>

<script setup lang="ts">
interface MenuItem {
  label?: string
  action?: () => void
  divider?: boolean
  danger?: boolean
  disabled?: boolean
}

defineProps<{
  x: number
  y: number
  items: MenuItem[]
}>()

const emit = defineEmits<{ close: [] }>()

function handleClick(item: MenuItem) {
  if (item.disabled) return
  item.action?.()
  emit('close')
}
</script>
