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
    history: () => ['session-history'] as const,
    detail: (id: string) => ['session', id] as const,
  },
  analytics: {
    heatmap: (from?: string, to?: string) => ['heatmap', from, to] as const,
    streak: () => ['streak'] as const,
    summary: () => ['analytics-summary'] as const,
    weeklyTrend: (weeks?: number) => ['analytics-weekly-trend', weeks] as const,
    personalRecords: (limit?: number) => ['analytics-personal-records', limit] as const,
  },
} as const
