import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import ExerciseSection from '../ExerciseSection'
import type { PendingSetLog, SubmittedSetLog } from '@/services/sessionService'
import { formatDisplayWeight } from '@/lib/unitFormatters'
import { advancedTechniqueMetadataFixture } from '@/test/advancedTechniqueMetadata'

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

const makeSetLog = (overrides: Partial<SubmittedSetLog> = {}): SubmittedSetLog => ({
  id: 'log-1',
  exerciseId: 'exercise-1',
  setNumber: 1,
  weight: 80,
  reps: 5,
  loggedAt: '2026-01-01T00:00:00Z',
  pending: false,
  ...overrides,
})

const makePendingSetLog = (overrides: Partial<PendingSetLog> = {}): PendingSetLog => ({
  id: 'queued-session-1-exercise-1-1-123',
  exerciseId: 'exercise-1',
  setNumber: 1,
  weight: 80,
  reps: 5,
  loggedAt: '2026-01-01T00:00:00Z',
  pending: true,
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
  isDeleteSetPending: false,
  isOnline: true,
  onLogSet: vi.fn(),
  onUpdateSetLog: vi.fn(),
  onDeleteSetLog: vi.fn(),
  onSetExerciseUnit: vi.fn(),
  advancedTechniques: advancedTechniqueMetadataFixture,
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
    expect(screen.getByRole('button', { name: 'Edit set' })).toBeInTheDocument()
  })

  it('disables Edit for pending (offline-queued) set logs', () => {
    renderSection({ exerciseLogs: [makePendingSetLog()] })
    expect(screen.getByRole('button', { name: 'Edit set' })).toBeDisabled()
  })

  it('calls onSetExerciseUnit when unit toggle is clicked', async () => {
    const onSetExerciseUnit = vi.fn()
    renderSection({ onSetExerciseUnit })
    await userEvent.click(screen.getByRole('button', { name: 'lb' }))
    expect(onSetExerciseUnit).toHaveBeenCalledWith('exercise-1', 'lb')
  })

  it('shows a Delete button for each logged set', () => {
    renderSection({ exerciseLogs: [makeSetLog()] })
    expect(screen.getByRole('button', { name: 'Delete set' })).toBeInTheDocument()
  })

  it('disables Delete for pending (offline-queued) set logs', () => {
    renderSection({ exerciseLogs: [makePendingSetLog()] })
    expect(screen.getByRole('button', { name: 'Delete set' })).toBeDisabled()
  })

  it('disables Delete when isDeleteSetPending is true', () => {
    renderSection({ exerciseLogs: [makeSetLog()], isDeleteSetPending: true })
    expect(screen.getByRole('button', { name: 'Delete set' })).toBeDisabled()
  })

  it('shows "(syncing…)" indicator for pending set logs', () => {
    renderSection({ exerciseLogs: [makePendingSetLog()] })
    expect(screen.getByText(/syncing/i)).toBeInTheDocument()
  })

  it('calls onDeleteSetLog when Delete is clicked and confirmed in dialog', async () => {
    const onDeleteSetLog = vi.fn()
    renderSection({ exerciseLogs: [makeSetLog()], onDeleteSetLog })
    await userEvent.click(screen.getByRole('button', { name: 'Delete set' }))
    // AlertDialog should be open — confirm button is inside it
    await userEvent.click(screen.getByRole('button', { name: 'Delete', hidden: false }))
    expect(onDeleteSetLog).toHaveBeenCalledWith('log-1')
  })

  it('does not call onDeleteSetLog when Cancel is clicked in the dialog', async () => {
    const onDeleteSetLog = vi.fn()
    renderSection({ exerciseLogs: [makeSetLog()], onDeleteSetLog })
    await userEvent.click(screen.getByRole('button', { name: 'Delete set' }))
    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }))
    expect(onDeleteSetLog).not.toHaveBeenCalled()
  })
})

