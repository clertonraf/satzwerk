import { useEffect, useState } from 'react'
import axios from 'axios'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toKilograms } from '@/features/sessions/sessionHelpers'
import { useOnlineStatus } from '@/hooks/useOnlineStatus'
import { offlineQueue } from '@/services/offlineQueue'
import { queryKeys } from '@/services/queryKeys'
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

export function useWorkoutSession({ onComplete }: { onComplete: () => void }) {
  const queryClient = useQueryClient()
  const isOnline = useOnlineStatus()
  const activeSession = useSessionStore((state) => state.activeSession)
  const setActiveSession = useSessionStore((state) => state.setActiveSession)
  const weightUnit = useSessionStore((state) => state.weightUnit)
  const setWeightUnit = useSessionStore((state) => state.setWeightUnit)
  const [conflictSession, setConflictSession] = useState<WorkoutSession | null>(null)
  const [pendingWorkoutGroupId, setPendingWorkoutGroupId] = useState<string | null>(null)
  const openSessionQuery = useQuery<WorkoutSession | null>({
    queryKey: queryKeys.sessions.open(),
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
  const startMutation = useMutation({
    mutationFn: (workoutGroupId: string) => sessionService.start(workoutGroupId),
  })
  const addSetLogMutation = useMutation({
    mutationFn: ({
      sessionId,
      exerciseId,
      setNumber,
      weight,
      reps,
    }: {
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
      queryClient.setQueryData(queryKeys.sessions.open(), startedSession)
      setPendingWorkoutGroupId(null)
      setConflictSession(null)
    } catch (error) {
      if (axios.isAxiosError(error) && error.response?.status === 409) {
        const openSession = await sessionService.getOpen()
        setActiveSession(openSession)
        queryClient.setQueryData(queryKeys.sessions.open(), openSession)
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
    queryClient.setQueryData(queryKeys.sessions.open(), updatedSession)
  }

  async function handleCompleteSession() {
    if (!session) {
      return
    }

    const completedSession = await completeMutation.mutateAsync(session.id)
    setActiveSession(null)
    queryClient.setQueryData(queryKeys.sessions.open(), null)
    queryClient.setQueryData<WorkoutSession[]>(queryKeys.sessions.history(), (current = []) => [completedSession, ...current])
    onComplete()
  }

  async function handleDiscardConflict() {
    if (!conflictSession) {
      return
    }

    const nextWorkoutGroupId = pendingWorkoutGroupId

    await discardMutation.mutateAsync(conflictSession.id)
    setActiveSession(null)
    queryClient.setQueryData(queryKeys.sessions.open(), null)
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

  return {
    session,
    weightUnit,
    setWeightUnit,
    conflictSession,
    isSessionLoading: openSessionQuery.isLoading,
    handleStartSession,
    handleLogSet,
    handleCompleteSession,
    handleDiscardConflict,
    clearConflictState,
    isStartPending: startMutation.isPending,
    isAddSetPending: addSetLogMutation.isPending,
    isCompletePending: completeMutation.isPending,
  }
}
