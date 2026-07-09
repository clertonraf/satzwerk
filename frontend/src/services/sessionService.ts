import { http } from './api'
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
  exerciseCount: number
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
export type SetLogUpdate = Pick<SetLog, 'weight' | 'reps'>

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
  start: (workoutGroupId: string) => http.post<WorkoutSession>('/sessions', { workoutGroupId }),
  getOpen: () => http.get<WorkoutSession>('/sessions/open'),
  getStartOptions: () => http.get<WorkoutPlanDetail>('/sessions/start-options'),
  getOpenPlanDetail: () => http.get<WorkoutPlanDetail>('/sessions/open/plan-detail'),
  addSetLog: (sessionId: string, data: AddSetLogRequest) =>
    http.post<SetLog>(`/sessions/${sessionId}/set-logs`, data),
  updateSetLog: (sessionId: string, setLogId: string, data: UpdateSetLogRequest) =>
    http.patch<SetLog>(`/sessions/${sessionId}/set-logs/${setLogId}`, data),
  complete: (sessionId: string, notes?: string) =>
    http.post<WorkoutSession>(`/sessions/${sessionId}/complete`, { notes }),
  discard: (sessionId: string) => http.delete(`/sessions/${sessionId}`),
  deleteSetLog: (sessionId: string, setLogId: string) =>
    http.delete(`/sessions/${sessionId}/set-logs/${setLogId}`),
  history: () => http.get<WorkoutSession[]>('/sessions/history'),
  getById: (id: string) => http.get<WorkoutSession>(`/sessions/${id}`),
  getReferenceWeights: (sessionId: string) =>
    http.get<ExerciseReferenceWeights[]>(`/sessions/${sessionId}/reference-weights`),
}
