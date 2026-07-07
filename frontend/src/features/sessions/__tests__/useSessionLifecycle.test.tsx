import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, renderHook, waitFor } from '@testing-library/react'
import type { ReactNode } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { queryKeys } from '@/services/queryKeys'
import type { PendingSetLog, WorkoutSession } from '@/services/sessionService'
import { sessionService } from '@/services/sessionService'
import { useSessionLifecycle } from '../useSessionLifecycle'

const mockAddSetLog = vi.fn()

vi.mock('@/features/sessions/useSessionTransport', () => ({
  useSessionTransport: () => ({
    transport: {
      addSetLog: mockAddSetLog,
      updateSetLog: vi.fn(),
    },
    isAddPending: false,
    isUpdatePending: false,
  }),
}))

vi.mock('@/services/sessionService', () => ({
  sessionService: {
    getOpen: vi.fn(),
    start: vi.fn(),
    complete: vi.fn(),
    discard: vi.fn(),
    deleteSetLog: vi.fn(),
  },
}))

function buildSession(overrides: Partial<WorkoutSession> = {}): WorkoutSession {
  return {
    id: 'session-1',
    workoutGroupId: 'group-1',
    workoutGroupTitle: 'Push Day',
    startedAt: '2024-01-01T00:00:00Z',
    completedAt: null,
    notes: null,
    setLogs: [],
    setCount: 0,
    ...overrides,
  }
}

function makePendingSetLog(overrides: Partial<PendingSetLog> = {}): PendingSetLog {
  return {
    id: 'queued-session-1-exercise-1-1-123',
    exerciseId: 'exercise-1',
    setNumber: 1,
    weight: 80,
    reps: 5,
    loggedAt: '2024-01-01T00:00:00Z',
    pending: true,
    ...overrides,
  }
}

describe('useSessionLifecycle', () => {
  let queryClient: QueryClient

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: {
        queries: {
          retry: false,
          staleTime: Infinity,
        },
      },
    })
    vi.mocked(sessionService.getOpen).mockResolvedValue(buildSession())
    mockAddSetLog.mockReset()
  })

  function Wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  }

  describe('pending set log reconciliation', () => {
    it('removes confirmed logs incrementally on partial flush (setCount delta < pending count)', async () => {
      mockAddSetLog
        .mockResolvedValueOnce(makePendingSetLog({ id: 'queued-1', setNumber: 1 }))
        .mockResolvedValueOnce(makePendingSetLog({ id: 'queued-2', setNumber: 2 }))
        .mockResolvedValueOnce(makePendingSetLog({ id: 'queued-3', setNumber: 3 }))

      const { result } = renderHook(() => useSessionLifecycle({ onComplete: vi.fn() }), { wrapper: Wrapper })

      await waitFor(() => expect(result.current.session?.id).toBe('session-1'))

      await act(async () => {
        await result.current.handleLogSet('exercise-1', 1, 80, 5, 'kg')
        await result.current.handleLogSet('exercise-1', 2, 85, 5, 'kg')
        await result.current.handleLogSet('exercise-1', 3, 90, 5, 'kg')
      })

      expect(result.current.pendingSetLogs).toHaveLength(3)

      // Simulate partial flush: server confirms 2 out of 3
      act(() => {
        queryClient.setQueryData(queryKeys.sessions.open(), buildSession({ setCount: 2 }))
      })

      await waitFor(() => expect(result.current.pendingSetLogs).toHaveLength(1))
    })

    it('clears all pending logs when all queued sets are confirmed at once', async () => {
      mockAddSetLog
        .mockResolvedValueOnce(makePendingSetLog({ id: 'queued-1', setNumber: 1 }))
        .mockResolvedValueOnce(makePendingSetLog({ id: 'queued-2', setNumber: 2 }))

      const { result } = renderHook(() => useSessionLifecycle({ onComplete: vi.fn() }), { wrapper: Wrapper })

      await waitFor(() => expect(result.current.session?.id).toBe('session-1'))

      await act(async () => {
        await result.current.handleLogSet('exercise-1', 1, 80, 5, 'kg')
        await result.current.handleLogSet('exercise-1', 2, 85, 5, 'kg')
      })

      expect(result.current.pendingSetLogs).toHaveLength(2)

      // All pending confirmed at once
      act(() => {
        queryClient.setQueryData(queryKeys.sessions.open(), buildSession({ setCount: 2 }))
      })

      await waitFor(() => expect(result.current.pendingSetLogs).toHaveLength(0))
    })

    it('clears all pending logs when session id changes (new session started)', async () => {
      mockAddSetLog
        .mockResolvedValueOnce(makePendingSetLog({ id: 'queued-1', setNumber: 1 }))
        .mockResolvedValueOnce(makePendingSetLog({ id: 'queued-2', setNumber: 2 }))

      const { result } = renderHook(() => useSessionLifecycle({ onComplete: vi.fn() }), { wrapper: Wrapper })

      await waitFor(() => expect(result.current.session?.id).toBe('session-1'))

      await act(async () => {
        await result.current.handleLogSet('exercise-1', 1, 80, 5, 'kg')
        await result.current.handleLogSet('exercise-1', 2, 85, 5, 'kg')
      })

      expect(result.current.pendingSetLogs).toHaveLength(2)

      // Simulate session change (e.g. forfeit → new session)
      act(() => {
        queryClient.setQueryData(queryKeys.sessions.open(), buildSession({ id: 'session-2', setCount: 0 }))
      })

      await waitFor(() => expect(result.current.pendingSetLogs).toHaveLength(0))
    })
  })
})
