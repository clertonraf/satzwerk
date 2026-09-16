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
      data: { exerciseId: 'e1', setNumber: 1, weight: 80, reps: 5, rir: null },
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
      data: { weight: 90, reps: 3, rir: null },
    })

    const ops = await offlineQueue.getAll()
    expect(ops).toHaveLength(1)
    expect(ops[0].type).toBe('update-set')
    if (ops[0].type === 'update-set') {
      expect(ops[0].setLogId).toBe('log-1')
    }
  })

  it('stores a delete-set op in the queue', async () => {
    await offlineQueue.enqueue({
      type: 'delete-set',
      sessionId: 's1',
      setLogId: 'log-1',
    })

    const ops = await offlineQueue.getAll()
    expect(ops).toHaveLength(1)
    expect(ops[0].type).toBe('delete-set')
    if (ops[0].type === 'delete-set') {
      expect(ops[0].setLogId).toBe('log-1')
    }
  })

  it('stamps queuedAt on each op', async () => {
    await offlineQueue.enqueue({
      type: 'add-set',
      sessionId: 's1',
      data: { exerciseId: 'e1', setNumber: 1, weight: 80, reps: 5, rir: null },
    })

    const ops = await offlineQueue.getAll()
    expect(ops[0].queuedAt).toBeGreaterThan(0)
  })
})

