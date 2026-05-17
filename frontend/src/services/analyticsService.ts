import { api } from './api'

export interface HeatmapEntry {
  date: string
  count: number
  intensity: number
}

export interface StreakResponse {
  currentStreak: number
  longestStreak: number
}

export const analyticsService = {
  heatmap: (from?: string, to?: string) =>
    api
      .get<HeatmapEntry[]>('/analytics/heatmap', {
        params: { ...(from && { from }), ...(to && { to }) },
      })
      .then((response) => response.data),

  streak: () => api.get<StreakResponse>('/analytics/streak').then((response) => response.data),
}
