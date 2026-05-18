import { create } from 'zustand'
import { persist } from 'zustand/middleware'

interface SessionState {
  weightUnit: 'kg' | 'lb'
  setWeightUnit: (unit: 'kg' | 'lb') => void
}

export const useSessionStore = create<SessionState>()(
  persist(
    (set) => ({
      weightUnit: 'kg',
      setWeightUnit: (unit) => set({ weightUnit: unit }),
    }),
    { name: 'satzwerk-session' }
  )
)
