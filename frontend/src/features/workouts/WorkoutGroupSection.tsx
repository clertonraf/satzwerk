import { useState } from 'react'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { Link } from 'react-router-dom'
import { z } from 'zod'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { ADVANCED_TECHNIQUE_OPTIONS } from '@/features/workouts/advancedTechnique'
import WorkoutExerciseRow from '@/features/workouts/WorkoutExerciseRow'
import type { WorkoutGroupDetail } from '@/services/planService'
import {
  workoutExerciseService,
  type CreateWorkoutExerciseRequest,
  type UpdateWorkoutExerciseRequest,
} from '@/services/workoutExerciseService'

const groupSchema = z.object({
  title: z.string().trim().min(1, 'Title is required'),
})

const exerciseSchema = z.object({
  exerciseId: z.string().trim().min(1, 'Exercise is required'),
  sets: z.number().int().min(1, 'Sets must be at least 1'),
  reps: z.number().int().min(1, 'Reps must be at least 1'),
  advancedTechnique: z.string().optional(),
})

type GroupFormValues = z.infer<typeof groupSchema>
type ExerciseFormValues = z.infer<typeof exerciseSchema>

export interface ExerciseOption {
  id: string
  name: string
}

export interface WorkoutGroupSectionProps {
  group: WorkoutGroupDetail
  planId: string
  exerciseOptions?: ExerciseOption[]
  onAddExercise?: (groupId: string, data: CreateWorkoutExerciseRequest) => void | Promise<unknown>
  onDeleteExercise: (groupId: string, exerciseId: string) => void | Promise<unknown>
  onDeleteGroup: (groupId: string) => void | Promise<unknown>
  onUpdateExercise?: (
    groupId: string,
    exerciseId: string,
    data: UpdateWorkoutExerciseRequest
  ) => void | Promise<unknown>
  onUpdateGroup?: (groupId: string, title: string) => void | Promise<unknown>
}

function normalizeExerciseValues(values: ExerciseFormValues): CreateWorkoutExerciseRequest {
  return {
    exerciseId: values.exerciseId,
    sets: values.sets,
    reps: values.reps,
    ...(values.advancedTechnique ? { advancedTechnique: values.advancedTechnique } : {}),
  }
}

