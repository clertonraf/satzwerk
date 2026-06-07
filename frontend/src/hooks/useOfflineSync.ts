import { useEffect, useRef } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { offlineQueue } from '@/services/offlineQueue'
import { queryKeys } from '@/services/queryKeys'
import type { WorkoutSession } from '@/services/sessionService'
import { useOnlineStatus } from './useOnlineStatus'

export function useOfflineSync() {
  const isOnline = useOnlineStatus()
  const queryClient = useQueryClient()
  const wasOffline = useRef(false)

  useEffect(() => {
    if (!isOnline) {
      wasOffline.current = true
      return
    }

    if (!wasOffline.current) {
      return
    }

    wasOffline.current = false

    void offlineQueue.flush().then(() => {
      const openSession = queryClient.getQueryData<WorkoutSession>(queryKeys.sessions.open())
      if (openSession?.id) {
        void queryClient.invalidateQueries({ queryKey: queryKeys.sessions.referenceWeights(openSession.id) })
      }
      void queryClient.invalidateQueries({ queryKey: queryKeys.sessions.open() })
    })
  }, [isOnline, queryClient])
}
