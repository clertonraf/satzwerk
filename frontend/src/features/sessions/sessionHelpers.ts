import type { WorkoutGroupCatalogEntry } from '@/lib/domainBuilders'

export function formatSessionDate(value: string | null | undefined) {
  if (!value) {
    return 'In progress'
  }

  const date = new Date(value)

  if (Number.isNaN(date.getTime())) {
    return value
  }

  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(date)
}

export function toKilograms(weight: number, unit: 'kg' | 'lb') {
  if (unit === 'kg') {
    return weight
  }

  return Number((weight / 2.20462).toFixed(3))
}

export function toPounds(weightKg: number) {
  return weightKg * 2.20462
}

export function computeAvgMinPerExercise(durationMinutes: number, exerciseCount: number): number | null {
  if (exerciseCount === 0) return null
  return Math.round((durationMinutes / exerciseCount) * 10) / 10
}

export function formatGroupStats(count: number, lastCompletedAt: string | null, now: Date = new Date()) {
  if (count === 0 || !lastCompletedAt) {
    return 'Never'
  }

  const completedAt = new Date(lastCompletedAt)
  if (Number.isNaN(completedAt.getTime())) {
    return `Done ${count}×`
  }

  const nowUtcDay = Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate())
  const completedUtcDay = Date.UTC(completedAt.getUTCFullYear(), completedAt.getUTCMonth(), completedAt.getUTCDate())
  const daysAgo = Math.max(0, Math.round((nowUtcDay - completedUtcDay) / 86400000))

  if (daysAgo === 0) return `Done ${count}×, today`
  if (daysAgo === 1) return `Done ${count}×, yesterday`
  return `Done ${count}×, ${daysAgo} days ago`
}

/**
 * Sorts workout group catalog entries for session startup:
 * 1. Never-done groups first, in plan orderIndex order.
 * 2. Done groups next, sorted by lastCompletedAt ascending (oldest first).
 */
export function sortGroupOptions(
  entries: WorkoutGroupCatalogEntry[],
  groupStatsMap: ReadonlyMap<string, { count: number; lastCompletedAt: string | null }>,
): WorkoutGroupCatalogEntry[] {
  return [...entries].sort((a, b) => {
    const aLast = groupStatsMap.get(a.group.id)?.lastCompletedAt ?? null
    const bLast = groupStatsMap.get(b.group.id)?.lastCompletedAt ?? null

    if (aLast === null && bLast === null) return a.group.orderIndex - b.group.orderIndex
    if (aLast === null) return -1
    if (bLast === null) return 1

    const aTime = new Date(aLast).getTime()
    const bTime = new Date(bLast).getTime()

    // Guard against invalid date strings producing NaN — sort before any valid-dated group
    // (after true never-done groups), in orderIndex order when both are invalid.
    if (Number.isNaN(aTime) && Number.isNaN(bTime)) return a.group.orderIndex - b.group.orderIndex
    if (Number.isNaN(aTime)) return -1
    if (Number.isNaN(bTime)) return 1

    return aTime - bTime || a.group.orderIndex - b.group.orderIndex
  })
}

/**
 * Returns the set completion percentage (0-100+, rounded to nearest integer),
 * or null when totalTargetSets is 0 or negative.
 */
export function computeSetCompletionPercentage(
  setCount: number,
  totalTargetSets: number,
): number | null {
  if (totalTargetSets <= 0) return null
  return Math.round((setCount / totalTargetSets) * 100)
}

/**
 * Returns Tailwind class strings for a logged-set row based on how far through
 * the target set count the given set number is. Pending logs always get the
 * neutral border so the syncing treatment is not obscured.
 *
 * Bands (set index is 1-based):
 *   - setNumber >= targetSets  →  green  (full/over completion)
 *   - setNumber >= targetSets / 2  →  amber  (mid completion)
 *   - otherwise  →  red  (low completion)
 */
export function setLogCompletionClass(setNumber: number, targetSets: number, pending: boolean): string {
  if (pending || targetSets <= 0) return 'border-border'
  if (setNumber >= targetSets) return 'border-green-500 bg-green-50 dark:bg-green-950/30'
  if (setNumber >= targetSets / 2) return 'border-amber-400 bg-amber-50 dark:bg-amber-950/30'
  return 'border-red-400 bg-red-50 dark:bg-red-950/30'
}
