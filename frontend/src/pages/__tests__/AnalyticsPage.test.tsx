import { expect, it, vi } from 'vitest'
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

it('renders the exercise selector and progress chart content', async () => {
  vi.mocked(analyticsService.topExercises).mockResolvedValue([
    { exerciseId: 'ex-1', exerciseName: 'Bench Press', setCount: 42 },
  ])
  vi.mocked(analyticsService.exerciseProgress).mockResolvedValue({
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
  })

  render(
    <QueryClientWrapper>
      <MemoryRouter>
        <AnalyticsPage />
      </MemoryRouter>
    </QueryClientWrapper>,
  )

  expect(await screen.findByRole('heading', { name: 'Analytics' })).toBeInTheDocument()
  expect(screen.getByText('Bench Press')).toBeInTheDocument()
  expect(screen.getByText('Top set progression')).toBeInTheDocument()
  expect(screen.getByText('85 kg × 6')).toBeInTheDocument()
})
