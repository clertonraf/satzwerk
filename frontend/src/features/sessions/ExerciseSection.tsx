import { useState } from 'react'
import { Pencil, Trash2 } from 'lucide-react'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import AdvancedTechniqueBadge from '@/features/sessions/AdvancedTechniqueBadge'
import ExerciseReferenceRow from '@/features/sessions/ExerciseReferenceRow'
import RestTimer from '@/features/sessions/RestTimer'
import SetInput from '@/features/sessions/SetInput'
import { toPounds, setLogCompletionClass } from '@/features/sessions/sessionHelpers'
import { getAdvancedTechniqueRestSeconds } from '@/features/workouts/advancedTechnique'
import { cn } from '@/lib/utils'
import { formatDisplayWeight } from '@/lib/unitFormatters'
import type { ExerciseReferenceWeights, SetLogResult } from '@/services/sessionService'
import type { WorkoutExerciseSummary } from '@/services/planService'

interface ExerciseSectionProps {
  exercise: WorkoutExerciseSummary
  exerciseName: string
  exerciseUnit: 'kg' | 'lb'
  exerciseLogs: SetLogResult[]
  referenceWeights: ExerciseReferenceWeights | undefined
  isReferenceWeightsLoading: boolean
  isAddSetPending: boolean
  isUpdateSetPending: boolean
  isDeleteSetPending: boolean
  isOnline: boolean
  onLogSet: (exerciseId: string, setNumber: number, weight: number, reps: number, unit: 'kg' | 'lb') => void
  onUpdateSetLog: (setLogId: string, weight: number, reps: number, unit: 'kg' | 'lb') => Promise<void>
  onDeleteSetLog: (setLogId: string) => void
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
  isDeleteSetPending,
  isOnline,
  onLogSet,
  onUpdateSetLog,
  onDeleteSetLog,
  onSetExerciseUnit,
}: ExerciseSectionProps) {
  const [editingSetLogId, setEditingSetLogId] = useState<string | null>(null)
  const [pendingDeleteSetLogId, setPendingDeleteSetLogId] = useState<string | null>(null)
  const pendingDeleteLog = pendingDeleteSetLogId ? exerciseLogs.find((l) => l.id === pendingDeleteSetLogId) : null
  const nextSetNumber = exerciseLogs.length + 1
  const techniqueRestSeconds = getAdvancedTechniqueRestSeconds(exercise.advancedTechnique)
  const lastLog = exerciseLogs.length > 0 ? exerciseLogs[exerciseLogs.length - 1] : undefined

  return (
    <Card className="border-border bg-background/70 shadow-none">
      <CardHeader>
        <div className="flex items-start justify-between gap-2">
          <div className="space-y-1.5">
            <CardTitle className="text-xl">{exerciseName}</CardTitle>
            <CardDescription>
              {exercise.toFailure
                ? `Target ${exercise.sets} sets until failure`
                : `Target ${exercise.sets} sets × ${exercise.reps} reps`}
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
          variant="inline"
          defaultWeight={lastLog ? (exerciseUnit === 'kg' ? lastLog.weight : toPounds(lastLog.weight)) : undefined}
          defaultReps={lastLog?.reps}
          onLog={({ reps, setNumber, weight }) => {
            onLogSet(exercise.exerciseId, setNumber, weight, reps, exerciseUnit)
          }}
        />
        {/* key uses the technique name, not the derived seconds, so that switching between
            techniques with the same rest duration (e.g. FST_7 and GIRONDA both 30 s) still
            remounts the timer and resets isRunning / secondsLeft. */}
        <RestTimer key={exercise.advancedTechnique ?? ''} defaultSeconds={techniqueRestSeconds ?? undefined} />
        {/* SST has zero rest between drops — show guidance instead of a timer */}
        {techniqueRestSeconds === 0 && exercise.advancedTechnique ? (
          <p className="text-sm text-muted-foreground">
            Drop sets: no rest — reduce load by 20–30% immediately after each set.
          </p>
        ) : null}

        {exerciseLogs.length > 0 ? (
          <div className="space-y-2">
            <p className="text-sm font-medium">Logged sets</p>
            <ul className="space-y-2 text-sm text-muted-foreground">
              {exerciseLogs.map((log) => (
                <li
                  key={log.id}
                  className={cn(
                    'rounded-lg border px-3 py-2',
                    setLogCompletionClass(log.setNumber, exercise.sets, log.pending ?? false),
                    log.pending && 'opacity-60',
                  )}
                >
                  {!log.pending && editingSetLogId === log.id ? (
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
                        {log.pending ? <span className="ml-2 text-xs text-muted-foreground">(syncing…)</span> : null}
                      </span>
                      <span className="flex items-center gap-1">
                        <Button
                          type="button"
                          size="icon"
                          variant="ghost"
                          aria-label="Edit set"
                          disabled={!isOnline || log.pending}
                          onClick={() => setEditingSetLogId(log.id)}
                        >
                          <Pencil className="size-4" />
                        </Button>
                        <Button
                          type="button"
                          size="icon"
                          variant="ghost"
                          aria-label="Delete set"
                          className="text-destructive hover:bg-destructive hover:text-destructive-foreground"
                          disabled={!isOnline || log.pending || isDeleteSetPending}
                          onClick={() => setPendingDeleteSetLogId(log.id)}
                        >
                          <Trash2 className="size-4" />
                        </Button>
                      </span>
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

      <AlertDialog
        open={Boolean(pendingDeleteSetLogId)}
        onOpenChange={(open) => !open && setPendingDeleteSetLogId(null)}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete set?</AlertDialogTitle>
            <AlertDialogDescription>
              {pendingDeleteLog
                ? `This will permanently remove Set ${pendingDeleteLog.setNumber} from this session.`
                : 'This will permanently remove this set from the session.'}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={isDeleteSetPending}>Cancel</AlertDialogCancel>
            <AlertDialogAction
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
              disabled={isDeleteSetPending}
              onClick={() => {
                if (pendingDeleteSetLogId) {
                  onDeleteSetLog(pendingDeleteSetLogId)
                  setPendingDeleteSetLogId(null)
                }
              }}
            >
              Delete
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </Card>
  )
}
