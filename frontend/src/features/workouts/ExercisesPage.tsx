import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
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
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import ExerciseForm from '@/features/workouts/ExerciseForm'
import {
  exerciseService,
  type CreateExerciseRequest,
  type Exercise,
} from '@/services/exerciseService'

export default function ExercisesPage() {
  const queryClient = useQueryClient()
  const [isFormOpen, setIsFormOpen] = useState(false)
  const [editingExercise, setEditingExercise] = useState<Exercise | null>(null)
  const [exerciseToDelete, setExerciseToDelete] = useState<Exercise | null>(null)
  const { data: exercises = [], isLoading } = useQuery({
    queryKey: ['exercises'],
    queryFn: () => exerciseService.list(),
  })

  const createExerciseMutation = useMutation({
    mutationFn: (data: CreateExerciseRequest) => exerciseService.create(data),
    onSuccess: (createdExercise) => {
      queryClient.setQueryData<Exercise[]>(['exercises'], (current = []) => [createdExercise, ...current])
      void queryClient.invalidateQueries({ queryKey: ['exercises'] })
      setIsFormOpen(false)
    },
  })

  const updateExerciseMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: CreateExerciseRequest }) => exerciseService.update(id, data),
    onSuccess: (updatedExercise) => {
      queryClient.setQueryData<Exercise[]>(['exercises'], (current = []) =>
        current.map((exercise) => (exercise.id === updatedExercise.id ? updatedExercise : exercise))
      )
      void queryClient.invalidateQueries({ queryKey: ['exercises'] })
      setEditingExercise(null)
      setIsFormOpen(false)
    },
  })

  const deleteExerciseMutation = useMutation({
    mutationFn: (id: string) => exerciseService.delete(id),
    onSuccess: (_, id) => {
      queryClient.setQueryData<Exercise[]>(['exercises'], (current = []) =>
        current.filter((exercise) => exercise.id !== id)
      )
      void queryClient.invalidateQueries({ queryKey: ['exercises'] })
      setExerciseToDelete(null)
    },
  })

  const isSaving = createExerciseMutation.isPending || updateExerciseMutation.isPending

  async function handleSubmit(data: CreateExerciseRequest) {
    if (editingExercise) {
      await updateExerciseMutation.mutateAsync({ id: editingExercise.id, data })
      return
    }

    await createExerciseMutation.mutateAsync(data)
  }

  function handleCreateOpen() {
    setEditingExercise(null)
    setIsFormOpen(true)
  }

  function handleEditOpen(exercise: Exercise) {
    setEditingExercise(exercise)
    setIsFormOpen(true)
  }

  const formDefaultValues = editingExercise
    ? {
        name: editingExercise.name,
        muscleGroup: editingExercise.muscleGroup,
        description: editingExercise.description ?? undefined,
        videoUrl: editingExercise.videoUrl ?? undefined,
        equipment: editingExercise.equipment ?? undefined,
      }
    : undefined

  return (
    <>
      <Card className="border-border bg-card/90 shadow-sm">
        <CardHeader className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
          <div className="space-y-1.5">
            <CardTitle>Exercises</CardTitle>
            <CardDescription>Manage your personal exercise catalog.</CardDescription>
          </div>
          <Button type="button" onClick={handleCreateOpen}>
            New exercise
          </Button>
        </CardHeader>
        <CardContent>
          {isLoading ? <p className="text-sm text-muted-foreground">Loading exercises...</p> : null}

          {!isLoading && exercises.length === 0 ? (
            <p className="text-sm text-muted-foreground">No exercises yet. Add your first exercise.</p>
          ) : null}

          {exercises.length > 0 ? (
            <ul className="space-y-3">
              {exercises.map((exercise) => (
                <li
                  key={exercise.id}
                  className="flex flex-col gap-4 rounded-lg border border-border px-4 py-4 sm:flex-row sm:items-start sm:justify-between"
                >
                  <div className="space-y-1">
                    <p className="font-medium">{exercise.name}</p>
                    <p className="text-sm text-muted-foreground">{exercise.muscleGroup}</p>
                    {exercise.description ? (
                      <p className="text-sm text-muted-foreground">{exercise.description}</p>
                    ) : null}
                    {exercise.equipment ? (
                      <p className="text-sm text-muted-foreground">Equipment: {exercise.equipment}</p>
                    ) : null}
                    {exercise.videoUrl ? (
                      <a
                        className="text-sm font-medium text-primary underline underline-offset-4"
                        href={exercise.videoUrl}
                        target="_blank"
                        rel="noreferrer"
                      >
                        Watch video
                      </a>
                    ) : null}
                  </div>
                  <div className="flex gap-2 self-start">
                    <Button type="button" variant="outline" onClick={() => handleEditOpen(exercise)}>
                      Edit
                    </Button>
                    <Button type="button" variant="destructive" onClick={() => setExerciseToDelete(exercise)}>
                      Delete
                    </Button>
                  </div>
                </li>
              ))}
            </ul>
          ) : null}
        </CardContent>
      </Card>

      <Dialog
        open={isFormOpen}
        onOpenChange={(open) => {
          setIsFormOpen(open)
          if (!open) {
            setEditingExercise(null)
          }
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{editingExercise ? 'Edit exercise' : 'New exercise'}</DialogTitle>
            <DialogDescription>
              Save changes to your personal exercise catalog.
            </DialogDescription>
          </DialogHeader>
          <ExerciseForm defaultValues={formDefaultValues} isLoading={isSaving} onSubmit={handleSubmit} />
        </DialogContent>
      </Dialog>

      <AlertDialog open={Boolean(exerciseToDelete)} onOpenChange={(open) => !open && setExerciseToDelete(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete exercise?</AlertDialogTitle>
            <AlertDialogDescription>
              This will permanently remove {exerciseToDelete?.name ?? 'this exercise'} from your catalog.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={deleteExerciseMutation.isPending}>Cancel</AlertDialogCancel>
            <AlertDialogAction
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
              disabled={deleteExerciseMutation.isPending}
              onClick={() => {
                if (exerciseToDelete) {
                  deleteExerciseMutation.mutate(exerciseToDelete.id)
                }
              }}
            >
              Confirm
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  )
}
