import axios from 'axios'
import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toKilograms } from '@/features/sessions/sessionHelpers'
import { useSessionTransport } from '@/features/sessions/useSessionTransport'
import { useOnlineStatus } from '@/hooks/useOnlineStatus'
import { queryKeys } from '@/services/queryKeys'
import type { AddSetLogRequest, UpdateSetLogRequest, WorkoutSession } from '@/services/sessionService'
import { sessionService } from '@/services/sessionService'

export type SessionPhase = 'idle' | 'conflict' | 'open' | 'completing'

export type SessionEvent =
  | { type: 'START'; workoutGroupId: string }
  | { type: 'RESUME' }
  | { type: 'DISCARD' }
  | { type: 'COMPLETE' }
  | { type: 'FORFEIT' }
  | { type: 'LOG_SET'; exerciseId: string; setNumber: number; weight: number; reps: number; unit: 'kg' | 'lb' }
  | { type: 'UPDATE_SET'; setLogId: string; weight: number; reps: number; unit: 'kg' | 'lb' }
  | { type: 'DELETE_SET'; setLogId: string }
  | { type: 'DISMISS_STALE_PLAN' }

export function useWorkoutSessionMachine({
  onComplete,
  onForfeit,
}: {
  onComplete: () => void
  onForfeit?: () => void
}) {
  const queryClient = useQueryClient()
  const isOnline = useOnlineStatus()
  const { transport, isAddPending, isUpdatePending } = useSessionTransport()

  const [phase, setPhase] = useState<SessionPhase>('idle')
  const [conflictSession, setConflictSession] = useState<WorkoutSession | null>(null)
  const [pendingGroupId, setPendingGroupId] = useState<string | null>(null)
  const [stalePlanError, setStalePlanError] = useState<string | null>(null)

  const openSessionQuery = useQuery<WorkoutSession | null>({
    queryKey: queryKeys.sessions.open(),
    queryFn: async () => {
      try {
        const session = await sessionService.getOpen()
        if (session) setPhase('open')
        return session
      } catch (error) {
        if (axios.isAxiosError(error) && error.response?.status === 404) {
          setPhase('idle')
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

  const deleteSetLogMutation = useMutation({
    mutationFn: ({ sessionId, setLogId }: { sessionId: string; setLogId: string }) =>
      sessionService.deleteSetLog(sessionId, setLogId),
  })

  const session = openSessionQuery.data ?? null

  // declared as plain function (not useCallback) to avoid instability from transport dep
  async function dispatch(event: SessionEvent) {
      switch (event.type) {
        case 'START': {
          if (!isOnline) return
          setStalePlanError(null)
          try {
            const started = await startMutation.mutateAsync(event.workoutGroupId)
            queryClient.setQueryData(queryKeys.sessions.open(), started)
            setPendingGroupId(null)
            setConflictSession(null)
            setPhase('open')
          } catch (error) {
            if (axios.isAxiosError(error) && error.response?.status === 409) {
              const openSession = await sessionService.getOpen()
              queryClient.setQueryData(queryKeys.sessions.open(), openSession)
              setPendingGroupId(event.workoutGroupId)
              setConflictSession(openSession)
              setPhase('conflict')
              return
            }
            if (axios.isAxiosError(error) && error.response?.status === 400) {
              setStalePlanError('Your active plan changed. Please select a group again.')
              await queryClient.invalidateQueries({ queryKey: queryKeys.sessions.startOptions() })
              return
            }
            throw error
          }
          break
        }

        case 'RESUME': {
          // Keep the existing open session; just clear conflict state.
          setConflictSession(null)
          setPendingGroupId(null)
          setPhase('open')
          break
        }

        case 'DISCARD': {
          if (!conflictSession) return
          const nextGroupId = pendingGroupId
          await discardMutation.mutateAsync(conflictSession.id)
          queryClient.setQueryData(queryKeys.sessions.open(), null)
          setConflictSession(null)
          setPendingGroupId(null)
          setPhase('idle')
          if (nextGroupId) {
            await dispatch({ type: 'START', workoutGroupId: nextGroupId })
          }
          break
        }

        case 'COMPLETE': {
          if (!session) return
          setPhase('completing')
          const completed = await completeMutation.mutateAsync(session.id)
          queryClient.setQueryData(queryKeys.sessions.open(), null)
          queryClient.setQueryData<WorkoutSession[]>(queryKeys.sessions.history(), (current = []) => [
            completed,
            ...current,
          ])
          setPhase('idle')
          onComplete()
          break
        }

        case 'FORFEIT': {
          if (!session) return
          await discardMutation.mutateAsync(session.id)
          queryClient.setQueryData(queryKeys.sessions.open(), null)
          setPhase('idle')
          onForfeit?.()
          break
        }

        case 'LOG_SET': {
          if (!session) return
          const payload: AddSetLogRequest = {
            exerciseId: event.exerciseId,
            setNumber: event.setNumber,
            weight: toKilograms(event.weight, event.unit),
            reps: event.reps,
          }
          const logged = await transport.addSetLog(session.id, payload)
          const newLogs = [...session.setLogs, logged]
          queryClient.setQueryData(queryKeys.sessions.open(), {
            ...session,
            setLogs: newLogs,
            setCount: newLogs.length,
          })
          break
        }

        case 'UPDATE_SET': {
          if (!session) return
          const payload: UpdateSetLogRequest = {
            weight: toKilograms(event.weight, event.unit),
            reps: event.reps,
          }
          const updated = await transport.updateSetLog(session.id, event.setLogId, payload)
          const existing = session.setLogs.find((l) => l.id === event.setLogId)
          const merged = existing ? { ...existing, weight: updated.weight, reps: updated.reps } : updated
          queryClient.setQueryData(queryKeys.sessions.open(), {
            ...session,
            setLogs: session.setLogs.map((l) => (l.id === event.setLogId ? merged : l)),
          })
          break
        }

        case 'DELETE_SET': {
          if (!session) return
          const sessionId = session.id
          await deleteSetLogMutation.mutateAsync({ sessionId, setLogId: event.setLogId })
          const current = queryClient.getQueryData<WorkoutSession>(queryKeys.sessions.open())
          if (!current) return
          const remaining = current.setLogs.filter((l) => l.id !== event.setLogId)
          queryClient.setQueryData(queryKeys.sessions.open(), {
            ...current,
            setLogs: remaining,
            setCount: remaining.length,
          })
          void queryClient.invalidateQueries({ queryKey: queryKeys.sessions.open() })
          void queryClient.invalidateQueries({ queryKey: queryKeys.sessions.referenceWeights(sessionId) })
          break
        }

        case 'DISMISS_STALE_PLAN': {
          setStalePlanError(null)
          break
        }
      }
  }

  return {
    phase,
    session,
    conflictSession,
    stalePlanError,
    dispatch,
    isSessionLoading: openSessionQuery.isLoading,
    isStartPending: startMutation.isPending,
    isAddSetPending: isAddPending,
    isUpdateSetPending: isUpdatePending,
    isDeleteSetPending: deleteSetLogMutation.isPending,
    isCompletePending: completeMutation.isPending,
    isForfeitPending: discardMutation.isPending,
  }
}
