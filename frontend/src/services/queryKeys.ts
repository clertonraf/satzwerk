export const queryKeys = {
  plans: {
    all: () => ['plans'] as const,
    metadata: (id: string) => ['plans', 'metadata', id] as const,
    structure: (id: string) => ['plans', 'structure', id] as const,
    advancedTechniques: () => ['advanced-techniques'] as const,
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
    leastExercises: (limit?: number) => ['least-exercises', limit] as const,
    exerciseProgress: (exerciseId: string) => ['exercise-progress', exerciseId] as const,
  },
  measurements: {
    all: () => ['measurements'] as const,
  },
  medications: {
    all: () => ['medications'] as const,
    today: () => ['medication-today'] as const,
    logs: (id: string, from: string, to: string) => ['medication-logs', id, from, to] as const,
    heatmap: (weeks: number) => ['medication-heatmap', weeks] as const,
    analytics: (id: string, granularity: string) => ['medication-analytics', id, granularity] as const,
    journal: () => ['medication-journal'] as const,
    journalRange: (from: string, to: string, timezoneOffsetMinutes: number) =>
      ['medication-journal', from, to, timezoneOffsetMinutes] as const,
  },
  tokens: {
    all: () => ['tokens'] as const,
  },
  partnerGrants: {
    active: () => ['active'] as const,
  },
} as const
