import { create } from 'zustand'

export interface User {
  id: string
  email: string
  displayName: string
}

interface AuthState {
  accessToken: string | null
  user: User | null
  setAccessToken: (token: string | null) => void
  setUser: (user: User | null) => void
  logout: () => void
}

export const useAuthStore = create<AuthState>((set) => ({
  accessToken: null,
  user: null,
  setAccessToken: (token) => set({ accessToken: token }),
  setUser: (user) => set({ user }),
  logout: () => {
    localStorage.removeItem('refreshToken')
    set({ accessToken: null, user: null })
  },
}))
