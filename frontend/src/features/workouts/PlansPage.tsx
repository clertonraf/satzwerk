import { useRef, useState } from 'react'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
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
import { Input } from '@/components/ui/input'
import PlanCard from '@/features/workouts/PlanCard'
import { planService, type WorkoutPlan } from '@/services/planService'
import { queryKeys } from '@/services/queryKeys'

const schema = z.object({
  name: z.string().trim().min(1, 'Name is required'),
})

type PlanFormValues = z.infer<typeof schema>

export default function PlansPage() {
  const queryClient = useQueryClient()
  const fileInputRef = useRef<HTMLInputElement>(null)
  const [isCreateOpen, setIsCreateOpen] = useState(false)
  const [planToDelete, setPlanToDelete] = useState<WorkoutPlan | null>(null)
  const {
    register,
    reset,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<PlanFormValues>({
    resolver: zodResolver(schema),
    defaultValues: { name: '' },
  })
  const { data: plans = [], isLoading } = useQuery({
    queryKey: queryKeys.plans.all(),
    queryFn: () => planService.list(),
  })

  const createPlanMutation = useMutation({
    mutationFn: (name: string) => planService.create(name),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.plans.all() })
      reset({ name: '' })
      setIsCreateOpen(false)
    },
  })

  const activatePlanMutation = useMutation({
    mutationFn: (id: string) => planService.activate(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.plans.all() }),
  })

  const deletePlanMutation = useMutation({
    mutationFn: (id: string) => planService.delete(id),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.plans.all() })
      setPlanToDelete(null)
    },
  })

  const importMutation = useMutation({
    mutationFn: (file: File) => planService.importFromFile(file),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.plans.all() })
    },
  })

  return (
    <>
      <Card className="border-border bg-card/90 shadow-sm">
        <CardHeader className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
          <div className="space-y-1.5">
            <CardTitle>Workout Plans</CardTitle>
            <CardDescription>Create, activate, and manage your workout plans.</CardDescription>
          </div>
          <div className="flex flex-wrap gap-2">
            <input
              ref={fileInputRef}
              type="file"
              accept=".xlsx"
              className="hidden"
              onChange={(e) => {
                const file = e.target.files?.[0]
                if (file) {
                  importMutation.mutate(file)
                  e.target.value = ''
                }
              }}
            />
            <Button
              type="button"
              variant="outline"
              onClick={() => fileInputRef.current?.click()}
              disabled={importMutation.isPending}
            >
              Import xlsx
            </Button>
            <Button
              type="button"
              onClick={() => {
                reset({ name: '' })
                setIsCreateOpen(true)
              }}
            >
              New Plan
            </Button>
          </div>
        </CardHeader>
        <CardContent className="space-y-4">
          {importMutation.isSuccess ? <p className="text-sm text-emerald-700">Import completed.</p> : null}
          {importMutation.isError ? <p className="text-sm text-destructive">Import failed. Check the file format.</p> : null}
          {isLoading ? <p className="text-sm text-muted-foreground">Loading workout plans...</p> : null}

          {!isLoading && plans.length === 0 ? (
            <p className="text-sm text-muted-foreground">No workout plans yet. Create your first plan.</p>
          ) : null}

          {plans.length > 0 ? (
            <div className="space-y-4">
              {plans.map((plan) => (
                <PlanCard
                  key={plan.id}
                  plan={plan}
                  onActivate={(id) => activatePlanMutation.mutateAsync(id)}
                  onDelete={(id) => {
                    const selectedPlan = plans.find((item) => item.id === id)
                    if (selectedPlan) {
                      setPlanToDelete(selectedPlan)
                    }
                  }}
                />
              ))}
            </div>
          ) : null}
        </CardContent>
      </Card>

      <Dialog
        open={isCreateOpen}
        onOpenChange={(open) => {
          setIsCreateOpen(open)
          if (!open) {
            reset({ name: '' })
          }
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>New workout plan</DialogTitle>
            <DialogDescription>Create a named workout plan for your training groups.</DialogDescription>
          </DialogHeader>

          <form
            className="space-y-4"
            onSubmit={handleSubmit(async (values) => {
              await createPlanMutation.mutateAsync(values.name.trim())
            })}
          >
            <div className="space-y-2">
              <label className="text-sm font-medium" htmlFor="new-plan-name">
                Name
              </label>
              <Input id="new-plan-name" {...register('name')} />
              {errors.name ? <p className="text-sm text-destructive">{errors.name.message}</p> : null}
            </div>

            <div className="flex justify-end gap-2">
              <Button type="button" variant="outline" onClick={() => setIsCreateOpen(false)}>
                Cancel
              </Button>
              <Button type="submit" disabled={isSubmitting || createPlanMutation.isPending}>
                Save
              </Button>
            </div>
          </form>
        </DialogContent>
      </Dialog>

      <AlertDialog open={Boolean(planToDelete)} onOpenChange={(open) => !open && setPlanToDelete(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete workout plan?</AlertDialogTitle>
            <AlertDialogDescription>
              This will permanently remove {planToDelete?.name ?? 'this workout plan'} and all its sessions.
            </AlertDialogDescription>
          </AlertDialogHeader>
          {deletePlanMutation.isError ? (
            <p className="text-sm text-destructive">Delete failed. Please try again.</p>
          ) : null}
          <AlertDialogFooter>
            <AlertDialogCancel disabled={deletePlanMutation.isPending}>Cancel</AlertDialogCancel>
            <AlertDialogAction
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
              disabled={deletePlanMutation.isPending}
              onClick={() => {
                if (planToDelete) {
                  deletePlanMutation.mutate(planToDelete.id)
                }
              }}
            >
              {deletePlanMutation.isPending ? 'Deleting…' : 'Confirm'}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  )
}
