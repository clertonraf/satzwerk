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

export interface DashboardSummary {
  currentStreak: number
  longestStreak: number
  sessionsThisMonth: number
  prsThisMonth: number
  totalSessions: number
  setsThisWeek: number
  activePlanDays?: number | null
}

export interface WeeklyTrendEntry {
  week: string
  setCount: number
  sessionCount: number
}

export interface PersonalRecord {
  exerciseId: string
  exerciseName: string
  weightKg: number
  achievedAt: string
}

export const analyticsService = {
  heatmap: (from?: string, to?: string) =>
    api
      .get<HeatmapEntry[]>('/analytics/heatmap', {
        params: { ...(from && { from }), ...(to && { to }) },
      })
      .then((response) => response.data),

  streak: () => api.get<StreakResponse>('/analytics/streak').then((response) => response.data),

  summary: () => api.get<DashboardSummary>('/analytics/summary').then((response) => response.data),

  weeklyTrend: (weeks?: number) =>
    api
      .get<WeeklyTrendEntry[]>('/analytics/weekly-trend', {
        params: { ...(weeks !== undefined && { weeks }) },
      })
      .then((response) => response.data),

  personalRecords: (limit?: number) =>
    api
      .get<PersonalRecord[]>('/analytics/personal-records', {
        params: { ...(limit !== undefined && { limit }) },
      })
      .then((response) => response.data),
}
