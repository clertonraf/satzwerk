import axios from 'axios'
import { useEffect, useReducer } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toKilograms } from '@/features/sessions/sessionHelpers'
import { useSessionTransport } from '@/features/sessions/useSessionTransport'
import {
  createInitialWorkoutSessionMachineState,
  workoutSessionMachineReducer,
} from '@/features/sessions/workoutSessionMachineReducer'
import { useOnlineStatus } from '@/hooks/useOnlineStatus'
import { queryKeys } from '@/services/queryKeys'
import type { AddSetLogRequest, PendingSetLog, UpdateSetLogRequest, WorkoutSession } from '@/services/sessionService'
import { sessionService } from '@/services/sessionService'

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
  const { transport, isAddPending, isUpdatePending, isDeletePending } = useSessionTransport()
  const [machineState, machineDispatch] = useReducer(
    workoutSessionMachineReducer,
    undefined,
    createInitialWorkoutSessionMachineState,
  )

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

  const session = openSessionQuery.data ?? null

  useEffect(() => {
    machineDispatch({
      type: 'session-synced',
      sessionId: session?.id ?? null,
      serverSetCount: session?.setCount ?? 0,
    })
  }, [session?.id, session?.setCount])

  const { phase, conflictSession, pendingGroupId, stalePlanError, pendingSetLogs } = machineState

  const startMutation = useMutation({
    mutationFn: (workoutGroupId: string) => sessionService.start(workoutGroupId),
  })

  const completeMutation = useMutation({
    mutationFn: (sessionId: string) => sessionService.complete(sessionId),
  })

  const discardMutation = useMutation({
    mutationFn: (sessionId: string) => sessionService.discard(sessionId),
  })

  // declared as plain function (not useCallback) to avoid instability from transport dep
  async function dispatch(event: SessionEvent) {
    switch (event.type) {
      case 'START': {
        if (!isOnline) return
        machineDispatch({ type: 'start-requested' })
        try {
          const started = await startMutation.mutateAsync(event.workoutGroupId)
          queryClient.setQueryData(queryKeys.sessions.open(), started)
          machineDispatch({
            type: 'start-succeeded',
            sessionId: started.id,
            serverSetCount: started.setCount,
          })
        } catch (error) {
          if (axios.isAxiosError(error) && error.response?.status === 409) {
            const openSession = await sessionService.getOpen()
            queryClient.setQueryData(queryKeys.sessions.open(), openSession)
            machineDispatch({
              type: 'start-conflicted',
              workoutGroupId: event.workoutGroupId,
              conflictSession: openSession,
            })
            return
          }
          if (axios.isAxiosError(error) && error.response?.status === 400) {
            machineDispatch({
              type: 'start-rejected-stale-plan',
              message: 'Your active plan changed. Please select a group again.',
            })
            await queryClient.invalidateQueries({ queryKey: queryKeys.sessions.startOptions() })
            return
          }
          throw error
        }
        break
      }

      case 'RESUME': {
        if (!conflictSession) return
        machineDispatch({ type: 'resume-conflict' })
        break
      }

      case 'DISCARD': {
        if (!conflictSession) return
        const nextGroupId = pendingGroupId
        await discardMutation.mutateAsync(conflictSession.id)
        queryClient.setQueryData(queryKeys.sessions.open(), null)
        machineDispatch({ type: 'conflict-discarded' })
        if (nextGroupId) {
          await dispatch({ type: 'START', workoutGroupId: nextGroupId })
        }
        break
      }

      case 'COMPLETE': {
        if (!session) return
        machineDispatch({ type: 'completion-started' })
        const completed = await completeMutation.mutateAsync(session.id)
        queryClient.setQueryData(queryKeys.sessions.open(), null)
        queryClient.setQueryData<WorkoutSession[]>(queryKeys.sessions.history(), (current = []) => [
          completed,
          ...current,
        ])
        machineDispatch({ type: 'completion-finished' })
        onComplete()
        break
      }

      case 'FORFEIT': {
        if (!session) return
        await discardMutation.mutateAsync(session.id)
        queryClient.setQueryData(queryKeys.sessions.open(), null)
        machineDispatch({ type: 'forfeit-finished' })
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
        const pendingLog: PendingSetLog = {
          id: crypto.randomUUID(),
          exerciseId: event.exerciseId,
          setNumber: event.setNumber,
          weight: toKilograms(event.weight, event.unit),
          reps: event.reps,
          loggedAt: new Date().toISOString(),
          pending: true,
        }
        machineDispatch({ type: 'pending-set-log-recorded', pendingSetLog: pendingLog })
        const logged = await transport.addSetLog(session.id, payload)
        queryClient.setQueryData<WorkoutSession>(queryKeys.sessions.open(), (current) => {
          if (!current) return current
          const newLogs = [...current.setLogs, logged]
          return { ...current, setLogs: newLogs, setCount: newLogs.length }
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
        queryClient.setQueryData<WorkoutSession>(queryKeys.sessions.open(), (current) => {
          if (!current) return current
          return {
            ...current,
            setLogs: current.setLogs.map((l) =>
              l.id === event.setLogId ? { ...l, weight: updated.weight, reps: updated.reps } : l,
            ),
          }
        })
        break
      }

      case 'DELETE_SET': {
        if (!session) return
        const sessionId = session.id
        const current = queryClient.getQueryData<WorkoutSession>(queryKeys.sessions.open())
        if (!current) return
        const remaining = current.setLogs.filter((l) => l.id !== event.setLogId)
        queryClient.setQueryData(queryKeys.sessions.open(), {
          ...current,
          setLogs: remaining,
          setCount: remaining.length,
        })
        try {
          await transport.deleteSetLog(sessionId, event.setLogId)
        } catch (error) {
          queryClient.setQueryData(queryKeys.sessions.open(), current)
          throw error
        }
        if (isOnline) {
          void queryClient.invalidateQueries({ queryKey: queryKeys.sessions.open() })
          void queryClient.invalidateQueries({ queryKey: queryKeys.sessions.referenceWeights(sessionId) })
        }
        break
      }

      case 'DISMISS_STALE_PLAN': {
        machineDispatch({ type: 'stale-plan-dismissed' })
        break
      }
    }
  }

  return {
    phase,
    session,
    pendingSetLogs,
    conflictSession,
    stalePlanError,
    dispatch,
    isSessionLoading: openSessionQuery.isLoading,
    isStartPending: startMutation.isPending,
    isAddSetPending: isAddPending,
    isUpdateSetPending: isUpdatePending,
    isDeleteSetPending: isDeletePending,
    isCompletePending: completeMutation.isPending,
    isForfeitPending: discardMutation.isPending,
  }
}