describe('offlineQueue.flush', () => {
  it('uses the batch endpoint for multiple queued ops in the same WorkoutSession and maps per-op results', async () => {
    const batchSetLogsSpy = vi.spyOn(sessionServiceModule.sessionService, 'batchSetLogs').mockResolvedValue({
      results: [
        {
          type: 'add-set',
          succeeded: true,
          setLog: {
            id: 'log-2',
            exerciseId: 'e1',
            setNumber: 2,
            weight: 82.5,
            reps: 5,
            rir: 1,
            loggedAt: '2026-01-01T00:00:00Z',
          },
          error: null,
        },
        {
          type: 'update-set',
          succeeded: false,
          setLog: null,
          error: 'Set log not found',
        },
        {
          type: 'delete-set',
          succeeded: true,
          setLog: null,
          error: null,
        },
      ],
    })
    const addSetLogSpy = vi.spyOn(sessionServiceModule.sessionService, 'addSetLog')
    const updateSetLogSpy = vi.spyOn(sessionServiceModule.sessionService, 'updateSetLog')
    const deleteSetLogSpy = vi.spyOn(sessionServiceModule.sessionService, 'deleteSetLog')

    await offlineQueue.enqueue({
      type: 'add-set',
      sessionId: 's1',
      clientSetLogId: 'queued-log-2',
      data: { exerciseId: 'e1', setNumber: 2, weight: 82.5, reps: 5, rir: 1 },
    })
    await offlineQueue.enqueue({
      type: 'update-set',
      sessionId: 's1',
      setLogId: 'log-1',
      data: { weight: 85, reps: 5, rir: 0 },
    })
    await offlineQueue.enqueue({
      type: 'delete-set',
      sessionId: 's1',
      setLogId: 'log-3',
    })

    const queuedOps = await offlineQueue.getAll()
    const result = await offlineQueue.flush()

    expect(batchSetLogsSpy).toHaveBeenCalledWith('s1', [
      {
        type: 'add-set',
        exerciseId: 'e1',
        setNumber: 2,
        weight: 82.5,
        reps: 5,
        rir: 1,
      },
      {
        type: 'update-set',
        setLogId: 'log-1',
        weight: 85,
        reps: 5,
        rir: 0,
      },
      {
        type: 'delete-set',
        setLogId: 'log-3',
      },
    ])
    expect(addSetLogSpy).not.toHaveBeenCalled()
    expect(updateSetLogSpy).not.toHaveBeenCalled()
    expect(deleteSetLogSpy).not.toHaveBeenCalled()
    expect(result).toEqual({
      succeeded: [
        {
          type: 'add-set',
          sessionId: 's1',
          queuedOpId: queuedOps[0].id!,
          clientSetLogId: 'queued-log-2',
          data: { exerciseId: 'e1', setNumber: 2, weight: 82.5, reps: 5, rir: 1 },
          serverSetLog: {
            id: 'log-2',
            exerciseId: 'e1',
            setNumber: 2,
            weight: 82.5,
            reps: 5,
            rir: 1,
            loggedAt: '2026-01-01T00:00:00Z',
          },
        },
        {
          type: 'delete-set',
          sessionId: 's1',
          queuedOpId: queuedOps[2].id!,
          setLogId: 'log-3',
        },
      ],
      failed: [
        {
          type: 'update-set',
          sessionId: 's1',
          queuedOpId: queuedOps[1].id!,
          setLogId: 'log-1',
          data: { weight: 85, reps: 5, rir: 0 },
          exhausted: false,
        },
      ],
    })

    const remaining = await offlineQueue.getAll()
    expect(remaining).toEqual([
      expect.objectContaining({
        id: queuedOps[1].id,
        retryCount: 1,
      }),
    ])
  })

  it('falls back to individual requests when the batch endpoint fails', async () => {
    const batchSetLogsSpy = vi
      .spyOn(sessionServiceModule.sessionService, 'batchSetLogs')
      .mockRejectedValue(new Error('batch unavailable'))
    const addSetLogSpy = vi.spyOn(sessionServiceModule.sessionService, 'addSetLog').mockResolvedValue({
      id: 'log-1',
      exerciseId: 'e1',
      setNumber: 1,
      weight: 80,
      reps: 5,
      rir: null,
      loggedAt: '2026-01-01T00:00:00Z',
    })
    const updateSetLogSpy = vi.spyOn(sessionServiceModule.sessionService, 'updateSetLog').mockResolvedValue({
      id: 'log-1',
      exerciseId: 'e1',
      setNumber: 1,
      weight: 82.5,
      reps: 5,
      rir: 1,
      loggedAt: '2026-01-01T00:00:00Z',
    })

    await offlineQueue.enqueue({
      type: 'add-set',
      sessionId: 's1',
      data: { exerciseId: 'e1', setNumber: 1, weight: 80, reps: 5, rir: null },
    })
    await offlineQueue.enqueue({
      type: 'update-set',
      sessionId: 's1',
      setLogId: 'log-1',
      data: { weight: 82.5, reps: 5, rir: 1 },
    })

    const result = await offlineQueue.flush()

    expect(batchSetLogsSpy).toHaveBeenCalledTimes(1)
    expect(addSetLogSpy).toHaveBeenCalledWith('s1', {
      exerciseId: 'e1',
      setNumber: 1,
      weight: 80,
      reps: 5,
      rir: null,
    })
    expect(updateSetLogSpy).toHaveBeenCalledWith('s1', 'log-1', {
      weight: 82.5,
      reps: 5,
      rir: 1,
    })
    expect(result.succeeded).toHaveLength(2)
    expect(result.failed).toEqual([])
    expect(await offlineQueue.getAll()).toEqual([])
  })

  it('dispatches add-set ops to sessionService.addSetLog', async () => {
    const addSetLogSpy = vi.spyOn(sessionServiceModule.sessionService, 'addSetLog').mockResolvedValue({
      id: 'log-1',
      exerciseId: 'e1',
      setNumber: 1,
      weight: 80,
      reps: 5,
      rir: null,
      loggedAt: '2026-01-01T00:00:00Z',
    })

    await offlineQueue.enqueue({
      type: 'add-set',
      sessionId: 's1',
      data: { exerciseId: 'e1', setNumber: 1, weight: 80, reps: 5, rir: null },
    })

    await offlineQueue.flush()

    expect(addSetLogSpy).toHaveBeenCalledWith('s1', { exerciseId: 'e1', setNumber: 1, weight: 80, reps: 5, rir: null })
  })

  it('dispatches update-set ops to sessionService.updateSetLog', async () => {
    const updateSetLogSpy = vi.spyOn(sessionServiceModule.sessionService, 'updateSetLog').mockResolvedValue({
      id: 'log-1',
      exerciseId: 'e1',
      setNumber: 1,
      weight: 90,
      reps: 3,
      rir: null,
      loggedAt: '2026-01-01T00:00:00Z',
    })

    await offlineQueue.enqueue({
      type: 'update-set',
      sessionId: 's1',
      setLogId: 'log-1',
      data: { weight: 90, reps: 3, rir: null },
    })

    await offlineQueue.flush()

    expect(updateSetLogSpy).toHaveBeenCalledWith('s1', 'log-1', { weight: 90, reps: 3, rir: null })
  })

  it('dispatches delete-set ops to sessionService.deleteSetLog', async () => {
    const deleteSetLogSpy = vi.spyOn(sessionServiceModule.sessionService, 'deleteSetLog').mockResolvedValue(
      undefined,
    )

    await offlineQueue.enqueue({
      type: 'delete-set',
      sessionId: 's1',
      setLogId: 'log-1',
    })

    await offlineQueue.flush()

    expect(deleteSetLogSpy).toHaveBeenCalledWith('s1', 'log-1')
  })

  it('removes successfully flushed ops from the queue', async () => {
    vi.spyOn(sessionServiceModule.sessionService, 'addSetLog').mockResolvedValue({
      id: 'log-1',
      exerciseId: 'e1',
      setNumber: 1,
      weight: 80,
      reps: 5,
      rir: null,
      loggedAt: '2026-01-01T00:00:00Z',
    })

    await offlineQueue.enqueue({
      type: 'add-set',
      sessionId: 's1',
      data: { exerciseId: 'e1', setNumber: 1, weight: 80, reps: 5, rir: null },
    })

    await offlineQueue.flush()

    const remaining = await offlineQueue.getAll()
    expect(remaining).toHaveLength(0)
  })

  it('returns zero counts when queue is empty', async () => {
    const result = await offlineQueue.flush()
    expect(result).toEqual({ succeeded: [], failed: [] })
  })

  it('returns success receipts for successful ops', async () => {
    vi.spyOn(sessionServiceModule.sessionService, 'addSetLog').mockResolvedValue({
      id: 'log-1',
      exerciseId: 'e1',
      setNumber: 1,
      weight: 80,
      reps: 5,
      rir: null,
      loggedAt: '2026-01-01T00:00:00Z',
    })

    await offlineQueue.enqueue({
      type: 'add-set',
      sessionId: 's1',
      data: { exerciseId: 'e1', setNumber: 1, weight: 80, reps: 5, rir: null },
    })

    const result = await offlineQueue.flush()
    expect(result).toEqual({
      succeeded: [
        expect.objectContaining({
          type: 'add-set',
          sessionId: 's1',
          clientSetLogId: null,
          data: { exerciseId: 'e1', setNumber: 1, weight: 80, reps: 5, rir: null },
          serverSetLog: {
            id: 'log-1',
            exerciseId: 'e1',
            setNumber: 1,
            weight: 80,
            reps: 5,
            rir: null,
            loggedAt: '2026-01-01T00:00:00Z',
          },
        }),
      ],
      failed: [],
    })
  })

  it('increments retryCount and returns failed receipts for failed ops', async () => {
    vi.spyOn(sessionServiceModule.sessionService, 'addSetLog').mockRejectedValue(new Error('Network error'))

    await offlineQueue.enqueue({
      type: 'add-set',
      sessionId: 's1',
      data: { exerciseId: 'e1', setNumber: 1, weight: 80, reps: 5, rir: null },
    })

    const result = await offlineQueue.flush()
    expect(result).toEqual({
      succeeded: [],
      failed: [
        expect.objectContaining({
          type: 'add-set',
          sessionId: 's1',
          clientSetLogId: null,
          data: { exerciseId: 'e1', setNumber: 1, weight: 80, reps: 5, rir: null },
          exhausted: false,
        }),
      ],
    })

    const ops = await offlineQueue.getAll()
    expect(ops).toHaveLength(1)
    expect(ops[0].retryCount).toBe(1)
  })

  it('deletes ops that have reached MAX_RETRIES and returns exhausted failure receipts', async () => {
    vi.spyOn(sessionServiceModule.sessionService, 'addSetLog').mockRejectedValue(new Error('Network error'))

    // Insert an op that is already at the retry cap (retryCount >= 3).
    await db.queuedOps.add({
      type: 'add-set',
      sessionId: 's1',
      data: { exerciseId: 'e1', setNumber: 1, weight: 80, reps: 5, rir: null },
      queuedAt: Date.now(),
      retryCount: 3,
    } as Parameters<typeof db.queuedOps.add>[0])

    const result = await offlineQueue.flush()

    // The capped op should be deleted and counted as failed.
    expect(result).toEqual({
      succeeded: [],
      failed: [
        expect.objectContaining({
          type: 'add-set',
          sessionId: 's1',
          clientSetLogId: null,
          data: { exerciseId: 'e1', setNumber: 1, weight: 80, reps: 5, rir: null },
          exhausted: true,
        }),
      ],
    })
    const remaining = await offlineQueue.getAll()
    expect(remaining).toHaveLength(0)
  })
})

describe('offlineQueue.clear', () => {
  it('empties the queue', async () => {
    await offlineQueue.enqueue({
      type: 'add-set',
      sessionId: 's1',
      data: { exerciseId: 'e1', setNumber: 1, weight: 80, reps: 5, rir: null },
    })

    await offlineQueue.clear()

    const ops = await offlineQueue.getAll()
    expect(ops).toHaveLength(0)
  })
})
