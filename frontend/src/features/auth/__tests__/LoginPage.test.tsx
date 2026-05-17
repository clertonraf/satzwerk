import { afterEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import LoginPage from '../LoginPage'
import { useAuthStore } from '@/store/auth'

vi.mock('@/services/authService', () => ({
  authService: {
    login: vi.fn(),
  },
}))

describe('LoginPage', () => {
  afterEach(() => {
    useAuthStore.setState({ accessToken: null, user: null })
    localStorage.removeItem('refreshToken')
  })

  it('renders email and password fields', () => {
    render(
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>
    )

    expect(screen.getByLabelText(/email/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/password/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /log in/i })).toBeInTheDocument()
  })

  it('shows validation error for invalid email', async () => {
    const user = userEvent.setup()

    render(
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>
    )

    await user.type(screen.getByLabelText(/email/i), 'not-an-email')
    await user.click(screen.getByRole('button', { name: /log in/i }))

    expect(await screen.findByText(/valid email/i)).toBeInTheDocument()
  })
})
