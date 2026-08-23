import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useOnlineStatus } from '@/hooks/useOnlineStatus'
import { queryKeys } from '@/services/queryKeys'
import { offlineQueue } from '@/services/offlineQueue'
import type {
  AddSetLogRequest,
  PendingSetLog,
  SetLog,
  SetLogResult,
  SetLogUpdate,
  UpdateSetLogRequest,
} from '@/services/sessionService'
import { sessionService } from '@/services/sessionService'

export interface SetLogTransport {
  addSetLog(sessionId: string, data: AddSetLogRequest): Promise<SetLogResult>
  updateSetLog(sessionId: string, setLogId: string, data: UpdateSetLogRequest): Promise<SetLogUpdate>
  deleteSetLog(sessionId: string, setLogId: string): Promise<void>
}

interface OnlineSetLogTransportDependencies {
  addSetLog(sessionId: string, data: AddSetLogRequest): Promise<SetLog>
  updateSetLog(sessionId: string, setLogId: string, data: UpdateSetLogRequest): Promise<SetLog>
  deleteSetLog(sessionId: string, setLogId: string): Promise<void>
}

interface QueuedSetLogTransportDependencies {
  enqueue: typeof offlineQueue.enqueue
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

export function createOnlineSetLogTransport({
  addSetLog,
  updateSetLog,
  deleteSetLog,
}: OnlineSetLogTransportDependencies): SetLogTransport {
  return {
    async addSetLog(sessionId, data) {
      const submitted = await addSetLog(sessionId, data)
      return { ...submitted, pending: false }
    },

    async updateSetLog(sessionId, setLogId, data) {
      const updated = await updateSetLog(sessionId, setLogId, data)
      return {
        weight: updated.weight,
        reps: updated.reps,
      }
    },

    deleteSetLog(sessionId, setLogId) {
      return deleteSetLog(sessionId, setLogId)
    },
  }
}

export function createQueuedSetLogTransport({
  enqueue,
}: QueuedSetLogTransportDependencies): SetLogTransport {
  return {
    async addSetLog(sessionId, data) {
      await enqueue({ type: 'add-set', sessionId, data })
      return createQueuedSetLog(sessionId, data)
    },

    async updateSetLog(sessionId, setLogId, data) {
      await enqueue({ type: 'update-set', sessionId, setLogId, data })
      return createQueuedUpdateSetLog(data)
    },

    deleteSetLog(sessionId, setLogId) {
      return enqueue({ type: 'delete-set', sessionId, setLogId }).then(() => undefined)
    },
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

  const deleteSetLogMutation = useMutation({
    mutationFn: ({ sessionId, setLogId }: { sessionId: string; setLogId: string }) =>
      sessionService.deleteSetLog(sessionId, setLogId),
    onSuccess: (_, { sessionId }) => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.sessions.referenceWeights(sessionId) })
    },
  })

  const onlineTransport = createOnlineSetLogTransport({
    addSetLog: async (sessionId, data) => addSetLogMutation.mutateAsync({ sessionId, data }),
    updateSetLog: async (sessionId, setLogId, data) =>
      updateSetLogMutation.mutateAsync({ sessionId, setLogId, data }),
    deleteSetLog: async (sessionId, setLogId) =>
      deleteSetLogMutation.mutateAsync({ sessionId, setLogId }),
  })
  const queuedTransport = createQueuedSetLogTransport({
    enqueue: offlineQueue.enqueue,
  })
  const transport = isOnline ? onlineTransport : queuedTransport

  return {
    transport,
    isAddPending: addSetLogMutation.isPending,
    isUpdatePending: updateSetLogMutation.isPending,
    isDeletePending: deleteSetLogMutation.isPending,
  }
}
