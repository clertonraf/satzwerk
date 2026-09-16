import { beforeEach, describe, expect, it, vi } from 'vitest'

const mockPost = vi.fn()
const mockCreate = vi.fn(() => ({ post: mockPost }))

vi.mock('axios', () => ({
  default: {
    create: mockCreate,
  },
}))

describe('authService', () => {
  beforeEach(() => {
    mockPost.mockReset()
    mockCreate.mockClear()
    mockCreate.mockReturnValue({ post: mockPost })
    localStorage.clear()
    document.cookie = 'refresh_csrf=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
    vi.resetModules()
  })

  it('sends the csrf header and credentials on refresh', async () => {
    document.cookie = 'refresh_csrf=cookie-csrf-token; path=/'
    mockPost.mockResolvedValueOnce({ data: { accessToken: 'next-access-token', csrfToken: 'next-csrf-token' } })

    const { authService } = await import('../authService')
    await authService.refresh()

    expect(mockCreate).toHaveBeenCalledWith({ baseURL: '/api/auth', withCredentials: true })
    expect(mockPost).toHaveBeenCalledWith('/refresh', undefined, {
      headers: { 'X-CSRF-Token': 'cookie-csrf-token' },
    })
  })

  it('sends credentials on logout', async () => {
    mockPost.mockResolvedValueOnce({ data: undefined })

    const { authService } = await import('../authService')
    await authService.logout()

    expect(mockPost).toHaveBeenCalledWith('/logout')
  })
})
