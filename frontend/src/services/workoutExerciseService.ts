import { api } from './api'
import type { WorkoutExerciseSummary } from './planService'

export interface CreateWorkoutExerciseRequest {
  exerciseId: string
  sets: number
  reps: number
  advancedTechnique?: string
}

export type UpdateWorkoutExerciseRequest = Partial<CreateWorkoutExerciseRequest>

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
