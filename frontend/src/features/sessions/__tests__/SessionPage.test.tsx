import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import axios, { type AxiosError } from 'axios'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import SessionPage from '../SessionPage'
import { QueryClientWrapper } from '@/test/QueryClientWrapper'
import { exerciseService } from '@/services/exerciseService'
import type { WorkoutPlanDetail } from '@/services/planService'
import { sessionService } from '@/services/sessionService'
import { useWorkoutSession } from '@/features/sessions/useWorkoutSession'
import { useOnlineStatus } from '@/hooks/useOnlineStatus'

vi.mock('@/services/sessionService', () => ({
  sessionService: {
    getStartOptions: vi.fn(),
    getOpenPlanDetail: vi.fn(),
    history: vi.fn(),
    getReferenceWeights: vi.fn(),
  },
}))

vi.mock('@/services/exerciseService', () => ({
  exerciseService: {
    list: vi.fn(),
  },
}))

vi.mock('@/features/sessions/useWorkoutSession', () => ({
  useWorkoutSession: vi.fn(),
}))

vi.mock('@/hooks/useOnlineStatus', () => ({
  useOnlineStatus: vi.fn(),
}))

const defaultWorkoutSessionState = {
  session: null,
  conflictSession: null,
  stalePlanError: null,
  isSessionLoading: false,
  handleStartSession: vi.fn(),
  handleLogSet: vi.fn(),
  handleUpdateSetLog: vi.fn(),
  handleDeleteSetLog: vi.fn(),
  handleCompleteSession: vi.fn(),
  handleForfeitSession: vi.fn(),
  handleDiscardConflict: vi.fn(),
  clearConflictState: vi.fn(),
  isStartPending: false,
  isAddSetPending: false,
  isUpdateSetPending: false,
  isDeleteSetPending: false,
  isCompletePending: false,
  isForfeitPending: false,
}

function renderPage() {
  render(
    <QueryClientWrapper>
      <MemoryRouter>
        <SessionPage />
      </MemoryRouter>
    </QueryClientWrapper>
  )
}

function buildWorkoutPlanDetail(overrides?: Partial<WorkoutPlanDetail>): WorkoutPlanDetail {
  return {
    id: 'plan-1',
    name: 'Push Pull Legs',
    source: 'MANUAL',
    isActive: true,
    createdAt: '2024-01-01T00:00:00Z',
    updatedAt: '2024-01-01T00:00:00Z',
    groups: [
      {
        id: 'group-1',
        title: 'Push Day',
        orderIndex: 0,
        exercises: [],
      },
    ],
    ...overrides,
  }
}

describe('SessionPage', () => {
  beforeEach(() => {
    vi.mocked(useWorkoutSession).mockReturnValue(defaultWorkoutSessionState)
    vi.mocked(useOnlineStatus).mockReturnValue(true)
    vi.mocked(exerciseService.list).mockResolvedValue([])
    vi.mocked(sessionService.history).mockResolvedValue([])
    vi.mocked(sessionService.getReferenceWeights).mockResolvedValue([])
    vi.mocked(sessionService.getStartOptions).mockResolvedValue(buildWorkoutPlanDetail())
    vi.mocked(sessionService.getOpenPlanDetail).mockResolvedValue(buildWorkoutPlanDetail())
  })

  afterEach(() => {
    vi.restoreAllMocks()
    vi.clearAllMocks()
  })

  it('shows loading state while start options are loading', async () => {
    vi.mocked(sessionService.getStartOptions).mockImplementation(() => new Promise(() => undefined))

    renderPage()

    expect(await screen.findByText('Loading workout groups...')).toBeInTheDocument()
    expect(sessionService.getStartOptions).toHaveBeenCalledTimes(1)
  })

  it('shows no-active-plan message when start-options returns null', async () => {
    const notFoundError = Object.assign(new Error('Not Found'), {
      isAxiosError: true,
      response: { status: 404 },
    })
    vi.mocked(sessionService.getStartOptions).mockRejectedValue(notFoundError)
    vi.spyOn(axios, 'isAxiosError').mockImplementation(
      (error): error is AxiosError => (error as { isAxiosError?: boolean }).isAxiosError === true
    )

    renderPage()

    expect(await screen.findByText(/no active plan/i)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Plans' })).toHaveAttribute('href', '/plans')
    expect(sessionService.getStartOptions).toHaveBeenCalledTimes(1)
  })

  it('shows workout groups from active plan', async () => {
    vi.mocked(sessionService.getStartOptions).mockResolvedValue(
      buildWorkoutPlanDetail({
        groups: [
          {
            id: 'group-1',
            title: 'Push Day',
            orderIndex: 0,
            exercises: [],
          },
        ],
      })
    )

    renderPage()

    expect(await screen.findByText('Push Day')).toBeInTheDocument()
    expect(screen.getByText(/Push Pull Legs · 0 exercises/i)).toBeInTheDocument()
    expect(sessionService.getStartOptions).toHaveBeenCalledTimes(1)
  })

  it('shows no workout groups message when active plan has no groups', async () => {
    vi.mocked(sessionService.getStartOptions).mockResolvedValue(buildWorkoutPlanDetail({ groups: [] }))

    renderPage()

    expect(await screen.findByText(/no workout groups found yet/i)).toBeInTheDocument()
    expect(sessionService.getStartOptions).toHaveBeenCalledTimes(1)
  })

  it('shows exercises for an existing workout session after page refresh', async () => {
    vi.mocked(useWorkoutSession).mockReturnValue({
      ...defaultWorkoutSessionState,
      session: {
        id: 'session-1',
        workoutGroupId: 'group-1',
        workoutGroupTitle: 'Push Day',
        startedAt: '2024-01-01T00:00:00Z',
        completedAt: null,
        notes: null,
        setLogs: [],
        setCount: 0,
      },
    })
    vi.mocked(exerciseService.list).mockResolvedValue([
      {
        id: 'exercise-1',
        name: 'Bench Press',
        muscleGroup: 'CHEST',
        description: null,
        videoUrl: null,
        equipment: null,
        createdAt: '2024-01-01T00:00:00Z',
        updatedAt: '2024-01-01T00:00:00Z',
      },
    ])
    vi.mocked(sessionService.getOpenPlanDetail).mockResolvedValue(
      buildWorkoutPlanDetail({
        isActive: true,
        groups: [
          {
            id: 'group-1',
            title: 'Push Day',
            orderIndex: 0,
            exercises: [
              {
                id: 'workout-exercise-1',
                exerciseId: 'exercise-1',
                exerciseName: 'Bench Press',
                sets: 4,
                reps: 8,
                advancedTechnique: null,
                toFailure: false,
                orderIndex: 0,
              },
            ],
          },
        ],
      })
    )

    renderPage()

    expect(await screen.findByText('Bench Press')).toBeInTheDocument()
    expect(screen.getByText(/Target 4 sets × 8 reps/i)).toBeInTheDocument()
  })
})
