import { afterEach, describe, expect, it, vi } from 'vitest'

async function loadTokenService() {
  vi.resetModules()
  return import('../tokenService')
}

describe('tokenService', () => {
  afterEach(() => {
    localStorage.clear()
    document.cookie = 'refresh_csrf=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
    vi.resetModules()
  })

  it('removes any legacy refresh token from localStorage on load', async () => {
    localStorage.setItem('refreshToken', 'legacy-token')

    await loadTokenService()

    expect(localStorage.getItem('refreshToken')).toBeNull()
  })

  it('returns the in-memory csrf token when one is saved', async () => {
    const { tokenService } = await loadTokenService()

    tokenService.saveCsrfToken('memory-csrf-token')

    expect(tokenService.getCsrfToken()).toBe('memory-csrf-token')
  })

  it('falls back to the csrf cookie when memory is empty', async () => {
    const { tokenService } = await loadTokenService()
    document.cookie = 'refresh_csrf=cookie-csrf-token; path=/'

    expect(tokenService.getCsrfToken()).toBe('cookie-csrf-token')
  })
})
