import Dexie, { type Table } from 'dexie'

export interface QueuedSetLog {
  id?: number
  sessionId: string
  exerciseId: string
  setNumber: number
  weight: number
  reps: number
  queuedAt: number
}

class SatzwerkDb extends Dexie {
  queuedSetLogs!: Table<QueuedSetLog>

  constructor() {
    super('satzwerk')

    this.version(1).stores({
      queuedSetLogs: '++id, sessionId, queuedAt',
    })
  }
}

export const db = new SatzwerkDb()
