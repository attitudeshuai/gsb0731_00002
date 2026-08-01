import { ref } from 'vue'
import { exportApi, type ExportProgress } from '@/api'

/**
 * Drives an async export job: start → poll progress → auto-download on completion,
 * with cancel support. Polling stops as soon as the job leaves the RUNNING state.
 */
export function useExportJob(connectionId: () => number) {
  const active = ref(false)
  const cancelling = ref(false)
  const progress = ref<ExportProgress | null>(null)
  const label = ref('')

  let timer: number | null = null

  function stopPolling() {
    if (timer !== null) {
      window.clearInterval(timer)
      timer = null
    }
  }

  async function poll(jobId: string) {
    try {
      const p = await exportApi.status(connectionId(), jobId)
      progress.value = p
      if (p.status !== 'RUNNING') {
        stopPolling()
        cancelling.value = false
        if (p.status === 'COMPLETED') {
          triggerDownload(jobId, p.filename)
        }
      }
    } catch (e) {
      stopPolling()
    }
  }

  function triggerDownload(jobId: string, filename: string) {
    const a = document.createElement('a')
    a.href = exportApi.downloadUrl(connectionId(), jobId)
    a.download = filename
    document.body.appendChild(a)
    a.click()
    a.remove()
  }

  async function startTable(database: string, table: string, format: string) {
    label.value = `${database}.${table}`
    await begin(() => exportApi.startTable(connectionId(), { database, table, format }))
  }

  async function startQuery(sql: string, format: string) {
    label.value = 'query result'
    await begin(() => exportApi.startQuery(connectionId(), { sql, format }))
  }

  async function begin(starter: () => Promise<ExportProgress>) {
    active.value = true
    cancelling.value = false
    progress.value = null
    const p = await starter()
    progress.value = p
    if (p.status === 'RUNNING') {
      timer = window.setInterval(() => poll(p.jobId), 500)
    } else if (p.status === 'COMPLETED') {
      triggerDownload(p.jobId, p.filename)
    }
  }

  async function cancel() {
    if (!progress.value) return
    cancelling.value = true
    try {
      await exportApi.cancel(connectionId(), progress.value.jobId)
    } catch {
      cancelling.value = false
    }
  }

  function close() {
    stopPolling()
    active.value = false
    cancelling.value = false
    progress.value = null
  }

  return { active, cancelling, progress, label, startTable, startQuery, cancel, close }
}
