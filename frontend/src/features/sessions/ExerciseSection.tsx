import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import AdvancedTechniqueBadge from '@/features/sessions/AdvancedTechniqueBadge'
import ExerciseReferenceRow from '@/features/sessions/ExerciseReferenceRow'
import RestTimer from '@/features/sessions/RestTimer'
import SetInput from '@/features/sessions/SetInput'
import { toPounds } from '@/features/sessions/sessionHelpers'
import { formatDisplayWeight } from '@/lib/unitFormatters'
import type { ExerciseReferenceWeights, SetLog } from '@/services/sessionService'
import type { WorkoutExerciseSummary } from '@/services/planService'

interface ExerciseSectionProps {
  exercise: WorkoutExerciseSummary
  exerciseName: string
  exerciseUnit: 'kg' | 'lb'
  exerciseLogs: SetLog[]
  referenceWeights: ExerciseReferenceWeights | undefined
  isReferenceWeightsLoading: boolean
  isAddSetPending: boolean
  isUpdateSetPending: boolean
  isOnline: boolean
  onLogSet: (exerciseId: string, setNumber: number, weight: number, reps: number, unit: 'kg' | 'lb') => void
  onUpdateSetLog: (setLogId: string, weight: number, reps: number, unit: 'kg' | 'lb') => Promise<void>
  onSetExerciseUnit: (exerciseId: string, unit: 'kg' | 'lb') => void
}

export default function ExerciseSection({
  exercise,
  exerciseName,
  exerciseUnit,
  exerciseLogs,
  referenceWeights,
  isReferenceWeightsLoading,
  isAddSetPending,
  isUpdateSetPending,
  isOnline,
  onLogSet,
  onUpdateSetLog,
  onSetExerciseUnit,
}: ExerciseSectionProps) {
  const [editingSetLogId, setEditingSetLogId] = useState<string | null>(null)
  const nextSetNumber = exerciseLogs.length + 1

  return (
    <Card className="border-border bg-background/70 shadow-none">
      <CardHeader>
        <div className="flex items-start justify-between gap-2">
          <div className="space-y-1.5">
            <CardTitle className="text-xl">{exerciseName}</CardTitle>
            <CardDescription>
              Target {exercise.sets} sets × {exercise.reps} reps
            </CardDescription>
            {exercise.advancedTechnique ? <AdvancedTechniqueBadge technique={exercise.advancedTechnique} /> : null}
          </div>
          <div className="flex shrink-0 items-center gap-1 rounded-lg border border-border p-1">
            <Button
              type="button"
              size="sm"
              variant={exerciseUnit === 'kg' ? 'default' : 'ghost'}
              onClick={() => onSetExerciseUnit(exercise.exerciseId, 'kg')}
            >
              kg
            </Button>
            <Button
              type="button"
              size="sm"
              variant={exerciseUnit === 'lb' ? 'default' : 'ghost'}
              onClick={() => onSetExerciseUnit(exercise.exerciseId, 'lb')}
            >
              lb
            </Button>
          </div>
        </div>
      </CardHeader>
      <CardContent className="space-y-4">
        <ExerciseReferenceRow
          referenceWeights={referenceWeights}
          isLoading={isReferenceWeightsLoading}
          unit={exerciseUnit}
        />
        <SetInput
          isLoading={isAddSetPending}
          setNumber={nextSetNumber}
          unit={exerciseUnit}
          onLog={({ reps, setNumber, weight }) => {
            onLogSet(exercise.exerciseId, setNumber, weight, reps, exerciseUnit)
          }}
        />
        <RestTimer />

        {exerciseLogs.length > 0 ? (
          <div className="space-y-2">
            <p className="text-sm font-medium">Logged sets</p>
            <ul className="space-y-2 text-sm text-muted-foreground">
              {exerciseLogs.map((log) => (
                <li key={log.id} className="rounded-lg border border-border px-3 py-2">
                  {editingSetLogId === log.id ? (
                    <SetInput
                      key={`${log.id}-${exerciseUnit}`}
                      isLoading={isUpdateSetPending}
                      setNumber={log.setNumber}
                      unit={exerciseUnit}
                      defaultWeight={exerciseUnit === 'kg' ? log.weight : toPounds(log.weight)}
                      defaultReps={log.reps}
                      submitLabel="Save"
                      resetOnSubmit={false}
                      onLog={({ weight, reps }) => {
                        onUpdateSetLog(log.id, weight, reps, exerciseUnit)
                          .then(() => setEditingSetLogId(null))
                          .catch(() => {
                            /* stay in edit mode so the user can retry */
                          })
                      }}
                      onCancel={() => setEditingSetLogId(null)}
                    />
                  ) : (
                    <div className="flex items-center justify-between">
                      <span>
                        Set {log.setNumber}: {formatDisplayWeight(log.weight, exerciseUnit)} × {log.reps}
                      </span>
                      <Button
                        type="button"
                        size="sm"
                        variant="ghost"
                        disabled={!isOnline || log.id.startsWith('queued-')}
                        onClick={() => setEditingSetLogId(log.id)}
                      >
                        Edit
                      </Button>
                    </div>
                  )}
                </li>
              ))}
            </ul>
          </div>
        ) : (
          <p className="text-sm text-muted-foreground">No sets logged yet.</p>
        )}
      </CardContent>
    </Card>
  )
}
