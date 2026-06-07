import axios from 'axios'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toKilograms } from '@/features/sessions/sessionHelpers'
import { useSessionTransport } from '@/features/sessions/useSessionTransport'
import { queryKeys } from '@/services/queryKeys'
import type { AddSetLogRequest, UpdateSetLogRequest, WorkoutSession } from '@/services/sessionService'
import { sessionService } from '@/services/sessionService'

export function useSessionLifecycle({ onComplete, onForfeit }: { onComplete: () => void; onForfeit?: () => void }) {
  const queryClient = useQueryClient()
  const transport = useSessionTransport()

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

  const updateSetLogMutation = useMutation({
    mutationFn: ({ sessionId, setLogId, payload }: { sessionId: string; setLogId: string; payload: UpdateSetLogRequest }) =>
      sessionService.updateSetLog(sessionId, setLogId, payload),
    onSuccess: (_, { sessionId }) => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.sessions.referenceWeights(sessionId) })
    },
  })

  const session = openSessionQuery.data ?? null

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

    const loggedSet = await transport.logSet(session.id, payload)
    const updatedSession = { ...session, setLogs: [...session.setLogs, loggedSet] }
    queryClient.setQueryData(queryKeys.sessions.open(), updatedSession)
  }

  async function handleUpdateSetLog(setLogId: string, weight: number, reps: number, unit: 'kg' | 'lb') {
    if (!session) {
      return
    }

    const payload: UpdateSetLogRequest = {
      weight: toKilograms(weight, unit),
      reps,
    }

    const updatedLog = await updateSetLogMutation.mutateAsync({ sessionId: session.id, setLogId, payload })
    const updatedSession = {
      ...session,
      setLogs: session.setLogs.map((log) => (log.id === setLogId ? updatedLog : log)),
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

  return {
    session,
    isSessionLoading: openSessionQuery.isLoading,
    handleLogSet,
    handleUpdateSetLog,
    handleCompleteSession,
    handleForfeitSession,
    startMutateAsync: startMutation.mutateAsync,
    discardMutateAsync: discardMutation.mutateAsync,
    isStartPending: startMutation.isPending,
    isAddSetPending: transport.isLogSetPending,
    isUpdateSetPending: updateSetLogMutation.isPending,
    isCompletePending: completeMutation.isPending,
    isForfeitPending: discardMutation.isPending,
  }
}
