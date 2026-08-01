<template>
  <div class="sql-editor" ref="host"></div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import { EditorState } from '@codemirror/state'
import { EditorView, keymap, lineNumbers, highlightActiveLine } from '@codemirror/view'
import { defaultKeymap, history, historyKeymap } from '@codemirror/commands'
import { sql, MySQL } from '@codemirror/lang-sql'
import { autocompletion, completionKeymap } from '@codemirror/autocomplete'
import { oneDark } from '@codemirror/theme-one-dark'

const props = defineProps<{
  modelValue: string
  schema?: Record<string, string[]>
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
  (e: 'run-all'): void
  (e: 'run-selection'): void
}>()

const host = ref<HTMLElement | null>(null)
let view: EditorView | null = null

function buildState(doc: string) {
  return EditorState.create({
    doc,
    extensions: [
      lineNumbers(),
      history(),
      highlightActiveLine(),
      autocompletion(),
      sql({ dialect: MySQL, schema: props.schema, upperCaseKeywords: true }),
      oneDark,
      keymap.of([
        {
          key: 'Ctrl-Enter',
          mac: 'Cmd-Enter',
          preventDefault: true,
          run: () => {
            emit('run-all')
            return true
          }
        },
        {
          key: 'Ctrl-Shift-Enter',
          mac: 'Cmd-Shift-Enter',
          preventDefault: true,
          run: () => {
            emit('run-selection')
            return true
          }
        },
        ...defaultKeymap,
        ...historyKeymap,
        ...completionKeymap
      ]),
      EditorView.updateListener.of((u) => {
        if (u.docChanged) {
          emit('update:modelValue', u.state.doc.toString())
        }
      }),
      EditorView.theme({
        '&': { height: '100%', fontSize: '13px' },
        '.cm-scroller': { fontFamily: 'var(--mono)' }
      })
    ]
  })
}

/** Returns the currently selected text, or empty string. */
function getSelection(): string {
  if (!view) return ''
  const { from, to } = view.state.selection.main
  return view.state.sliceDoc(from, to)
}

defineExpose({ getSelection })

onMounted(() => {
  view = new EditorView({
    state: buildState(props.modelValue),
    parent: host.value!
  })
})

watch(
  () => props.modelValue,
  (val) => {
    if (view && val !== view.state.doc.toString()) {
      view.dispatch({ changes: { from: 0, to: view.state.doc.length, insert: val } })
    }
  }
)

watch(
  () => props.schema,
  () => {
    if (view) {
      const current = view.state.doc.toString()
      view.setState(buildState(current))
    }
  }
)

onBeforeUnmount(() => {
  view?.destroy()
  view = null
})
</script>

<style scoped>
.sql-editor {
  height: 100%;
  width: 100%;
  overflow: hidden;
}
</style>
