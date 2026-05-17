import { api } from './api'

export interface Exercise {
  id: string
  name: string
  muscleGroup: string
  description: string | null
  videoUrl: string | null
  equipment: string | null
  createdAt: string
  updatedAt: string
}

export interface CreateExerciseRequest {
  name: string
  muscleGroup: string
  description?: string
  videoUrl?: string
  equipment?: string
}

export type UpdateExerciseRequest = Partial<CreateExerciseRequest>

export const exerciseService = {
  list: (muscleGroup?: string) =>
    api.get<Exercise[]>('/exercises', { params: muscleGroup ? { muscleGroup } : {} }).then((response) => response.data),
  get: (id: string) => api.get<Exercise>(`/exercises/${id}`).then((response) => response.data),
  create: (data: CreateExerciseRequest) => api.post<Exercise>('/exercises', data).then((response) => response.data),
  update: (id: string, data: UpdateExerciseRequest) =>
    api.patch<Exercise>(`/exercises/${id}`, data).then((response) => response.data),
  delete: (id: string) => api.delete(`/exercises/${id}`).then(() => undefined),
}
