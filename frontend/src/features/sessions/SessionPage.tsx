import { useEffect, useMemo, useState } from 'react'
import { useQueries, useQuery } from '@tanstack/react-query'
import { Link, useNavigate } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import RestTimer from '@/features/sessions/RestTimer'
import ForfeitSessionModal from '@/features/sessions/ForfeitSessionModal'
import ResumeDiscardModal from '@/features/sessions/ResumeDiscardModal'
import SetInput from '@/features/sessions/SetInput'
import WorkoutGroupPreviewModal from '@/features/sessions/WorkoutGroupPreviewModal'
import { buildWorkoutGroupCatalog, formatDisplayWeight, formatSessionDate } from '@/features/sessions/sessionHelpers'
import { formatAdvancedTechnique } from '@/features/workouts/advancedTechnique'
import { useWorkoutSession } from '@/features/sessions/useWorkoutSession'
import { useOnlineStatus } from '@/hooks/useOnlineStatus'
import { exerciseService } from '@/services/exerciseService'
import { planService } from '@/services/planService'
import { queryKeys } from '@/services/queryKeys'
import type { WorkoutGroupDetail } from '@/services/planService'

export default function SessionPage() {
  const navigate = useNavigate()
  const isOnline = useOnlineStatus()
  const [isForfeitModalOpen, setIsForfeitModalOpen] = useState(false)
  const [previewGroup, setPreviewGroup] = useState<{ group: WorkoutGroupDetail; planName: string } | null>(null)
  const [exerciseUnits, setExerciseUnits] = useState<Record<string, 'kg' | 'lb'>>({})
  const setExerciseUnit = (exerciseId: string, unit: 'kg' | 'lb') =>
    setExerciseUnits((prev) => ({ ...prev, [exerciseId]: unit }))

  const {
    session,
    conflictSession,
    isSessionLoading,
    handleStartSession,
    handleLogSet,
    handleCompleteSession,
    handleForfeitSession,
    handleDiscardConflict,
    clearConflictState,
    isStartPending,
    isAddSetPending,
    isCompletePending,
    isForfeitPending,
  } = useWorkoutSession({
    onComplete: () => navigate('/history'),
    onForfeit: () => navigate('/session'),
  })

  useEffect(() => {
    setExerciseUnits({})
  }, [session?.id])
  const plansQuery = useQuery({
    queryKey: queryKeys.plans.all(),
    queryFn: () => planService.list(),
  })
  const exercisesQuery = useQuery({
    queryKey: queryKeys.exercises.all(),
    queryFn: () => exerciseService.list(),
  })
  const planDetailsQueries = useQueries({
    queries: (plansQuery.data ?? []).map((plan) => ({
      queryKey: queryKeys.plans.detail(plan.id),
      queryFn: () => planService.get(plan.id),
    })),
  })

  const planDetails = planDetailsQueries.flatMap((query) => (query.data ? [query.data] : []))
  const groupCatalog = useMemo(() => buildWorkoutGroupCatalog(planDetails), [planDetails])
  const groupOptions = useMemo(
    () =>
      Object.values(groupCatalog).sort((left, right) => {
        if (left.plan.isActive !== right.plan.isActive) {
          return Number(right.plan.isActive) - Number(left.plan.isActive)
        }

        if (left.plan.name !== right.plan.name) {
          return left.plan.name.localeCompare(right.plan.name)
        }

        return left.group.orderIndex - right.group.orderIndex
      }),
    [groupCatalog]
  )
  const exercisesById = useMemo(
    () => new Map((exercisesQuery.data ?? []).map((exercise) => [exercise.id, exercise])),
    [exercisesQuery.data]
  )
  const currentGroupEntry = session ? groupCatalog[session.workoutGroupId] : undefined
  const queryError = session
    ? null
    : plansQuery.error ??
      exercisesQuery.error ??
      planDetailsQueries.find((query) => query.error)?.error
  const isCatalogLoading =
    plansQuery.isLoading || exercisesQuery.isLoading || planDetailsQueries.some((query) => query.isLoading)

  if (queryError) {
    return <p className="text-sm text-destructive">Could not load workout session data.</p>
  }

  if (!session && isSessionLoading) {
    return <p className="text-sm text-muted-foreground">Loading workout session...</p>
  }

  return (
    <div className="space-y-6">
      {conflictSession ? (
        <ResumeDiscardModal onResume={clearConflictState} onDiscard={() => void handleDiscardConflict()} />
      ) : null}

      {isForfeitModalOpen ? (
        <ForfeitSessionModal
          onConfirm={() => {
            setIsForfeitModalOpen(false)
            void handleForfeitSession()
          }}
          onCancel={() => setIsForfeitModalOpen(false)}
        />
      ) : null}

      {previewGroup ? (
        <WorkoutGroupPreviewModal
          group={previewGroup.group}
          planName={previewGroup.planName}
          onClose={() => setPreviewGroup(null)}
        />
      ) : null}

      <Card className="border-border bg-card/90 shadow-sm">
        <CardHeader className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
          <div className="space-y-1.5">
            <CardTitle>{session ? currentGroupEntry?.group.title ?? 'Workout Session' : 'Start workout session'}</CardTitle>
            <CardDescription>
              {session
                ? `${currentGroupEntry?.plan.name ?? 'Workout plan'} · Started ${formatSessionDate(session.startedAt)}`
                : 'Choose a workout group to begin training.'}
            </CardDescription>
          </div>
        </CardHeader>
        <CardContent>
          {!session ? (
            isCatalogLoading ? (
              <p className="text-sm text-muted-foreground">Loading workout groups...</p>
            ) : groupOptions.length === 0 ? (
              <p className="text-sm text-muted-foreground">
                No workout groups found yet. Build your training split on the <Link className="underline" to="/plans">Plans</Link>{' '}
                page.
              </p>
            ) : (
              <div className="space-y-3">
                {!isOnline ? (
                  <p className="text-sm text-muted-foreground">
                    Reconnect to start a new workout. Your current session data stays available offline.
                  </p>
                ) : null}
                {groupOptions.map(({ group, plan }) => (
                  <div
                    key={group.id}
                    className="flex w-full items-center justify-between rounded-lg border border-border px-4 py-4"
                  >
                    <span>
                      <span className="block font-medium">{group.title}</span>
                      <span className="block text-sm text-muted-foreground">
                        {plan.name} · {group.exercises.length} exercises
                      </span>
                    </span>
                    <span className="flex items-center gap-2">
                      <Button
                        type="button"
                        size="sm"
                        variant="ghost"
                        onClick={() => setPreviewGroup({ group, planName: plan.name })}
                      >
                        Preview
                      </Button>
                      <Button
                        type="button"
                        size="sm"
                        disabled={!isOnline || isStartPending}
                        onClick={() => {
                          void handleStartSession(group.id)
                        }}
                      >
                        Start
                      </Button>
                    </span>
                  </div>
                ))}
              </div>
            )
          ) : null}

          {session ? (
            <div className="space-y-4">
              {isCatalogLoading ? (
                <p className="text-sm text-muted-foreground">Loading workout details...</p>
              ) : currentGroupEntry?.group.exercises.length ? (
                currentGroupEntry.group.exercises
                  .slice()
                  .sort((left, right) => left.orderIndex - right.orderIndex)
                  .map((exercise) => {
                    const exerciseLogs = session.setLogs.filter((log) => log.exerciseId === exercise.exerciseId)
                    const nextSetNumber = exerciseLogs.length + 1
                    const exerciseName = exercisesById.get(exercise.exerciseId)?.name ?? `Exercise ${exercise.exerciseId}`
                    const exerciseUnit = exerciseUnits[exercise.exerciseId] ?? 'kg'

                    return (
                      <Card key={exercise.id} className="border-border bg-background/70 shadow-none">
                        <CardHeader>
                          <div className="flex items-start justify-between gap-2">
                            <div className="space-y-1.5">
                              <CardTitle className="text-xl">{exerciseName}</CardTitle>
                              <CardDescription>
                                Target {exercise.sets} sets × {exercise.reps} reps
                              </CardDescription>
                              {formatAdvancedTechnique(exercise.advancedTechnique) ? (
                                <span className="inline-flex w-fit rounded-full bg-secondary px-2.5 py-1 text-xs font-medium text-secondary-foreground">
                                  {formatAdvancedTechnique(exercise.advancedTechnique)}
                                </span>
                              ) : null}
                            </div>
                            <div className="flex shrink-0 items-center gap-1 rounded-lg border border-border p-1">
                              <Button
                                type="button"
                                size="sm"
                                variant={exerciseUnit === 'kg' ? 'default' : 'ghost'}
                                onClick={() => setExerciseUnit(exercise.exerciseId, 'kg')}
                              >
                                kg
                              </Button>
                              <Button
                                type="button"
                                size="sm"
                                variant={exerciseUnit === 'lb' ? 'default' : 'ghost'}
                                onClick={() => setExerciseUnit(exercise.exerciseId, 'lb')}
                              >
                                lb
                              </Button>
                            </div>
                          </div>
                        </CardHeader>
                        <CardContent className="space-y-4">
                          <SetInput
                            isLoading={isAddSetPending}
                            setNumber={nextSetNumber}
                            unit={exerciseUnit}
                            onLog={({ reps, setNumber, weight }) => {
                              void handleLogSet(exercise.exerciseId, setNumber, weight, reps, exerciseUnit)
                            }}
                          />
                          <RestTimer />

                          {exerciseLogs.length > 0 ? (
                            <div className="space-y-2">
                              <p className="text-sm font-medium">Logged sets</p>
                              <ul className="space-y-2 text-sm text-muted-foreground">
                                {exerciseLogs.map((log) => (
                                  <li key={log.id} className="rounded-lg border border-border px-3 py-2">
                                    Set {log.setNumber}: {formatDisplayWeight(log.weight, exerciseUnit)} × {log.reps}
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
                  onClick={() => setIsForfeitModalOpen(true)}
                >
                  Forfeit session
                </Button>
                <Button type="button" disabled={isCompletePending || isForfeitPending} onClick={() => void handleCompleteSession()}>
                  Push Workout
                </Button>
              </div>
            </div>
          ) : null}
        </CardContent>
      </Card>
    </div>
  )
}
