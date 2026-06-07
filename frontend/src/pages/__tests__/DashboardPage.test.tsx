import { afterEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import axios from 'axios'
import DashboardPage from '../DashboardPage'
import { QueryClientWrapper } from '@/test/QueryClientWrapper'
import { analyticsService } from '@/services/analyticsService'
import { sessionService } from '@/services/sessionService'

vi.mock('@/services/analyticsService', () => ({
  analyticsService: {
    heatmap: vi.fn(),
    streak: vi.fn(),
  },
}))

vi.mock('@/services/sessionService', () => ({
  sessionService: {
    history: vi.fn(),
    getOpen: vi.fn(),
  },
}))

describe('DashboardPage', () => {
  afterEach(() => {
    vi.clearAllMocks()
  })

  it('renders the heatmap section', async () => {
    vi.mocked(analyticsService.heatmap).mockResolvedValue([])
    vi.mocked(analyticsService.streak).mockResolvedValue({ currentStreak: 0, longestStreak: 0 })
    vi.mocked(sessionService.history).mockResolvedValue([])
    vi.mocked(sessionService.getOpen).mockResolvedValue(null)

    render(
      <QueryClientWrapper>
        <MemoryRouter>
          <DashboardPage />
        </MemoryRouter>
      </QueryClientWrapper>
    )

    expect(await screen.findByRole('heading', { name: /activity/i })).toBeInTheDocument()
  })

  it('shows "Start session" when no open WorkoutSession exists', async () => {
    vi.mocked(analyticsService.heatmap).mockResolvedValue([])
    vi.mocked(analyticsService.streak).mockResolvedValue({ currentStreak: 0, longestStreak: 0 })
    vi.mocked(sessionService.history).mockResolvedValue([])
    vi.mocked(sessionService.getOpen).mockResolvedValue(null)

    render(
      <QueryClientWrapper>
        <MemoryRouter>
          <DashboardPage />
        </MemoryRouter>
      </QueryClientWrapper>
    )

    expect(await screen.findByRole('link', { name: 'Start session' })).toBeInTheDocument()
  })

  it('shows "Resume session" when an open WorkoutSession exists', async () => {
    const openSession = {
      id: 'session-1',
      workoutGroupId: 'group-1',
      workoutGroupTitle: 'Push Day',
      startedAt: '2026-06-07T09:00:00Z',
      completedAt: null,
      notes: null,
      setLogs: [],
      setCount: 0,
    }

    vi.mocked(analyticsService.heatmap).mockResolvedValue([])
    vi.mocked(analyticsService.streak).mockResolvedValue({ currentStreak: 0, longestStreak: 0 })
    vi.mocked(sessionService.history).mockResolvedValue([])
    vi.mocked(sessionService.getOpen).mockResolvedValue(openSession)

    render(
      <QueryClientWrapper>
        <MemoryRouter>
          <DashboardPage />
        </MemoryRouter>
      </QueryClientWrapper>
    )

    expect(await screen.findByRole('link', { name: 'Resume session' })).toBeInTheDocument()
  })

  it('shows "Start session" when GET /sessions/open returns 404 (no active session)', async () => {
    const notFoundError = Object.assign(new axios.AxiosError('Not Found'), {
      response: { status: 404, data: {}, headers: {}, config: {}, statusText: 'Not Found' },
    })

    vi.mocked(analyticsService.heatmap).mockResolvedValue([])
    vi.mocked(analyticsService.streak).mockResolvedValue({ currentStreak: 0, longestStreak: 0 })
    vi.mocked(sessionService.history).mockResolvedValue([])
    vi.mocked(sessionService.getOpen).mockRejectedValue(notFoundError)

    render(
      <QueryClientWrapper>
        <MemoryRouter>
          <DashboardPage />
        </MemoryRouter>
      </QueryClientWrapper>
    )

    expect(await screen.findByRole('link', { name: 'Start session' })).toBeInTheDocument()
  })
})
