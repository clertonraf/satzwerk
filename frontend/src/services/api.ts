import axios from 'axios'
import { useAuthStore } from '@/store/auth'
import { tokenService } from '@/services/tokenService'

export const api = axios.create({ baseURL: '/api' })

api.interceptors.request.use((config) => {
  const token = tokenService.getAccessToken()

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

import type { AxiosRequestConfig } from 'axios'

/** Typed HTTP facade that unwraps `.data` automatically. Use instead of `api.*` in service modules. */
export const http = {
  get: <T>(url: string, config?: AxiosRequestConfig) => api.get<T>(url, config).then((r) => r.data),
  post: <T>(url: string, data?: unknown, config?: AxiosRequestConfig) =>
    api.post<T>(url, data, config).then((r) => r.data),
  put: <T>(url: string, data?: unknown, config?: AxiosRequestConfig) =>
    api.put<T>(url, data, config).then((r) => r.data),
  patch: <T>(url: string, data?: unknown, config?: AxiosRequestConfig) =>
    api.patch<T>(url, data, config).then((r) => r.data),
  delete: <T = void>(url: string, config?: AxiosRequestConfig) =>
    api.delete<T>(url, config).then((r) => r.data),
}
