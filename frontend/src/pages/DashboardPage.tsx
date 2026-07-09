import axios from 'axios'
import { useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import ContributionHeatmap from '@/features/analytics/ContributionHeatmap'
import DashboardSettingsButton from '@/features/analytics/DashboardSettingsButton'
import DashboardSummaryGrid from '@/features/analytics/DashboardSummaryGrid'
import RecentPRsCard from '@/features/analytics/RecentPRsCard'
import WeeklyTrendChart from '@/features/analytics/WeeklyTrendChart'
import LastSessionCard from '@/features/sessions/LastSessionCard'
import { analyticsService } from '@/services/analyticsService'
import { queryKeys } from '@/services/queryKeys'
import { sessionService } from '@/services/sessionService'
import { useAuthStore } from '@/store/auth'
import { useDashboardPreferences, type DashboardWidgetId } from '@/store/dashboardPreferences'

const TREND_WEEKS = 8
const PR_LIMIT = 5

function parseJwtSub(token: string): string {
  try {
    return (JSON.parse(atob(token.split('.')[1])) as { sub?: string }).sub ?? ''
  } catch {
    return ''
  }
}

const subtractUtcMonths = (date: Date, months: number): Date => {
  const y = date.getUTCFullYear()
  const m = date.getUTCMonth()
  const d = date.getUTCDate()
  const targetMonth = m - months
  const lastDayOfTarget = new Date(Date.UTC(y, targetMonth + 1, 0)).getUTCDate()
  return new Date(Date.UTC(y, targetMonth, Math.min(d, lastDayOfTarget)))
}

export default function DashboardPage() {
  const accessToken = useAuthStore((s) => s.accessToken)
  const userId = useMemo(() => (accessToken ? parseJwtSub(accessToken) : ''), [accessToken])
  const visibleWidgets = useDashboardPreferences((s) => s.getVisibleWidgets(userId))
  const setVisibleWidgets = useDashboardPreferences((s) => s.setVisibleWidgets)

  const handleToggle = (widgetId: DashboardWidgetId, visible: boolean) => {
    const updated = visible
      ? [...visibleWidgets, widgetId]
      : visibleWidgets.filter((id) => id !== widgetId)
    setVisibleWidgets(userId, updated)
  }

  const isVisible = (id: DashboardWidgetId) => visibleWidgets.includes(id)
  const now = new Date()
  const todayUtc = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate()))
  const fromDate = subtractUtcMonths(todayUtc, 3).toISOString().slice(0, 10)
  const toDate = todayUtc.toISOString().slice(0, 10)

  const { data: heatmapEntries = [], isLoading: heatmapLoading } = useQuery({
    queryKey: queryKeys.analytics.heatmap(fromDate, toDate),
    queryFn: () => analyticsService.heatmap(fromDate, toDate),
  })
  const { data: summary, isLoading: summaryLoading, isError: summaryError } = useQuery({
    queryKey: queryKeys.analytics.summary(),
    queryFn: analyticsService.summary,
  })
  const { data: weeklyTrend, isLoading: weeklyTrendLoading, isError: weeklyTrendError } = useQuery({
    queryKey: queryKeys.analytics.weeklyTrend(TREND_WEEKS),
    queryFn: () => analyticsService.weeklyTrend(TREND_WEEKS),
  })
  const { data: personalRecords, isLoading: personalRecordsLoading, isError: personalRecordsError } = useQuery({
    queryKey: queryKeys.analytics.personalRecords(PR_LIMIT),
    queryFn: () => analyticsService.personalRecords(PR_LIMIT),
  })
  const { data: history = [] } = useQuery({
    queryKey: queryKeys.sessions.history(),
    queryFn: sessionService.history,
  })

  const { data: openSession = null } = useQuery({
    queryKey: queryKeys.sessions.open(),
    queryFn: async () => {
      try {
        return await sessionService.getOpen()
      } catch (error) {
        if (axios.isAxiosError(error) && error.response?.status === 404) {
          return null
        }
        throw error
      }
    },
  })

  const lastSession = history[0] ?? null

  return (
    <div className="space-y-6">
      {isVisible('summary-grid') && (
        <DashboardSummaryGrid data={summary} isLoading={summaryLoading} isError={summaryError} />
      )}

      <section>
        <div className="mb-3 flex items-center justify-between">
          <h2 className="text-sm font-semibold uppercase tracking-widest text-muted-foreground">Activity</h2>
          <DashboardSettingsButton visibleWidgets={visibleWidgets} onToggle={handleToggle} />
        </div>
        {isVisible('activity-heatmap') && (
          <div className="rounded-xl border border-border bg-card p-4">
            {heatmapLoading ? null : <ContributionHeatmap entries={heatmapEntries} from={fromDate} to={toDate} />}
          </div>
        )}
      </section>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        {isVisible('last-session') && lastSession ? <LastSessionCard session={lastSession} /> : null}
        {isVisible('recent-prs') && !personalRecordsLoading && !personalRecordsError && (
          <RecentPRsCard records={personalRecords ?? []} />
        )}
      </div>

      {isVisible('weekly-trend') && (
        <section>
          <h2 className="mb-3 text-sm font-semibold uppercase tracking-widest text-muted-foreground">Weekly Trend</h2>
          <Card>
            <CardHeader className="pb-2">
              <CardTitle className="text-sm">Sets per week</CardTitle>
            </CardHeader>
            <CardContent>
              {!weeklyTrendLoading && !weeklyTrendError && <WeeklyTrendChart entries={weeklyTrend ?? []} />}
            </CardContent>
          </Card>
        </section>
      )}

      <div className="flex gap-3">
        <Button asChild>
          <Link to="/session">{openSession != null ? 'Resume session' : 'Start session'}</Link>
        </Button>
        <Button asChild variant="outline">
          <Link to="/history">View history</Link>
        </Button>
      </div>
    </div>
  )
}

