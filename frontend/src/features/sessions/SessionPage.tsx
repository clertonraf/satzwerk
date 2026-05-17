import { useEffect, useMemo, useState } from 'react'
import axios from 'axios'
import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import RestTimer from '@/features/sessions/RestTimer'
import ResumeDiscardModal from '@/features/sessions/ResumeDiscardModal'
import SetInput from '@/features/sessions/SetInput'
import {
  buildWorkoutGroupCatalog,
  formatDisplayWeight,
  formatSessionDate,
  toKilograms,
} from '@/features/sessions/sessionHelpers'
import { useOnlineStatus } from '@/hooks/useOnlineStatus'
import { exerciseService } from '@/services/exerciseService'
import { planService } from '@/services/planService'
import { offlineQueue } from '@/services/offlineQueue'
import type { AddSetLogRequest, SetLog, WorkoutSession } from '@/services/sessionService'
import { sessionService } from '@/services/sessionService'
import { useSessionStore } from '@/store/session'

function createQueuedSetLog(sessionId: string, payload: AddSetLogRequest): SetLog {
  const timestamp = Date.now()

  return {
    id: `queued-${sessionId}-${payload.exerciseId}-${payload.setNumber}-${timestamp}`,
    ...payload,
    loggedAt: new Date(timestamp).toISOString(),
  }
}

