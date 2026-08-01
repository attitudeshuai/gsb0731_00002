import { defineStore } from 'pinia'
import { ref } from 'vue'

export type ThemeMode = 'light' | 'dark'

export const useAppStore = defineStore('app', () => {
  const sidebarCollapsed = ref(false)
  const theme = ref<ThemeMode>('light')

  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  function setTheme(mode: ThemeMode) {
    theme.value = mode
  }

  return {
    sidebarCollapsed,
    theme,
    toggleSidebar,
    setTheme
  }
})
