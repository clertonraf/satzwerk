import { http } from './api'

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
  list: () => http.get<WorkoutPlan[]>('/plans'),
  get: (id: string) => http.get<WorkoutPlanDetail>(`/plans/${id}`),
  create: (name: string) => http.post<WorkoutPlan>('/plans', { name }),
  update: (id: string, name: string) => http.patch<WorkoutPlan>(`/plans/${id}`, { name }),
  delete: (id: string) => http.delete(`/plans/${id}`),
  activate: (id: string) => http.post<WorkoutPlan>(`/plans/${id}/activate`),
  importFromFile: (file: File) => {
    const formData = new FormData()
    formData.append('file', file)
    return http.post<WorkoutPlan>('/plans/import', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },
}

export const workoutGroupService = {
  create: (planId: string, title: string) =>
    http.post<WorkoutGroupDetail>(`/plans/${planId}/groups`, { title }),
  update: (planId: string, groupId: string, title: string) =>
    http.patch<WorkoutGroupDetail>(`/plans/${planId}/groups/${groupId}`, { title }),
  delete: (planId: string, groupId: string) => http.delete(`/plans/${planId}/groups/${groupId}`),
}

export const workoutExerciseService = {
  create: (planId: string, groupId: string, data: CreateWorkoutExerciseRequest) =>
    http.post<WorkoutExerciseSummary>(`/plans/${planId}/groups/${groupId}/exercises`, data),
  update: (planId: string, groupId: string, exerciseId: string, data: UpdateWorkoutExerciseRequest) =>
    http.patch<WorkoutExerciseSummary>(`/plans/${planId}/groups/${groupId}/exercises/${exerciseId}`, data),
  reorder: (planId: string, groupId: string, exerciseId: string, direction: 'UP' | 'DOWN') =>
    http.patch(`/plans/${planId}/groups/${groupId}/exercises/${exerciseId}/order`, { direction }),
  delete: (planId: string, groupId: string, exerciseId: string) =>
    http.delete(`/plans/${planId}/groups/${groupId}/exercises/${exerciseId}`),
}