export default function WorkoutGroupSection({
  group,
  planId,
  exerciseOptions = [],
  onAddExercise,
  onDeleteExercise,
  onDeleteGroup,
  onUpdateExercise,
  onUpdateGroup,
}: WorkoutGroupSectionProps) {
  const queryClient = useQueryClient()
  const [isAddExerciseOpen, setIsAddExerciseOpen] = useState(false)
  const [isEditGroupOpen, setIsEditGroupOpen] = useState(false)
  const {
    register: registerGroup,
    reset: resetGroup,
    handleSubmit: handleGroupSubmit,
    formState: { errors: groupErrors, isSubmitting: isUpdatingGroup },
  } = useForm<GroupFormValues>({
    resolver: zodResolver(groupSchema),
    defaultValues: { title: group.title },
  })
  const {
    register: registerExercise,
    reset: resetExercise,
    handleSubmit: handleExerciseSubmit,
    formState: { errors: exerciseErrors, isSubmitting: isAddingExercise },
  } = useForm<ExerciseFormValues>({
    resolver: zodResolver(exerciseSchema),
    defaultValues: {
      exerciseId: '',
      sets: 3,
      reps: 10,
      advancedTechnique: '',
    },
  })

  function handleOpenEditGroup() {
    resetGroup({ title: group.title })
    setIsEditGroupOpen(true)
  }

  function handleOpenAddExercise() {
    resetExercise({
      exerciseId: '',
      sets: 3,
      reps: 10,
      advancedTechnique: '',
    })
    setIsAddExerciseOpen(true)
  }

  const reorderMutation = useMutation({
    mutationFn: ({ exerciseId, direction }: { exerciseId: string; direction: 'UP' | 'DOWN' }) =>
      workoutExerciseService.reorder(planId, group.id, exerciseId, direction),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['plans', planId] }),
  })

  const exercises = group.exercises.slice().sort((left, right) => left.orderIndex - right.orderIndex)

  return (
    <>
      <Card className="border-border bg-card/90 shadow-sm">
        <CardHeader className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
          <div className="space-y-1.5">
            <CardTitle className="text-xl">{group.title}</CardTitle>
            <p className="text-sm text-muted-foreground">Workout group #{group.orderIndex + 1}</p>
          </div>

          <div className="flex flex-wrap gap-2">
            <Button type="button" variant="outline" onClick={handleOpenEditGroup}>
              Edit title
            </Button>
            <Button type="button" variant="outline" onClick={handleOpenAddExercise}>
              Add Exercise
            </Button>
            <Button type="button" variant="destructive" onClick={() => onDeleteGroup(group.id)}>
              Delete Group
            </Button>
          </div>
        </CardHeader>

        <CardContent className="space-y-3">
          {exercises.length === 0 ? (
            <p className="text-sm text-muted-foreground">No exercises in this workout group yet.</p>
          ) : null}

          {exercises.map((exercise, index) => (
            <WorkoutExerciseRow
              key={exercise.id}
              exercise={exercise}
              isFirst={index === 0}
              isLast={index === exercises.length - 1}
              onDelete={(exerciseId) => onDeleteExercise(group.id, exerciseId)}
              onMoveDown={(exerciseId) => reorderMutation.mutate({ exerciseId, direction: 'DOWN' })}
              onMoveUp={(exerciseId) => reorderMutation.mutate({ exerciseId, direction: 'UP' })}
              onUpdate={
                onUpdateExercise
                  ? (exerciseId, data) => onUpdateExercise(group.id, exerciseId, data)
                  : undefined
              }
            />
          ))}
        </CardContent>
      </Card>

      <Dialog open={isEditGroupOpen} onOpenChange={setIsEditGroupOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Edit workout group</DialogTitle>
            <DialogDescription>Update the title shown for this workout group.</DialogDescription>
          </DialogHeader>

          <form
            className="space-y-4"
            onSubmit={handleGroupSubmit(async (values) => {
              await onUpdateGroup?.(group.id, values.title.trim())
              setIsEditGroupOpen(false)
            })}
          >
            <div className="space-y-2">
              <label className="text-sm font-medium" htmlFor={`group-title-${group.id}`}>
                Title
              </label>
              <Input id={`group-title-${group.id}`} {...registerGroup('title')} />
              {groupErrors.title ? <p className="text-sm text-destructive">{groupErrors.title.message}</p> : null}
            </div>

            <div className="flex justify-end gap-2">
              <Button type="button" variant="outline" onClick={() => setIsEditGroupOpen(false)}>
                Cancel
              </Button>
              <Button type="submit" disabled={isUpdatingGroup}>
                Save
              </Button>
            </div>
          </form>
        </DialogContent>
      </Dialog>

      <Dialog open={isAddExerciseOpen} onOpenChange={setIsAddExerciseOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Add workout exercise</DialogTitle>
            <DialogDescription>Select an exercise and define the prescribed targets.</DialogDescription>
          </DialogHeader>

          {exerciseOptions.length === 0 ? (
            <p className="text-sm text-muted-foreground">
              Add exercises to your catalog first on the <Link className="underline" to="/exercises">Exercises</Link>{' '}
              page.
            </p>
          ) : (
            <form
              className="space-y-4"
              onSubmit={handleExerciseSubmit(async (values) => {
                await onAddExercise?.(group.id, normalizeExerciseValues(values))
                setIsAddExerciseOpen(false)
              })}
            >
              <div className="space-y-2">
                <label className="text-sm font-medium" htmlFor={`exercise-id-${group.id}`}>
                  Exercise
                </label>
                <select
                  id={`exercise-id-${group.id}`}
                  className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
                  {...registerExercise('exerciseId')}
                >
                  <option value="">Select an exercise</option>
                  {exerciseOptions.map((exercise) => (
                    <option key={exercise.id} value={exercise.id}>
                      {exercise.name}
                    </option>
                  ))}
                </select>
                {exerciseErrors.exerciseId ? (
                  <p className="text-sm text-destructive">{exerciseErrors.exerciseId.message}</p>
                ) : null}
              </div>

              <div className="grid gap-4 sm:grid-cols-2">
                <div className="space-y-2">
                  <label className="text-sm font-medium" htmlFor={`exercise-sets-${group.id}`}>
                    Sets
                  </label>
                  <Input
                    id={`exercise-sets-${group.id}`}
                    type="number"
                    min={1}
                    {...registerExercise('sets', { valueAsNumber: true })}
                  />
                  {exerciseErrors.sets ? <p className="text-sm text-destructive">{exerciseErrors.sets.message}</p> : null}
                </div>

                <div className="space-y-2">
                  <label className="text-sm font-medium" htmlFor={`exercise-reps-${group.id}`}>
                    Reps
                  </label>
                  <Input
                    id={`exercise-reps-${group.id}`}
                    type="number"
                    min={1}
                    {...registerExercise('reps', { valueAsNumber: true })}
                  />
                  {exerciseErrors.reps ? <p className="text-sm text-destructive">{exerciseErrors.reps.message}</p> : null}
                </div>
              </div>

              <div className="space-y-2">
                <label className="text-sm font-medium" htmlFor={`exercise-advanced-technique-${group.id}`}>
                  Advanced technique
                </label>
                <select
                  id={`exercise-advanced-technique-${group.id}`}
                  className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
                  {...registerExercise('advancedTechnique')}
                >
                  {ADVANCED_TECHNIQUE_OPTIONS.map((option) => (
                    <option key={option.value || 'none'} value={option.value}>
                      {option.label}
                    </option>
                  ))}
                </select>
              </div>

              <div className="flex justify-end gap-2">
                <Button type="button" variant="outline" onClick={() => setIsAddExerciseOpen(false)}>
                  Cancel
                </Button>
                <Button type="submit" disabled={isAddingExercise}>
                  Save
                </Button>
              </div>
            </form>
          )}
        </DialogContent>
      </Dialog>
    </>
  )
}
