import { defineStore } from 'pinia'
import {
  cancelExportTask,
  createExportTask,
  downloadExportTask,
  getExportTask,
} from '@/api/metadata'
import { toast } from '@/ui/toast'
import type { ExportFormat, ExportTask, FilterCondition } from '@/types'

const POLL_INTERVAL = 800

/** taskId -> 轮询定时器（非响应式，模块级管理） */
const pollTimers = new Map<string, number>()

function isTerminal(status: ExportTask['status']) {
  return status === 'COMPLETED' || status === 'FAILED' || status === 'CANCELLED'
}

export const useExportTasksStore = defineStore('exportTasks', {
  state: () => ({
    /** 近期导出任务，key 为 taskId */
    tasks: new Map<string, ExportTask>(),
    /** 展示顺序（新任务在前） */
    order: [] as string[],
    /** 进度浮层是否可见（关闭浮层不影响任务运行） */
    overlayVisible: false,
  }),

  actions: {
    /** 创建导出任务并纳入跟踪（TableDataTab / ConnectionTree 统一入口） */
    async startExport(
      connId: number,
      db: string,
      table: string,
      format: ExportFormat,
      filters?: FilterCondition[],
    ) {
      const task = await createExportTask(connId, db, table, format, filters)
      this.track(task)
      toast.info(`已开始导出 ${task.tableName}.${task.format}`)
    },

    track(task: ExportTask) {
      this.tasks.set(task.taskId, task)
      this.order = [task.taskId, ...this.order.filter((id) => id !== task.taskId)]
      this.overlayVisible = true
      if (isTerminal(task.status)) {
        void this.handleTerminal(task)
      } else {
        this.startPolling(task.taskId)
      }
    },

    startPolling(taskId: string) {
      this.stopPolling(taskId)
      pollTimers.set(
        taskId,
        window.setInterval(() => void this.refresh(taskId), POLL_INTERVAL),
      )
    },

    stopPolling(taskId: string) {
      const timer = pollTimers.get(taskId)
      if (timer != null) {
        window.clearInterval(timer)
        pollTimers.delete(taskId)
      }
    },

    async refresh(taskId: string) {
      try {
        const task = await getExportTask(taskId)
        this.tasks.set(taskId, task)
        if (isTerminal(task.status)) {
          this.stopPolling(taskId)
          await this.handleTerminal(task)
        }
      } catch {
        /* 单次轮询失败保留任务，等待下一次轮询 */
      }
    },

    /** 终态处理：toast 提示，COMPLETED 自动触发下载 */
    async handleTerminal(task: ExportTask) {
      if (task.status === 'COMPLETED') {
        toast.success(`导出完成：${task.tableName}.${task.format}（${task.rowsExported} 行）`)
        try {
          await downloadExportTask(task.taskId, `${task.tableName}.${task.format}`)
        } catch {
          /* 拦截器已提示 */
        }
      } else if (task.status === 'FAILED') {
        toast.error(`导出失败：${task.errorMessage || '未知错误'}`)
      } else {
        toast.info(`已取消导出 ${task.tableName}`)
      }
    },

    /** 取消运行中的任务 */
    async cancel(taskId: string) {
      const task = this.tasks.get(taskId)
      if (!task || isTerminal(task.status)) return
      await cancelExportTask(taskId)
      await this.refresh(taskId)
    },

    /** 手动重试下载 */
    async download(taskId: string) {
      const task = this.tasks.get(taskId)
      if (!task || task.status !== 'COMPLETED') return
      try {
        await downloadExportTask(taskId, `${task.tableName}.${task.format}`)
      } catch {
        /* 拦截器已提示 */
      }
    },

    /** 从列表移除（仅终态任务可移除） */
    dismiss(taskId: string) {
      const task = this.tasks.get(taskId)
      if (!task || !isTerminal(task.status)) return
      this.stopPolling(taskId)
      this.tasks.delete(taskId)
      this.order = this.order.filter((id) => id !== taskId)
      if (this.order.length === 0) this.overlayVisible = false
    },

    hideOverlay() {
      this.overlayVisible = false
    },

    showOverlay() {
      if (this.order.length > 0) this.overlayVisible = true
    },
  },
})
