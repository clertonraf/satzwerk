import { describe, expect, it } from 'vitest'
import { buildGroupStatsMap } from '@/lib/domainBuilders'
import { formatGroupStats } from '../sessionHelpers'
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

import { computeSetCompletionPercentage } from '../sessionHelpers'

describe('computeSetCompletionPercentage', () => {
  it('returns 80 for 12/15 sets', () => {
    expect(computeSetCompletionPercentage(12, 15)).toBe(80)
  })

  it('rounds down: 1/3 → 33', () => {
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
