import { api } from './api'

export interface WorkoutPlan {
  id: string
  name: string
  source: string
  isActive: boolean
  createdAt: string
  updatedAt: string
}

export interface WorkoutExerciseSummary {
  id: string
  exerciseId: string
  exerciseName: string
  sets: number
  reps: number
  advancedTechnique: string | null
  toFailure: boolean
  orderIndex: number
}

export interface WorkoutGroupDetail {
  id: string
  title: string
  orderIndex: number
  exercises: WorkoutExerciseSummary[]
}

export interface WorkoutPlanDetail extends WorkoutPlan {
  groups: WorkoutGroupDetail[]
}

export interface CreateWorkoutExerciseRequest {
  exerciseId: string
  sets: number
  reps: number
  advancedTechnique?: string
}

export type UpdateWorkoutExerciseRequest = Partial<CreateWorkoutExerciseRequest>

export const planService = {
  list: () => api.get<WorkoutPlan[]>('/plans').then((response) => response.data),
  get: (id: string) => api.get<WorkoutPlanDetail>(`/plans/${id}`).then((response) => response.data),
  create: (name: string) => api.post<WorkoutPlan>('/plans', { name }).then((response) => response.data),
  update: (id: string, name: string) => api.patch<WorkoutPlan>(`/plans/${id}`, { name }).then((response) => response.data),
  delete: (id: string) => api.delete(`/plans/${id}`).then(() => undefined),
  activate: (id: string) => api.post<WorkoutPlan>(`/plans/${id}/activate`).then((response) => response.data),
  importFromFile: (file: File) => {
    const formData = new FormData()
    formData.append('file', file)
    return api
      .post<WorkoutPlan>('/plans/import', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      .then((response) => response.data)
  },
}

export const workoutGroupService = {
  create: (planId: string, title: string) =>
    api.post<WorkoutGroupDetail>(`/plans/${planId}/groups`, { title }).then((response) => response.data),
  update: (planId: string, groupId: string, title: string) =>
    api.patch<WorkoutGroupDetail>(`/plans/${planId}/groups/${groupId}`, { title }).then((response) => response.data),
  delete: (planId: string, groupId: string) => api.delete(`/plans/${planId}/groups/${groupId}`).then(() => undefined),
}

export const workoutExerciseService = {
  create: (planId: string, groupId: string, data: CreateWorkoutExerciseRequest) =>
    api.post<WorkoutExerciseSummary>(`/plans/${planId}/groups/${groupId}/exercises`, data).then((response) => response.data),
  update: (planId: string, groupId: string, exerciseId: string, data: UpdateWorkoutExerciseRequest) =>
    api.patch<WorkoutExerciseSummary>(`/plans/${planId}/groups/${groupId}/exercises/${exerciseId}`, data).then((response) => response.data),
  reorder: (planId: string, groupId: string, exerciseId: string, direction: 'UP' | 'DOWN') =>
    api.patch(`/plans/${planId}/groups/${groupId}/exercises/${exerciseId}/order`, { direction }).then((response) => response.data),
  delete: (planId: string, groupId: string, exerciseId: string) =>
    api.delete(`/plans/${planId}/groups/${groupId}/exercises/${exerciseId}`).then(() => undefined),
}
