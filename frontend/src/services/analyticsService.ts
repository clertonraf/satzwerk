import { http } from './api'

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
  activePlanDays: number | null
  avgSessionDurationMinutes: number | null
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
  reps: number
  achievedAt: string
}

export interface TopExercise {
  exerciseId: string
  exerciseName: string
  setCount: number
}

export const analyticsService = {
  heatmap: (from?: string, to?: string) =>
    http.get<HeatmapEntry[]>('/analytics/heatmap', {
      params: { ...(from && { from }), ...(to && { to }) },
    }),

  streak: () => http.get<StreakResponse>('/analytics/streak'),

  summary: () => http.get<DashboardSummary>('/analytics/summary'),

  weeklyTrend: (weeks?: number) =>
    http.get<WeeklyTrendEntry[]>('/analytics/weekly-trend', {
      params: { ...(weeks !== undefined && { weeks }) },
    }),

  personalRecords: (limit?: number) =>
    http.get<PersonalRecord[]>('/analytics/personal-records', {
      params: { ...(limit !== undefined && { limit }) },
    }),

  topExercises: (limit?: number) =>
    http.get<TopExercise[]>('/analytics/top-exercises', {
      params: { ...(limit !== undefined && { limit }) },
    }),
}
