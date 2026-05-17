import 'fake-indexeddb/auto'
import { beforeEach, describe, expect, it } from 'vitest'
import { db } from '@/lib/db'
import { offlineQueue } from '../offlineQueue'

describe('offlineQueue', () => {
  beforeEach(async () => {
    await db.queuedSetLogs.clear()
  })

  it('enqueues a set log', async () => {
    await offlineQueue.enqueue({ sessionId: 's1', exerciseId: 'e1', setNumber: 1, weight: 80, reps: 5 })

    const items = await offlineQueue.getAll()

    expect(items).toHaveLength(1)
    expect(items[0].exerciseId).toBe('e1')
  })

  it('clears queue after flush', async () => {
    await offlineQueue.enqueue({ sessionId: 's1', exerciseId: 'e1', setNumber: 1, weight: 80, reps: 5 })

    await offlineQueue.clear()

    const items = await offlineQueue.getAll()

    expect(items).toHaveLength(0)
  })
})
