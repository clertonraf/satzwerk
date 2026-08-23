import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, renderHook, waitFor } from '@testing-library/react'
import axios, { type AxiosError } from 'axios'
import type { ReactNode } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { FlushSucceededReceipt } from '@/services/offlineQueue'
import { queryKeys } from '@/services/queryKeys'
import type { PendingSetLog, WorkoutSession } from '@/services/sessionService'
import { sessionService } from '@/services/sessionService'
import { useWorkoutSessionMachine } from '../useWorkoutSessionMachine'

type AddSetSyncReceipt = Extract<FlushSucceededReceipt, { type: 'add-set' }>

const mockAddSetLog = vi.fn()
const mockDeleteSetLog = vi.fn()
const mockUseOnlineStatus = vi.fn()

vi.mock('@/hooks/useOnlineStatus', () => ({
  useOnlineStatus: () => mockUseOnlineStatus(),
}))

vi.mock('@/features/sessions/useSessionTransport', () => ({
  useSessionTransport: () => ({
    transport: { addSetLog: mockAddSetLog, updateSetLog: vi.fn(), deleteSetLog: mockDeleteSetLog },
    isAddPending: false,
    isUpdatePending: false,
    isDeletePending: false,
  }),
}))

vi.mock('@/services/sessionService', () => ({
  sessionService: {
    start: vi.fn(),
    getOpen: vi.fn(),
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
    exerciseCount: 0,
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

function buildSetLog(setNumber: number): WorkoutSession['setLogs'][number] {
  return {
    id: `log-${setNumber}`,
    exerciseId: 'ex-1',
    setNumber,
    weight: 80 + setNumber * 5,
    reps: 5,
    loggedAt: '2024-01-01T00:00:00Z',
  }
}

function makeSyncReceipt(overrides: Partial<AddSetSyncReceipt> = {}): AddSetSyncReceipt {
  return {
    type: 'add-set',
    sessionId: 'session-1',
    queuedOpId: 1,
    clientSetLogId: 'queued-session-1-exercise-1-1-123',
    data: {
      exerciseId: 'exercise-1',
      setNumber: 1,
      weight: 80,
      reps: 5,
    },
    serverSetLog: {
      id: 'log-1',
      exerciseId: 'exercise-1',
      setNumber: 1,
      weight: 80,
      reps: 5,
      loggedAt: '2024-01-01T00:00:00Z',
    },
    ...overrides,
  }
}

describe('useWorkoutSessionMachine', () => {
  let queryClient: QueryClient

  function Wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  }

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false, staleTime: Infinity } },
    })
    // Pre-seed the open-session cache with null so the useQuery's initial fetch
    // never fires during tests. Tests that need a specific session can override
    // via queryClient.setQueryData() before rendering the hook.
    queryClient.setQueryData(queryKeys.sessions.open(), null)
    mockUseOnlineStatus.mockReset()
    mockUseOnlineStatus.mockReturnValue(true)
    mockAddSetLog.mockReset()
    mockDeleteSetLog.mockReset()
    vi.mocked(sessionService.start).mockReset()
    // Reset getOpen before applying the default 404 rejection so prior call
    // history doesn't bleed across tests.
    vi.mocked(sessionService.getOpen).mockReset()
    // Mock getOpen with a 404 AxiosError — type-safe default for the conflict path,
    // where the machine explicitly calls sessionService.getOpen() after a 409.
    // The open-session cache is pre-seeded with null above, so the useQuery's
    // initial fetch never fires and this mock is only consumed by direct getOpen() calls.
    vi.mocked(sessionService.getOpen).mockRejectedValue(
      Object.assign(new Error('Not Found'), { isAxiosError: true, response: { status: 404 } }),
    )
    vi.mocked(sessionService.complete).mockReset()
    vi.mocked(sessionService.discard).mockReset()
    vi.mocked(sessionService.deleteSetLog).mockReset()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  describe('START', () => {
    it('sets open session in cache on success', async () => {
      const session = buildSession()
      vi.mocked(sessionService.start).mockResolvedValue(session)

      const { result } = renderHook(
        () => useWorkoutSessionMachine({ onComplete: vi.fn(), onForfeit: vi.fn() }),
        { wrapper: Wrapper },
      )

      await act(async () => {
        await result.current.dispatch({ type: 'START', workoutGroupId: 'group-1' })
      })

      expect(queryClient.getQueryData(queryKeys.sessions.open())).toEqual(session)
      await waitFor(() => expect(result.current.phase).toBe('open'))
      expect(result.current.conflictSession).toBeNull()
    })

    it('sets conflict phase and fetches open session when START returns 409', async () => {
      const conflictError = Object.assign(new Error('conflict'), {
        isAxiosError: true,
        response: { status: 409 },
      })
      vi.spyOn(axios, 'isAxiosError').mockImplementation(
        (e): e is AxiosError => (e as { isAxiosError?: boolean }).isAxiosError === true,
      )
      vi.mocked(sessionService.start).mockRejectedValue(conflictError)
      const openSession = buildSession({ id: 'existing-session', workoutGroupId: 'g-2' })
      vi.mocked(sessionService.getOpen).mockResolvedValue(openSession)

      const { result } = renderHook(
        () => useWorkoutSessionMachine({ onComplete: vi.fn(), onForfeit: vi.fn() }),
        { wrapper: Wrapper },
      )

      await act(async () => {
        await result.current.dispatch({ type: 'START', workoutGroupId: 'group-1' })
      })

      expect(result.current.phase).toBe('conflict')
      expect(result.current.conflictSession).toEqual(openSession)
    })

    it('invalidates start options and sets stalePlanError when START returns 400', async () => {
      const staleError = Object.assign(new Error('stale'), {
        isAxiosError: true,
        response: { status: 400 },
      })
      vi.spyOn(axios, 'isAxiosError').mockImplementation(
        (e): e is AxiosError => (e as { isAxiosError?: boolean }).isAxiosError === true,
      )
      vi.mocked(sessionService.start).mockRejectedValue(staleError)
      const invalidateQueries = vi.spyOn(queryClient, 'invalidateQueries').mockResolvedValue()

      const { result } = renderHook(
        () => useWorkoutSessionMachine({ onComplete: vi.fn(), onForfeit: vi.fn() }),
        { wrapper: Wrapper },
      )

      await act(async () => {
        await result.current.dispatch({ type: 'START', workoutGroupId: 'group-1' })
      })

      expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: queryKeys.sessions.startOptions() })
      expect(result.current.stalePlanError).toBeTruthy()
      expect(result.current.conflictSession).toBeNull()
    })

    it('is a no-op when offline', async () => {
      mockUseOnlineStatus.mockReturnValue(false)

      const { result } = renderHook(
        () => useWorkoutSessionMachine({ onComplete: vi.fn(), onForfeit: vi.fn() }),
        { wrapper: Wrapper },
      )

      await act(async () => {
        await result.current.dispatch({ type: 'START', workoutGroupId: 'group-1' })
      })

      expect(sessionService.start).not.toHaveBeenCalled()
    })
  })

  describe('RESUME', () => {
    it('clears conflict state and returns to open phase', async () => {
      const conflictError = Object.assign(new Error('conflict'), {
        isAxiosError: true,
        response: { status: 409 },
      })
      vi.spyOn(axios, 'isAxiosError').mockImplementation(
        (e): e is AxiosError => (e as { isAxiosError?: boolean }).isAxiosError === true,
      )
      vi.mocked(sessionService.start).mockRejectedValue(conflictError)
      const openSession = buildSession({ id: 'existing-session' })
      vi.mocked(sessionService.getOpen).mockResolvedValue(openSession)

      queryClient.setQueryData(queryKeys.sessions.open(), openSession)

      const { result } = renderHook(
        () => useWorkoutSessionMachine({ onComplete: vi.fn(), onForfeit: vi.fn() }),
        { wrapper: Wrapper },
      )

      await act(async () => {
        await result.current.dispatch({ type: 'START', workoutGroupId: 'group-1' })
      })

      expect(result.current.phase).toBe('conflict')

      await act(async () => {
        await result.current.dispatch({ type: 'RESUME' })
      })

      expect(result.current.phase).toBe('open')
      expect(result.current.conflictSession).toBeNull()
    })
  })

  describe('DISCARD', () => {
    it('discards the conflict session and starts the pending group', async () => {
      const conflictError = Object.assign(new Error('conflict'), {
        isAxiosError: true,
        response: { status: 409 },
      })
      vi.spyOn(axios, 'isAxiosError').mockImplementation(
        (e): e is AxiosError => (e as { isAxiosError?: boolean }).isAxiosError === true,
      )
      vi.mocked(sessionService.start).mockRejectedValueOnce(conflictError)
      const conflictingSession = buildSession({ id: 'existing-session', workoutGroupId: 'g-2' })
      vi.mocked(sessionService.getOpen).mockResolvedValue(conflictingSession)

      const newSession = buildSession({ id: 'new-session', workoutGroupId: 'group-1' })
      vi.mocked(sessionService.start).mockResolvedValueOnce(newSession)
      vi.mocked(sessionService.discard).mockResolvedValue(undefined)

      const { result } = renderHook(
        () => useWorkoutSessionMachine({ onComplete: vi.fn(), onForfeit: vi.fn() }),
        { wrapper: Wrapper },
      )

      await act(async () => {
        await result.current.dispatch({ type: 'START', workoutGroupId: 'group-1' })
      })

      expect(result.current.phase).toBe('conflict')

      await act(async () => {
        await result.current.dispatch({ type: 'DISCARD' })
      })

      expect(sessionService.discard).toHaveBeenCalledWith('existing-session')
      expect(sessionService.start).toHaveBeenLastCalledWith('group-1')
      expect(result.current.conflictSession).toBeNull()
    })
  })

  describe('COMPLETE', () => {
    it('completes session, prepends to history cache, and calls onComplete', async () => {
      const session = buildSession()
      const completedSession = buildSession({ completedAt: '2024-01-01T01:00:00Z' })
      queryClient.setQueryData(queryKeys.sessions.open(), session)
      vi.mocked(sessionService.complete).mockResolvedValue(completedSession)
      const onComplete = vi.fn()

      const { result } = renderHook(
        () => useWorkoutSessionMachine({ onComplete, onForfeit: vi.fn() }),
        { wrapper: Wrapper },
      )

      await act(async () => {
        await result.current.dispatch({ type: 'COMPLETE' })
      })

      expect(sessionService.complete).toHaveBeenCalledWith('session-1')
      expect(queryClient.getQueryData(queryKeys.sessions.open())).toBeNull()
      const history = queryClient.getQueryData<WorkoutSession[]>(queryKeys.sessions.history())
      expect(history?.[0]).toEqual(completedSession)
      expect(onComplete).toHaveBeenCalledOnce()
    })
  })

  describe('FORFEIT', () => {
    it('discards session, clears open session cache, and calls onForfeit', async () => {
      const session = buildSession()
      queryClient.setQueryData(queryKeys.sessions.open(), session)
      vi.mocked(sessionService.discard).mockResolvedValue(undefined)
      const onForfeit = vi.fn()

      const { result } = renderHook(
        () => useWorkoutSessionMachine({ onComplete: vi.fn(), onForfeit }),
        { wrapper: Wrapper },
      )

      await act(async () => {
        await result.current.dispatch({ type: 'FORFEIT' })
      })

      expect(sessionService.discard).toHaveBeenCalledWith('session-1')
      expect(queryClient.getQueryData(queryKeys.sessions.open())).toBeNull()
      expect(onForfeit).toHaveBeenCalledOnce()
    })
  })

  describe('DELETE_SET', () => {
    it('optimistically removes set log while offline and leaves reconnect confirmation to the queue', async () => {
      mockUseOnlineStatus.mockReturnValue(false)
      const setLog = {
        id: 'log-1',
        exerciseId: 'ex-1',
        setNumber: 1,
        weight: 80,
        reps: 5,
        loggedAt: '2024-01-01T00:00:00Z',
      }
      const session = buildSession({ setLogs: [setLog], setCount: 1 })
      queryClient.setQueryData(queryKeys.sessions.open(), session)
      let resolveDelete = () => {}
      mockDeleteSetLog.mockImplementation(
        () =>
          new Promise<void>((resolve) => {
            resolveDelete = resolve
          }),
      )
      const invalidateQueries = vi.spyOn(queryClient, 'invalidateQueries').mockResolvedValue()

      const { result } = renderHook(
        () => useWorkoutSessionMachine({ onComplete: vi.fn(), onForfeit: vi.fn() }),
        { wrapper: Wrapper },
      )

      let dispatchPromise: Promise<void> | undefined
      await act(async () => {
        dispatchPromise = result.current.dispatch({ type: 'DELETE_SET', setLogId: 'log-1' })
        await Promise.resolve()
      })

      const updated = queryClient.getQueryData<WorkoutSession>(queryKeys.sessions.open())
      expect(mockDeleteSetLog).toHaveBeenCalledWith('session-1', 'log-1')
      expect(updated?.setLogs).toHaveLength(0)
      expect(updated?.setCount).toBe(0)
      expect(invalidateQueries).not.toHaveBeenCalled()

      await act(async () => {
        resolveDelete()
        await dispatchPromise
      })

      expect(invalidateQueries).not.toHaveBeenCalled()
    })
  })

  describe('pending set log reconciliation', () => {
    it('removes confirmed logs incrementally from explicit sync receipts (partial flush)', async () => {
      // Use deferred promises so transport does not update session cache yet.
      // This lets pendingSetLogs accumulate before reconciliation fires.
      const resolvers: Array<() => void> = []
      mockAddSetLog.mockImplementation(
        () => new Promise<PendingSetLog>((r) => { resolvers.push(() => r(makePendingSetLog())) }),
      )

      queryClient.setQueryData(queryKeys.sessions.open(), buildSession())

      const { result } = renderHook(
        () => useWorkoutSessionMachine({ onComplete: vi.fn(), onForfeit: vi.fn() }),
        { wrapper: Wrapper },
      )

      // Start 3 dispatches without awaiting so setPendingSetLogs fires for each
      // before transport resolves. The sync state updates are flushed by act().
      let dispatchPromises: Promise<void>[] = []
      await act(async () => {
        dispatchPromises = [
          result.current.dispatch({ type: 'LOG_SET', exerciseId: 'ex-1', setNumber: 1, weight: 80, reps: 5, unit: 'kg' }),
          result.current.dispatch({ type: 'LOG_SET', exerciseId: 'ex-1', setNumber: 2, weight: 85, reps: 5, unit: 'kg' }),
          result.current.dispatch({ type: 'LOG_SET', exerciseId: 'ex-1', setNumber: 3, weight: 90, reps: 5, unit: 'kg' }),
        ]
        await Promise.resolve() // flush sync setPendingSetLogs updates
      })

      expect(result.current.pendingSetLogs).toHaveLength(3)

      // Simulate partial confirmation with out-of-order receipts.
      await act(async () => {
        queryClient.setQueryData(
          queryKeys.sessions.syncReceipts('session-1'),
          [
            makeSyncReceipt({
              queuedOpId: 2,
              clientSetLogId: result.current.pendingSetLogs[2].id,
              data: {
                exerciseId: 'ex-1',
                setNumber: 3,
                weight: 90,
                reps: 5,
              },
              serverSetLog: buildSetLog(3),
            }),
            makeSyncReceipt({
              queuedOpId: 1,
              clientSetLogId: result.current.pendingSetLogs[0].id,
              data: {
                exerciseId: 'ex-1',
                setNumber: 1,
                weight: 80,
                reps: 5,
              },
              serverSetLog: buildSetLog(1),
            }),
          ],
        )
      })

      await waitFor(() => expect(result.current.pendingSetLogs).toHaveLength(1))
      expect(result.current.pendingSetLogs[0].setNumber).toBe(2)

      // Resolve deferred transport and await dispatch completion to clean up
      await act(async () => {
        resolvers.forEach((r) => r())
        await Promise.all(dispatchPromises)
      })
    })

    it('clears all pending logs when all queued sets are confirmed at once', async () => {
      const resolvers: Array<() => void> = []
      mockAddSetLog.mockImplementation(
        () => new Promise<PendingSetLog>((r) => { resolvers.push(() => r(makePendingSetLog())) }),
      )

      queryClient.setQueryData(queryKeys.sessions.open(), buildSession())

      const { result } = renderHook(
        () => useWorkoutSessionMachine({ onComplete: vi.fn(), onForfeit: vi.fn() }),
        { wrapper: Wrapper },
      )

      let dispatchPromises: Promise<void>[] = []
      await act(async () => {
        dispatchPromises = [
          result.current.dispatch({ type: 'LOG_SET', exerciseId: 'ex-1', setNumber: 1, weight: 80, reps: 5, unit: 'kg' }),
          result.current.dispatch({ type: 'LOG_SET', exerciseId: 'ex-1', setNumber: 2, weight: 85, reps: 5, unit: 'kg' }),
        ]
        await Promise.resolve()
      })

      expect(result.current.pendingSetLogs).toHaveLength(2)

      await act(async () => {
        queryClient.setQueryData(
          queryKeys.sessions.syncReceipts('session-1'),
          [
            makeSyncReceipt({
              queuedOpId: 1,
              clientSetLogId: result.current.pendingSetLogs[0].id,
              data: {
                exerciseId: 'ex-1',
                setNumber: 1,
                weight: 80,
                reps: 5,
              },
              serverSetLog: buildSetLog(1),
            }),
            makeSyncReceipt({
              queuedOpId: 2,
              clientSetLogId: result.current.pendingSetLogs[1].id,
              data: {
                exerciseId: 'ex-1',
                setNumber: 2,
                weight: 85,
                reps: 5,
              },
              serverSetLog: buildSetLog(2),
            }),
          ],
        )
      })

      await waitFor(() => expect(result.current.pendingSetLogs).toHaveLength(0))

      await act(async () => {
        resolvers.forEach((r) => r())
        await Promise.all(dispatchPromises)
      })
    })

    it('ignores replayed sync receipts after the matching pending logs are already cleared', async () => {
      const resolvers: Array<() => void> = []
      mockAddSetLog.mockImplementation(
        () => new Promise<PendingSetLog>((r) => { resolvers.push(() => r(makePendingSetLog())) }),
      )

      queryClient.setQueryData(queryKeys.sessions.open(), buildSession())

      const { result } = renderHook(
        () => useWorkoutSessionMachine({ onComplete: vi.fn(), onForfeit: vi.fn() }),
        { wrapper: Wrapper },
      )

      let dispatchPromises: Promise<void>[] = []
      await act(async () => {
        dispatchPromises = [
          result.current.dispatch({ type: 'LOG_SET', exerciseId: 'ex-1', setNumber: 1, weight: 80, reps: 5, unit: 'kg' }),
        ]
        await Promise.resolve()
      })

      const receipt = makeSyncReceipt({
        clientSetLogId: result.current.pendingSetLogs[0].id,
        data: {
          exerciseId: 'ex-1',
          setNumber: 1,
          weight: 80,
          reps: 5,
        },
        serverSetLog: buildSetLog(1),
      })

      await act(async () => {
        queryClient.setQueryData(queryKeys.sessions.syncReceipts('session-1'), [receipt])
      })

      await waitFor(() => expect(result.current.pendingSetLogs).toHaveLength(0))

      await act(async () => {
        queryClient.setQueryData(queryKeys.sessions.syncReceipts('session-1'), [receipt])
      })

      expect(result.current.pendingSetLogs).toHaveLength(0)

      await act(async () => {
        resolvers.forEach((r) => r())
        await Promise.all(dispatchPromises)
      })
    })

    it('clears all pending logs when session id changes (new session started)', async () => {
      const resolvers: Array<() => void> = []
      mockAddSetLog.mockImplementation(
        () => new Promise<PendingSetLog>((r) => { resolvers.push(() => r(makePendingSetLog())) }),
      )

      queryClient.setQueryData(queryKeys.sessions.open(), buildSession())

      const { result } = renderHook(
        () => useWorkoutSessionMachine({ onComplete: vi.fn(), onForfeit: vi.fn() }),
        { wrapper: Wrapper },
      )

      let dispatchPromises: Promise<void>[] = []
      await act(async () => {
        dispatchPromises = [
          result.current.dispatch({ type: 'LOG_SET', exerciseId: 'ex-1', setNumber: 1, weight: 80, reps: 5, unit: 'kg' }),
          result.current.dispatch({ type: 'LOG_SET', exerciseId: 'ex-1', setNumber: 2, weight: 85, reps: 5, unit: 'kg' }),
        ]
        await Promise.resolve()
      })

      expect(result.current.pendingSetLogs).toHaveLength(2)

      // Simulate session change (e.g. forfeit → new session)
      await act(async () => {
        queryClient.setQueryData(queryKeys.sessions.open(), buildSession({ id: 'session-2', setCount: 0 }))
      })

      await waitFor(() => expect(result.current.pendingSetLogs).toHaveLength(0))

      await act(async () => {
        resolvers.forEach((r) => r())
        await Promise.all(dispatchPromises)
      })
    })
    it('keeps pendingSetLogs after dispatch until an explicit receipt confirms the queued SetLog', async () => {
      // Simulates the offline case: transport resolves immediately with a queued PendingSetLog.
      mockAddSetLog.mockResolvedValue(makePendingSetLog({ setNumber: 1 }))

      queryClient.setQueryData(queryKeys.sessions.open(), buildSession())

      const { result } = renderHook(
        () => useWorkoutSessionMachine({ onComplete: vi.fn(), onForfeit: vi.fn() }),
        { wrapper: Wrapper },
      )

      await act(async () => {
        await result.current.dispatch({
          type: 'LOG_SET',
          exerciseId: 'ex-1',
          setNumber: 1,
          weight: 80,
          reps: 5,
          unit: 'kg',
        })
      })

      expect(result.current.pendingSetLogs).toHaveLength(1)
      // The queued log is tracked in session.setLogs until the server confirms it
      const session = queryClient.getQueryData<WorkoutSession>(queryKeys.sessions.open())
      expect(session?.setLogs).toHaveLength(1)
      expect(session?.setCount).toBe(1)

      await act(async () => {
        queryClient.setQueryData(queryKeys.sessions.syncReceipts('session-1'), [
          makeSyncReceipt({
            clientSetLogId: result.current.pendingSetLogs[0].id,
            data: {
              exerciseId: 'ex-1',
              setNumber: 1,
              weight: 80,
              reps: 5,
            },
            serverSetLog: buildSetLog(1),
          }),
        ])
      })

      await waitFor(() => expect(result.current.pendingSetLogs).toHaveLength(0))
    })
  })
})
