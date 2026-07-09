import { afterEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import HistoryPage from '../HistoryPage'
import { QueryClientWrapper } from '@/test/QueryClientWrapper'
import { sessionService } from '@/services/sessionService'
import { planService } from '@/services/planService'
import { exerciseService } from '@/services/exerciseService'
import type { WorkoutSession } from '@/services/sessionService'
import type { WorkoutPlan, WorkoutPlanDetail } from '@/services/planService'

vi.mock('@/services/sessionService', () => ({
  sessionService: {
    history: vi.fn(),
  },
}))

vi.mock('@/services/planService', () => ({
  planService: {
    list: vi.fn(),
    get: vi.fn(),
  },
}))

vi.mock('@/services/exerciseService', () => ({
  exerciseService: {
    list: vi.fn(),
  },
}))

const GROUP_ID = 'group-1'
const PLAN_ID = 'plan-1'

function makeSession(overrides: Partial<WorkoutSession> = {}): WorkoutSession {
  return {
    id: 'session-1',
    workoutGroupId: GROUP_ID,
    workoutGroupTitle: 'Push Day',
    startedAt: '2026-06-01T10:00:00Z',
    completedAt: '2026-06-01T11:00:00Z',
    notes: null,
    setLogs: [],
    setCount: 12,
    ...overrides,
  }
}

const mockPlan: WorkoutPlan = {
  id: PLAN_ID,
  name: 'My Plan',
  source: 'manual',
  isActive: true,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
}

function makePlanDetail(totalSetsPerExercise: number): WorkoutPlanDetail {
  return {
    ...mockPlan,
    groups: [
      {
        id: GROUP_ID,
        title: 'Push Day',
        orderIndex: 0,
        exercises: [
          {
            id: 'we-1',
            exerciseId: 'ex-1',
            exerciseName: 'Bench Press',
            sets: totalSetsPerExercise,
            reps: 10,
            advancedTechnique: null,
            toFailure: false,
            orderIndex: 0,
          },
          {
            id: 'we-2',
            exerciseId: 'ex-2',
            exerciseName: 'Overhead Press',
            sets: totalSetsPerExercise,
            reps: 10,
            advancedTechnique: null,
            toFailure: false,
            orderIndex: 1,
          },
          {
            id: 'we-3',
            exerciseId: 'ex-3',
            exerciseName: 'Tricep Extension',
            sets: totalSetsPerExercise,
            reps: 10,
            advancedTechnique: null,
            toFailure: false,
            orderIndex: 2,
          },
        ],
      },
    ],
  }
}

describe('HistoryPage', () => {
  afterEach(() => {
    vi.clearAllMocks()
  })

  it('renders the completion percentage when totalTargetSets > 0', async () => {
    // 3 exercises × 5 sets = 15 target sets; session.setCount = 12 → 12/15 = 80%
    vi.mocked(sessionService.history).mockResolvedValue([makeSession({ setCount: 12 })])
    vi.mocked(planService.list).mockResolvedValue([mockPlan])
    vi.mocked(planService.get).mockResolvedValue(makePlanDetail(5))
    vi.mocked(exerciseService.list).mockResolvedValue([])

    render(
      <QueryClientWrapper>
        <MemoryRouter>
          <HistoryPage />
        </MemoryRouter>
      </QueryClientWrapper>,
    )

    expect(await screen.findByText('80% sets completed')).toBeInTheDocument()
  })

  it('omits the completion percentage when totalTargetSets is 0', async () => {
    // Plan has no exercises → targetSets = 0 → no percentage rendered
    vi.mocked(sessionService.history).mockResolvedValue([makeSession({ setCount: 5 })])
    vi.mocked(planService.list).mockResolvedValue([mockPlan])
    vi.mocked(planService.get).mockResolvedValue({ ...mockPlan, groups: [{ id: GROUP_ID, title: 'Push Day', orderIndex: 0, exercises: [] }] })
    vi.mocked(exerciseService.list).mockResolvedValue([])

    render(
      <QueryClientWrapper>
        <MemoryRouter>
          <HistoryPage />
        </MemoryRouter>
      </QueryClientWrapper>,
    )

    await screen.findByText('Push Day')
    expect(screen.queryByText(/% sets completed/i)).not.toBeInTheDocument()
  })
})
