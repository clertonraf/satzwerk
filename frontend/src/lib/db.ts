import Dexie, { type Table } from 'dexie'
import type { AddSetLogRequest, UpdateSetLogRequest } from '@/services/sessionService'

export interface QueuedSetLog {
  id?: number
  sessionId: string
  exerciseId: string
  setNumber: number
  weight: number
  reps: number
  queuedAt: number
}

type QueuedOpBase = { id?: number; sessionId: string; queuedAt: number; retryCount: number }

export type QueuedOp =
  | (QueuedOpBase & { type: 'add-set'; data: AddSetLogRequest })
  | (QueuedOpBase & { type: 'update-set'; setLogId: string; data: UpdateSetLogRequest })

class SatzwerkDb extends Dexie {
  queuedSetLogs!: Table<QueuedSetLog>
  queuedOps!: Table<QueuedOp>

  constructor() {
    super('satzwerk')

    this.version(1).stores({
      queuedSetLogs: '++id, sessionId, queuedAt',
    })

    this.version(2).stores({
      queuedSetLogs: null,
      queuedOps: '++id, type, sessionId, queuedAt',
    })

    this.version(3).stores({
      queuedOps: '++id, type, sessionId, queuedAt',
    }).upgrade((tx) => {
      return tx.table<QueuedOp>('queuedOps').toCollection().modify((op) => {
        if (op.retryCount === undefined) {
          op.retryCount = 0
        }
      })
    })
  }
}

export const db = new SatzwerkDb()
