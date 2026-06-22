import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import ExerciseSection from '../ExerciseSection'
import type { SetLog } from '@/services/sessionService'
import { formatDisplayWeight } from '@/lib/unitFormatters'

const makeExercise = () => ({
  id: 'we-1',
  exerciseId: 'exercise-1',
  exerciseName: 'Bench Press',
  sets: 4,
  reps: 8,
  advancedTechnique: null,
  toFailure: false,
  orderIndex: 0,
})

const makeSetLog = (overrides: Partial<SetLog> = {}): SetLog => ({
  id: 'log-1',
  exerciseId: 'exercise-1',
  setNumber: 1,
  weight: 80,
  reps: 5,
  loggedAt: '2026-01-01T00:00:00Z',
  ...overrides,
})

const defaultProps = {
  exercise: makeExercise(),
  exerciseName: 'Bench Press',
  exerciseUnit: 'kg' as const,
  exerciseLogs: [],
  referenceWeights: undefined,
  isReferenceWeightsLoading: false,
  isAddSetPending: false,
  isUpdateSetPending: false,
  isOnline: true,
  onLogSet: vi.fn(),
  onUpdateSetLog: vi.fn(),
  onSetExerciseUnit: vi.fn(),
}

function renderSection(props = {}) {
  render(<ExerciseSection {...defaultProps} {...props} />)
}

describe('ExerciseSection', () => {
  it('renders exercise name and target sets/reps', () => {
    renderSection()
    expect(screen.getByText('Bench Press')).toBeInTheDocument()
    expect(screen.getByText('Target 4 sets × 8 reps')).toBeInTheDocument()
  })

  it('shows "No sets logged yet" when exerciseLogs is empty', () => {
    renderSection()
    expect(screen.getByText('No sets logged yet.')).toBeInTheDocument()
  })

  it('renders logged sets with formatted weight', () => {
    renderSection({ exerciseLogs: [makeSetLog()] })
    expect(screen.getByText(`Set 1: ${formatDisplayWeight(80, 'kg')} × 5`)).toBeInTheDocument()
  })

  it('renders logged sets in lb when unit is lb', () => {
    renderSection({
      exerciseLogs: [makeSetLog()],
      exerciseUnit: 'lb',
    })
    expect(screen.getByText(`Set 1: ${formatDisplayWeight(80, 'lb')} × 5`)).toBeInTheDocument()
  })

  it('shows edit button for each logged set', () => {
    renderSection({ exerciseLogs: [makeSetLog()] })
    expect(screen.getByRole('button', { name: 'Edit' })).toBeInTheDocument()
  })

  it('disables Edit for queued set logs', () => {
    renderSection({ exerciseLogs: [makeSetLog({ id: 'queued-session-1-exercise-1-1-123' })] })
    expect(screen.getByRole('button', { name: 'Edit' })).toBeDisabled()
  })

  it('calls onSetExerciseUnit when unit toggle is clicked', async () => {
    const onSetExerciseUnit = vi.fn()
    renderSection({ onSetExerciseUnit })
    await userEvent.click(screen.getByRole('button', { name: 'lb' }))
    expect(onSetExerciseUnit).toHaveBeenCalledWith('exercise-1', 'lb')
  })
})
