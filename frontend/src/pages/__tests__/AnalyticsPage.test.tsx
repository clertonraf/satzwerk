import { expect, it, vi, describe, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClientWrapper } from '@/test/QueryClientWrapper'
import AnalyticsPage from '../AnalyticsPage'
import { analyticsService } from '@/services/analyticsService'

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
    expect(screen.getByText('Bench Press')).toBeInTheDocument()
    expect(await screen.findByText('Top set progression')).toBeInTheDocument()
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
