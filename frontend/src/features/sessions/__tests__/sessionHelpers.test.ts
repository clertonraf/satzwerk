import { describe, expect, it } from 'vitest'
import { buildGroupStatsMap } from '@/lib/domainBuilders'
import { computeAvgMinPerExercise, computeSetCompletionPercentage, formatGroupStats, sortGroupOptions } from '../sessionHelpers'
import type { WorkoutGroupCatalogEntry } from '@/lib/domainBuilders'
import type { WorkoutPlanDetail } from '@/services/planService'
import type { WorkoutSession } from '@/services/sessionService'

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

describe('formatGroupStats', () => {
  it('returns Never when the group has never been completed', () => {
    expect(formatGroupStats(0, null)).toBe('Never')
  })

  it('formats completions from today', () => {
    expect(formatGroupStats(1, '2026-06-10T12:00:00.000Z', new Date('2026-06-10T12:00:00.000Z'))).toBe('Done 1×, today')
  })

  it('formats completions from yesterday', () => {
    expect(formatGroupStats(2, '2026-06-09T12:00:00.000Z', new Date('2026-06-10T12:00:00.000Z'))).toBe('Done 2×, yesterday')
  })

  it('formats completions from earlier days', () => {
    expect(formatGroupStats(3, '2026-06-08T12:00:00.000Z', new Date('2026-06-10T12:00:00.000Z'))).toBe('Done 3×, 2 days ago')
  })

  it('omits the day label for an invalid date string', () => {
    expect(formatGroupStats(2, 'not-a-date')).toBe('Done 2×')
  })

  it('clamps to today when the client clock is behind the server', () => {
    expect(formatGroupStats(1, '2026-06-10T12:00:00.000Z', new Date('2026-06-09T12:00:00.000Z'))).toBe('Done 1×, today')
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
      ])
    )
  })

  it('skips sessions with null completedAt entirely — they do not increment the count', () => {
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


const basePlan: WorkoutPlanDetail = {
  id: 'plan-1',
  name: 'PPL',
  source: 'MANUAL',
  isActive: true,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
  groups: [],
}

function makeEntry(id: string, orderIndex: number): WorkoutGroupCatalogEntry {
  return { group: { id, title: id, orderIndex, exercises: [] }, plan: basePlan }
}

describe('sortGroupOptions', () => {
  it('preserves plan orderIndex order when all groups are never done', () => {
    const entries = [makeEntry('b', 1), makeEntry('a', 0), makeEntry('c', 2)]
    const result = sortGroupOptions(entries, new Map())
    expect(result.map((e) => e.group.id)).toEqual(['a', 'b', 'c'])
  })

  it('sorts all-done groups by lastCompletedAt ascending (oldest first)', () => {
    const entries = [makeEntry('a', 0), makeEntry('b', 1), makeEntry('c', 2)]
    const stats = new Map([
      ['a', { count: 1, lastCompletedAt: '2026-06-10T00:00:00Z' }],
      ['b', { count: 1, lastCompletedAt: '2026-06-08T00:00:00Z' }],
      ['c', { count: 1, lastCompletedAt: '2026-06-09T00:00:00Z' }],
    ])
    const result = sortGroupOptions(entries, stats)
    expect(result.map((e) => e.group.id)).toEqual(['b', 'c', 'a'])
  })

  it('puts never-done groups first (by orderIndex), then done groups (oldest first)', () => {
    const entries = [makeEntry('done-old', 0), makeEntry('never', 1), makeEntry('done-new', 2)]
    const stats = new Map([
      ['done-old', { count: 2, lastCompletedAt: '2026-06-01T00:00:00Z' }],
      ['done-new', { count: 1, lastCompletedAt: '2026-06-10T00:00:00Z' }],
    ])
    const result = sortGroupOptions(entries, stats)
    expect(result.map((e) => e.group.id)).toEqual(['never', 'done-old', 'done-new'])
  })

  it('multiple never-done groups maintain relative orderIndex order', () => {
    const entries = [makeEntry('g3', 2), makeEntry('g1', 0), makeEntry('g2', 1)]
    const result = sortGroupOptions(entries, new Map())
    expect(result.map((e) => e.group.id)).toEqual(['g1', 'g2', 'g3'])
  })

  it('does not mutate the input array', () => {
    const entries = [makeEntry('b', 1), makeEntry('a', 0)]
    const original = [...entries]
    sortGroupOptions(entries, new Map())
    expect(entries[0].group.id).toBe(original[0].group.id)
  })

  it('sorts by orderIndex as tie-breaker when two done groups have the same lastCompletedAt', () => {
    const sameDate = '2026-06-08T00:00:00Z'
    const entries = [makeEntry('g2', 1), makeEntry('g1', 0)]
    const stats = new Map([
      ['g1', { count: 1, lastCompletedAt: sameDate }],
      ['g2', { count: 1, lastCompletedAt: sameDate }],
    ])
    const result = sortGroupOptions(entries, stats)
    expect(result.map((e) => e.group.id)).toEqual(['g1', 'g2'])
  })
})

describe('computeSetCompletionPercentage', () => {
  it('returns 80 for 12/15 sets', () => {
    expect(computeSetCompletionPercentage(12, 15)).toBe(80)
  })

  it('rounds to nearest integer: 1/3 → 33', () => {
    expect(computeSetCompletionPercentage(1, 3)).toBe(33)
  })

  it('allows over 100%: 20/15 → 133', () => {
    expect(computeSetCompletionPercentage(20, 15)).toBe(133)
  })

  it('returns null when totalTargetSets is 0', () => {
    expect(computeSetCompletionPercentage(5, 0)).toBeNull()
  })

  it('returns null when totalTargetSets is negative', () => {
    expect(computeSetCompletionPercentage(5, -1)).toBeNull()
  })
})

describe('computeAvgMinPerExercise', () => {
  it('returns null when exerciseCount is 0', () => {
    expect(computeAvgMinPerExercise(60, 0)).toBeNull()
  })

  it('divides duration by exercise count and rounds to 1 decimal', () => {
    expect(computeAvgMinPerExercise(60, 4)).toBe(15)
  })

  it('rounds fractional results to 1 decimal place', () => {
    expect(computeAvgMinPerExercise(65, 4)).toBe(16.3)
  })

  it('returns the full duration when there is 1 exercise', () => {
    expect(computeAvgMinPerExercise(45, 1)).toBe(45)
  })
})
