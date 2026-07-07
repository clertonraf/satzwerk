import { api } from './api'
import type { WorkoutPlanDetail } from './planService'

export interface WorkoutSession {
  id: string
  workoutGroupId: string
  workoutGroupTitle: string
  startedAt: string
  completedAt: string | null
  notes: string | null
  setLogs: SetLog[]
  setCount: number
}

export interface SetLog {
  id: string
  exerciseId: string
  setNumber: number
  weight: number
  reps: number
  loggedAt: string
}

export type SubmittedSetLog = SetLog & { pending: false }
export type PendingSetLog = Omit<SetLog, 'id'> & { id: string; pending: true }
export type SetLogResult = SubmittedSetLog | PendingSetLog

export interface AddSetLogRequest {
  exerciseId: string
  setNumber: number
  weight: number
  reps: number
}

export interface UpdateSetLogRequest {
  weight: number
  reps: number
}

export interface ExerciseReferenceWeights {
  exerciseId: string
  previousWeightKg: number | null
  prWeightKg: number | null
  estimatedOneRepMaxKg: number | null
  suggestedWeightKg: number | null
}

export const sessionService = {
  start: (workoutGroupId: string) => api.post<WorkoutSession>('/sessions', { workoutGroupId }).then((response) => response.data),
  getOpen: () => api.get<WorkoutSession>('/sessions/open').then((response) => response.data),
  getStartOptions: () => api.get<WorkoutPlanDetail>('/sessions/start-options').then((response) => response.data),
  getOpenPlanDetail: () => api.get<WorkoutPlanDetail>('/sessions/open/plan-detail').then((response) => response.data),
  addSetLog: (sessionId: string, data: AddSetLogRequest) =>
    api.post<SetLog>(`/sessions/${sessionId}/set-logs`, data).then((response) => response.data),
  updateSetLog: (sessionId: string, setLogId: string, data: UpdateSetLogRequest) =>
    api.patch<SetLog>(`/sessions/${sessionId}/set-logs/${setLogId}`, data).then((response) => response.data),
  complete: (sessionId: string, notes?: string) =>
    api.post<WorkoutSession>(`/sessions/${sessionId}/complete`, { notes }).then((response) => response.data),
  discard: (sessionId: string) => api.delete(`/sessions/${sessionId}`).then(() => undefined),
  deleteSetLog: (sessionId: string, setLogId: string) =>
    api.delete(`/sessions/${sessionId}/set-logs/${setLogId}`).then(() => undefined),
  history: () => api.get<WorkoutSession[]>('/sessions/history').then((response) => response.data),
  getById: (id: string) => api.get<WorkoutSession>(`/sessions/${id}`).then((response) => response.data),
  getReferenceWeights: (sessionId: string) =>
    api.get<ExerciseReferenceWeights[]>(`/sessions/${sessionId}/reference-weights`).then((response) => response.data),
}
