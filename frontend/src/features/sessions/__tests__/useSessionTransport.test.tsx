import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, renderHook } from '@testing-library/react'
import type { ReactNode } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { offlineQueue } from '@/services/offlineQueue'
import { sessionService } from '@/services/sessionService'
import {
  createOnlineSetLogTransport,
  createQueuedSetLogTransport,
  useSessionTransport,
} from '../useSessionTransport'

vi.mock('@/services/sessionService', () => ({
  sessionService: {
    addSetLog: vi.fn(),
    updateSetLog: vi.fn(),
    deleteSetLog: vi.fn(),
  },
}))

vi.mock('@/services/offlineQueue', () => ({
  offlineQueue: {
    enqueue: vi.fn(),
    flush: vi.fn(),
    getAll: vi.fn(),
    clear: vi.fn(),
  },
}))

let mockIsOnline = true
vi.mock('@/hooks/useOnlineStatus', () => ({
  useOnlineStatus: () => mockIsOnline,
}))

const addSetLogRequest = {
  exerciseId: 'exercise-1',
  setNumber: 1,
  weight: 80,
  reps: 5,
}

const submittedSetLog = {
  id: 'setlog-1',
  exerciseId: 'exercise-1',
  setNumber: 1,
  weight: 80,
  reps: 5,
  loggedAt: '2026-01-01T00:00:00Z',
}

function makeWrapper(queryClient: QueryClient) {
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  )
}

describe('createOnlineSetLogTransport', () => {
  beforeEach(() => {
    vi.mocked(sessionService.addSetLog).mockReset()
    vi.mocked(sessionService.updateSetLog).mockReset()
    vi.mocked(sessionService.deleteSetLog).mockReset()
  })

  it('submits addSetLog through the online adapter contract', async () => {
    vi.mocked(sessionService.addSetLog).mockResolvedValue(submittedSetLog)

    const transport = createOnlineSetLogTransport({
      addSetLog: sessionService.addSetLog,
      updateSetLog: sessionService.updateSetLog,
      deleteSetLog: sessionService.deleteSetLog,
    })

    await expect(transport.addSetLog('session-1', addSetLogRequest)).resolves.toEqual({
      ...submittedSetLog,
      pending: false,
    })

    expect(sessionService.addSetLog).toHaveBeenCalledWith('session-1', addSetLogRequest)
  })

  it('submits updateSetLog through the online adapter contract', async () => {
    vi.mocked(sessionService.updateSetLog).mockResolvedValue({
      ...submittedSetLog,
      weight: 82.5,
      reps: 4,
    })

    const transport = createOnlineSetLogTransport({
      addSetLog: sessionService.addSetLog,
      updateSetLog: sessionService.updateSetLog,
      deleteSetLog: sessionService.deleteSetLog,
    })

    await expect(
      transport.updateSetLog('session-1', 'setlog-1', { weight: 82.5, reps: 4 }),
    ).resolves.toEqual({
      weight: 82.5,
      reps: 4,
    })

    expect(sessionService.updateSetLog).toHaveBeenCalledWith('session-1', 'setlog-1', {
      weight: 82.5,
      reps: 4,
    })
  })

  it('submits deleteSetLog through the online adapter contract', async () => {
    vi.mocked(sessionService.deleteSetLog).mockResolvedValue(undefined)

    const transport = createOnlineSetLogTransport({
      addSetLog: sessionService.addSetLog,
      updateSetLog: sessionService.updateSetLog,
      deleteSetLog: sessionService.deleteSetLog,
    })

    await expect(transport.deleteSetLog('session-1', 'setlog-1')).resolves.toBeUndefined()

    expect(sessionService.deleteSetLog).toHaveBeenCalledWith('session-1', 'setlog-1')
  })
})

describe('createQueuedSetLogTransport', () => {
  beforeEach(() => {
    vi.mocked(offlineQueue.enqueue).mockReset()
  })

  it('queues addSetLog through the queued adapter contract', async () => {
    vi.mocked(offlineQueue.enqueue).mockResolvedValue(1)

    const transport = createQueuedSetLogTransport({
      enqueue: offlineQueue.enqueue,
    })

    const queued = await transport.addSetLog('session-1', addSetLogRequest)

    expect(offlineQueue.enqueue).toHaveBeenCalledWith({
      type: 'add-set',
      sessionId: 'session-1',
      data: addSetLogRequest,
    })
    expect(queued).toMatchObject({
      ...addSetLogRequest,
      pending: true,
    })
    expect(queued.id).toMatch(/^queued-session-1-exercise-1-1-\d+$/)
  })

  it('queues updateSetLog through the queued adapter contract', async () => {
    vi.mocked(offlineQueue.enqueue).mockResolvedValue(1)

    const transport = createQueuedSetLogTransport({
      enqueue: offlineQueue.enqueue,
    })

    await expect(
      transport.updateSetLog('session-1', 'setlog-1', { weight: 82.5, reps: 4 }),
    ).resolves.toEqual({
      weight: 82.5,
      reps: 4,
    })

    expect(offlineQueue.enqueue).toHaveBeenCalledWith({
      type: 'update-set',
      sessionId: 'session-1',
      setLogId: 'setlog-1',
      data: { weight: 82.5, reps: 4 },
    })
  })

  it('queues deleteSetLog through the queued adapter contract', async () => {
    vi.mocked(offlineQueue.enqueue).mockResolvedValue(1)

    const transport = createQueuedSetLogTransport({
      enqueue: offlineQueue.enqueue,
    })

    await expect(transport.deleteSetLog('session-1', 'setlog-1')).resolves.toBeUndefined()

    expect(offlineQueue.enqueue).toHaveBeenCalledWith({
      type: 'delete-set',
      sessionId: 'session-1',
      setLogId: 'setlog-1',
    })
  })
})

