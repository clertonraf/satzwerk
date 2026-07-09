import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, renderHook } from '@testing-library/react'
import type { ReactNode } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { offlineQueue } from '@/services/offlineQueue'
import { sessionService } from '@/services/sessionService'
import { useSessionTransport } from '../useSessionTransport'

vi.mock('@/services/sessionService', () => ({
  sessionService: {
    addSetLog: vi.fn(),
    updateSetLog: vi.fn(),
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

function makeWrapper(queryClient: QueryClient) {
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  )
}

describe('useSessionTransport — offline updateSetLog', () => {
  let queryClient: QueryClient

  beforeEach(() => {
    vi.mocked(offlineQueue.enqueue).mockReset()
    vi.mocked(sessionService.updateSetLog).mockReset()
    mockIsOnline = false
    queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  })

  it('returns only weight and reps when offline — no exerciseId or setNumber', async () => {
    vi.mocked(offlineQueue.enqueue).mockResolvedValue(1)

    const { result } = renderHook(() => useSessionTransport(), {
      wrapper: makeWrapper(queryClient),
    })

    let update: { weight: number; reps: number } | undefined
    await act(async () => {
      update = await result.current.transport.updateSetLog('session-1', 'setlog-42', {
        weight: 75,
        reps: 8,
      })
    })

    expect(update).toEqual({ weight: 75, reps: 8 })
  })

  it('enqueues an update-set op when offline', async () => {
    vi.mocked(offlineQueue.enqueue).mockResolvedValue(1)

    const { result } = renderHook(() => useSessionTransport(), {
      wrapper: makeWrapper(queryClient),
    })

    await act(async () => {
      await result.current.transport.updateSetLog('session-1', 'setlog-42', {
        weight: 75,
        reps: 8,
      })
    })

    expect(offlineQueue.enqueue).toHaveBeenCalledWith({
      type: 'update-set',
      sessionId: 'session-1',
      setLogId: 'setlog-42',
      data: { weight: 75, reps: 8 },
    })
  })

  it('does not call sessionService.updateSetLog when offline', async () => {
    vi.mocked(offlineQueue.enqueue).mockResolvedValue(1)

    const { result } = renderHook(() => useSessionTransport(), {
      wrapper: makeWrapper(queryClient),
    })

    await act(async () => {
      await result.current.transport.updateSetLog('session-1', 'setlog-42', {
        weight: 75,
        reps: 8,
      })
    })

    expect(sessionService.updateSetLog).not.toHaveBeenCalled()
  })
})