export default function SessionPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const isOnline = useOnlineStatus()
  const activeSession = useSessionStore((state) => state.activeSession)
  const setActiveSession = useSessionStore((state) => state.setActiveSession)
  const weightUnit = useSessionStore((state) => state.weightUnit)
  const setWeightUnit = useSessionStore((state) => state.setWeightUnit)
  const [conflictSession, setConflictSession] = useState<WorkoutSession | null>(null)
  const [pendingWorkoutGroupId, setPendingWorkoutGroupId] = useState<string | null>(null)
  const plansQuery = useQuery({
    queryKey: ['plans'],
    queryFn: () => planService.list(),
  })
  const exercisesQuery = useQuery({
    queryKey: ['exercises'],
    queryFn: () => exerciseService.list(),
  })
  const openSessionQuery = useQuery<WorkoutSession | null>({
    queryKey: ['open-session'],
    queryFn: async () => {
      try {
        return await sessionService.getOpen()
      } catch (error) {
        if (axios.isAxiosError(error) && error.response?.status === 404) {
          return null
        }

        throw error
      }
    },
  })
  const planDetailsQueries = useQueries({
    queries: (plansQuery.data ?? []).map((plan) => ({
      queryKey: ['plans', plan.id],
      queryFn: () => planService.get(plan.id),
    })),
  })
  const startMutation = useMutation({
    mutationFn: (workoutGroupId: string) => sessionService.start(workoutGroupId),
  })
  const addSetLogMutation = useMutation({
    mutationFn: ({ sessionId, exerciseId, setNumber, weight, reps }: {
      sessionId: string
      exerciseId: string
      setNumber: number
      weight: number
      reps: number
    }) =>
      sessionService.addSetLog(sessionId, {
        exerciseId,
        setNumber,
        weight,
        reps,
      }),
  })
  const completeMutation = useMutation({
    mutationFn: (sessionId: string) => sessionService.complete(sessionId),
  })
  const discardMutation = useMutation({
    mutationFn: (sessionId: string) => sessionService.discard(sessionId),
  })

  const session = activeSession ?? openSessionQuery.data ?? null
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
      openSessionQuery.error ??
      planDetailsQueries.find((query) => query.error)?.error
  const isCatalogLoading =
    plansQuery.isLoading || exercisesQuery.isLoading || planDetailsQueries.some((query) => query.isLoading)

  useEffect(() => {
    if (openSessionQuery.data !== undefined) {
      setActiveSession(openSessionQuery.data)
    }
  }, [openSessionQuery.data, setActiveSession])

  async function handleStartSession(workoutGroupId: string) {
    if (!isOnline) {
      return
    }

    try {
      const startedSession = await startMutation.mutateAsync(workoutGroupId)
      setActiveSession(startedSession)
      queryClient.setQueryData(['open-session'], startedSession)
      setPendingWorkoutGroupId(null)
      setConflictSession(null)
    } catch (error) {
      if (axios.isAxiosError(error) && error.response?.status === 409) {
        const openSession = await sessionService.getOpen()
        setActiveSession(openSession)
        queryClient.setQueryData(['open-session'], openSession)
        setPendingWorkoutGroupId(workoutGroupId)
        setConflictSession(openSession)
        return
      }

      throw error
    }
  }

  async function handleLogSet(exerciseId: string, setNumber: number, weight: number, reps: number) {
    if (!session) {
      return
    }

    const payload: AddSetLogRequest = {
      exerciseId,
      setNumber,
      weight: toKilograms(weight, weightUnit),
      reps,
    }

    let loggedSet: SetLog

    if (isOnline) {
      loggedSet = await addSetLogMutation.mutateAsync({
        sessionId: session.id,
        ...payload,
      })
    } else {
      await offlineQueue.enqueue({ sessionId: session.id, ...payload })
      loggedSet = createQueuedSetLog(session.id, payload)
    }

    const updatedSession = {
      ...session,
      setLogs: [...session.setLogs, loggedSet],
    }

    setActiveSession(updatedSession)
    queryClient.setQueryData(['open-session'], updatedSession)
  }

  async function handleCompleteSession() {
    if (!session) {
      return
    }

    const completedSession = await completeMutation.mutateAsync(session.id)
    setActiveSession(null)
    queryClient.setQueryData(['open-session'], null)
    queryClient.setQueryData<WorkoutSession[]>(['session-history'], (current = []) => [completedSession, ...current])
    navigate('/history')
  }

  async function handleDiscardConflict() {
    if (!conflictSession) {
      return
    }

    const nextWorkoutGroupId = pendingWorkoutGroupId

    await discardMutation.mutateAsync(conflictSession.id)
    setActiveSession(null)
    queryClient.setQueryData(['open-session'], null)
    setConflictSession(null)
    setPendingWorkoutGroupId(null)

    if (nextWorkoutGroupId) {
      await handleStartSession(nextWorkoutGroupId)
    }
  }

  function clearConflictState() {
    setPendingWorkoutGroupId(null)
    setConflictSession(null)
  }

  if (queryError) {
    return <p className="text-sm text-destructive">Could not load workout session data.</p>
  }

  if (!session && openSessionQuery.isLoading) {
    return <p className="text-sm text-muted-foreground">Loading workout session...</p>
  }

  return (
    <div className="space-y-6">
      {conflictSession ? (
        <ResumeDiscardModal
          onResume={() => {
            setActiveSession(conflictSession)
            queryClient.setQueryData(['open-session'], conflictSession)
            clearConflictState()
          }}
          onDiscard={() => {
            void handleDiscardConflict()
          }}
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

          <div className="flex items-center gap-2 self-start rounded-lg border border-border p-1">
            <Button type="button" size="sm" variant={weightUnit === 'kg' ? 'default' : 'ghost'} onClick={() => setWeightUnit('kg')}>
              kg
            </Button>
            <Button type="button" size="sm" variant={weightUnit === 'lb' ? 'default' : 'ghost'} onClick={() => setWeightUnit('lb')}>
              lb
            </Button>
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
                  <button
                    key={group.id}
                    className="flex w-full items-center justify-between rounded-lg border border-border px-4 py-4 text-left transition hover:border-primary hover:bg-accent disabled:cursor-not-allowed disabled:opacity-60"
                    type="button"
                    disabled={!isOnline || startMutation.isPending}
                    onClick={() => {
                      void handleStartSession(group.id)
                    }}
                  >
                    <span>
                      <span className="block font-medium">{group.title}</span>
                      <span className="block text-sm text-muted-foreground">
                        {plan.name} · {group.exercises.length} exercises
                      </span>
                    </span>
                    <span className="text-sm font-medium text-primary">Start</span>
                  </button>
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

                    return (
                      <Card key={exercise.id} className="border-border bg-background/70 shadow-none">
                        <CardHeader>
                          <CardTitle className="text-xl">{exerciseName}</CardTitle>
                          <CardDescription>
                            Target {exercise.sets} sets × {exercise.reps} reps
                          </CardDescription>
                        </CardHeader>
                        <CardContent className="space-y-4">
                          <SetInput
                            isLoading={addSetLogMutation.isPending}
                            setNumber={nextSetNumber}
                            onLog={({ reps, setNumber, weight }) => {
                              void handleLogSet(exercise.exerciseId, setNumber, weight, reps)
                            }}
                          />
                          <RestTimer />

                          {exerciseLogs.length > 0 ? (
                            <div className="space-y-2">
                              <p className="text-sm font-medium">Logged sets</p>
                              <ul className="space-y-2 text-sm text-muted-foreground">
                                {exerciseLogs.map((log) => (
                                  <li key={log.id} className="rounded-lg border border-border px-3 py-2">
                                    Set {log.setNumber}: {formatDisplayWeight(log.weight, weightUnit)} × {log.reps}
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

              <div className="flex justify-end">
                <Button type="button" disabled={completeMutation.isPending} onClick={() => void handleCompleteSession()}>
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
