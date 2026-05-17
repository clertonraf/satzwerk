import { afterEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import RegisterPage from '../RegisterPage'
import { useAuthStore } from '@/store/auth'

vi.mock('@/services/authService', () => ({
  authService: {
    register: vi.fn(),
  },
}))

describe('RegisterPage', () => {
  afterEach(() => {
    useAuthStore.setState({ accessToken: null, user: null })
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
