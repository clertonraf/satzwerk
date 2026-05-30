import { useState } from 'react'
import axios from 'axios'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toKilograms } from '@/features/sessions/sessionHelpers'
import { useOnlineStatus } from '@/hooks/useOnlineStatus'
import { useSessionTransport } from '@/features/sessions/useSessionTransport'
import { queryKeys } from '@/services/queryKeys'
import type { AddSetLogRequest, WorkoutSession } from '@/services/sessionService'
import { sessionService } from '@/services/sessionService'
import { useSessionStore } from '@/store/session'

export function useWorkoutSession({ onComplete, onForfeit }: { onComplete: () => void; onForfeit?: () => void }) {
  const queryClient = useQueryClient()
  const isOnline = useOnlineStatus()
  const transport = useSessionTransport()
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
  const completeMutation = useMutation({
    mutationFn: (sessionId: string) => sessionService.complete(sessionId),
  })
  const discardMutation = useMutation({
    mutationFn: (sessionId: string) => sessionService.discard(sessionId),
  })

  const session = openSessionQuery.data ?? null

  async function handleStartSession(workoutGroupId: string) {
    if (!isOnline) {
      return
    }

    try {
      const startedSession = await startMutation.mutateAsync(workoutGroupId)
      queryClient.setQueryData(queryKeys.sessions.open(), startedSession)
      setPendingWorkoutGroupId(null)
      setConflictSession(null)
    } catch (error) {
      if (axios.isAxiosError(error) && error.response?.status === 409) {
        const openSession = await sessionService.getOpen()
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

    const loggedSet = await transport.logSet(session.id, payload)
    const updatedSession = { ...session, setLogs: [...session.setLogs, loggedSet] }
    queryClient.setQueryData(queryKeys.sessions.open(), updatedSession)
  }

  async function handleCompleteSession() {
    if (!session) {
      return
    }

    const completedSession = await completeMutation.mutateAsync(session.id)
    queryClient.setQueryData(queryKeys.sessions.open(), null)
    queryClient.setQueryData<WorkoutSession[]>(queryKeys.sessions.history(), (current = []) => [completedSession, ...current])
    onComplete()
  }

  async function handleForfeitSession() {
    if (!session) {
      return
    }

    await discardMutation.mutateAsync(session.id)
    queryClient.setQueryData(queryKeys.sessions.open(), null)
    onForfeit?.()
  }

  async function handleDiscardConflict() {
    if (!conflictSession) {
      return
    }

    const nextWorkoutGroupId = pendingWorkoutGroupId

    await discardMutation.mutateAsync(conflictSession.id)
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
    handleForfeitSession,
    handleDiscardConflict,
    clearConflictState,
    isStartPending: startMutation.isPending,
    isAddSetPending: transport.isLogSetPending,
    isCompletePending: completeMutation.isPending,
    isForfeitPending: discardMutation.isPending,
  }
}

