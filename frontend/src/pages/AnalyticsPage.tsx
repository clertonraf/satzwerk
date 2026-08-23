import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import ExerciseProgressChart from '@/features/analytics/ExerciseProgressChart'
import ExerciseSessionHistoryCard from '@/features/analytics/ExerciseSessionHistoryCard'
import { analyticsService } from '@/services/analyticsService'
import { queryKeys } from '@/services/queryKeys'

export default function AnalyticsPage() {
  const { data: topExercises, isLoading: topExercisesLoading } = useQuery({
    queryKey: queryKeys.analytics.topExercises(20),
    queryFn: () => analyticsService.topExercises(20),
  })
  const exercises = topExercises ?? []
  const [selectedExerciseId, setSelectedExerciseId] = useState<string | null>(null)
  const effectiveExerciseId = selectedExerciseId ?? exercises[0]?.exerciseId ?? null

  const progressQuery = useQuery({
    queryKey: queryKeys.analytics.exerciseProgress(effectiveExerciseId ?? ''),
    queryFn: () => analyticsService.exerciseProgress(effectiveExerciseId!),
    enabled: effectiveExerciseId !== null,
  })

  if (topExercisesLoading) return null

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
            {exercises.map((exercise) => (
              <button
                key={exercise.exerciseId}
                type="button"
                className="rounded-full border px-3 py-1 text-sm"
                onClick={() => setSelectedExerciseId(exercise.exerciseId)}
              >
                {exercise.exerciseName}
              </button>
            ))}
          </div>
        </CardContent>
      </Card>

      <ExerciseProgressChart progress={progressQuery.data ?? null} isLoading={progressQuery.isLoading} />
      <ExerciseSessionHistoryCard progress={progressQuery.data ?? null} isLoading={progressQuery.isLoading} />
    </div>
  )
}
