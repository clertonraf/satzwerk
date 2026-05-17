import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { renderHook, waitFor } from '@testing-library/react'
import type { ReactNode } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { offlineQueue } from '@/services/offlineQueue'
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
    const client = new QueryClient({
      defaultOptions: {
        queries: {
          retry: false,
        },
      },
    })
    const invalidateQueries = vi.spyOn(client, 'invalidateQueries').mockResolvedValue()

    function Wrapper({ children }: { children: ReactNode }) {
      return <QueryClientProvider client={client}>{children}</QueryClientProvider>
    }

    mockUseOnlineStatus.mockReturnValue(false)
    vi.mocked(offlineQueue.flush).mockResolvedValue([])

    const { rerender } = renderHook(() => useOfflineSync(), { wrapper: Wrapper })

    mockUseOnlineStatus.mockReturnValue(true)
    rerender()

    await waitFor(() => expect(offlineQueue.flush).toHaveBeenCalledTimes(1))
    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: ['open-session'] })
  })
})
