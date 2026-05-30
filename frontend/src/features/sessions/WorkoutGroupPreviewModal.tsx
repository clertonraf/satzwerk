import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { formatAdvancedTechnique } from '@/features/workouts/advancedTechnique'
import type { WorkoutGroupDetail } from '@/services/planService'

interface WorkoutGroupPreviewModalProps {
  group: WorkoutGroupDetail
  planName: string
  onClose: () => void
}

export default function WorkoutGroupPreviewModal({
  group,
  planName,
  onClose,
}: WorkoutGroupPreviewModalProps) {
  const exercises = group.exercises.slice().sort((a, b) => a.orderIndex - b.orderIndex)

  return (
    <Dialog open onOpenChange={(open) => { if (!open) onClose() }}>
      <DialogContent className="max-h-[80vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{group.title}</DialogTitle>
          <DialogDescription>{planName}</DialogDescription>
        </DialogHeader>

        {exercises.length === 0 ? (
          <p className="text-sm text-muted-foreground">No exercises in this workout group.</p>
        ) : (
          <ul className="space-y-3">
            {exercises.map((exercise) => (
              <li
                key={exercise.id}
                className="rounded-lg border border-border px-4 py-3 space-y-1"
              >
                <p className="font-medium">{exercise.exerciseName}</p>
                <p className="text-sm text-muted-foreground">
                  {exercise.toFailure
                    ? `${exercise.sets} sets × to failure`
                    : `${exercise.sets} sets × ${exercise.reps} reps`}
                </p>
                {formatAdvancedTechnique(exercise.advancedTechnique) ? (
                  <span className="inline-flex w-fit rounded-full bg-secondary px-2.5 py-1 text-xs font-medium text-secondary-foreground">
                    {formatAdvancedTechnique(exercise.advancedTechnique)}
                  </span>
                ) : null}
              </li>
            ))}
          </ul>
        )}
      </DialogContent>
    </Dialog>
  )
}
