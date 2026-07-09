export const queryKeys = {
  plans: {
    all: () => ['plans'] as const,
    detail: (id: string) => ['plans', id] as const,
  },
  exercises: {
    all: () => ['exercises'] as const,
  },
  sessions: {
    open: () => ['open-session'] as const,
    startOptions: () => ['session-start-options'] as const,
    openPlanDetail: (sessionId: string) => ['session-open-plan-detail', sessionId] as const,
    history: () => ['session-history'] as const,
    detail: (id: string) => ['session', id] as const,
    referenceWeights: (sessionId: string) => ['reference-weights', sessionId] as const,
  },
  analytics: {
    heatmap: (from?: string, to?: string) => ['heatmap', from, to] as const,
    streak: () => ['streak'] as const,
    summary: () => ['summary'] as const,
    weeklyTrend: (weeks?: number) => ['weekly-trend', weeks] as const,
    personalRecords: (limit?: number) => ['personal-records', limit] as const,
    topExercises: (limit?: number) => ['top-exercises', limit] as const,
  },
} as const
