import { db, type QueuedOp } from '@/lib/db'
import { sessionService } from './sessionService'
import type { AddSetLogRequest, UpdateSetLogRequest } from './sessionService'

const MAX_RETRIES = 3

type EnqueuePayload =
  | { type: 'add-set'; sessionId: string; data: AddSetLogRequest }
  | { type: 'update-set'; sessionId: string; setLogId: string; data: UpdateSetLogRequest }

export interface FlushResult {
  succeeded: number
  failed: number
}

export const offlineQueue = {
  enqueue: (payload: EnqueuePayload) =>
    db.queuedOps.add({ ...payload, queuedAt: Date.now(), retryCount: 0 } as QueuedOp),

  getAll: () => db.queuedOps.toArray(),

  clear: () => db.queuedOps.clear(),

  /**
   * Attempts to replay all queued operations against the live API.
   *
   * Guarantees:
   * - Idempotent: calling flush() when the queue is empty is a no-op.
   * - Ordered: operations are replayed in the order they were enqueued.
   * - Partial-success: each operation is attempted independently; a failure in one
   *   does not prevent the others from being retried.
   * - Retry-capped: operations that have already failed MAX_RETRIES times are
   *   permanently dropped and counted as failed in the result.
   *
   * Expected call site: createFlushScheduler (via useOfflineSync) on reconnect.
   * Do not call from multiple concurrent triggers -- parallel flush() calls will
   * race on the same queue entries.
   */
  flush: async (): Promise<FlushResult> => {
    const ops = await db.queuedOps.toArray()

    if (ops.length === 0) {
      return { succeeded: 0, failed: 0 }
    }

    // Drop ops that have exceeded the retry cap -- they are permanently failed.
    const exceededIds = ops.filter((op) => op.retryCount >= MAX_RETRIES).map((op) => op.id!)
    if (exceededIds.length > 0) {
      await Promise.all(exceededIds.map((id) => db.queuedOps.delete(id)))
    }

    const retryable = ops.filter((op) => op.retryCount < MAX_RETRIES)
    if (retryable.length === 0) {
      return { succeeded: 0, failed: exceededIds.length }
    }

    const results = await Promise.allSettled(
      retryable.map((op) => {
        if (op.type === 'add-set') {
          return sessionService.addSetLog(op.sessionId, op.data)
        }
        return sessionService.updateSetLog(op.sessionId, op.setLogId, op.data)
      }),
    )

    const succeeded = retryable.filter((_, index) => results[index].status === 'fulfilled')
    const failed = retryable.filter((_, index) => results[index].status === 'rejected')

    await Promise.all(succeeded.map((op) => db.queuedOps.delete(op.id!)))
    await Promise.all(
      failed.map((op) => db.queuedOps.update(op.id!, { retryCount: op.retryCount + 1 })),
    )

    return {
      succeeded: succeeded.length,
      failed: failed.length + exceededIds.length,
    }
  },
}
