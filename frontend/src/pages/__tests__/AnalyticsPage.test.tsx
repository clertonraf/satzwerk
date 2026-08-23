import { expect, it, vi, describe, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { QueryClientWrapper } from '@/test/QueryClientWrapper'
import AnalyticsPage from '../AnalyticsPage'
import { analyticsService } from '@/services/analyticsService'
import { exerciseService } from '@/services/exerciseService'
import { queryKeys } from '@/services/queryKeys'

vi.mock('@/services/analyticsService', () => ({
  analyticsService: {
    exerciseProgress: vi.fn(),
  },
}))

vi.mock('@/services/exerciseService', () => ({
  exerciseService: {
    list: vi.fn(),
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

function buildExercise(id: string, name: string, muscleGroup: string) {
  return {
    id,
    name,
    muscleGroup,
    description: null,
    videoUrl: null,
    equipment: null,
    createdAt: '',
    updatedAt: '',
  }
}

describe('AnalyticsPage', () => {
  beforeEach(() => {
    vi.mocked(exerciseService.list).mockReset()
    vi.mocked(analyticsService.exerciseProgress).mockReset()
  })

  it('renders the exercise selector and progress chart content', async () => {
    vi.mocked(exerciseService.list).mockResolvedValue([buildExercise('ex-1', 'Bench Press', 'CHEST')])
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
    vi.mocked(exerciseService.list).mockResolvedValue([
      buildExercise('ex-1', 'Bench Press', 'CHEST'),
      buildExercise('ex-2', 'Squat', 'LEGS'),
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

  it('renders error state when exercise list query fails', async () => {
    vi.mocked(exerciseService.list).mockRejectedValue(new Error('Network error'))

    render(
      <QueryClientWrapper>
        <MemoryRouter>
          <AnalyticsPage />
        </MemoryRouter>
      </QueryClientWrapper>,
    )

    expect(await screen.findByText('Could not load exercises. Please try again later.')).toBeInTheDocument()
    expect(screen.queryByText('Create an exercise to see analytics.')).toBeNull()
  })

  it('renders empty state when the exercise list resolves to an empty array', async () => {
    vi.mocked(exerciseService.list).mockResolvedValue([])

    render(
      <QueryClientWrapper>
        <MemoryRouter>
          <AnalyticsPage />
        </MemoryRouter>
      </QueryClientWrapper>,
    )

    expect(await screen.findByText('Create an exercise to see analytics.')).toBeInTheDocument()
    expect(screen.queryByRole('button')).toBeNull()
  })

  it('falls back to first exercise when selectedExerciseId is not in the current exercise list', async () => {
    vi.mocked(exerciseService.list).mockResolvedValue([
      buildExercise('ex-1', 'Bench Press', 'CHEST'),
      buildExercise('ex-2', 'Squat', 'LEGS'),
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
    queryClient.setQueryData(queryKeys.exercises.all(), [buildExercise('ex-1', 'Bench Press', 'CHEST')])

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

  it('shows the exercise pill, per-exercise empty state, and no recent-sessions section when progress has no data', async () => {
    vi.mocked(exerciseService.list).mockResolvedValue([buildExercise('ex-1', 'Bench Press', 'CHEST')])
    vi.mocked(analyticsService.exerciseProgress).mockResolvedValue({
      exerciseId: 'ex-1',
      exerciseName: 'Bench Press',
      points: [],
      recentSessions: [],
    })

    render(
      <QueryClientWrapper>
        <MemoryRouter>
          <AnalyticsPage />
        </MemoryRouter>
      </QueryClientWrapper>,
    )

    // Exercise pill is still rendered and marked as selected
    const pill = await screen.findByRole('button', { name: 'Bench Press' })
    expect(pill).toHaveAttribute('aria-pressed', 'true')

    // Per-exercise empty-state message is shown
    expect(await screen.findByText('No completed sessions for this Exercise yet.')).toBeInTheDocument()

    // Recent-sessions section is absent — heading and any session rows must not exist
    expect(screen.queryByText('Recent sessions')).toBeNull()
  })

  it('renders progress error state when exerciseProgress query fails', async () => {
    vi.mocked(exerciseService.list).mockResolvedValue([buildExercise('ex-1', 'Bench Press', 'CHEST')])
    vi.mocked(analyticsService.exerciseProgress).mockRejectedValue(new Error('Network error'))

    render(
      <QueryClientWrapper>
        <MemoryRouter>
          <AnalyticsPage />
        </MemoryRouter>
      </QueryClientWrapper>,
    )

    await screen.findByRole('button', { name: 'Bench Press' })
    expect(
      await screen.findByText('Could not load progress for this Exercise. Please try again later.'),
    ).toBeInTheDocument()
    // Chart and history must not appear in an error state
    expect(screen.queryByText('Top set progression')).toBeNull()
    expect(screen.queryByText('Recent sessions')).toBeNull()
    expect(screen.queryByText('No completed sessions for this Exercise yet.')).toBeNull()
  })

  it('shows a loading message while exercise progress is being fetched', async () => {
    vi.mocked(exerciseService.list).mockResolvedValue([buildExercise('ex-1', 'Bench Press', 'CHEST')])
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
