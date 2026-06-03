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
    heatmap: () => ['heatmap'] as const,
    streak: () => ['streak'] as const,
  },
} as const
