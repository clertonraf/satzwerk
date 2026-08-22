import type { WorkoutPlanDetail } from '@/services/planService'
import type { WorkoutSession } from '@/services/sessionService'

/**
 * Computes the WorkoutPlan completion percentage across all executed WorkoutGroups.
 *
 * For each WorkoutGroup in the active plan that has at least one completed
 * WorkoutSession, uses the most recent completed session's setCount as the
 * numerator contribution, and the sum of that group's WorkoutExercise.sets as
 * the denominator contribution.
 *
 * WorkoutGroups that have never been executed are excluded from both numerator
 * and denominator so they don't deflate the percentage for a partially-run plan.
 *
 * Returns null when no plan groups have been executed yet, or when the denominator
 * would be zero (all executed groups have no exercises defined).
 */
export function computePlanCompletionPercentage(
  plan: WorkoutPlanDetail,
  history: WorkoutSession[],
): number | null {
  let numerator = 0
  let denominator = 0

  for (const group of plan.groups) {
    const mostRecentCompleted = history
      .filter((s) => s.workoutGroupId === group.id && s.completedAt != null)
      .sort((a, b) => new Date(b.completedAt!).getTime() - new Date(a.completedAt!).getTime())[0]

    if (!mostRecentCompleted) continue

    numerator += mostRecentCompleted.setCount
    denominator += group.exercises.reduce((sum, ex) => sum + ex.sets, 0)
  }

  if (denominator <= 0) return null
  return Math.round((numerator / denominator) * 100)
}
