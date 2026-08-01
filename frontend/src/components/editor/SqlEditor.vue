<template>
  <div ref="editorContainer" class="sql-editor"></div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import { EditorState, Compartment } from '@codemirror/state'
import { EditorView, keymap, lineNumbers, highlightActiveLine, highlightActiveLineGutter } from '@codemirror/view'
import { defaultKeymap, history, historyKeymap, indentWithTab } from '@codemirror/commands'
import { sql, MySQL } from '@codemirror/lang-sql'
import { autocompletion } from '@codemirror/autocomplete'
import { syntaxHighlighting, HighlightStyle } from '@codemirror/language'
import { tags as t } from '@lezer/highlight'

const props = defineProps<{
  modelValue: string
  readOnly?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
  'execute': [selectedText: string]
  'execute-all': []
}>()

const editorContainer = ref<HTMLDivElement>()
let view: EditorView | null = null
const readOnlyCompartment = new Compartment()

const darkTheme = EditorView.theme({
  '&': {
    backgroundColor: '#1e1e2e',
    color: '#e0e0e8',
    height: '100%',
    fontSize: '13px'
  },
  '.cm-content': {
    caretColor: '#5b8af5',
    fontFamily: 'var(--font-mono)',
    padding: '8px 0'
  },
  '.cm-gutters': {
    backgroundColor: '#252536',
    color: '#707088',
    border: 'none',
    borderRight: '1px solid #3a3a52'
  },
  '.cm-activeLineGutter': {
    backgroundColor: '#2d2d44'
  },
  '.cm-activeLine': {
    backgroundColor: 'rgba(91,138,245,0.08)'
  },
  '.cm-cursor': {
    borderLeftColor: '#5b8af5'
  },
  '&.cm-focused .cm-selectionBackground, .cm-selectionBackground, .cm-content ::selection': {
    backgroundColor: 'rgba(91,138,245,0.3)'
  },
  '.cm-tooltip': {
    backgroundColor: '#2d2d44',
    border: '1px solid #3a3a52',
    color: '#e0e0e8'
  },
  '.cm-tooltip-autocomplete > ul > li[aria-selected]': {
    backgroundColor: '#3d3d5c',
    color: '#fff'
  }
}, { dark: true })

const highlightStyle = HighlightStyle.define([
  { tag: t.keyword, color: '#c678dd', fontWeight: 'bold' },
  { tag: t.string, color: '#98c379' },
  { tag: t.number, color: '#d19a66' },
  { tag: t.comment, color: '#707088', fontStyle: 'italic' },
  { tag: t.variableName, color: '#e06c75' },
  { tag: t.function(t.variableName), color: '#61afef' },
  { tag: t.typeName, color: '#e5c07b' },
  { tag: t.operator, color: '#56b6c2' },
  { tag: t.punctuation, color: '#abb2bf' },
  { tag: t.definition(t.variableName), color: '#e06c75' }
])

function executeAll() {
  emit('execute-all')
  return true
}

function executeSelected() {
  if (view) {
    const { from, to, empty } = view.state.selection.main
    const selected = empty ? view.state.doc.toString() : view.state.sliceDoc(from, to)
    emit('execute', selected)
  }
  return true
}

onMounted(() => {
  if (!editorContainer.value) return

  const state = EditorState.create({
    doc: props.modelValue || '',
    extensions: [
      lineNumbers(),
      highlightActiveLine(),
      highlightActiveLineGutter(),
      history(),
      autocompletion(),
      sql({ dialect: MySQL }),
      syntaxHighlighting(highlightStyle),
      darkTheme,
      readOnlyCompartment.of(EditorState.readOnly.of(!!props.readOnly)),
      keymap.of([
        { key: 'Ctrl-Enter', run: executeAll, preventDefault: true },
        { key: 'Ctrl-Shift-Enter', run: executeSelected, preventDefault: true },
        indentWithTab,
        ...defaultKeymap,
        ...historyKeymap
      ]),
      EditorView.updateListener.of((update) => {
        if (update.docChanged) {
          emit('update:modelValue', update.state.doc.toString())
        }
      })
    ]
  })

  view = new EditorView({ state, parent: editorContainer.value })
})

watch(() => props.modelValue, (val) => {
  if (view && val !== view.state.doc.toString()) {
    view.dispatch({
      changes: { from: 0, to: view.state.doc.length, insert: val || '' }
    })
  }
})

watch(() => props.readOnly, (val) => {
  if (view) {
    view.dispatch({
      effects: readOnlyCompartment.reconfigure(EditorState.readOnly.of(!!val))
    })
  }
})

onBeforeUnmount(() => {
  view?.destroy()
})

defineExpose({
  focus: () => view?.focus(),
  getSelection: () => {
    if (!view) return ''
    const { from, to, empty } = view.state.selection.main
    return empty ? '' : view.state.sliceDoc(from, to)
  }
})
</script>

<style scoped>
.sql-editor {
  height: 100%;
  overflow: hidden;
}
.sql-editor :deep(.cm-editor) {
  height: 100%;
}
.sql-editor :deep(.cm-scroller) {
  overflow: auto;
}
</style>
