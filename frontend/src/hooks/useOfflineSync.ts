import { useMemo, useEffect, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { createFlushScheduler } from '@/services/flushScheduler'
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
  const [flushError, setFlushError] = useState<string | null>(null)

  const scheduler = useMemo(() => createFlushScheduler(offlineQueue.flush), [])

  useEffect(() => {
    void scheduler
      .onConnectivityChange(isOnline)
      .then((result) => {
        if (result === null) return
        const openSession = queryClient.getQueryData<WorkoutSession>(queryKeys.sessions.open())
        if (openSession?.id) {
          void queryClient.invalidateQueries({ queryKey: queryKeys.sessions.referenceWeights(openSession.id) })
        }
        void queryClient.invalidateQueries({ queryKey: queryKeys.sessions.open() })

        if (result.failed > 0) {
          setFlushError(
            `${result.failed} set${result.failed === 1 ? '' : 's'} from your offline session could not be saved.`,
          )
        } else {
          setFlushError(null)
        }
      })
      .catch(() => {
        setFlushError('Offline sync failed unexpectedly. Please reload the page.')
      })
  }, [isOnline, queryClient, scheduler])

  return {
    flushError,
    dismissFlushError: () => setFlushError(null),
  }
}
