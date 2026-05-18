import axios from 'axios'
import { useAuthStore } from '@/store/auth'
import { tokenService } from '@/services/tokenService'

export const api = axios.create({ baseURL: '/api' })

api.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken

  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }

  return config
})

let isRefreshing = false
let failedQueue: Array<{ resolve: (value: unknown) => void; reject: (error: unknown) => void }> = []

const flushQueue = (error: unknown, token: string | null = null) => {
  failedQueue.forEach(({ resolve, reject }) => {
    if (error) {
      reject(error)
      return
    }

    resolve(token)
  })

  failedQueue = []
}

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const original = error.config as typeof error.config & {
      _retry?: boolean
      headers: Record<string, string>
    }

    if (error.response?.status !== 401 || original._retry) {
      throw error
    }

    if (isRefreshing) {
      return new Promise((resolve, reject) => failedQueue.push({ resolve, reject }))
        .then((token) => {
          original.headers.Authorization = `Bearer ${token}`
          return api(original)
        })
        .catch((queueError) => Promise.reject(queueError))
    }

    original._retry = true
    isRefreshing = true

    try {
      const refreshToken = tokenService.getRefreshToken()

      if (!refreshToken) {
        throw new Error('No refresh token')
      }

      const { data } = await axios.post('/api/auth/refresh', { refreshToken })
      useAuthStore.getState().setAccessToken(data.accessToken)
      tokenService.saveRefreshToken(data.refreshToken)
      flushQueue(null, data.accessToken)
      original.headers.Authorization = `Bearer ${data.accessToken}`
      return api(original)
    } catch (refreshError) {
      flushQueue(refreshError)
      useAuthStore.getState().logout()
      throw refreshError
    } finally {
      isRefreshing = false
    }
  }
)
