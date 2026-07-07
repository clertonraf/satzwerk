import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, renderHook } from '@testing-library/react'
import axios, { type AxiosError } from 'axios'
import type { ReactNode } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { queryKeys } from '@/services/queryKeys'
import { sessionService } from '@/services/sessionService'
import { useWorkoutSessionMachine } from '../useWorkoutSessionMachine'

const mockUseOnlineStatus = vi.fn()

vi.mock('@/hooks/useOnlineStatus', () => ({
  useOnlineStatus: () => mockUseOnlineStatus(),
}))

vi.mock('@/features/sessions/useSessionTransport', () => ({
  useSessionTransport: () => ({
    transport: { addSetLog: vi.fn(), updateSetLog: vi.fn() },
    isAddPending: false,
    isUpdatePending: false,
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

describe('useWorkoutSessionMachine', () => {
  beforeEach(() => {
    mockUseOnlineStatus.mockReset()
    mockUseOnlineStatus.mockReturnValue(true)
    vi.mocked(sessionService.start).mockReset()
    vi.mocked(sessionService.getOpen).mockReset()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('invalidates start options when START fails with stale plan (400)', async () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const invalidateQueries = vi.spyOn(client, 'invalidateQueries').mockResolvedValue()
    const staleError = Object.assign(new Error('stale'), {
      isAxiosError: true,
      response: { status: 400 },
    })
    vi.spyOn(axios, 'isAxiosError').mockImplementation(
      (e): e is AxiosError => (e as { isAxiosError?: boolean }).isAxiosError === true,
    )
    vi.mocked(sessionService.start).mockRejectedValue(staleError)

    function Wrapper({ children }: { children: ReactNode }) {
      return <QueryClientProvider client={client}>{children}</QueryClientProvider>
    }

    const { result } = renderHook(
      () => useWorkoutSessionMachine({ onComplete: vi.fn(), onForfeit: vi.fn() }),
      { wrapper: Wrapper },
    )

    await act(async () => {
      await result.current.dispatch({ type: 'START', workoutGroupId: 'group-1' })
    })

    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: queryKeys.sessions.startOptions() })
    expect(result.current.conflictSession).toBeNull()
    expect(result.current.stalePlanError).toBeTruthy()
  })

  it('sets conflict phase when START returns 409', async () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const conflictError = Object.assign(new Error('conflict'), {
      isAxiosError: true,
      response: { status: 409 },
    })
    vi.spyOn(axios, 'isAxiosError').mockImplementation(
      (e): e is AxiosError => (e as { isAxiosError?: boolean }).isAxiosError === true,
    )
    vi.mocked(sessionService.start).mockRejectedValue(conflictError)
    const openSession = { id: 'existing-session', workoutGroupId: 'g-2', setLogs: [], setCount: 0, startedAt: '' }
    vi.mocked(sessionService.getOpen).mockResolvedValue(openSession as never)

    function Wrapper({ children }: { children: ReactNode }) {
      return <QueryClientProvider client={client}>{children}</QueryClientProvider>
    }

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
})
