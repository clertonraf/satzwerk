import { afterEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import WorkoutGroupSection from '../WorkoutGroupSection'
import { workoutExerciseService } from '@/services/workoutExerciseService'
import { QueryClientWrapper } from '@/test/QueryClientWrapper'

vi.mock('@/services/workoutExerciseService', async () => {
  const actual = await vi.importActual<typeof import('@/services/workoutExerciseService')>('@/services/workoutExerciseService')

  return {
    ...actual,
    workoutExerciseService: {
      ...actual.workoutExerciseService,
      reorder: vi.fn(),
    },
  }
})

const group = {
  id: 'g1',
  title: 'Treino A',
  orderIndex: 0,
  exercises: [{ id: 'e1', exerciseId: 'ex1', exerciseName: 'Bench Press', sets: 4, reps: 8, advancedTechnique: null, toFailure: false, orderIndex: 0 }],
}

describe('WorkoutGroupSection', () => {
  afterEach(() => {
    vi.clearAllMocks()
  })

  it('renders group title', () => {
    render(
      <QueryClientWrapper>
        <MemoryRouter>
          <WorkoutGroupSection planId="plan-1" group={group} onDeleteGroup={vi.fn()} onDeleteExercise={vi.fn()} />
        </MemoryRouter>
      </QueryClientWrapper>
    )

    expect(screen.getByText('Treino A')).toBeInTheDocument()
  })

  it('renders exercise row for each exercise', () => {
    render(
      <QueryClientWrapper>
        <MemoryRouter>
          <WorkoutGroupSection planId="plan-1" group={group} onDeleteGroup={vi.fn()} onDeleteExercise={vi.fn()} />
        </MemoryRouter>
      </QueryClientWrapper>
    )

    expect(screen.getByText(/4 sets/i)).toBeInTheDocument()
    expect(screen.getByText(/8 reps/i)).toBeInTheDocument()
  })

  it('reorders exercises when move down is clicked', async () => {
    const user = userEvent.setup()

    vi.mocked(workoutExerciseService.reorder).mockResolvedValue(undefined)

    render(
      <QueryClientWrapper>
        <MemoryRouter>
          <WorkoutGroupSection
            planId="plan-1"
            group={{
              ...group,
              exercises: [
                group.exercises[0],
                { id: 'e2', exerciseId: 'ex2', exerciseName: 'Squat', sets: 3, reps: 12, advancedTechnique: null, toFailure: false, orderIndex: 1 },
              ],
            }}
            onDeleteGroup={vi.fn()}
            onDeleteExercise={vi.fn()}
          />
        </MemoryRouter>
      </QueryClientWrapper>
    )

    await user.click(screen.getAllByRole('button', { name: /move down/i })[0])

    await waitFor(() => {
      expect(workoutExerciseService.reorder).toHaveBeenCalledWith('plan-1', 'g1', 'e1', 'DOWN')
    })
  })
})
