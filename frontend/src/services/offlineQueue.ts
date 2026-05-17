import { db, type QueuedSetLog } from '@/lib/db'
import { sessionService } from './sessionService'

type EnqueuePayload = Omit<QueuedSetLog, 'id' | 'queuedAt'>

export const offlineQueue = {
  enqueue: (payload: EnqueuePayload) => db.queuedSetLogs.add({ ...payload, queuedAt: Date.now() }),

  getAll: () => db.queuedSetLogs.toArray(),

  clear: () => db.queuedSetLogs.clear(),

  flush: async () => {
    const items = await db.queuedSetLogs.toArray()

    if (items.length === 0) {
      return []
    }

    const results = await Promise.allSettled(
      items.map((item) =>
        sessionService.addSetLog(item.sessionId, {
          exerciseId: item.exerciseId,
          setNumber: item.setNumber,
          weight: item.weight,
          reps: item.reps,
        })
      )
    )

    const succeeded = items.filter((_, index) => results[index].status === 'fulfilled')
    await Promise.all(succeeded.map((item) => db.queuedSetLogs.delete(item.id!)))

    return results
  },
}
