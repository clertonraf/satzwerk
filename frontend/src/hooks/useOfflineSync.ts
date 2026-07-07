import { useEffect, useRef, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { offlineQueue } from '@/services/offlineQueue'
import { queryKeys } from '@/services/queryKeys'
import type { WorkoutSession } from '@/services/sessionService'
import { useOnlineStatus } from './useOnlineStatus'

export interface OfflineSyncState {
  flushError: string | null
  dismissFlushError: () => void
}

export function useOfflineSync(): OfflineSyncState {
  const isOnline = useOnlineStatus()
  const queryClient = useQueryClient()
  const wasOffline = useRef(false)
  const [flushError, setFlushError] = useState<string | null>(null)

  useEffect(() => {
    if (!isOnline) {
      wasOffline.current = true
      return
    }

    if (!wasOffline.current) {
      return
    }

    wasOffline.current = false

    void offlineQueue.flush().then(({ failed }) => {
      const openSession = queryClient.getQueryData<WorkoutSession>(queryKeys.sessions.open())
      if (openSession?.id) {
        void queryClient.invalidateQueries({ queryKey: queryKeys.sessions.referenceWeights(openSession.id) })
      }
      void queryClient.invalidateQueries({ queryKey: queryKeys.sessions.open() })

      if (failed > 0) {
        setFlushError(`${failed} set${failed === 1 ? '' : 's'} from your offline session could not be saved.`)
      }
    })
  }, [isOnline, queryClient])

  return {
    flushError,
    dismissFlushError: () => setFlushError(null),
  }
}
