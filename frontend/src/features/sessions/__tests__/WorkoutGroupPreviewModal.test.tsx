import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import WorkoutGroupPreviewModal from '../WorkoutGroupPreviewModal'
import type { WorkoutGroupDetail } from '@/services/planService'

const baseGroup: WorkoutGroupDetail = {
  id: 'group-1',
  title: 'Push Day',
  orderIndex: 0,
  exercises: [
    {
      id: 'we-1',
      exerciseId: 'ex-1',
      exerciseName: 'Bench Press',
      sets: 4,
      reps: 8,
      advancedTechnique: null,
      toFailure: false,
      orderIndex: 0,
    },
    {
      id: 'we-2',
      exerciseId: 'ex-2',
      exerciseName: 'Overhead Press',
      sets: 3,
      reps: 10,
      advancedTechnique: 'SST',
      toFailure: false,
      orderIndex: 1,
    },
    {
      id: 'we-3',
      exerciseId: 'ex-3',
      exerciseName: 'Tricep Pushdown',
      sets: 3,
      reps: 0,
      advancedTechnique: null,
      toFailure: true,
      orderIndex: 2,
    },
  ],
}

describe('WorkoutGroupPreviewModal', () => {
  it('renders group title and plan name', () => {
    render(<WorkoutGroupPreviewModal group={baseGroup} planName="Plan A" onClose={vi.fn()} />)

    expect(screen.getByText('Push Day')).toBeInTheDocument()
    expect(screen.getByText('Plan A')).toBeInTheDocument()
  })

  it('renders all exercises with sets × reps', () => {
    render(<WorkoutGroupPreviewModal group={baseGroup} planName="Plan A" onClose={vi.fn()} />)

    expect(screen.getByText('Bench Press')).toBeInTheDocument()
    expect(screen.getByText('4 sets × 8 reps')).toBeInTheDocument()

    expect(screen.getByText('Overhead Press')).toBeInTheDocument()
    expect(screen.getByText('3 sets × 10 reps')).toBeInTheDocument()
  })

  it('shows "to failure" instead of reps when toFailure is true', () => {
    render(<WorkoutGroupPreviewModal group={baseGroup} planName="Plan A" onClose={vi.fn()} />)

    expect(screen.getByText('Tricep Pushdown')).toBeInTheDocument()
    expect(screen.getByText('3 sets × to failure')).toBeInTheDocument()
  })

  it('renders advanced technique badge when present', () => {
    render(<WorkoutGroupPreviewModal group={baseGroup} planName="Plan A" onClose={vi.fn()} />)

    expect(screen.getByText('SST')).toBeInTheDocument()
  })

  it('calls onClose when dialog is dismissed', async () => {
    const user = userEvent.setup()
    const onClose = vi.fn()

    render(<WorkoutGroupPreviewModal group={baseGroup} planName="Plan A" onClose={onClose} />)

    await user.keyboard('{Escape}')

    expect(onClose).toHaveBeenCalled()
  })

  it('shows empty state when group has no exercises', () => {
    const emptyGroup: WorkoutGroupDetail = { ...baseGroup, exercises: [] }

    render(<WorkoutGroupPreviewModal group={emptyGroup} planName="Plan A" onClose={vi.fn()} />)

    expect(screen.getByText('No exercises in this workout group.')).toBeInTheDocument()
  })
})
