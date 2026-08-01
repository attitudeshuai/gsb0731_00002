import { reactive } from 'vue'

export type ToastType = 'success' | 'error' | 'info'

export interface ToastItem {
  id: number
  message: string
  type: ToastType
}

export const toastState = reactive<{ items: ToastItem[] }>({ items: [] })

let seq = 1

function push(message: string, type: ToastType, duration: number) {
  const id = seq++
  toastState.items.push({ id, message, type })
  window.setTimeout(() => dismiss(id), duration)
}

export function dismiss(id: number) {
  const idx = toastState.items.findIndex((t) => t.id === id)
  if (idx >= 0) toastState.items.splice(idx, 1)
}

export const toast = {
  success(message: string, duration = 2500) {
    push(message, 'success', duration)
  },
  error(message: string, duration = 4500) {
    push(message, 'error', duration)
  },
  info(message: string, duration = 3000) {
    push(message, 'info', duration)
  },
}
