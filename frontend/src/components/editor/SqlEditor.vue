<template>
  <div class="sql-editor" ref="containerRef"></div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import { EditorView, keymap, lineNumbers, highlightActiveLine, highlightActiveLineGutter } from '@codemirror/view'
import { EditorState, Compartment } from '@codemirror/state'
import { sql, MySQL } from '@codemirror/lang-sql'
import { autocompletion } from '@codemirror/autocomplete'
import { defaultKeymap, history, historyKeymap, indentWithTab } from '@codemirror/commands'
import { bracketMatching, indentOnInput, foldGutter, foldKeymap } from '@codemirror/language'
import { searchKeymap, highlightSelectionMatches } from '@codemirror/search'
import { oneDark } from '@codemirror/theme-one-dark'

const props = withDefaults(
  defineProps<{
    modelValue: string
    placeholder?: string
    readOnly?: boolean
    connectionId?: number
  }>(),
  {
    placeholder: '输入 SQL 语句...',
    readOnly: false
  }
)

const emit = defineEmits<{
  'update:modelValue': [value: string]
  execute: []
  'execute-selection': []
}>()

const containerRef = ref<HTMLElement | null>(null)
let view: EditorView | null = null
const readOnlyCompartment = new Compartment()

const executeCmd = keymap.of([
  {
    key: 'Ctrl-Enter',
    run: () => {
      if (!view) return false
      const sel = view.state.selection.main
      if (sel && sel.from !== sel.to) {
        emit('execute-selection')
      } else {
        emit('execute')
      }
      return true
    }
  },
  {
    key: 'Ctrl-Shift-Enter',
    run: () => {
      emit('execute-selection')
      return true
    }
  }
])

function createEditor() {
  if (!containerRef.value) return

  const updateListener = EditorView.updateListener.of((update) => {
    if (update.docChanged) {
      emit('update:modelValue', update.state.doc.toString())
    }
  })

  const state = EditorState.create({
    doc: props.modelValue,
    extensions: [
      lineNumbers(),
      highlightActiveLineGutter(),
      history(),
      foldGutter(),
      indentOnInput(),
      bracketMatching(),
      autocompletion(),
      highlightActiveLine(),
      highlightSelectionMatches(),
      readOnlyCompartment.of(EditorState.readOnly.of(props.readOnly)),
      keymap.of([
        ...defaultKeymap,
        ...historyKeymap,
        ...foldKeymap,
        ...searchKeymap,
        indentWithTab
      ]),
      executeCmd,
      sql({
        dialect: MySQL,
        upperCaseKeywords: true
      }),
      oneDark,
      updateListener,
      EditorView.lineWrapping,
      EditorView.theme({
        '&': {
          height: '100%',
          fontSize: '14px'
        },
        '.cm-scroller': {
          overflow: 'auto',
          fontFamily: "'JetBrains Mono', 'Fira Code', Consolas, Monaco, monospace"
        },
        '.cm-content': {
          padding: '8px 0'
        },
        '.cm-gutters': {
          backgroundColor: '#282c34',
          borderRight: '1px solid #3a3f4b'
        }
      })
    ]
  })

  view = new EditorView({ state, parent: containerRef.value })
}

function refresh() {
  if (view) {
    view.requestMeasure()
  }
}

function setValue(value: string) {
  if (view && view.state.doc.toString() !== value) {
    view.dispatch({
      changes: { from: 0, to: view.state.doc.length, insert: value }
    })
  }
}

watch(
  () => props.modelValue,
  (val) => {
    if (view && val !== view.state.doc.toString()) {
      setValue(val)
    }
  }
)

watch(
  () => props.readOnly,
  (val) => {
    if (view) {
      view.dispatch({
        effects: readOnlyCompartment.reconfigure(EditorState.readOnly.of(val))
      })
    }
  }
)

onMounted(() => {
  createEditor()
})

onBeforeUnmount(() => {
  if (view) {
    view.destroy()
    view = null
  }
})

defineExpose({
  refresh,
  getView: () => view,
  getSelection: () => {
    if (!view) return ''
    const sel = view.state.selection.main
    return view.state.sliceDoc(sel.from, sel.to)
  }
})
</script>

<style scoped lang="scss">
.sql-editor {
  width: 100%;
  height: 100%;
  overflow: hidden;
  background: #282c34;
  border-radius: 4px;

  :deep(.cm-editor) {
    height: 100%;
  }

  :deep(.cm-focused) {
    outline: none !important;
  }
}
</style>
