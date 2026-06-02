import { afterEach, describe, expect, it } from 'vitest'
import { act, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import ProtectedRoute from '../ProtectedRoute'
import { useAuthStore } from '@/store/auth'

describe('ProtectedRoute', () => {
  afterEach(() => {
    act(() => {
      useAuthStore.setState({ accessToken: null, user: null, isRestoring: false })
    })
  })

  it('redirects to /login when not authenticated', () => {
    act(() => {
      useAuthStore.setState({ accessToken: null, user: null, isRestoring: false })
    })

    render(
      <MemoryRouter initialEntries={['/dashboard']}>
        <Routes>
          <Route
            path="/dashboard"
            element={
              <ProtectedRoute>
                <div>secret</div>
              </ProtectedRoute>
            }
          />
          <Route path="/login" element={<div>login page</div>} />
        </Routes>
      </MemoryRouter>
    )

    expect(screen.getByText('login page')).toBeInTheDocument()
    expect(screen.queryByText('secret')).not.toBeInTheDocument()
  })

  it('renders children when authenticated', () => {
    act(() => {
      useAuthStore.setState({ accessToken: 'valid-token', user: null, isRestoring: false })
    })

    render(
      <MemoryRouter initialEntries={['/dashboard']}>
        <Routes>
          <Route
            path="/dashboard"
            element={
              <ProtectedRoute>
                <div>secret</div>
              </ProtectedRoute>
            }
          />
          <Route path="/login" element={<div>login page</div>} />
        </Routes>
      </MemoryRouter>
    )

    expect(screen.getByText('secret')).toBeInTheDocument()
  })

  it('renders nothing while session is being restored', () => {
    act(() => {
      useAuthStore.setState({ accessToken: null, user: null, isRestoring: true })
    })

    const { container } = render(
      <MemoryRouter initialEntries={['/dashboard']}>
        <Routes>
          <Route
            path="/dashboard"
            element={
              <ProtectedRoute>
                <div>secret</div>
              </ProtectedRoute>
            }
          />
          <Route path="/login" element={<div>login page</div>} />
        </Routes>
      </MemoryRouter>
    )

    expect(container.firstChild).toBeNull()
    expect(screen.queryByText('login page')).not.toBeInTheDocument()
    expect(screen.queryByText('secret')).not.toBeInTheDocument()
  })
})
