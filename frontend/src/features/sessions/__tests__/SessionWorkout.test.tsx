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
  }
}

const defaultProps = {
  session: makeSession(0),
  pendingSetLogs: [],
  currentGroupEntry: makeGroupEntry([5, 5, 5]),
  exercisesById: new Map([['exercise-0', { id: 'exercise-0', name: 'Bench Press', muscleGroup: 'Chest', description: null, videoUrl: null, equipment: null, createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z' }]]),
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
  it('shows set count / total and percentage above action buttons', () => {
    render(
      <SessionWorkout
        {...defaultProps}
        session={makeSession(12)}
        currentGroupEntry={makeGroupEntry([5, 5, 5])}
      />
    )

    // 12/15 = 80%
    expect(screen.getByText(/12 \/ 15 sets · 80%/)).toBeInTheDocument()
  })

  it('hides progress line when totalTargetSets is 0', () => {
    render(
      <SessionWorkout
        {...defaultProps}
        session={makeSession(5)}
        currentGroupEntry={makeGroupEntry([])}
      />
    )

    expect(screen.queryByText(/sets ·/)).not.toBeInTheDocument()
  })

  it('hides progress line when currentGroupEntry is undefined', () => {
    render(
      <SessionWorkout
        {...defaultProps}
        session={makeSession(3)}
        currentGroupEntry={undefined}
      />
    )

    expect(screen.queryByText(/sets ·/)).not.toBeInTheDocument()
  })
})
