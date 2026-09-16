import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { act, renderHook, waitFor } from '@testing-library/react'
import { useRestoreSession } from '../useRestoreSession'
import { useAuthStore } from '@/store/auth'
import { authService } from '@/services/authService'

vi.mock('@/services/authService', () => ({
  authService: {
    refresh: vi.fn(),
    logout: vi.fn(),
  },
}))

describe('useRestoreSession', () => {
  beforeEach(() => {
    vi.mocked(authService.refresh).mockReset()
    vi.mocked(authService.logout).mockReset()
    useAuthStore.setState({ accessToken: null, csrfToken: null, user: null, isRestoring: true })
    document.cookie = 'refresh_csrf=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
    localStorage.clear()
  })

  afterEach(() => {
    useAuthStore.setState({ accessToken: null, csrfToken: null, user: null, isRestoring: false })
    document.cookie = 'refresh_csrf=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
  })

  it('restores the session with a csrf cookie after a reload', async () => {
    document.cookie = 'refresh_csrf=reload-csrf-token; path=/'
    vi.mocked(authService.refresh).mockResolvedValue({
      accessToken: 'restored-access-token',
      csrfToken: 'rotated-csrf-token',
    })

    renderHook(() => useRestoreSession())

    await waitFor(() => {
      expect(useAuthStore.getState().accessToken).toBe('restored-access-token')
      expect(useAuthStore.getState().csrfToken).toBe('rotated-csrf-token')
      expect(useAuthStore.getState().isRestoring).toBe(false)
    })
  })

  it('logs out when refresh fails', async () => {
    document.cookie = 'refresh_csrf=reload-csrf-token; path=/'
    vi.mocked(authService.refresh).mockRejectedValue(new Error('forbidden'))
    vi.mocked(authService.logout).mockResolvedValue(undefined)

    renderHook(() => useRestoreSession())

    await waitFor(() => {
      expect(vi.mocked(authService.logout)).toHaveBeenCalledTimes(1)
      expect(useAuthStore.getState().accessToken).toBeNull()
      expect(useAuthStore.getState().csrfToken).toBeNull()
      expect(useAuthStore.getState().isRestoring).toBe(false)
    })

    await act(async () => {})
  })
})
