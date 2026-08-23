import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import ExerciseProgressChart from '@/features/analytics/ExerciseProgressChart'
import ExerciseSessionHistoryCard from '@/features/analytics/ExerciseSessionHistoryCard'
import { analyticsService } from '@/services/analyticsService'
import { queryKeys } from '@/services/queryKeys'

export const TOP_EXERCISES_LIMIT = 20

export default function AnalyticsPage() {
  const { data: topExercises, isLoading: topExercisesLoading, isError: topExercisesError } = useQuery({
    queryKey: queryKeys.analytics.topExercises(TOP_EXERCISES_LIMIT),
    queryFn: () => analyticsService.topExercises(TOP_EXERCISES_LIMIT),
  })
  const exercises = topExercises ?? []
  const [selectedExerciseId, setSelectedExerciseId] = useState<string | null>(null)
  const selectedIsPresent = selectedExerciseId !== null && exercises.some((e) => e.exerciseId === selectedExerciseId)
  const effectiveExerciseId = selectedIsPresent ? selectedExerciseId : (exercises[0]?.exerciseId ?? null)

  const progressQuery = useQuery({
    queryKey: queryKeys.analytics.exerciseProgress(effectiveExerciseId!),
    queryFn: () => analyticsService.exerciseProgress(effectiveExerciseId!),
    enabled: effectiveExerciseId !== null,
  })

  if (topExercisesLoading)
    return (
      <div className="space-y-6">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Analytics</h1>
          <p className="text-sm text-muted-foreground">Inspect one Exercise at a time.</p>
        </div>
        <Card>
          <CardContent className="pt-6">
            <div className="h-8 animate-pulse rounded bg-muted" />
          </CardContent>
        </Card>
      </div>
    )

  if (topExercisesError)
    return (
      <div className="space-y-6">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Analytics</h1>
          <p className="text-sm text-muted-foreground">Inspect one Exercise at a time.</p>
        </div>
        <Card>
          <CardContent className="pt-6">
            <p className="text-sm text-destructive">Could not load exercises. Please try again later.</p>
          </CardContent>
        </Card>
      </div>
    )

  if (exercises.length === 0)
    return (
      <div className="space-y-6">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Analytics</h1>
          <p className="text-sm text-muted-foreground">Inspect one Exercise at a time.</p>
        </div>
        <Card>
          <CardContent className="pt-6">
            <p className="text-sm text-muted-foreground">Log a workout to see your exercise analytics.</p>
          </CardContent>
        </Card>
      </div>
    )

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Analytics</h1>
        <p className="text-sm text-muted-foreground">Inspect one Exercise at a time.</p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="text-sm">Exercise</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="flex flex-wrap gap-2">
            {exercises.map((exercise) => {
              const isActive = exercise.exerciseId === effectiveExerciseId
              return (
                <button
                  key={exercise.exerciseId}
                  type="button"
                  className={
                    isActive
                      ? 'rounded-full border px-3 py-1 text-sm bg-primary text-primary-foreground border-primary font-medium'
                      : 'rounded-full border px-3 py-1 text-sm'
                  }
                  aria-pressed={isActive}
                  onClick={() => setSelectedExerciseId(exercise.exerciseId)}
                >
                  {exercise.exerciseName}
                </button>
              )
            })}
          </div>
        </CardContent>
      </Card>

      <ExerciseProgressChart progress={progressQuery.data ?? null} isLoading={progressQuery.isLoading} />
      <ExerciseSessionHistoryCard progress={progressQuery.data ?? null} isLoading={progressQuery.isLoading} />
    </div>
  )
}
