import 'fake-indexeddb/auto'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { db } from '@/lib/db'
import * as sessionServiceModule from '@/services/sessionService'
import { offlineQueue } from '../offlineQueue'

beforeEach(async () => {
  await db.queuedOps.clear()
  vi.restoreAllMocks()
})

describe('offlineQueue.enqueue', () => {
  it('stores an add-set op in the queue', async () => {
    await offlineQueue.enqueue({
      type: 'add-set',
      sessionId: 's1',
      data: { exerciseId: 'e1', setNumber: 1, weight: 80, reps: 5 },
    })

    const ops = await offlineQueue.getAll()
    expect(ops).toHaveLength(1)
    expect(ops[0].type).toBe('add-set')
    expect(ops[0].sessionId).toBe('s1')
  })

  it('stores an update-set op in the queue', async () => {
    await offlineQueue.enqueue({
      type: 'update-set',
      sessionId: 's1',
      setLogId: 'log-1',
      data: { weight: 90, reps: 3 },
    })

    const ops = await offlineQueue.getAll()
    expect(ops).toHaveLength(1)
    expect(ops[0].type).toBe('update-set')
    if (ops[0].type === 'update-set') {
      expect(ops[0].setLogId).toBe('log-1')
    }
  })

  it('stamps queuedAt on each op', async () => {
    await offlineQueue.enqueue({
      type: 'add-set',
      sessionId: 's1',
      data: { exerciseId: 'e1', setNumber: 1, weight: 80, reps: 5 },
    })

    const ops = await offlineQueue.getAll()
    expect(ops[0].queuedAt).toBeGreaterThan(0)
  })
})

describe('offlineQueue.flush', () => {
  it('dispatches add-set ops to sessionService.addSetLog', async () => {
    const addSetLogSpy = vi.spyOn(sessionServiceModule.sessionService, 'addSetLog').mockResolvedValue({
      id: 'log-1',
      exerciseId: 'e1',
      setNumber: 1,
      weight: 80,
      reps: 5,
      loggedAt: '2026-01-01T00:00:00Z',
    })

    await offlineQueue.enqueue({
      type: 'add-set',
      sessionId: 's1',
      data: { exerciseId: 'e1', setNumber: 1, weight: 80, reps: 5 },
    })

    await offlineQueue.flush()

    expect(addSetLogSpy).toHaveBeenCalledWith('s1', { exerciseId: 'e1', setNumber: 1, weight: 80, reps: 5 })
  })

  it('dispatches update-set ops to sessionService.updateSetLog', async () => {
    const updateSetLogSpy = vi.spyOn(sessionServiceModule.sessionService, 'updateSetLog').mockResolvedValue({
      id: 'log-1',
      exerciseId: 'e1',
      setNumber: 1,
      weight: 90,
      reps: 3,
      loggedAt: '2026-01-01T00:00:00Z',
    })

    await offlineQueue.enqueue({
      type: 'update-set',
      sessionId: 's1',
      setLogId: 'log-1',
      data: { weight: 90, reps: 3 },
    })

    await offlineQueue.flush()

    expect(updateSetLogSpy).toHaveBeenCalledWith('s1', 'log-1', { weight: 90, reps: 3 })
  })

  it('removes successfully flushed ops from the queue', async () => {
    vi.spyOn(sessionServiceModule.sessionService, 'addSetLog').mockResolvedValue({
      id: 'log-1',
      exerciseId: 'e1',
      setNumber: 1,
      weight: 80,
      reps: 5,
      loggedAt: '2026-01-01T00:00:00Z',
    })

    await offlineQueue.enqueue({
      type: 'add-set',
      sessionId: 's1',
      data: { exerciseId: 'e1', setNumber: 1, weight: 80, reps: 5 },
    })

    await offlineQueue.flush()

    const remaining = await offlineQueue.getAll()
    expect(remaining).toHaveLength(0)
  })

  it('returns an empty array when queue is empty', async () => {
    const result = await offlineQueue.flush()
    expect(result).toEqual([])
  })
})

describe('offlineQueue.clear', () => {
  it('empties the queue', async () => {
    await offlineQueue.enqueue({
      type: 'add-set',
      sessionId: 's1',
      data: { exerciseId: 'e1', setNumber: 1, weight: 80, reps: 5 },
    })

    await offlineQueue.clear()

    const ops = await offlineQueue.getAll()
    expect(ops).toHaveLength(0)
  })
})

