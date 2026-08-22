import { describe, expect, it } from 'vitest'
import { computePlanCompletionPercentage } from '../planCompletion'
import type { WorkoutPlanDetail } from '@/services/planService'
import type { WorkoutSession } from '@/services/sessionService'

const makePlan = (overrides: Partial<WorkoutPlanDetail> = {}): WorkoutPlanDetail => ({
  id: 'plan-1',
  name: 'PPL',
  source: 'MANUAL',
  isActive: true,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
  groups: [
    {
      id: 'group-1',
      title: 'Push Day',
      orderIndex: 0,
      exercises: [
        { id: 'we-1', exerciseId: 'exercise-1', exerciseName: 'Bench Press', sets: 4, reps: 8, advancedTechnique: null, toFailure: false, orderIndex: 0 },
      ],
    },
    {
      id: 'group-2',
      title: 'Pull Day',
      orderIndex: 1,
      exercises: [
        { id: 'we-2', exerciseId: 'exercise-2', exerciseName: 'Row', sets: 3, reps: 10, advancedTechnique: null, toFailure: false, orderIndex: 0 },
      ],
    },
    {
      id: 'group-3',
      title: 'Leg Day',
      orderIndex: 2,
      exercises: [
        { id: 'we-3', exerciseId: 'exercise-3', exerciseName: 'Squat', sets: 5, reps: 5, advancedTechnique: null, toFailure: false, orderIndex: 0 },
      ],
    },
  ],
  ...overrides,
})

const makeSession = (overrides: Partial<WorkoutSession> = {}): WorkoutSession => ({
  id: 'session-1',
  workoutGroupId: 'group-1',
  workoutGroupTitle: 'Push Day',
  startedAt: '2026-06-01T10:00:00Z',
  completedAt: '2026-06-01T11:00:00Z',
  notes: null,
  setLogs: [],
  setCount: 4,
  exerciseCount: 1,
  ...overrides,
})

describe('computePlanCompletionPercentage', () => {
  it('returns null when no WorkoutGroups have been executed', () => {
    expect(computePlanCompletionPercentage(makePlan(), [])).toBeNull()
  })

  it('excludes never-executed WorkoutGroups from the denominator', () => {
    const history = [makeSession({ workoutGroupId: 'group-1', setCount: 2 })]
    expect(computePlanCompletionPercentage(makePlan(), history)).toBe(50)
  })

  it('uses the most recent completed WorkoutSession for repeated WorkoutGroup executions', () => {
    const history = [
      makeSession({ id: 'session-1', workoutGroupId: 'group-1', completedAt: '2026-06-01T11:00:00Z', setCount: 2 }),
      makeSession({ id: 'session-2', workoutGroupId: 'group-1', completedAt: '2026-06-03T11:00:00Z', setCount: 4 }),
      makeSession({ id: 'session-3', workoutGroupId: 'group-2', completedAt: '2026-06-02T11:00:00Z', setCount: 3 }),
    ]

    expect(computePlanCompletionPercentage(makePlan(), history)).toBe(100)
  })

  it('returns null when only executed WorkoutGroups have zero expected sets', () => {
    const plan = makePlan({
      groups: [
        { id: 'group-1', title: 'Push Day', orderIndex: 0, exercises: [] },
      ],
    })
    const history = [makeSession({ workoutGroupId: 'group-1', setCount: 2 })]
    expect(computePlanCompletionPercentage(plan, history)).toBeNull()
  })

  it('allows over 100% when extra sets were logged', () => {
    const plan = makePlan({
      groups: [
        { id: 'group-1', title: 'Push Day', orderIndex: 0, exercises: [
          { id: 'we-1', exerciseId: 'exercise-1', exerciseName: 'Bench Press', sets: 3, reps: 8, advancedTechnique: null, toFailure: false, orderIndex: 0 },
        ] },
      ],
    })
    const history = [makeSession({ workoutGroupId: 'group-1', setCount: 5 })]
    // 5/3 = 166.6... → 167%
    expect(computePlanCompletionPercentage(plan, history)).toBe(167)
  })

  it('rounds to the nearest integer', () => {
    const plan = makePlan({
      groups: [
        { id: 'group-1', title: 'Push Day', orderIndex: 0, exercises: [
          { id: 'we-1', exerciseId: 'exercise-1', exerciseName: 'Bench Press', sets: 3, reps: 8, advancedTechnique: null, toFailure: false, orderIndex: 0 },
        ] },
      ],
    })
    const history = [makeSession({ workoutGroupId: 'group-1', setCount: 1 })]
    // 1/3 = 33.33... → 33
    expect(computePlanCompletionPercentage(plan, history)).toBe(33)
  })

  it('skips sessions with null completedAt', () => {
    const history = [
      makeSession({ id: 'session-1', workoutGroupId: 'group-1', completedAt: '2026-06-01T11:00:00Z', setCount: 4 }),
      makeSession({ id: 'session-2', workoutGroupId: 'group-1', completedAt: null, setCount: 99 }),
    ]
    // Only the completed session contributes; setCount=4, denominator=4 → 100%
    expect(computePlanCompletionPercentage(makePlan(), history)).toBe(100)
  })
})
