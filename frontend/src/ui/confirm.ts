import { reactive } from 'vue'

interface ConfirmOptions {
  title?: string
  danger?: boolean
  okText?: string
}

interface ConfirmState {
  visible: boolean
  title: string
  message: string
  danger: boolean
  okText: string
  resolve: ((v: boolean) => void) | null
}

export const confirmState = reactive<ConfirmState>({
  visible: false,
  title: '确认操作',
  message: '',
  danger: false,
  okText: '确定',
  resolve: null,
})

export function confirmDialog(message: string, opts: ConfirmOptions = {}): Promise<boolean> {
  return new Promise((resolve) => {
    confirmState.visible = true
    confirmState.title = opts.title ?? '确认操作'
    confirmState.message = message
    confirmState.danger = opts.danger ?? false
    confirmState.okText = opts.okText ?? '确定'
    confirmState.resolve = resolve
  })
}

export function resolveConfirm(v: boolean) {
  confirmState.visible = false
  const r = confirmState.resolve
  confirmState.resolve = null
  r?.(v)
}
