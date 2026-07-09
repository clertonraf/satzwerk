import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { FlushResult } from '@/services/offlineQueue'
import { createFlushScheduler } from '../flushScheduler'

describe('createFlushScheduler', () => {
  const makeFlush = (result: FlushResult = { succeeded: 0, failed: 0 }) => vi.fn().mockResolvedValue(result)

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
    const flush = makeFlush({ succeeded: 2, failed: 0 })
    const scheduler = createFlushScheduler(flush)

    await scheduler.onConnectivityChange(false)
    const result = await scheduler.onConnectivityChange(true)

    expect(flush).toHaveBeenCalledTimes(1)
    expect(result).toEqual({ succeeded: 2, failed: 0 })
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
