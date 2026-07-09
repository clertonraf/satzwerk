import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import axios from 'axios'
import DashboardPage from '../DashboardPage'
import { QueryClientWrapper } from '@/test/QueryClientWrapper'
import { analyticsService, type DashboardSummary } from '@/services/analyticsService'
import { sessionService } from '@/services/sessionService'
import { queryKeys } from '@/services/queryKeys'
import { useDashboardPreferences } from '@/store/dashboardPreferences'
import { useAuthStore } from '@/store/auth'

vi.mock('@/services/analyticsService', () => ({
  analyticsService: {
    heatmap: vi.fn(),
    streak: vi.fn(),
    summary: vi.fn(),
    weeklyTrend: vi.fn(),
    personalRecords: vi.fn(),
    topExercises: vi.fn(),
  },
}))

vi.mock('@/services/sessionService', () => ({
  sessionService: {
    history: vi.fn(),
    getOpen: vi.fn(),
  },
}))

const defaultSummary: DashboardSummary = {
  currentStreak: 0,
  longestStreak: 0,
  sessionsThisMonth: 0,
  prsThisMonth: 0,
  totalSessions: 0,
  setsThisWeek: 0,
  activePlanDays: null,
  avgSessionDurationMinutes: null,
}

describe('DashboardPage', () => {
  const notFoundError = Object.assign(new axios.AxiosError('Not Found'), {
    response: { status: 404, data: {}, headers: {}, config: {}, statusText: 'Not Found' },
  })

  // Minimal fake JWT with sub='user-1' encoded as base64url (matching real JWT format).
  const fakeJwtPayload = btoa(JSON.stringify({ sub: 'user-1' })).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_')
  const fakeJwt = `header.${fakeJwtPayload}.sig`

  beforeEach(() => {
    useAuthStore.setState({ accessToken: fakeJwt })
    useDashboardPreferences.setState({ visibleWidgets: {} })
    vi.mocked(analyticsService.heatmap).mockReset()
    vi.mocked(analyticsService.streak).mockReset()
    vi.mocked(analyticsService.summary).mockReset()
    vi.mocked(analyticsService.weeklyTrend).mockReset()
    vi.mocked(analyticsService.personalRecords).mockReset()
    vi.mocked(analyticsService.topExercises).mockReset()
    vi.mocked(sessionService.history).mockReset()
    vi.mocked(sessionService.getOpen).mockReset()
    vi.mocked(analyticsService.heatmap).mockResolvedValue([])
    vi.mocked(analyticsService.streak).mockResolvedValue({ currentStreak: 0, longestStreak: 0 })
    vi.mocked(analyticsService.summary).mockResolvedValue(defaultSummary)
    vi.mocked(analyticsService.weeklyTrend).mockResolvedValue([])
    vi.mocked(analyticsService.personalRecords).mockResolvedValue([])
    vi.mocked(analyticsService.topExercises).mockResolvedValue([])
    vi.mocked(sessionService.history).mockResolvedValue([])
    vi.mocked(sessionService.getOpen).mockRejectedValue(notFoundError)
  })

  afterEach(() => {
    vi.resetAllMocks()
    useDashboardPreferences.setState({ visibleWidgets: {} })
    localStorage.removeItem('satzwerk-dashboard-prefs')
    useAuthStore.setState({ accessToken: null })
  })

  it('renders the heatmap section', async () => {
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

    expect(await screen.findByRole('heading', { name: /activity/i })).toBeInTheDocument()
  })

  it('shows "Start session" when no open WorkoutSession exists (404)', async () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })

    vi.mocked(analyticsService.heatmap).mockResolvedValue([])
    vi.mocked(analyticsService.streak).mockResolvedValue({ currentStreak: 0, longestStreak: 0 })
    vi.mocked(sessionService.history).mockResolvedValue([])
    vi.mocked(sessionService.getOpen).mockRejectedValue(notFoundError)

    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter>
          <DashboardPage />
        </MemoryRouter>
      </QueryClientProvider>
    )

    expect(await screen.findByRole('link', { name: 'Start session' })).toBeInTheDocument()

    await waitFor(() => {
      const state = queryClient.getQueryState(queryKeys.sessions.open())
      expect(state?.status).toBe('success')
      expect(state?.data).toBeNull()
    })
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
      exerciseCount: 0,
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

  it('renders TopExercisesCard with exercises when query resolves with data', async () => {
    vi.mocked(analyticsService.topExercises).mockResolvedValue([
      { exerciseId: 'ex-1', exerciseName: 'Bench Press', setCount: 42 },
    ])

    render(
      <QueryClientWrapper>
        <MemoryRouter>
          <DashboardPage />
        </MemoryRouter>
      </QueryClientWrapper>
    )

    expect(await screen.findByText('Bench Press')).toBeInTheDocument()
    expect(screen.getByText('42 sets')).toBeInTheDocument()
  })

  it('does not render TopExercisesCard content when query errors', async () => {
    vi.mocked(analyticsService.topExercises).mockRejectedValue(new Error('Network error'))

    render(
      <QueryClientWrapper>
        <MemoryRouter>
          <DashboardPage />
        </MemoryRouter>
      </QueryClientWrapper>
    )

    expect(await screen.findByRole('link', { name: 'Start session' })).toBeInTheDocument()
    expect(screen.queryByText('Most trained exercises')).not.toBeInTheDocument()
  })

  it('does not render LastSessionCard when last-session widget is hidden', async () => {
    useDashboardPreferences.getState().setVisibleWidgets('user-1', ['summary-grid', 'activity-heatmap', 'recent-prs', 'weekly-trend'])
    vi.mocked(sessionService.history).mockResolvedValue([
      {
        id: 'session-1',
        workoutGroupId: 'group-1',
        workoutGroupTitle: 'Push Day',
        startedAt: '2026-06-07T09:00:00Z',
        completedAt: '2026-06-07T10:00:00Z',
        notes: null,
        setLogs: [],
        setCount: 0,
        exerciseCount: 0,
      },
    ])

    render(
      <QueryClientWrapper>
        <MemoryRouter>
          <DashboardPage />
        </MemoryRouter>
      </QueryClientWrapper>
    )

    // Wait for the page to load
    expect(await screen.findByRole('link', { name: 'Start session' })).toBeInTheDocument()
    expect(screen.queryByText('Last Session')).not.toBeInTheDocument()
  })

  it('does not render the weekly trend section when weekly-trend widget is hidden', async () => {
    useDashboardPreferences.getState().setVisibleWidgets('user-1', ['summary-grid', 'activity-heatmap', 'last-session', 'recent-prs'])

    render(
      <QueryClientWrapper>
        <MemoryRouter>
          <DashboardPage />
        </MemoryRouter>
      </QueryClientWrapper>
    )

    expect(await screen.findByRole('link', { name: 'Start session' })).toBeInTheDocument()
    expect(screen.queryByText('Weekly Trend')).not.toBeInTheDocument()
  })
})
