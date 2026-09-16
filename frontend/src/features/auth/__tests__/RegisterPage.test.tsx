import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import RegisterPage from '../RegisterPage'
import { useAuthStore } from '@/store/auth'
import { authService } from '@/services/authService'

vi.mock('@/services/authService', () => ({
  authService: {
    register: vi.fn(),
  },
}))

describe('RegisterPage', () => {
  beforeEach(() => {
    vi.mocked(authService.register).mockReset()
    useAuthStore.setState({ accessToken: null, csrfToken: null, user: null, isRestoring: false })
    localStorage.clear()
  })

  afterEach(() => {
    useAuthStore.setState({ accessToken: null, csrfToken: null, user: null, isRestoring: false })
    localStorage.clear()
  })

  it('renders all fields', () => {
    render(
      <MemoryRouter>
        <RegisterPage />
      </MemoryRouter>
    )

    expect(screen.getByLabelText(/email/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/password/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/display name/i)).toBeInTheDocument()
  })

  it('stores the access token and csrf token in memory after register', async () => {
    const user = userEvent.setup()
    vi.mocked(authService.register).mockResolvedValueOnce({
      accessToken: 'access-token',
      csrfToken: 'csrf-token',
    })

    render(
      <MemoryRouter>
        <RegisterPage />
      </MemoryRouter>
    )

    await user.type(screen.getByLabelText(/email/i), 'user@example.com')
    await user.type(screen.getByLabelText(/display name/i), 'User')
    await user.type(screen.getByLabelText(/password/i), 'password123')
    await user.click(screen.getByRole('button', { name: /register/i }))

    await waitFor(() => {
      expect(useAuthStore.getState().accessToken).toBe('access-token')
      expect(useAuthStore.getState().csrfToken).toBe('csrf-token')
      expect(localStorage.getItem('refreshToken')).toBeNull()
    })
  })

  it('shows error when password too short', async () => {
    const user = userEvent.setup()

    render(
      <MemoryRouter>
        <RegisterPage />
      </MemoryRouter>
    )

    await user.type(screen.getByLabelText(/password/i), 'short')
    await user.click(screen.getByRole('button', { name: /register/i }))

    expect(await screen.findByText(/at least 8/i)).toBeInTheDocument()
  })
})
