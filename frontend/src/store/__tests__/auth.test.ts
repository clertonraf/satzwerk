import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { useAuthStore } from '../auth'
import { tokenService } from '@/services/tokenService'

describe('useAuthStore', () => {
  beforeEach(() => {
    tokenService.clearTokens()
    useAuthStore.setState({ accessToken: null, csrfToken: null, user: null, isRestoring: false })
  })

  afterEach(() => {
    tokenService.clearTokens()
    useAuthStore.setState({ accessToken: null, csrfToken: null, user: null, isRestoring: false })
    localStorage.clear()
  })

  it('stores the csrf token in memory and clears auth state on logout', () => {
    useAuthStore.getState().setAccessToken('access-token')
    useAuthStore.getState().setCsrfToken('csrf-token')

    expect(useAuthStore.getState().accessToken).toBe('access-token')
    expect(useAuthStore.getState().csrfToken).toBe('csrf-token')
    expect(tokenService.getAccessToken()).toBe('access-token')
    expect(tokenService.getCsrfToken()).toBe('csrf-token')
    expect(localStorage.getItem('refreshToken')).toBeNull()

    useAuthStore.getState().logout()

    expect(useAuthStore.getState().accessToken).toBeNull()
    expect(useAuthStore.getState().csrfToken).toBeNull()
    expect(useAuthStore.getState().user).toBeNull()
    expect(useAuthStore.getState().isRestoring).toBe(false)
  })
})
