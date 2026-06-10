import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, renderHook } from '@testing-library/react'
import type { ReactNode } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { queryKeys } from '@/services/queryKeys'
import { sessionService } from '@/services/sessionService'
import { useConflictResolution } from '../useConflictResolution'

const mockUseOnlineStatus = vi.fn()

vi.mock('@/hooks/useOnlineStatus', () => ({
  useOnlineStatus: () => mockUseOnlineStatus(),
}))

vi.mock('@/services/sessionService', () => ({
  sessionService: {
    getOpen: vi.fn(),
  },
}))

describe('useConflictResolution', () => {
  beforeEach(() => {
    mockUseOnlineStatus.mockReset()
    mockUseOnlineStatus.mockReturnValue(true)
    vi.mocked(sessionService.getOpen).mockReset()
  })

  it('refreshes start options when starting a session fails because the plan is inactive', async () => {
    const client = new QueryClient({
      defaultOptions: {
        queries: {
          retry: false,
        },
      },
    })
    const invalidateQueries = vi.spyOn(client, 'invalidateQueries').mockResolvedValue()
    const startMutateAsync = vi.fn().mockRejectedValue({
      isAxiosError: true,
      response: { status: 400 },
    })

    function Wrapper({ children }: { children: ReactNode }) {
      return <QueryClientProvider client={client}>{children}</QueryClientProvider>
    }

    const { result } = renderHook(
      () =>
        useConflictResolution({
          startMutateAsync,
          discardMutateAsync: vi.fn(),
        }),
      { wrapper: Wrapper }
    )

    await expect(
      act(async () => {
        await result.current.handleStartSession('group-1')
      })
    ).resolves.toBeUndefined()

    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: queryKeys.sessions.startOptions() })
    expect(result.current.conflictSession).toBeNull()
  })
})
