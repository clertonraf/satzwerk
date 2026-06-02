import { useEffect } from 'react'
import axios from 'axios'
import { useAuthStore } from '@/store/auth'
import { tokenService } from '@/services/tokenService'

export function useRestoreSession() {
  useEffect(() => {
    const refreshToken = tokenService.getRefreshToken()

    if (!refreshToken) {
      return
    }

    axios
      .post<{ accessToken: string; refreshToken: string }>('/api/auth/refresh', { refreshToken })
      .then(({ data }) => {
        useAuthStore.getState().setAccessToken(data.accessToken)
        tokenService.saveRefreshToken(data.refreshToken)
      })
      .catch(() => {
        useAuthStore.getState().logout()
      })
      .finally(() => {
        useAuthStore.getState().setIsRestoring(false)
      })
  }, [])
}
