import { useEffect } from 'react'
import { authService } from '@/services/authService'
import { tokenService } from '@/services/tokenService'
import { useAuthStore } from '@/store/auth'

export function useRestoreSession() {
  useEffect(() => {
    if (!tokenService.hasRefreshSession()) {
      useAuthStore.getState().setIsRestoring(false)
      return
    }

    authService
      .refresh()
      .then((data) => {
        useAuthStore.getState().setAccessToken(data.accessToken)
        useAuthStore.getState().setCsrfToken(data.csrfToken)
      })
      .catch(() => {
        useAuthStore.getState().logout()
      })
      .finally(() => {
        useAuthStore.getState().setIsRestoring(false)
      })
  }, [])
}
