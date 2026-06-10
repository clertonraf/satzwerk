import { useState } from 'react'
import axios from 'axios'
import { useQueryClient } from '@tanstack/react-query'
import { useOnlineStatus } from '@/hooks/useOnlineStatus'
import { queryKeys } from '@/services/queryKeys'
import type { WorkoutSession } from '@/services/sessionService'
import { sessionService } from '@/services/sessionService'

interface ConflictResolutionProps {
  startMutateAsync: (workoutGroupId: string) => Promise<WorkoutSession>
  discardMutateAsync: (sessionId: string) => Promise<unknown>
}

export function useConflictResolution({ startMutateAsync, discardMutateAsync }: ConflictResolutionProps) {
  const queryClient = useQueryClient()
  const isOnline = useOnlineStatus()
  const [conflictSession, setConflictSession] = useState<WorkoutSession | null>(null)
  const [pendingWorkoutGroupId, setPendingWorkoutGroupId] = useState<string | null>(null)
  const [stalePlanError, setStalePlanError] = useState<string | null>(null)

  async function handleStartSession(workoutGroupId: string) {
    if (!isOnline) {
      return
    }

    setStalePlanError(null)

    try {
      const startedSession = await startMutateAsync(workoutGroupId)
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

      if (axios.isAxiosError(error) && error.response?.status === 400) {
        setStalePlanError('Your active plan changed. Please select a group again.')
        await queryClient.invalidateQueries({ queryKey: queryKeys.sessions.startOptions() })
        return
      }

      throw error
    }
  }

  async function handleDiscardConflict() {
    if (!conflictSession) {
      return
    }

    const nextWorkoutGroupId = pendingWorkoutGroupId

    await discardMutateAsync(conflictSession.id)
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
    setStalePlanError(null)
  }

  return {
    conflictSession,
    stalePlanError,
    handleStartSession,
    handleDiscardConflict,
    clearConflictState,
  }
}
