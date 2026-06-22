import { db, type QueuedOp } from '@/lib/db'
import { sessionService } from './sessionService'
import type { AddSetLogRequest, UpdateSetLogRequest } from './sessionService'

type EnqueuePayload =
  | { type: 'add-set'; sessionId: string; data: AddSetLogRequest }
  | { type: 'update-set'; sessionId: string; setLogId: string; data: UpdateSetLogRequest }

export const offlineQueue = {
  enqueue: (payload: EnqueuePayload) => db.queuedOps.add({ ...payload, queuedAt: Date.now() } as QueuedOp),

  getAll: () => db.queuedOps.toArray(),

  clear: () => db.queuedOps.clear(),

  flush: async () => {
    const ops = await db.queuedOps.toArray()

    if (ops.length === 0) {
      return []
    }

    const results = await Promise.allSettled(
      ops.map((op) => {
        if (op.type === 'add-set') {
          return sessionService.addSetLog(op.sessionId, op.data)
        }
        return sessionService.updateSetLog(op.sessionId, op.setLogId, op.data)
      }),
    )

    const succeeded = ops.filter((_, index) => results[index].status === 'fulfilled')
    await Promise.all(succeeded.map((op) => db.queuedOps.delete(op.id!)))

    return results
  },
}

