import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useOnlineStatus } from '@/hooks/useOnlineStatus'
import { queryKeys } from '@/services/queryKeys'
import { offlineQueue } from '@/services/offlineQueue'
import type { AddSetLogRequest, PendingSetLog, SetLogResult, SetLogUpdate, UpdateSetLogRequest } from '@/services/sessionService'
import { sessionService } from '@/services/sessionService'

export interface SetLogTransport {
  addSetLog(sessionId: string, data: AddSetLogRequest): Promise<SetLogResult>
  updateSetLog(sessionId: string, setLogId: string, data: UpdateSetLogRequest): Promise<SetLogUpdate>
}

function createQueuedSetLog(sessionId: string, payload: AddSetLogRequest): PendingSetLog {
  const timestamp = Date.now()

  return {
    id: `queued-${sessionId}-${payload.exerciseId}-${payload.setNumber}-${timestamp}`,
    ...payload,
    loggedAt: new Date(timestamp).toISOString(),
    pending: true,
  }
}

function createQueuedUpdateSetLog(data: UpdateSetLogRequest): SetLogUpdate {
  return {
    weight: data.weight,
    reps: data.reps,
  }
}

export function useSessionTransport() {
  const queryClient = useQueryClient()
  const isOnline = useOnlineStatus()

  const addSetLogMutation = useMutation({
    mutationFn: ({ sessionId, data }: { sessionId: string; data: AddSetLogRequest }) =>
      sessionService.addSetLog(sessionId, data),
    onSuccess: (_, { sessionId }) => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.sessions.referenceWeights(sessionId) })
    },
  })

  const updateSetLogMutation = useMutation({
    mutationFn: ({ sessionId, setLogId, data }: { sessionId: string; setLogId: string; data: UpdateSetLogRequest }) =>
      sessionService.updateSetLog(sessionId, setLogId, data),
    onSuccess: (_, { sessionId }) => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.sessions.referenceWeights(sessionId) })
    },
  })

  const transport: SetLogTransport = {
    async addSetLog(sessionId, data) {
      if (isOnline) {
        const submitted = await addSetLogMutation.mutateAsync({ sessionId, data })
        return { ...submitted, pending: false }
      }
      await offlineQueue.enqueue({ type: 'add-set', sessionId, data })
      return createQueuedSetLog(sessionId, data)
    },

    async updateSetLog(sessionId, setLogId, data) {
      if (isOnline) {
        return updateSetLogMutation.mutateAsync({ sessionId, setLogId, data })
      }
      await offlineQueue.enqueue({ type: 'update-set', sessionId, setLogId, data })
      return createQueuedUpdateSetLog(data)
    },
  }

  return {
    transport,
    isAddPending: addSetLogMutation.isPending,
    isUpdatePending: updateSetLogMutation.isPending,
  }
}

