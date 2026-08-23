import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, renderHook, waitFor } from '@testing-library/react'
import type { ReactNode } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { offlineQueue } from '@/services/offlineQueue'
import { queryKeys } from '@/services/queryKeys'
import { useOfflineSync } from '../useOfflineSync'

const mockUseOnlineStatus = vi.fn()

vi.mock('../useOnlineStatus', () => ({
  useOnlineStatus: () => mockUseOnlineStatus(),
}))

vi.mock('@/services/offlineQueue', () => ({
  offlineQueue: {
    flush: vi.fn(),
  },
}))

describe('useOfflineSync', () => {
  beforeEach(() => {
    mockUseOnlineStatus.mockReset()
    vi.mocked(offlineQueue.flush).mockReset()
  })

  it('flushes the queue when the app comes back online', async () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const invalidateQueries = vi.spyOn(client, 'invalidateQueries').mockResolvedValue()

    function Wrapper({ children }: { children: ReactNode }) {
      return <QueryClientProvider client={client}>{children}</QueryClientProvider>
    }

    mockUseOnlineStatus.mockReturnValue(false)
    vi.mocked(offlineQueue.flush).mockResolvedValue({
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

    const { rerender } = renderHook(() => useOfflineSync(), { wrapper: Wrapper })

    mockUseOnlineStatus.mockReturnValue(true)
    rerender()

    await waitFor(() => expect(offlineQueue.flush).toHaveBeenCalledTimes(1))
    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: queryKeys.sessions.open() })
    expect(client.getQueryData(queryKeys.sessions.syncReceipts('session-1'))).toEqual([
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
    ])
  })

  it('sets flushError when flush returns failed ops', async () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    vi.spyOn(client, 'invalidateQueries').mockResolvedValue()

    function Wrapper({ children }: { children: ReactNode }) {
      return <QueryClientProvider client={client}>{children}</QueryClientProvider>
    }

    mockUseOnlineStatus.mockReturnValue(false)
    vi.mocked(offlineQueue.flush).mockResolvedValue({
      succeeded: [],
      failed: [
        {
          type: 'add-set',
          sessionId: 'session-1',
          queuedOpId: 1,
          clientSetLogId: 'queued-1',
          data: { exerciseId: 'exercise-1', setNumber: 1, weight: 80, reps: 5 },
          exhausted: false,
        },
        {
          type: 'add-set',
          sessionId: 'session-1',
          queuedOpId: 2,
          clientSetLogId: 'queued-2',
          data: { exerciseId: 'exercise-1', setNumber: 2, weight: 85, reps: 5 },
          exhausted: false,
        },
      ],
    })

    const { rerender, result } = renderHook(() => useOfflineSync(), { wrapper: Wrapper })

    mockUseOnlineStatus.mockReturnValue(true)
    rerender()

    await waitFor(() => expect(result.current.flushError).not.toBeNull())
    expect(result.current.flushError).toContain('2 sets')
  })

  it('clears flushError on dismissFlushError', async () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    vi.spyOn(client, 'invalidateQueries').mockResolvedValue()

    function Wrapper({ children }: { children: ReactNode }) {
      return <QueryClientProvider client={client}>{children}</QueryClientProvider>
    }

    mockUseOnlineStatus.mockReturnValue(false)
    vi.mocked(offlineQueue.flush).mockResolvedValue({
      succeeded: [],
      failed: [
        {
          type: 'add-set',
          sessionId: 'session-1',
          queuedOpId: 1,
          clientSetLogId: 'queued-1',
          data: { exerciseId: 'exercise-1', setNumber: 1, weight: 80, reps: 5 },
          exhausted: false,
        },
      ],
    })

    const { rerender, result } = renderHook(() => useOfflineSync(), { wrapper: Wrapper })

    mockUseOnlineStatus.mockReturnValue(true)
    rerender()

    await waitFor(() => expect(result.current.flushError).not.toBeNull())

    act(() => result.current.dismissFlushError())
    expect(result.current.flushError).toBeNull()
  })
})