describe('useSessionTransport', () => {
  let queryClient: QueryClient

  beforeEach(() => {
    vi.mocked(offlineQueue.enqueue).mockReset()
    vi.mocked(sessionService.addSetLog).mockReset()
    vi.mocked(sessionService.updateSetLog).mockReset()
    vi.mocked(sessionService.deleteSetLog).mockReset()
    queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  })

  it('uses the same caller contract to submit addSetLog online', async () => {
    mockIsOnline = true
    vi.mocked(sessionService.addSetLog).mockResolvedValue(submittedSetLog)

    const { result } = renderHook(() => useSessionTransport(), {
      wrapper: makeWrapper(queryClient),
    })

    await expect(
      act(async () => result.current.transport.addSetLog('session-1', addSetLogRequest)),
    ).resolves.toEqual({
      ...submittedSetLog,
      pending: false,
    })

    expect(sessionService.addSetLog).toHaveBeenCalledWith('session-1', addSetLogRequest)
    expect(offlineQueue.enqueue).not.toHaveBeenCalled()
  })

  it('uses the same caller contract to submit addSetLog offline', async () => {
    mockIsOnline = false
    vi.mocked(offlineQueue.enqueue).mockResolvedValue(1)

    const { result } = renderHook(() => useSessionTransport(), {
      wrapper: makeWrapper(queryClient),
    })

    let queued: unknown

    await act(async () => {
      queued = await result.current.transport.addSetLog('session-1', addSetLogRequest)
    })

    expect(offlineQueue.enqueue).toHaveBeenCalledWith({
      type: 'add-set',
      sessionId: 'session-1',
      data: addSetLogRequest,
    })
    expect(sessionService.addSetLog).not.toHaveBeenCalled()
    expect(queued).toMatchObject({
      ...addSetLogRequest,
      pending: true,
    })
  })

  it('uses the same caller contract to update a SetLog online', async () => {
    mockIsOnline = true
    vi.mocked(sessionService.updateSetLog).mockResolvedValue({
      ...submittedSetLog,
      weight: 82.5,
      reps: 4,
    })

    const { result } = renderHook(() => useSessionTransport(), {
      wrapper: makeWrapper(queryClient),
    })

    let updated: unknown

    await act(async () => {
      updated = await result.current.transport.updateSetLog('session-1', 'setlog-1', {
        weight: 82.5,
        reps: 4,
      })
    })

    expect(sessionService.updateSetLog).toHaveBeenCalledWith('session-1', 'setlog-1', {
      weight: 82.5,
      reps: 4,
    })
    expect(offlineQueue.enqueue).not.toHaveBeenCalled()
    expect(updated).toEqual({
      weight: 82.5,
      reps: 4,
    })
  })

  it('uses the same caller contract to update a SetLog offline', async () => {
    mockIsOnline = false
    vi.mocked(offlineQueue.enqueue).mockResolvedValue(1)

    const { result } = renderHook(() => useSessionTransport(), {
      wrapper: makeWrapper(queryClient),
    })

    let updated: unknown

    await act(async () => {
      updated = await result.current.transport.updateSetLog('session-1', 'setlog-1', {
        weight: 82.5,
        reps: 4,
      })
    })

    expect(offlineQueue.enqueue).toHaveBeenCalledWith({
      type: 'update-set',
      sessionId: 'session-1',
      setLogId: 'setlog-1',
      data: { weight: 82.5, reps: 4 },
    })
    expect(sessionService.updateSetLog).not.toHaveBeenCalled()
    expect(updated).toEqual({
      weight: 82.5,
      reps: 4,
    })
  })

  it('uses the same caller contract to delete a SetLog online', async () => {
    mockIsOnline = true
    vi.mocked(sessionService.deleteSetLog).mockResolvedValue(undefined)

    const { result } = renderHook(() => useSessionTransport(), {
      wrapper: makeWrapper(queryClient),
    })

    await act(async () => {
      await result.current.transport.deleteSetLog('session-1', 'setlog-1')
    })

    expect(sessionService.deleteSetLog).toHaveBeenCalledWith('session-1', 'setlog-1')
    expect(offlineQueue.enqueue).not.toHaveBeenCalled()
  })

  it('uses the same caller contract to delete a SetLog offline', async () => {
    mockIsOnline = false
    vi.mocked(offlineQueue.enqueue).mockResolvedValue(1)

    const { result } = renderHook(() => useSessionTransport(), {
      wrapper: makeWrapper(queryClient),
    })

    await act(async () => {
      await result.current.transport.deleteSetLog('session-1', 'setlog-1')
    })

    expect(offlineQueue.enqueue).toHaveBeenCalledWith({
      type: 'delete-set',
      sessionId: 'session-1',
      setLogId: 'setlog-1',
    })
    expect(sessionService.deleteSetLog).not.toHaveBeenCalled()
  })
})
