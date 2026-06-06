import type { WorkoutPlanDetail } from '@/services/planService'
import type { WorkoutSession } from '@/services/sessionService'

export interface WorkoutGroupCatalogEntry {
  group: WorkoutPlanDetail['groups'][number]
  plan: WorkoutPlanDetail
}

export function buildWorkoutGroupCatalog(plans: WorkoutPlanDetail[]) {
  const catalog: Record<string, WorkoutGroupCatalogEntry> = {}

  plans.forEach((plan) => {
    plan.groups.forEach((group) => {
      catalog[group.id] = { group, plan }
    })
  })

  return catalog
}

export function buildGroupStatsMap(sessions: WorkoutSession[]) {
  const stats = new Map<string, { count: number; lastCompletedAt: string | null }>()

  sessions.forEach((session) => {
    const existing = stats.get(session.workoutGroupId)

    if (!existing) {
      stats.set(session.workoutGroupId, {
        count: 1,
        lastCompletedAt: session.completedAt,
      })
      return
    }

    stats.set(session.workoutGroupId, {
      count: existing.count + 1,
      lastCompletedAt: !session.completedAt
        ? existing.lastCompletedAt
        : !existing.lastCompletedAt || new Date(existing.lastCompletedAt) < new Date(session.completedAt)
          ? session.completedAt
          : existing.lastCompletedAt,
    })
  })

  return stats
}

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

export function convertWeightHint(rawInput: string, unit: 'kg' | 'lb'): string | null {
  const value = parseFloat(rawInput)
  if (isNaN(value) || value <= 0) return null
  if (unit === 'kg') {
    return `≈ ${Number((value * 2.20462).toFixed(3))} lb`
  }
  return `≈ ${Number((value / 2.20462).toFixed(3))} kg`
}

export function formatDisplayWeight(weight: number, unit: 'kg' | 'lb') {
  const displayWeight = unit === 'kg' ? weight : weight * 2.20462
  return `${Number(displayWeight.toFixed(1))} ${unit}`
}

export function formatGroupStats(count: number, lastCompletedAt: string | null, now: Date = new Date()) {
  if (count === 0 || !lastCompletedAt) {
    return 'Never'
  }

  const completedAt = new Date(lastCompletedAt)
  const nowUtcDay = Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate())
  const completedUtcDay = Date.UTC(completedAt.getUTCFullYear(), completedAt.getUTCMonth(), completedAt.getUTCDate())
  const daysAgo = Math.round((nowUtcDay - completedUtcDay) / 86400000)

  if (daysAgo === 0) return `Done ${count}×, today`
  if (daysAgo === 1) return `Done ${count}×, yesterday`
  return `Done ${count}×, ${daysAgo} days ago`
}
