import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface Toast {
  id: number
  type: 'success' | 'error' | 'info'
  message: string
}

export const useUiStore = defineStore('ui', () => {
  const toasts = ref<Toast[]>([])
  let seq = 1

  function notify(type: Toast['type'], message: string) {
    const id = seq++
    toasts.value.push({ id, type, message })
    setTimeout(() => dismiss(id), 4000)
  }

  function dismiss(id: number) {
    toasts.value = toasts.value.filter((t) => t.id !== id)
  }

  const success = (m: string) => notify('success', m)
  const error = (m: string) => notify('error', m)
  const info = (m: string) => notify('info', m)

  return { toasts, notify, dismiss, success, error, info }
})
