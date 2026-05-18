import { useMutation } from '@tanstack/react-query'
import { useOnlineStatus } from '@/hooks/useOnlineStatus'
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
  const isOnline = useOnlineStatus()
  const addSetLogMutation = useMutation({
    mutationFn: ({
      sessionId,
      payload,
    }: {
      sessionId: string
      payload: AddSetLogRequest
    }) => sessionService.addSetLog(sessionId, payload),
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
