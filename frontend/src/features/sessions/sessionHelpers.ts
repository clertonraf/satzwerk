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

    return new Date(aLast).getTime() - new Date(bLast).getTime()
  })
}
