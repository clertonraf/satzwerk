import { expect, it, vi, describe, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { QueryClientWrapper } from '@/test/QueryClientWrapper'
import AnalyticsPage, { TOP_EXERCISES_LIMIT } from '../AnalyticsPage'
import { analyticsService } from '@/services/analyticsService'
import { queryKeys } from '@/services/queryKeys'

vi.mock('@/services/analyticsService', () => ({
  analyticsService: {
    exerciseProgress: vi.fn(),
    topExercises: vi.fn(),
  },
}))

const mockProgress = {
  exerciseId: 'ex-1',
  exerciseName: 'Bench Press',
  points: [
    {
      sessionId: 'session-1',
      sessionDate: '2026-08-01',
      topSetWeightKg: 85,
      topSetReps: 6,
      estimatedOneRepMaxKg: 102,
    },
  ],
  recentSessions: [
    {
      sessionId: 'session-1',
      sessionDate: '2026-08-01',
      workoutGroupTitle: 'Push Day',
      topSetLabel: '85 kg × 6',
    },
  ],
}

describe('AnalyticsPage', () => {
  beforeEach(() => {
    vi.mocked(analyticsService.topExercises).mockReset()
    vi.mocked(analyticsService.exerciseProgress).mockReset()
  })

  it('renders the exercise selector and progress chart content', async () => {
    vi.mocked(analyticsService.topExercises).mockResolvedValue([
      { exerciseId: 'ex-1', exerciseName: 'Bench Press', setCount: 42 },
    ])
    vi.mocked(analyticsService.exerciseProgress).mockResolvedValue(mockProgress)

    render(
      <QueryClientWrapper>
        <MemoryRouter>
          <AnalyticsPage />
        </MemoryRouter>
      </QueryClientWrapper>,
    )

    expect(await screen.findByRole('heading', { name: 'Analytics' })).toBeInTheDocument()
    expect(await screen.findByText('Bench Press')).toBeInTheDocument()
    expect(await screen.findByText('Top set progression')).toBeInTheDocument()
    expect(await screen.findByText('Estimated 1RM')).toBeInTheDocument()
    expect(await screen.findByText('Recent sessions')).toBeInTheDocument()
    expect(await screen.findByText('Push Day')).toBeInTheDocument()
    expect(await screen.findByText('85 kg × 6')).toBeInTheDocument()
  })

  it('marks the default-selected exercise pill as active via aria-pressed', async () => {
    vi.mocked(analyticsService.topExercises).mockResolvedValue([
      { exerciseId: 'ex-1', exerciseName: 'Bench Press', setCount: 42 },
      { exerciseId: 'ex-2', exerciseName: 'Squat', setCount: 30 },
    ])
    vi.mocked(analyticsService.exerciseProgress).mockResolvedValue(mockProgress)

    render(
      <QueryClientWrapper>
        <MemoryRouter>
          <AnalyticsPage />
        </MemoryRouter>
      </QueryClientWrapper>,
    )

    const benchButton = await screen.findByRole('button', { name: 'Bench Press' })
    const squatButton = screen.getByRole('button', { name: 'Squat' })

    expect(benchButton).toHaveAttribute('aria-pressed', 'true')
    expect(squatButton).toHaveAttribute('aria-pressed', 'false')
  })

  it('renders error state when topExercises query fails', async () => {
    vi.mocked(analyticsService.topExercises).mockRejectedValue(new Error('Network error'))

    render(
      <QueryClientWrapper>
        <MemoryRouter>
          <AnalyticsPage />
        </MemoryRouter>
      </QueryClientWrapper>,
    )

    expect(await screen.findByText('Could not load exercises. Please try again later.')).toBeInTheDocument()
    expect(screen.queryByText('Log a workout to see your exercise analytics.')).toBeNull()
  })

  it('renders empty state when topExercises resolves to an empty array', async () => {
    vi.mocked(analyticsService.topExercises).mockResolvedValue([])

    render(
      <QueryClientWrapper>
        <MemoryRouter>
          <AnalyticsPage />
        </MemoryRouter>
      </QueryClientWrapper>,
    )

    expect(await screen.findByText('Log a workout to see your exercise analytics.')).toBeInTheDocument()
    expect(screen.queryByRole('button')).toBeNull()
  })

  it('falls back to first exercise when selectedExerciseId is not in the current topExercises list', async () => {
    // Step 1: start with two exercises (ex-1 default, ex-2 available)
    vi.mocked(analyticsService.topExercises).mockResolvedValue([
      { exerciseId: 'ex-1', exerciseName: 'Bench Press', setCount: 42 },
      { exerciseId: 'ex-2', exerciseName: 'Squat', setCount: 30 },
    ])
    vi.mocked(analyticsService.exerciseProgress).mockResolvedValue(mockProgress)

    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const { rerender } = render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter>
          <AnalyticsPage />
        </MemoryRouter>
      </QueryClientProvider>,
    )

    // Step 2: click ex-2 to make it the explicit selection
    const squatButton = await screen.findByRole('button', { name: 'Squat' })
    await userEvent.click(squatButton)
    expect(squatButton).toHaveAttribute('aria-pressed', 'true')

    // Step 3: topExercises refreshes — ex-2 disappears, only ex-1 remains
    vi.mocked(analyticsService.exerciseProgress).mockReset()
    vi.mocked(analyticsService.exerciseProgress).mockResolvedValue(mockProgress)
    queryClient.setQueryData(
      queryKeys.analytics.topExercises(TOP_EXERCISES_LIMIT),
      [{ exerciseId: 'ex-1', exerciseName: 'Bench Press', setCount: 42 }],
    )

    rerender(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter>
          <AnalyticsPage />
        </MemoryRouter>
      </QueryClientProvider>,
    )

    // Step 4: page falls back to ex-1 (the only remaining exercise)
    const benchButton = await screen.findByRole('button', { name: 'Bench Press' })
    expect(benchButton).toHaveAttribute('aria-pressed', 'true')
    expect(screen.queryByRole('button', { name: 'Squat' })).toBeNull()
    expect(vi.mocked(analyticsService.exerciseProgress)).toHaveBeenCalledWith('ex-1')
    expect(vi.mocked(analyticsService.exerciseProgress)).not.toHaveBeenCalledWith('ex-2')
  })

  it('shows a loading message while exercise progress is being fetched', async () => {
    vi.mocked(analyticsService.topExercises).mockResolvedValue([
      { exerciseId: 'ex-1', exerciseName: 'Bench Press', setCount: 42 },
    ])
    // Never resolves — keeps the query in loading state
    vi.mocked(analyticsService.exerciseProgress).mockReturnValue(new Promise(() => {}))

    render(
      <QueryClientWrapper>
        <MemoryRouter>
          <AnalyticsPage />
        </MemoryRouter>
      </QueryClientWrapper>,
    )

    // Exercise list loads first; then the progress query is pending
    await screen.findByRole('button', { name: 'Bench Press' })
    expect(await screen.findByText('Loading progress…')).toBeInTheDocument()
  })
})
