import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useOnlineStatus } from '@/hooks/useOnlineStatus'
import { queryKeys } from '@/services/queryKeys'
import { offlineQueue } from '@/services/offlineQueue'
import type { AddSetLogRequest, SetLog } from '@/services/sessionService'
import { sessionService } from '@/services/sessionService'

function createQueuedSetLog(sessionId: string, payload: AddSetLogRequest): SetLog {
  const timestamp = Date.now()

  return {
    id: `queued-${sessionId}-${payload.exerciseId}-${payload.setNumber}-${timestamp}`,
    ...payload,
    loggedAt: new Date(timestamp).toISOString(),
  }
}

export function useSessionTransport() {
  const queryClient = useQueryClient()
  const isOnline = useOnlineStatus()
  const addSetLogMutation = useMutation({
    mutationFn: ({
      sessionId,
      payload,
    }: {
      sessionId: string
      payload: AddSetLogRequest
    }) => sessionService.addSetLog(sessionId, payload),
    onSuccess: (_, { sessionId }) => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.sessions.referenceWeights(sessionId) })
    },
  })

  async function logSet(sessionId: string, payload: AddSetLogRequest): Promise<SetLog> {
    if (isOnline) {
      return addSetLogMutation.mutateAsync({ sessionId, payload })
    }

    await offlineQueue.enqueue({ sessionId, ...payload })
    return createQueuedSetLog(sessionId, payload)
  }

  return { logSet, isLogSetPending: addSetLogMutation.isPending }
}
