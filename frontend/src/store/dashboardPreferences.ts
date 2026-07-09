import { create } from 'zustand'
import { persist } from 'zustand/middleware'

export type DashboardWidgetId =
  | 'summary-grid'
  | 'activity-heatmap'
  | 'last-session'
  | 'recent-prs'
  | 'weekly-trend'

export const ALL_WIDGET_IDS: DashboardWidgetId[] = [
  'summary-grid',
  'activity-heatmap',
  'last-session',
  'recent-prs',
  'weekly-trend',
]

export const WIDGET_LABELS: Record<DashboardWidgetId, string> = {
  'summary-grid': 'Stats overview',
  'activity-heatmap': 'Activity heatmap',
  'last-session': 'Last session',
  'recent-prs': 'Recent PRs',
  'weekly-trend': 'Weekly trend',
}

interface DashboardPreferencesState {
  /** userId → array of visible widget IDs (all visible when no entry exists) */
  visibleWidgets: Record<string, DashboardWidgetId[]>
  setVisibleWidgets: (userId: string, widgets: DashboardWidgetId[]) => void
  getVisibleWidgets: (userId: string) => DashboardWidgetId[]
}

export const useDashboardPreferences = create<DashboardPreferencesState>()(
  persist(
    (set, get) => ({
      visibleWidgets: {},
      setVisibleWidgets: (userId, widgets) =>
        set((state) => ({ visibleWidgets: { ...state.visibleWidgets, [userId]: widgets } })),
      getVisibleWidgets: (userId) => get().visibleWidgets[userId] ?? ALL_WIDGET_IDS,
    }),
    { name: 'satzwerk-dashboard-prefs' },
  ),
)