describe('ExerciseSection rest timer defaultSeconds wiring', () => {
  it('uses RestTimer default (90s) when exercise has no advanced technique — ExerciseSection passes undefined', async () => {
    const user = userEvent.setup()
    renderSection({ exercise: makeExercise() })

    await user.click(screen.getByRole('button', { name: /start rest/i }))

    expect(screen.getByText(/1:30/i)).toBeInTheDocument()
  })

  it('passes GVT rest duration (60s) to RestTimer when exercise technique is GVT', async () => {
    const user = userEvent.setup()
    renderSection({ exercise: { ...makeExercise(), advancedTechnique: 'GVT' } })

    await user.click(screen.getByRole('button', { name: /start rest/i }))

    expect(screen.getByText(/1:00/i)).toBeInTheDocument()
  })

  it('passes REST_PAUSE rest duration (20s) to RestTimer when exercise technique is REST_PAUSE', async () => {
    const user = userEvent.setup()
    renderSection({ exercise: { ...makeExercise(), advancedTechnique: 'REST_PAUSE' } })

    await user.click(screen.getByRole('button', { name: /start rest/i }))

    expect(screen.getByText(/0:20/i)).toBeInTheDocument()
  })

  it('renders no RestTimer for SST technique (0s rest)', () => {
    renderSection({ exercise: { ...makeExercise(), advancedTechnique: 'SST' } })

    expect(screen.queryByRole('button', { name: /start rest/i })).not.toBeInTheDocument()
  })
})

describe('ExerciseSection #182 — to-failure display', () => {
  it('shows "Target X sets until failure" when toFailure is true', () => {
    renderSection({ exercise: { ...makeExercise(), toFailure: true, reps: 0 } })
    expect(screen.getByText('Target 4 sets until failure')).toBeInTheDocument()
    expect(screen.queryByText(/× 0 reps/)).not.toBeInTheDocument()
  })

  it('shows normal "Target X sets × Y reps" when toFailure is false', () => {
    renderSection()
    expect(screen.getByText('Target 4 sets × 8 reps')).toBeInTheDocument()
  })
})

describe('ExerciseSection #181 — SST drop-set guidance', () => {
  it('shows drop-set guidance text when technique is SST', () => {
    renderSection({ exercise: { ...makeExercise(), advancedTechnique: 'SST' } })
    expect(screen.getByText(/drop sets/i)).toBeInTheDocument()
    expect(screen.getByText(/no rest/i)).toBeInTheDocument()
  })

  it('does not show drop-set guidance for non-SST techniques', () => {
    renderSection({ exercise: { ...makeExercise(), advancedTechnique: 'GVT' } })
    expect(screen.queryByText(/drop sets/i)).not.toBeInTheDocument()
  })

  it('does not show drop-set guidance when there is no technique', () => {
    renderSection()
    expect(screen.queryByText(/drop sets/i)).not.toBeInTheDocument()
  })
})

describe('ExerciseSection #183 — pre-fill SetInput with previous set', () => {
  it('pre-fills weight and reps from last logged set', () => {
    renderSection({ exerciseLogs: [makeSetLog({ weight: 80, reps: 10 })] })
    const weightInput = screen.getByLabelText(/weight/i) as HTMLInputElement
    expect(weightInput.value).toBe('80')
    const repsInputs = screen.getAllByLabelText(/reps/i)
    // first reps input belongs to the new-set form
    expect(repsInputs[0].getAttribute('value') ?? (repsInputs[0] as HTMLInputElement).value).toBe('10')
  })

  it('does not pre-fill when no sets have been logged', () => {
    renderSection({ exerciseLogs: [] })
    const weightInput = screen.getByLabelText(/weight/i) as HTMLInputElement
    expect(weightInput.value).toBe('')
  })
})

