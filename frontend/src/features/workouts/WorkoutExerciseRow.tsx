import { useState } from 'react'
import { zodResolver } from '@hookform/resolvers/zod'
import { ChevronDown, ChevronUp } from 'lucide-react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import {
  buildAdvancedTechniqueOptions,
  formatAdvancedTechnique,
} from '@/features/workouts/advancedTechnique'
import type {
  AdvancedTechniqueMetadata,
  UpdateWorkoutExerciseRequest,
  WorkoutExerciseSummary,
} from '@/services/planService'

const schema = z.object({
  sets: z.number().int().min(1, 'Sets must be at least 1'),
  reps: z.number().int().min(1, 'Reps must be at least 1'),
  advancedTechnique: z.string().optional(),
})

type WorkoutExerciseFormValues = z.infer<typeof schema>

interface WorkoutExerciseRowProps {
  exercise: WorkoutExerciseSummary
  advancedTechniques?: AdvancedTechniqueMetadata[]
  isFirst: boolean
  isLast: boolean
  onDelete?: (exerciseId: string) => void | Promise<unknown>
  onMoveDown: (exerciseId: string) => void | Promise<unknown>
  onMoveUp: (exerciseId: string) => void | Promise<unknown>
  onUpdate?: (exerciseId: string, data: UpdateWorkoutExerciseRequest) => void | Promise<unknown>
}

function normalizeValues(values: WorkoutExerciseFormValues): UpdateWorkoutExerciseRequest {
  return {
    sets: values.sets,
    reps: values.reps,
    ...(values.advancedTechnique ? { advancedTechnique: values.advancedTechnique } : {}),
  }
}

export default function WorkoutExerciseRow({
  exercise,
  advancedTechniques = [],
  isFirst,
  isLast,
  onDelete,
  onMoveDown,
  onMoveUp,
  onUpdate,
}: WorkoutExerciseRowProps) {
  const [isEditOpen, setIsEditOpen] = useState(false)
  const {
    register,
    reset,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<WorkoutExerciseFormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      sets: exercise.sets,
      reps: exercise.reps,
      advancedTechnique: exercise.advancedTechnique ?? '',
    },
  })

  const advancedTechniqueLabel = formatAdvancedTechnique(advancedTechniques, exercise.advancedTechnique)
  const advancedTechniqueOptions = buildAdvancedTechniqueOptions(advancedTechniques)

  function handleEditOpen() {
    reset({
      sets: exercise.sets,
      reps: exercise.reps,
      advancedTechnique: exercise.advancedTechnique ?? '',
    })
    setIsEditOpen(true)
  }

  return (
    <>
      <div className="flex flex-col gap-3 rounded-lg border border-border px-4 py-4 sm:flex-row sm:items-center sm:justify-between">
        <div className="space-y-1">
          <p className="font-medium">{exercise.exerciseName}</p>
          <p className="text-sm text-muted-foreground">
            {exercise.sets} sets × {exercise.reps} reps
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          {advancedTechniqueLabel ? (
            <span className="inline-flex rounded-full bg-secondary px-2.5 py-1 text-xs font-medium">
              {advancedTechniqueLabel}
            </span>
          ) : null}
          <Button
            type="button"
            variant="outline"
            size="icon"
            aria-label="Move up"
            disabled={isFirst}
            onClick={() => onMoveUp(exercise.id)}
          >
            <ChevronUp />
          </Button>
          <Button
            type="button"
            variant="outline"
            size="icon"
            aria-label="Move down"
            disabled={isLast}
            onClick={() => onMoveDown(exercise.id)}
          >
            <ChevronDown />
          </Button>
          <Button type="button" variant="outline" onClick={handleEditOpen}>
            Edit
          </Button>
          <Button type="button" variant="destructive" onClick={() => onDelete?.(exercise.id)}>
            Delete
          </Button>
        </div>
      </div>

      <Dialog open={isEditOpen} onOpenChange={setIsEditOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Edit workout exercise</DialogTitle>
            <DialogDescription>Update the prescribed sets, reps, and advanced technique.</DialogDescription>
          </DialogHeader>

          <form
            className="space-y-4"
            onSubmit={handleSubmit(async (values) => {
              await onUpdate?.(exercise.id, normalizeValues(values))
              setIsEditOpen(false)
            })}
          >
            <div className="space-y-2">
              <label className="text-sm font-medium" htmlFor={`sets-${exercise.id}`}>
                Sets
              </label>
              <Input id={`sets-${exercise.id}`} type="number" min={1} {...register('sets', { valueAsNumber: true })} />
              {errors.sets ? <p className="text-sm text-destructive">{errors.sets.message}</p> : null}
            </div>

            <div className="space-y-2">
              <label className="text-sm font-medium" htmlFor={`reps-${exercise.id}`}>
                Reps
              </label>
              <Input id={`reps-${exercise.id}`} type="number" min={1} {...register('reps', { valueAsNumber: true })} />
              {errors.reps ? <p className="text-sm text-destructive">{errors.reps.message}</p> : null}
            </div>

            <div className="space-y-2">
              <label className="text-sm font-medium" htmlFor={`advancedTechnique-${exercise.id}`}>
                Advanced technique
              </label>
              <select
                id={`advancedTechnique-${exercise.id}`}
                className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
                {...register('advancedTechnique')}
              >
                {advancedTechniqueOptions.map((option) => (
                  <option key={option.value || 'none'} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </div>

            <div className="flex justify-end gap-2">
              <Button type="button" variant="outline" onClick={() => setIsEditOpen(false)}>
                Cancel
              </Button>
              <Button type="submit" disabled={isSubmitting}>
                Save
              </Button>
            </div>
          </form>
        </DialogContent>
      </Dialog>
    </>
  )
}
