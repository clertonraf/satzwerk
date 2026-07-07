import axios from 'axios'
import { useEffect, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toKilograms } from '@/features/sessions/sessionHelpers'
import { useSessionTransport } from '@/features/sessions/useSessionTransport'
import { queryKeys } from '@/services/queryKeys'
import type { AddSetLogRequest, PendingSetLog, UpdateSetLogRequest, WorkoutSession } from '@/services/sessionService'
import { sessionService } from '@/services/sessionService'

export function useSessionLifecycle({ onComplete, onForfeit }: { onComplete: () => void; onForfeit?: () => void }) {
  const queryClient = useQueryClient()
  const { transport, isAddPending, isUpdatePending } = useSessionTransport()
  const [pendingSetLogs, setPendingSetLogs] = useState<PendingSetLog[]>([])

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

  const deleteSetLogMutation = useMutation({
    mutationFn: ({ sessionId, setLogId }: { sessionId: string; setLogId: string }) =>
      sessionService.deleteSetLog(sessionId, setLogId),
  })

  const session = openSessionQuery.data ?? null

  const prevServerSetCountRef = useRef(session?.setCount ?? 0)

  // Reconcile pending logs when the session is refetched from the server after queue flush.
  // Once setCount increases past what we knew before going offline, server has confirmed them.
  useEffect(() => {
    const serverCount = session?.setCount ?? 0
    if (pendingSetLogs.length > 0 && serverCount > prevServerSetCountRef.current) {
      setPendingSetLogs([])
    }
    prevServerSetCountRef.current = serverCount
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [session?.setCount])

  async function handleLogSet(exerciseId: string, setNumber: number, weight: number, reps: number, unit: 'kg' | 'lb') {
    if (!session) {
      return
    }

    const payload: AddSetLogRequest = {
      exerciseId,
      setNumber,
      weight: toKilograms(weight, unit),
      reps,
    }

    const result = await transport.addSetLog(session.id, payload)
    if (result.pending) {
      // Offline: track locally; server does not know yet
      setPendingSetLogs((prev) => [...prev, result])
    } else {
      const newSetLogs = [...session.setLogs, result]
      queryClient.setQueryData(queryKeys.sessions.open(), {
        ...session,
        setLogs: newSetLogs,
        setCount: newSetLogs.length,
      })
    }
  }

  async function handleUpdateSetLog(setLogId: string, weight: number, reps: number, unit: 'kg' | 'lb') {
    if (!session) {
      return
    }

    const payload: UpdateSetLogRequest = {
      weight: toKilograms(weight, unit),
      reps,
    }

    const updatedLog = await transport.updateSetLog(session.id, setLogId, payload)
    const existingLog = session.setLogs.find((log) => log.id === setLogId)
    const mergedLog = existingLog ? { ...existingLog, weight: updatedLog.weight, reps: updatedLog.reps } : updatedLog
    const updatedSession = {
      ...session,
      setLogs: session.setLogs.map((log) => (log.id === setLogId ? mergedLog : log)),
    }
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

  async function handleDeleteSetLog(setLogId: string) {
    if (!session) {
      return
    }

    const sessionId = session.id
    await deleteSetLogMutation.mutateAsync({ sessionId, setLogId })
    const current = queryClient.getQueryData<WorkoutSession>(queryKeys.sessions.open())
    if (!current) return
    const remainingLogs = current.setLogs.filter((log) => log.id !== setLogId)
    queryClient.setQueryData(queryKeys.sessions.open(), {
      ...current,
      setLogs: remainingLogs,
      setCount: remainingLogs.length,
    })
    void queryClient.invalidateQueries({ queryKey: queryKeys.sessions.open() })
    void queryClient.invalidateQueries({ queryKey: queryKeys.sessions.referenceWeights(sessionId) })
  }

  return {
    session,
    pendingSetLogs,
    isSessionLoading: openSessionQuery.isLoading,
    handleLogSet,
    handleUpdateSetLog,
    handleDeleteSetLog,
    handleCompleteSession,
    handleForfeitSession,
    startMutateAsync: startMutation.mutateAsync,
    discardMutateAsync: discardMutation.mutateAsync,
    isStartPending: startMutation.isPending,
    isAddSetPending: isAddPending,
    isUpdateSetPending: isUpdatePending,
    isDeleteSetPending: deleteSetLogMutation.isPending,
    isCompletePending: completeMutation.isPending,
    isForfeitPending: discardMutation.isPending,
  }
}
