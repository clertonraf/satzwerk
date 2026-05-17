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