describe('ExerciseSection #196 — completion-based row coloring', () => {
  // exercise.sets = 4, so:
  //   setNumber 1 (< 2 = 4/2) → red
  //   setNumber 2 (>= 2 but < 4) → amber
  //   setNumber 4 (>= 4) → green
  //   setNumber 5 (> 4) → green (over-target)

  it('applies red styling to a row in low completion (set 1 of 4)', () => {
    const log = makeSetLog({ setNumber: 1 })
    renderSection({ exerciseLogs: [log] })
    const listItem = screen.getByText(/Set 1:/).closest('li')
    expect(listItem?.className).toMatch(/red/)
  })

  it('applies amber styling to a row in mid completion (set 2 of 4)', () => {
    const log = makeSetLog({ setNumber: 2 })
    renderSection({ exerciseLogs: [log] })
    const listItem = screen.getByText(/Set 2:/).closest('li')
    expect(listItem?.className).toMatch(/amber/)
  })

  it('applies amber styling to a row in mid completion (set 3 of 4)', () => {
    const log = makeSetLog({ setNumber: 3 })
    renderSection({ exerciseLogs: [log] })
    const listItem = screen.getByText(/Set 3:/).closest('li')
    expect(listItem?.className).toMatch(/amber/)
  })

  it('applies green styling to a row at full completion (set 4 of 4)', () => {
    const log = makeSetLog({ setNumber: 4 })
    renderSection({ exerciseLogs: [log] })
    const listItem = screen.getByText(/Set 4:/).closest('li')
    expect(listItem?.className).toMatch(/green/)
  })

  it('applies green styling to an over-target row (set 5 of 4)', () => {
    const log = makeSetLog({ setNumber: 5 })
    renderSection({ exerciseLogs: [log] })
    const listItem = screen.getByText(/Set 5:/).closest('li')
    expect(listItem?.className).toMatch(/green/)
  })

  it('applies completion colors the same way for toFailure exercises (set 1 of 4 → red)', () => {
    const log = makeSetLog({ setNumber: 1 })
    renderSection({
      exercise: { ...makeExercise(), toFailure: true, reps: 0 },
      exerciseLogs: [log],
    })
    const listItem = screen.getByText(/Set 1:/).closest('li')
    expect(listItem?.className).toMatch(/red/)
  })

  it('applies completion colors the same way for toFailure exercises (set 4 of 4 → green)', () => {
    const log = makeSetLog({ setNumber: 4 })
    renderSection({
      exercise: { ...makeExercise(), toFailure: true, reps: 0 },
      exerciseLogs: [log],
    })
    const listItem = screen.getByText(/Set 4:/).closest('li')
    expect(listItem?.className).toMatch(/green/)
  })

  it('pending rows get the neutral border styling (no completion color)', () => {
    const log = makePendingSetLog({ setNumber: 4 }) // would be green if not pending
    renderSection({ exerciseLogs: [log] })
    const listItem = screen.getByText(/Set 4:/).closest('li')
    expect(listItem?.className).not.toMatch(/green/)
    expect(listItem?.className).not.toMatch(/amber/)
    expect(listItem?.className).not.toMatch(/red/)
  })
})

describe('ExerciseSection #187 — reps-exceeded highlight (replaced by completion coloring)', () => {
  it('set 1 of 4 with reps exceeding target is red (completion-based, not reps-based)', () => {
    const log = makeSetLog({ reps: 12 }) // exercise.reps = 8, setNumber = 1
    renderSection({ exerciseLogs: [log] })
    const listItem = screen.getByText(/Set 1:/).closest('li')
    expect(listItem?.className).toMatch(/red/)
    expect(listItem?.className).not.toMatch(/green/)
  })

  it('set 4 of 4 with reps at target is green (full completion)', () => {
    const log = makeSetLog({ reps: 8, setNumber: 4 })
    renderSection({ exerciseLogs: [log] })
    const listItem = screen.getByText(/Set 4:/).closest('li')
    expect(listItem?.className).toMatch(/green/)
  })

  it('does not add green for pending set logs even at full completion', () => {
    const log = makePendingSetLog({ reps: 15, setNumber: 4 })
    renderSection({ exerciseLogs: [log] })
    const listItem = screen.getByText(/Set 4:/).closest('li')
    expect(listItem?.className).not.toMatch(/green/)
  })
})
