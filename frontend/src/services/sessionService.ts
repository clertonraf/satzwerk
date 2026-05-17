import { api } from './api'

export interface WorkoutSession {
  id: string
  workoutGroupId: string
  startedAt: string
  completedAt: string | null
  notes: string | null
  setLogs: SetLog[]
}

export interface SetLog {
  id: string
  exerciseId: string
  setNumber: number
  weight: number
  reps: number
  loggedAt: string
}

export interface AddSetLogRequest {
  exerciseId: string
  setNumber: number
  weight: number
  reps: number
}

export const sessionService = {
  start: (workoutGroupId: string) => api.post<WorkoutSession>('/sessions', { workoutGroupId }).then((response) => response.data),
  getOpen: () => api.get<WorkoutSession>('/sessions/open').then((response) => response.data),
  addSetLog: (sessionId: string, data: AddSetLogRequest) =>
    api.post<SetLog>(`/sessions/${sessionId}/set-logs`, data).then((response) => response.data),
  complete: (sessionId: string, notes?: string) =>
    api.post<WorkoutSession>(`/sessions/${sessionId}/complete`, { notes }).then((response) => response.data),
  discard: (sessionId: string) => api.delete(`/sessions/${sessionId}`).then(() => undefined),
  history: () => api.get<WorkoutSession[]>('/sessions/history').then((response) => response.data),
}
