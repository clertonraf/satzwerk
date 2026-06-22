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
    if (!session.completedAt) return

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
      lastCompletedAt:
        !existing.lastCompletedAt || new Date(existing.lastCompletedAt) < new Date(session.completedAt)
          ? session.completedAt
          : existing.lastCompletedAt,
    })
  })

  return stats
}
