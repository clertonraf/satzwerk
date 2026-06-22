import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useOnlineStatus } from '@/hooks/useOnlineStatus'
import { queryKeys } from '@/services/queryKeys'
import { offlineQueue } from '@/services/offlineQueue'
import type { AddSetLogRequest, SetLog, UpdateSetLogRequest } from '@/services/sessionService'
import { sessionService } from '@/services/sessionService'

export interface SetLogTransport {
  addSetLog(sessionId: string, data: AddSetLogRequest): Promise<SetLog>
  updateSetLog(sessionId: string, setLogId: string, data: UpdateSetLogRequest): Promise<SetLog>
}

function createQueuedSetLog(sessionId: string, payload: AddSetLogRequest): SetLog {
  const timestamp = Date.now()

  return {
    id: `queued-${sessionId}-${payload.exerciseId}-${payload.setNumber}-${timestamp}`,
    ...payload,
    loggedAt: new Date(timestamp).toISOString(),
  }
}

function createQueuedUpdateSetLog(setLogId: string, data: UpdateSetLogRequest): SetLog {
  return {
    id: setLogId,
    exerciseId: '',
    setNumber: 0,
    weight: data.weight,
    reps: data.reps,
    loggedAt: new Date().toISOString(),
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
        return addSetLogMutation.mutateAsync({ sessionId, data })
      }
      await offlineQueue.enqueue({ type: 'add-set', sessionId, data })
      return createQueuedSetLog(sessionId, data)
    },

    async updateSetLog(sessionId, setLogId, data) {
      if (isOnline) {
        return updateSetLogMutation.mutateAsync({ sessionId, setLogId, data })
      }
      await offlineQueue.enqueue({ type: 'update-set', sessionId, setLogId, data })
      return createQueuedUpdateSetLog(setLogId, data)
    },
  }

  return {
    transport,
    isAddPending: addSetLogMutation.isPending,
    isUpdatePending: updateSetLogMutation.isPending,
  }
}

