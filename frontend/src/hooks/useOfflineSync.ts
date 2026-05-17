import { useEffect, useRef } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { offlineQueue } from '@/services/offlineQueue'
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
      void queryClient.invalidateQueries({ queryKey: ['open-session'] })
    })
  }, [isOnline, queryClient])
}
