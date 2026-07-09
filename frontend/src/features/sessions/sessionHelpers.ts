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
