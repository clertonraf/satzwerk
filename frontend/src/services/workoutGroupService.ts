import { api } from './api'
import type { WorkoutGroupDetail } from './planService'

export const workoutGroupService = {
  create: (planId: string, title: string) =>
    api.post<WorkoutGroupDetail>(`/plans/${planId}/groups`, { title }).then((response) => response.data),
  update: (planId: string, groupId: string, title: string) =>
    api.patch<WorkoutGroupDetail>(`/plans/${planId}/groups/${groupId}`, { title }).then((response) => response.data),
  delete: (planId: string, groupId: string) => api.delete(`/plans/${planId}/groups/${groupId}`).then(() => undefined),
}
