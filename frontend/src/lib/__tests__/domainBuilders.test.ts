import { describe, expect, it } from 'vitest'
import type { WorkoutSession } from '@/services/sessionService'
import type { WorkoutPlanDetail } from '@/services/planService'
import { buildGroupStatsMap, buildWorkoutGroupCatalog } from '../domainBuilders'

const makeSession = (overrides: Partial<WorkoutSession> = {}): WorkoutSession => ({
  id: 'session-1',
  workoutGroupId: 'group-1',
  workoutGroupTitle: 'Push Day',
  startedAt: '2026-06-01T10:00:00Z',
  completedAt: '2026-06-01T11:00:00Z',
  notes: null,
  setLogs: [],
  setCount: 12,
  exerciseCount: 3,
  ...overrides,
})

const makePlan = (overrides: Partial<WorkoutPlanDetail> = {}): WorkoutPlanDetail => ({
  id: 'plan-1',
  name: 'My Plan',
  source: 'manual',
  isActive: true,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
  groups: [
    { id: 'group-1', title: 'Push Day', orderIndex: 0, exercises: [] },
    { id: 'group-2', title: 'Pull Day', orderIndex: 1, exercises: [] },
  ],
  ...overrides,
})

describe('buildWorkoutGroupCatalog', () => {
  it('returns an empty catalog when no plans are provided', () => {
    expect(buildWorkoutGroupCatalog([])).toEqual({})
  })

  it('indexes each WorkoutGroup by its id', () => {
    const plan = makePlan()
    const catalog = buildWorkoutGroupCatalog([plan])

    expect(catalog['group-1']).toEqual({ group: plan.groups[0], plan })
    expect(catalog['group-2']).toEqual({ group: plan.groups[1], plan })
  })

  it('indexes groups from multiple plans', () => {
    const plan1 = makePlan({ id: 'plan-1', groups: [{ id: 'group-1', title: 'Push', orderIndex: 0, exercises: [] }] })
    const plan2 = makePlan({ id: 'plan-2', groups: [{ id: 'group-3', title: 'Legs', orderIndex: 0, exercises: [] }] })
    const catalog = buildWorkoutGroupCatalog([plan1, plan2])

    expect(Object.keys(catalog)).toHaveLength(2)
    expect(catalog['group-3'].plan.id).toBe('plan-2')
  })
})

describe('buildGroupStatsMap', () => {
  it('returns an empty map when there is no history', () => {
    expect(buildGroupStatsMap([])).toEqual(new Map())
  })

  it('counts sessions for the same WorkoutGroup and keeps the latest completion time', () => {
    const stats = buildGroupStatsMap([
      makeSession({ completedAt: '2026-06-08T10:00:00Z' }),
      makeSession({ id: 'session-2', completedAt: '2026-06-10T10:00:00Z' }),
    ])

    expect(stats.get('group-1')).toEqual({
      count: 2,
      lastCompletedAt: '2026-06-10T10:00:00Z',
    })
  })

  it('tracks stats separately for different WorkoutGroups', () => {
    const stats = buildGroupStatsMap([
      makeSession({ workoutGroupId: 'group-1', completedAt: '2026-06-08T10:00:00Z' }),
      makeSession({ id: 'session-2', workoutGroupId: 'group-2', completedAt: '2026-06-09T10:00:00Z' }),
    ])

    expect(stats).toEqual(
      new Map([
        ['group-1', { count: 1, lastCompletedAt: '2026-06-08T10:00:00Z' }],
        ['group-2', { count: 1, lastCompletedAt: '2026-06-09T10:00:00Z' }],
      ]),
    )
  })

  it('skips sessions with null completedAt — they do not increment the count', () => {
    const stats = buildGroupStatsMap([
      makeSession({ completedAt: '2026-06-08T12:00:00.000Z' }),
      makeSession({ id: 'session-2', completedAt: null }),
    ])

    expect(stats.get('group-1')).toEqual({
      count: 1,
      lastCompletedAt: '2026-06-08T12:00:00.000Z',
    })
  })
})
