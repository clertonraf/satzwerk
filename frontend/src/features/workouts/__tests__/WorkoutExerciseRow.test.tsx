import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import WorkoutExerciseRow from '../WorkoutExerciseRow'

const exercise = {
  id: 'e1',
  exerciseId: 'ex1',
  exerciseName: 'Bench Press',
  sets: 3,
  reps: 10,
  advancedTechnique: null,
  toFailure: false,
  orderIndex: 0,
}

describe('WorkoutExerciseRow', () => {
  it('renders up and down buttons', () => {
    render(
      <WorkoutExerciseRow
        exercise={exercise}
        onMoveUp={vi.fn()}
        onMoveDown={vi.fn()}
        onDelete={vi.fn()}
        isFirst={false}
        isLast={false}
      />
    )

    expect(screen.getByRole('button', { name: /move up/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /move down/i })).toBeInTheDocument()
  })

  it('disables Up button when isFirst=true', () => {
    render(
      <WorkoutExerciseRow
        exercise={exercise}
        onMoveUp={vi.fn()}
        onMoveDown={vi.fn()}
        onDelete={vi.fn()}
        isFirst={true}
        isLast={false}
      />
    )

    expect(screen.getByRole('button', { name: /move up/i })).toBeDisabled()
  })

  it('disables Down button when isLast=true', () => {
    render(
      <WorkoutExerciseRow
        exercise={exercise}
        onMoveUp={vi.fn()}
        onMoveDown={vi.fn()}
        onDelete={vi.fn()}
        isFirst={false}
        isLast={true}
      />
    )

    expect(screen.getByRole('button', { name: /move down/i })).toBeDisabled()
  })

  it('calls onMoveUp when Up clicked', async () => {
    const user = userEvent.setup()
    const onMoveUp = vi.fn()

    render(
      <WorkoutExerciseRow
        exercise={exercise}
        onMoveUp={onMoveUp}
        onMoveDown={vi.fn()}
        onDelete={vi.fn()}
        isFirst={false}
        isLast={false}
      />
    )

    await user.click(screen.getByRole('button', { name: /move up/i }))

    expect(onMoveUp).toHaveBeenCalledWith('e1')
  })
})
