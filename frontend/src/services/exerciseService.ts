import { http } from './api'

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
    http.get<Exercise[]>('/exercises', { params: muscleGroup ? { muscleGroup } : {} }),
  get: (id: string) => http.get<Exercise>(`/exercises/${id}`),
  create: (data: CreateExerciseRequest) => http.post<Exercise>('/exercises', data),
  update: (id: string, data: UpdateExerciseRequest) => http.patch<Exercise>(`/exercises/${id}`, data),
  delete: (id: string) => http.delete(`/exercises/${id}`),
}
