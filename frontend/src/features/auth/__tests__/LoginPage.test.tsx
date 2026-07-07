import { afterEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import axios from 'axios'
import LoginPage from '../LoginPage'
import { useAuthStore } from '@/store/auth'
import { authService } from '@/services/authService'

vi.mock('@/services/authService', () => ({
  authService: {
    login: vi.fn(),
  },
}))

const mockLogin = authService.login as ReturnType<typeof vi.fn>

function makeAxiosError(status: number | null, data?: unknown, hasRequest = true) {
  const err = new axios.AxiosError('error', undefined, undefined, hasRequest ? {} : undefined, status !== null
    ? ({ status, data } as unknown as import('axios').AxiosResponse)
    : undefined)
  return err
}

async function submitForm(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText(/email/i), 'user@example.com')
  await user.type(screen.getByLabelText(/password/i), 'password123')
  await user.click(screen.getByRole('button', { name: /log in/i }))
}

describe('LoginPage', () => {
  afterEach(() => {
    useAuthStore.setState({ accessToken: null, user: null })
    localStorage.removeItem('refreshToken')
    vi.clearAllMocks()
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

  it('shows "Incorrect email or password" on 401', async () => {
    const user = userEvent.setup()
    mockLogin.mockRejectedValueOnce(makeAxiosError(401))

    render(<MemoryRouter><LoginPage /></MemoryRouter>)
    await submitForm(user)

    expect(await screen.findByText('Incorrect email or password')).toBeInTheDocument()
  })

  it('shows connectivity message on network error (no response)', async () => {
    const user = userEvent.setup()
    mockLogin.mockRejectedValueOnce(makeAxiosError(null, undefined, true))

    render(<MemoryRouter><LoginPage /></MemoryRouter>)
    await submitForm(user)

    expect(await screen.findByText('Check your connection and try again')).toBeInTheDocument()
  })

  it('shows generic message on Axios error with no response and no request (setup/cancellation error)', async () => {
    const user = userEvent.setup()
    mockLogin.mockRejectedValueOnce(makeAxiosError(null, undefined, false))

    render(<MemoryRouter><LoginPage /></MemoryRouter>)
    await submitForm(user)

    expect(await screen.findByText('Unable to log in right now')).toBeInTheDocument()
  })

  it('shows rate-limit message on 429', async () => {
    const user = userEvent.setup()
    mockLogin.mockRejectedValueOnce(makeAxiosError(429))

    render(<MemoryRouter><LoginPage /></MemoryRouter>)
    await submitForm(user)

    expect(await screen.findByText('Too many attempts — please wait a moment')).toBeInTheDocument()
  })

  it('shows server-error message on 500', async () => {
    const user = userEvent.setup()
    mockLogin.mockRejectedValueOnce(makeAxiosError(500))

    render(<MemoryRouter><LoginPage /></MemoryRouter>)
    await submitForm(user)

    expect(await screen.findByText('Something went wrong on our end — try again shortly')).toBeInTheDocument()
  })

  it('shows server-error message on 503', async () => {
    const user = userEvent.setup()
    mockLogin.mockRejectedValueOnce(makeAxiosError(503))

    render(<MemoryRouter><LoginPage /></MemoryRouter>)
    await submitForm(user)

    expect(await screen.findByText('Something went wrong on our end — try again shortly')).toBeInTheDocument()
  })

  it('shows backend message on other HTTP error when message is present', async () => {
    const user = userEvent.setup()
    mockLogin.mockRejectedValueOnce(makeAxiosError(422, { message: 'Account suspended' }))

    render(<MemoryRouter><LoginPage /></MemoryRouter>)
    await submitForm(user)

    expect(await screen.findByText('Account suspended')).toBeInTheDocument()
  })

  it('shows generic message on other HTTP error when no backend message', async () => {
    const user = userEvent.setup()
    mockLogin.mockRejectedValueOnce(makeAxiosError(422, {}))

    render(<MemoryRouter><LoginPage /></MemoryRouter>)
    await submitForm(user)

    expect(await screen.findByText('Unable to log in right now')).toBeInTheDocument()
  })
})
