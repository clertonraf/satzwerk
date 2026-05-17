import { useState } from 'react'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { useParams } from 'react-router-dom'
import { z } from 'zod'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import WorkoutGroupSection from '@/features/workouts/WorkoutGroupSection'
import { exerciseService } from '@/services/exerciseService'
import {
  planService,
  workoutExerciseService,
  workoutGroupService,
  type CreateWorkoutExerciseRequest,
  type UpdateWorkoutExerciseRequest,
} from '@/services/planService'
import { queryKeys } from '@/services/queryKeys'

const nameSchema = z.object({
  name: z.string().trim().min(1, 'Name is required'),
})

const groupSchema = z.object({
  title: z.string().trim().min(1, 'Title is required'),
})

type NameFormValues = z.infer<typeof nameSchema>
type GroupFormValues = z.infer<typeof groupSchema>

export default function PlanBuilderPage() {
  const { planId } = useParams<{ planId: string }>()
  const queryClient = useQueryClient()
  const [isAddGroupOpen, setIsAddGroupOpen] = useState(false)
  const [isEditingName, setIsEditingName] = useState(false)
  const {
    register: registerName,
    reset: resetName,
    handleSubmit: handleNameSubmit,
    formState: { errors: nameErrors, isSubmitting: isUpdatingName },
  } = useForm<NameFormValues>({
    resolver: zodResolver(nameSchema),
    defaultValues: { name: '' },
  })
  const {
    register: registerGroup,
    reset: resetGroup,
    handleSubmit: handleGroupSubmit,
    formState: { errors: groupErrors, isSubmitting: isCreatingGroup },
  } = useForm<GroupFormValues>({
    resolver: zodResolver(groupSchema),
    defaultValues: { title: '' },
  })

  const { data: plan, isLoading } = useQuery({
    queryKey: queryKeys.plans.detail(planId ?? ''),
    queryFn: () => planService.get(planId!),
    enabled: Boolean(planId),
  })
  const { data: exerciseCatalog = [] } = useQuery({
    queryKey: queryKeys.exercises.all(),
    queryFn: () => exerciseService.list(),
  })

  async function refreshPlanData() {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: queryKeys.plans.all() }),
      queryClient.invalidateQueries({ queryKey: queryKeys.plans.detail(planId!) }),
    ])
  }

  const updatePlanMutation = useMutation({
    mutationFn: (name: string) => planService.update(planId!, name),
    onSuccess: async () => {
      await refreshPlanData()
      setIsEditingName(false)
    },
  })

  const createGroupMutation = useMutation({
    mutationFn: (title: string) => workoutGroupService.create(planId!, title),
    onSuccess: async () => {
      await refreshPlanData()
      setIsAddGroupOpen(false)
      resetGroup({ title: '' })
    },
  })

  const updateGroupMutation = useMutation({
    mutationFn: ({ groupId, title }: { groupId: string; title: string }) => workoutGroupService.update(planId!, groupId, title),
    onSuccess: refreshPlanData,
  })

  const deleteGroupMutation = useMutation({
    mutationFn: (groupId: string) => workoutGroupService.delete(planId!, groupId),
    onSuccess: refreshPlanData,
  })

  const createExerciseMutation = useMutation({
    mutationFn: ({ groupId, data }: { groupId: string; data: CreateWorkoutExerciseRequest }) =>
      workoutExerciseService.create(planId!, groupId, data),
    onSuccess: refreshPlanData,
  })

  const updateExerciseMutation = useMutation({
    mutationFn: ({ groupId, exerciseId, data }: { groupId: string; exerciseId: string; data: UpdateWorkoutExerciseRequest }) =>
      workoutExerciseService.update(planId!, groupId, exerciseId, data),
    onSuccess: refreshPlanData,
  })

  const deleteExerciseMutation = useMutation({
    mutationFn: ({ groupId, exerciseId }: { groupId: string; exerciseId: string }) =>
      workoutExerciseService.delete(planId!, groupId, exerciseId),
    onSuccess: refreshPlanData,
  })

  if (!planId) {
    return <p className="text-sm text-muted-foreground">Workout plan not found.</p>
  }

  if (isLoading || !plan) {
    return <p className="text-sm text-muted-foreground">Loading workout plan...</p>
  }

  const exerciseOptions = exerciseCatalog.map((exercise) => ({ id: exercise.id, name: exercise.name }))

  return (
    <div className="space-y-6">
      <Card className="border-border bg-card/90 shadow-sm">
        <CardHeader className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
          <div className="space-y-1.5">
            <CardTitle>{plan.name}</CardTitle>
            <CardDescription>
              {plan.source} · {plan.isActive ? 'Active workout plan' : 'Inactive workout plan'}
            </CardDescription>
          </div>
          <div className="flex flex-wrap gap-2">
            <Button
              type="button"
              variant="outline"
              onClick={() => {
                resetName({ name: plan.name })
                setIsEditingName(true)
              }}
            >
              Edit Name
            </Button>
            <Button
              type="button"
              onClick={() => {
                resetGroup({ title: '' })
                setIsAddGroupOpen(true)
              }}
            >
              Add Group
            </Button>
          </div>
        </CardHeader>
        <CardContent>
          <p className="text-sm text-muted-foreground">
            Build ordered workout groups and prescribe exercises for each one.
          </p>
        </CardContent>
      </Card>

      {plan.groups.length === 0 ? (
        <p className="text-sm text-muted-foreground">No workout groups yet. Add your first group.</p>
      ) : (
        <div className="space-y-4">
          {plan.groups
            .slice()
            .sort((left, right) => left.orderIndex - right.orderIndex)
            .map((group) => (
              <WorkoutGroupSection
                key={group.id}
                planId={planId}
                group={group}
                exerciseOptions={exerciseOptions}
                onAddExercise={(groupId, data) => createExerciseMutation.mutateAsync({ groupId, data })}
                onDeleteExercise={(groupId, exerciseId) => deleteExerciseMutation.mutateAsync({ groupId, exerciseId })}
                onDeleteGroup={(groupId) => deleteGroupMutation.mutateAsync(groupId)}
                onUpdateExercise={(groupId, exerciseId, data) =>
                  updateExerciseMutation.mutateAsync({ groupId, exerciseId, data })
                }
                onUpdateGroup={(groupId, title) => updateGroupMutation.mutateAsync({ groupId, title })}
              />
            ))}
        </div>
      )}

      <Dialog
        open={isEditingName}
        onOpenChange={(open) => {
          setIsEditingName(open)
          if (!open) {
            resetName({ name: plan.name })
          }
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Edit workout plan</DialogTitle>
            <DialogDescription>Update the workout plan name.</DialogDescription>
          </DialogHeader>

          <form
            className="space-y-4"
            onSubmit={handleNameSubmit(async (values) => {
              await updatePlanMutation.mutateAsync(values.name.trim())
            })}
          >
            <div className="space-y-2">
              <label className="text-sm font-medium" htmlFor="plan-name">
                Name
              </label>
              <Input id="plan-name" {...registerName('name')} />
              {nameErrors.name ? <p className="text-sm text-destructive">{nameErrors.name.message}</p> : null}
            </div>

            <div className="flex justify-end gap-2">
              <Button type="button" variant="outline" onClick={() => setIsEditingName(false)}>
                Cancel
              </Button>
              <Button type="submit" disabled={isUpdatingName || updatePlanMutation.isPending}>
                Save
              </Button>
            </div>
          </form>
        </DialogContent>
      </Dialog>

      <Dialog
        open={isAddGroupOpen}
        onOpenChange={(open) => {
          setIsAddGroupOpen(open)
          if (!open) {
            resetGroup({ title: '' })
          }
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Add workout group</DialogTitle>
            <DialogDescription>Create a new training group inside this workout plan.</DialogDescription>
          </DialogHeader>

          <form
            className="space-y-4"
            onSubmit={handleGroupSubmit(async (values) => {
              await createGroupMutation.mutateAsync(values.title.trim())
            })}
          >
            <div className="space-y-2">
              <label className="text-sm font-medium" htmlFor="group-title">
                Title
              </label>
              <Input id="group-title" {...registerGroup('title')} />
              {groupErrors.title ? <p className="text-sm text-destructive">{groupErrors.title.message}</p> : null}
            </div>

            <div className="flex justify-end gap-2">
              <Button type="button" variant="outline" onClick={() => setIsAddGroupOpen(false)}>
                Cancel
              </Button>
              <Button type="submit" disabled={isCreatingGroup || createGroupMutation.isPending}>
                Save
              </Button>
            </div>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  )
}
