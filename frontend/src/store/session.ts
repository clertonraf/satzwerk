import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { WorkoutSession } from '@/services/sessionService'

interface SessionState {
  activeSession: WorkoutSession | null
  weightUnit: 'kg' | 'lb'
  setActiveSession: (session: WorkoutSession | null) => void
  setWeightUnit: (unit: 'kg' | 'lb') => void
}

export const useSessionStore = create<SessionState>()(
  persist(
    (set) => ({
      activeSession: null,
      weightUnit: 'kg',
      setActiveSession: (session) => set({ activeSession: session }),
      setWeightUnit: (unit) => set({ weightUnit: unit }),
    }),
    { name: 'satzwerk-session' }
  )
)
