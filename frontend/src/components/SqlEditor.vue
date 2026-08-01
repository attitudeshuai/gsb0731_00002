<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { EditorState } from '@codemirror/state'
import {
  EditorView,
  keymap,
  lineNumbers,
  highlightActiveLine,
  highlightActiveLineGutter,
  drawSelection,
  placeholder,
} from '@codemirror/view'
import { defaultKeymap, history, historyKeymap, indentWithTab } from '@codemirror/commands'
import { sql, MySQL } from '@codemirror/lang-sql'
import {
  autocompletion,
  completionKeymap,
  closeBrackets,
  closeBracketsKeymap,
  type Completion,
  type CompletionContext,
  type CompletionResult,
} from '@codemirror/autocomplete'
import { searchKeymap, highlightSelectionMatches } from '@codemirror/search'
import { bracketMatching, indentOnInput } from '@codemirror/language'
import { oneDark } from '@codemirror/theme-one-dark'

const props = withDefaults(
  defineProps<{
    modelValue: string
    /** 补全用：表名 */
    tables?: string[]
    /** 补全用：列名 */
    columns?: string[]
    placeholder?: string
  }>(),
  {
    tables: () => [],
    columns: () => [],
    placeholder: '输入 SQL，Ctrl+Enter 执行，Ctrl+Shift+Enter 执行选中',
  },
)

const emit = defineEmits<{
  (e: 'update:modelValue', v: string): void
  (e: 'execute', mode: 'all' | 'selection'): void
}>()

const KEYWORDS = [
  'SELECT', 'FROM', 'WHERE', 'INSERT', 'INTO', 'VALUES', 'UPDATE', 'SET', 'DELETE',
  'CREATE', 'TABLE', 'ALTER', 'DROP', 'TRUNCATE', 'INDEX', 'VIEW', 'DATABASE',
  'JOIN', 'LEFT', 'RIGHT', 'INNER', 'OUTER', 'CROSS', 'ON', 'AS', 'DISTINCT',
  'GROUP', 'BY', 'ORDER', 'HAVING', 'LIMIT', 'OFFSET', 'UNION', 'ALL',
  'AND', 'OR', 'NOT', 'NULL', 'IS', 'IN', 'LIKE', 'BETWEEN', 'EXISTS',
  'CASE', 'WHEN', 'THEN', 'ELSE', 'END', 'ASC', 'DESC',
  'COUNT', 'SUM', 'AVG', 'MIN', 'MAX', 'IFNULL', 'COALESCE', 'NOW', 'CONCAT',
  'PRIMARY', 'KEY', 'FOREIGN', 'REFERENCES', 'DEFAULT', 'AUTO_INCREMENT',
  'SHOW', 'DESCRIBE', 'EXPLAIN', 'USE', 'GRANT', 'COMMIT', 'ROLLBACK', 'BEGIN',
]

const editorEl = ref<HTMLElement | null>(null)
let view: EditorView | null = null
let applyingExternal = false

function completionSource(ctx: CompletionContext): CompletionResult | null {
  const word = ctx.matchBefore(/[\w$]*/)
  if (!word || (word.from === word.to && !ctx.explicit)) return null
  const options: Completion[] = []
  for (const t of props.tables) {
    options.push({ label: t, type: 'class', detail: 'table', boost: 2 })
  }
  for (const c of props.columns) {
    options.push({ label: c, type: 'property', detail: 'column', boost: 1 })
  }
  for (const k of KEYWORDS) {
    options.push({ label: k, type: 'keyword' })
  }
  return {
    from: word ? word.from : ctx.pos,
    options,
    validFor: /^[\w$]*$/,
  }
}

const editorTheme = EditorView.theme(
  {
    '&': {
      height: '100%',
      fontSize: '13px',
      backgroundColor: 'var(--bg-0)',
    },
    '.cm-scroller': {
      overflow: 'auto',
      fontFamily: 'var(--font-mono)',
      lineHeight: '1.55',
    },
    '.cm-gutters': {
      backgroundColor: 'var(--bg-0)',
      color: '#4b5263',
      border: 'none',
      borderRight: '1px solid var(--border-soft)',
    },
    '.cm-activeLine': {
      backgroundColor: 'rgba(79, 140, 255, 0.07)',
    },
    '.cm-activeLineGutter': {
      backgroundColor: 'rgba(79, 140, 255, 0.10)',
    },
    '&.cm-focused': {
      outline: 'none',
    },
    '.cm-selectionBackground, &.cm-focused .cm-selectionBackground': {
      backgroundColor: 'rgba(79, 140, 255, 0.25) !important',
    },
    '.cm-tooltip': {
      backgroundColor: 'var(--bg-2)',
      border: '1px solid var(--border)',
      color: 'var(--text-0)',
    },
    '.cm-tooltip.cm-tooltip-autocomplete > ul > li[aria-selected]': {
      backgroundColor: 'var(--accent)',
      color: '#fff',
    },
  },
  { dark: true },
)

onMounted(() => {
  const executeKeymap = keymap.of([
    {
      key: 'Ctrl-Enter',
      run: () => {
        emit('execute', 'all')
        return true
      },
    },
    {
      key: 'Ctrl-Shift-Enter',
      run: () => {
        emit('execute', 'selection')
        return true
      },
    },
  ])

  const state = EditorState.create({
    doc: props.modelValue,
    extensions: [
      lineNumbers(),
      highlightActiveLineGutter(),
      highlightActiveLine(),
      drawSelection(),
      history(),
      indentOnInput(),
      bracketMatching(),
      closeBrackets(),
      EditorView.lineWrapping,
      placeholder(props.placeholder),
      sql({ dialect: MySQL, upperCaseKeywords: true }),
      autocompletion({ override: [completionSource], activateOnTyping: true }),
      highlightSelectionMatches(),
      executeKeymap,
      keymap.of([
        ...closeBracketsKeymap,
        ...defaultKeymap,
        ...historyKeymap,
        ...searchKeymap,
        ...completionKeymap,
        indentWithTab,
      ]),
      oneDark,
      editorTheme,
      EditorView.updateListener.of((update) => {
        if (update.docChanged && !applyingExternal) {
          emit('update:modelValue', update.state.doc.toString())
        }
      }),
    ],
  })

  view = new EditorView({ state, parent: editorEl.value! })
})

watch(
  () => props.modelValue,
  (val) => {
    if (!view) return
    const current = view.state.doc.toString()
    if (val !== current) {
      applyingExternal = true
      view.dispatch({
        changes: { from: 0, to: current.length, insert: val },
      })
      applyingExternal = false
    }
  },
)

onBeforeUnmount(() => {
  view?.destroy()
  view = null
})

function getSelectionText(): string {
  if (!view) return ''
  const { from, to } = view.state.selection.main
  return view.state.sliceDoc(from, to)
}

function focus() {
  view?.focus()
}

defineExpose({ getSelectionText, focus })
</script>

<template>
  <div ref="editorEl" class="sql-editor"></div>
</template>

<style scoped>
.sql-editor {
  height: 100%;
  width: 100%;
  overflow: hidden;
  background: var(--bg-0);
}
</style>
