import { create } from 'zustand'
import { tokenService } from '@/services/tokenService'

export interface User {
  id: string
  email: string
  displayName: string
}

interface AuthState {
  accessToken: string | null
  user: User | null
  isRestoring: boolean
  setAccessToken: (token: string | null) => void
  setUser: (user: User | null) => void
  setIsRestoring: (isRestoring: boolean) => void
  logout: () => void
}

export const useAuthStore = create<AuthState>((set) => ({
  accessToken: null,
  user: null,
  isRestoring: tokenService.getRefreshToken() !== null,
  setAccessToken: (token) => {
    tokenService.saveAccessToken(token)
    set({ accessToken: token })
  },
  setUser: (user) => set({ user }),
  setIsRestoring: (isRestoring) => set({ isRestoring }),
  logout: () => {
    tokenService.clearTokens()
    set({ accessToken: null, user: null, isRestoring: false })
  },
}))
