import { Button } from '@/components/ui/button'
import ExerciseSection from '@/features/sessions/ExerciseSection'
import type { WorkoutGroupCatalogEntry } from '@/lib/domainBuilders'
import type { ExerciseReferenceWeights, SetLog, WorkoutSession } from '@/services/sessionService'
import type { Exercise } from '@/services/exerciseService'

interface SessionWorkoutProps {
  session: WorkoutSession
  currentGroupEntry: WorkoutGroupCatalogEntry | undefined
  exercisesById: Map<string, Exercise>
  referenceWeightsMap: Map<string, ExerciseReferenceWeights>
  isReferenceWeightsLoading: boolean
  isCatalogLoading: boolean
  isOnline: boolean
  exerciseUnits: Record<string, 'kg' | 'lb'>
  isAddSetPending: boolean
  isUpdateSetPending: boolean
  isDeleteSetPending: boolean
  isCompletePending: boolean
  isForfeitPending: boolean
  onLogSet: (exerciseId: string, setNumber: number, weight: number, reps: number, unit: 'kg' | 'lb') => void
  onUpdateSetLog: (setLogId: string, weight: number, reps: number, unit: 'kg' | 'lb') => Promise<void>
  onDeleteSetLog: (setLogId: string) => void
  onSetExerciseUnit: (exerciseId: string, unit: 'kg' | 'lb') => void
  onComplete: () => void
  onForfeit: () => void
}

export default function SessionWorkout({
  session,
  currentGroupEntry,
  exercisesById,
  referenceWeightsMap,
  isReferenceWeightsLoading,
  isCatalogLoading,
  isOnline,
  exerciseUnits,
  isAddSetPending,
  isUpdateSetPending,
  isDeleteSetPending,
  isCompletePending,
  isForfeitPending,
  onLogSet,
  onUpdateSetLog,
  onDeleteSetLog,
  onSetExerciseUnit,
  onComplete,
  onForfeit,
}: SessionWorkoutProps) {
  const logsByExerciseId = session.setLogs.reduce<Map<string, SetLog[]>>((acc, log) => {
    const bucket = acc.get(log.exerciseId)
    if (bucket) {
      bucket.push(log)
    } else {
      acc.set(log.exerciseId, [log])
    }
    return acc
  }, new Map())

  return (
    <div className="space-y-4">
      {isCatalogLoading ? (
        <p className="text-sm text-muted-foreground">Loading workout details...</p>
      ) : currentGroupEntry?.group.exercises.length ? (
        currentGroupEntry.group.exercises
          .slice()
          .sort((left, right) => left.orderIndex - right.orderIndex)
          .map((exercise) => {
            const exerciseName = exercisesById.get(exercise.exerciseId)?.name ?? `Exercise ${exercise.exerciseId}`
            const exerciseUnit = exerciseUnits[exercise.exerciseId] ?? 'kg'

            return (
              <ExerciseSection
                key={exercise.id}
                exercise={exercise}
                exerciseName={exerciseName}
                exerciseUnit={exerciseUnit}
                exerciseLogs={logsByExerciseId.get(exercise.exerciseId) ?? []}
                referenceWeights={referenceWeightsMap.get(exercise.exerciseId)}
                isReferenceWeightsLoading={isReferenceWeightsLoading}
                isAddSetPending={isAddSetPending}
                isUpdateSetPending={isUpdateSetPending}
                isDeleteSetPending={isDeleteSetPending}
                isOnline={isOnline}
                onLogSet={onLogSet}
                onUpdateSetLog={onUpdateSetLog}
                onDeleteSetLog={onDeleteSetLog}
                onSetExerciseUnit={onSetExerciseUnit}
              />
            )
          })
      ) : (
        <p className="text-sm text-muted-foreground">
          {isOnline
            ? 'This workout group has no exercises yet.'
            : 'Workout details are unavailable offline. Logged sets stay saved and will sync when you reconnect.'}
        </p>
      )}

      <div className="flex justify-between">
        <Button
          type="button"
          variant="outline"
          className="border-destructive text-destructive hover:bg-destructive hover:text-destructive-foreground"
          disabled={isForfeitPending || isCompletePending}
          onClick={onForfeit}
        >
          Forfeit session
        </Button>
        <Button type="button" disabled={isCompletePending || isForfeitPending} onClick={onComplete}>
          Push Workout
        </Button>
      </div>
    </div>
  )
}
