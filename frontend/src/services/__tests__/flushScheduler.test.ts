import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { FlushResult } from '@/services/offlineQueue'
import { createFlushScheduler } from '../flushScheduler'

describe('createFlushScheduler', () => {
  const makeFlush = (result: FlushResult = { succeeded: [], failed: [] }) =>
    vi.fn().mockResolvedValue(result)

  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('does not flush when device was not previously offline', async () => {
    const flush = makeFlush()
    const scheduler = createFlushScheduler(flush)

    const result = await scheduler.onConnectivityChange(true)

    expect(flush).not.toHaveBeenCalled()
    expect(result).toBeNull()
  })

  it('does not flush while device is offline', async () => {
    const flush = makeFlush()
    const scheduler = createFlushScheduler(flush)

    await scheduler.onConnectivityChange(false)
    const result = await scheduler.onConnectivityChange(false)

    expect(flush).not.toHaveBeenCalled()
    expect(result).toBeNull()
  })

  it('flushes when device transitions from offline to online', async () => {
    const flush = makeFlush({
      succeeded: [
        {
          type: 'add-set',
          sessionId: 'session-1',
          queuedOpId: 1,
          clientSetLogId: 'queued-1',
          data: { exerciseId: 'exercise-1', setNumber: 1, weight: 80, reps: 5 },
          serverSetLog: {
            id: 'log-1',
            exerciseId: 'exercise-1',
            setNumber: 1,
            weight: 80,
            reps: 5,
            loggedAt: '2026-01-01T00:00:00Z',
          },
        },
      ],
      failed: [],
    })
    const scheduler = createFlushScheduler(flush)

    await scheduler.onConnectivityChange(false)
    const result = await scheduler.onConnectivityChange(true)

    expect(flush).toHaveBeenCalledTimes(1)
    expect(result).toEqual({
      succeeded: [
        {
          type: 'add-set',
          sessionId: 'session-1',
          queuedOpId: 1,
          clientSetLogId: 'queued-1',
          data: { exerciseId: 'exercise-1', setNumber: 1, weight: 80, reps: 5 },
          serverSetLog: {
            id: 'log-1',
            exerciseId: 'exercise-1',
            setNumber: 1,
            weight: 80,
            reps: 5,
            loggedAt: '2026-01-01T00:00:00Z',
          },
        },
      ],
      failed: [],
    })
  })

  it('does not flush again on a second consecutive online event', async () => {
    const flush = makeFlush()
    const scheduler = createFlushScheduler(flush)

    await scheduler.onConnectivityChange(false)
    await scheduler.onConnectivityChange(true)  // first reconnect — flushes
    const result = await scheduler.onConnectivityChange(true) // already online — no flush

    expect(flush).toHaveBeenCalledTimes(1)
    expect(result).toBeNull()
  })

  it('flushes again after a second offline → online cycle', async () => {
    const flush = makeFlush()
    const scheduler = createFlushScheduler(flush)

    await scheduler.onConnectivityChange(false)
    await scheduler.onConnectivityChange(true) // first reconnect
    await scheduler.onConnectivityChange(false) // goes offline again
    await scheduler.onConnectivityChange(true) // second reconnect

    expect(flush).toHaveBeenCalledTimes(2)
  })
})
