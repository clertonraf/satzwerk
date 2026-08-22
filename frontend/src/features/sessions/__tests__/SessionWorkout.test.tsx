import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import SessionWorkout from '../SessionWorkout'
import type { WorkoutGroupCatalogEntry } from '@/lib/domainBuilders'
import type { WorkoutPlanDetail } from '@/services/planService'
import type { WorkoutSession } from '@/services/sessionService'

const basePlan: WorkoutPlanDetail = {
  id: 'plan-1',
  name: 'PPL',
  source: 'MANUAL',
  isActive: true,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
  groups: [],
}

function makeGroupEntry(sets: number[]): WorkoutGroupCatalogEntry {
  return {
    group: {
      id: 'group-1',
      title: 'Push Day',
      orderIndex: 0,
      exercises: sets.map((s, i) => ({
        id: `we-${i}`,
        exerciseId: `exercise-${i}`,
        exerciseName: `Exercise ${i}`,
        sets: s,
        reps: 8,
        toFailure: false,
        orderIndex: i,
        advancedTechnique: null,
      })),
    },
    plan: basePlan,
  }
}

function makeSession(setCount: number): WorkoutSession {
  return {
    id: 'session-1',
    workoutGroupId: 'group-1',
    workoutGroupTitle: 'Push Day',
    startedAt: '2026-01-01T00:00:00Z',
    completedAt: null,
    notes: null,
    setLogs: Array.from({ length: setCount }, (_, i) => ({
      id: `log-${i}`,
      exerciseId: 'exercise-0',
      setNumber: i + 1,
      weight: 80,
      reps: 5,
      loggedAt: '2026-01-01T00:00:00Z',
    })),
    setCount,
    exerciseCount: 0,
  }
}

const defaultProps = {
  session: makeSession(0),
  pendingSetLogs: [],
  currentGroupEntry: makeGroupEntry([5, 5, 5]),
  exercisesById: new Map([
    [
      'exercise-0',
      {
        id: 'exercise-0',
        name: 'Bench Press',
        muscleGroup: 'Chest',
        description: null,
        videoUrl: null,
        equipment: null,
        createdAt: '2026-01-01T00:00:00Z',
        updatedAt: '2026-01-01T00:00:00Z',
      },
    ],
  ]),
  referenceWeightsMap: new Map(),
  isReferenceWeightsLoading: false,
  isCatalogLoading: false,
  isOnline: true,
  exerciseUnits: {},
  isAddSetPending: false,
  isUpdateSetPending: false,
  isDeleteSetPending: false,
  isCompletePending: false,
  isForfeitPending: false,
  onLogSet: vi.fn(),
  onUpdateSetLog: vi.fn(),
  onDeleteSetLog: vi.fn(),
  onSetExerciseUnit: vi.fn(),
  onComplete: vi.fn(),
  onForfeit: vi.fn(),
}

describe('SessionWorkout completion progress', () => {
  it('shows a progress bar and set count label when sets are logged', () => {
    render(
      <SessionWorkout
        {...defaultProps}
        session={makeSession(12)}
        currentGroupEntry={makeGroupEntry([5, 5, 5])}
      />
    )

    // progress bar
    expect(screen.getByRole('progressbar')).toBeInTheDocument()
    // caption
    expect(screen.getByText(/12 \/ 15 sets/)).toBeInTheDocument()
    // percentage label: 12/15 = 80%
    expect(screen.getByText(/80%/)).toBeInTheDocument()
  })

  it('shows 0% when no sets have been logged', () => {
    render(
      <SessionWorkout
        {...defaultProps}
        session={makeSession(0)}
        currentGroupEntry={makeGroupEntry([5, 5, 5])}
      />
    )

    expect(screen.getByRole('progressbar')).toBeInTheDocument()
    expect(screen.getByText(/0%/)).toBeInTheDocument()
  })

  it('includes pending SetLogs in the displayed percentage', () => {
    const pending = [
      { id: 'pending-1', exerciseId: 'exercise-0', setNumber: 1, weight: 80, reps: 5, loggedAt: '2026-01-01T00:00:00Z', pending: true as const },
      { id: 'pending-2', exerciseId: 'exercise-0', setNumber: 2, weight: 80, reps: 5, loggedAt: '2026-01-01T00:00:00Z', pending: true as const },
    ]
    render(
      <SessionWorkout
        {...defaultProps}
        session={makeSession(0)}
        pendingSetLogs={pending}
        currentGroupEntry={makeGroupEntry([5, 5, 5])}
      />
    )

    // 2 pending / 15 target = 13%
    expect(screen.getByText(/13%/)).toBeInTheDocument()
    expect(screen.getByText(/2 \/ 15 sets/)).toBeInTheDocument()
  })

  it('renders over-target percentage label without hiding the full bar', () => {
    render(
      <SessionWorkout
        {...defaultProps}
        session={makeSession(20)}
        currentGroupEntry={makeGroupEntry([5, 5, 5])}
      />
    )

    // 20/15 = 133%
    expect(screen.getByText(/133%/)).toBeInTheDocument()
    expect(screen.getByRole('progressbar')).toBeInTheDocument()
  })

  it('hides progress bar when totalTargetSets is 0', () => {
    render(
      <SessionWorkout
        {...defaultProps}
        session={makeSession(5)}
        currentGroupEntry={makeGroupEntry([])}
      />
    )

    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument()
  })

  it('hides progress bar when currentGroupEntry is undefined', () => {
    render(
      <SessionWorkout
        {...defaultProps}
        session={makeSession(3)}
        currentGroupEntry={undefined}
      />
    )

    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument()
  })
})
