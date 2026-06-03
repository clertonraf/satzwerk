import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import ContributionHeatmap from '@/features/analytics/ContributionHeatmap'
import StreakCard from '@/features/analytics/StreakCard'
import { analyticsService } from '@/services/analyticsService'
import { queryKeys } from '@/services/queryKeys'
import { sessionService } from '@/services/sessionService'

const subtractUtcMonths = (date: Date, months: number): Date => {
  const y = date.getUTCFullYear()
  const m = date.getUTCMonth()
  const d = date.getUTCDate()
  const targetMonth = m - months
  // Day 0 of the month after targetMonth gives the last day of targetMonth,
  // handling negative months and year boundaries via JS Date rollover.
  const lastDayOfTarget = new Date(Date.UTC(y, targetMonth + 1, 0)).getUTCDate()
  return new Date(Date.UTC(y, targetMonth, Math.min(d, lastDayOfTarget)))
}

export default function DashboardPage() {
  const now = new Date()
  const todayUtc = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate()))
  const fromDate = subtractUtcMonths(todayUtc, 3).toISOString().slice(0, 10)
  const toDate = todayUtc.toISOString().slice(0, 10)

  const { data: heatmapEntries = [], isLoading: heatmapLoading } = useQuery({
    queryKey: queryKeys.analytics.heatmap(fromDate, toDate),
    queryFn: () => analyticsService.heatmap(fromDate, toDate),
  })
  const { data: streakData } = useQuery({
    queryKey: queryKeys.analytics.streak(),
    queryFn: analyticsService.streak,
  })
  const { data: history = [] } = useQuery({
    queryKey: queryKeys.sessions.history(),
    queryFn: sessionService.history,
  })

  const lastSession = history[0] ?? null

  return (
    <div className="space-y-6">
      <section>
        <h2 className="mb-3 text-sm font-semibold uppercase tracking-widest text-muted-foreground">Activity</h2>
        <div className="overflow-x-auto rounded-xl border border-border bg-card p-4">
          {heatmapLoading ? null : <ContributionHeatmap entries={heatmapEntries} from={fromDate} to={toDate} />}
        </div>
      </section>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        {streakData ? <StreakCard currentStreak={streakData.currentStreak} longestStreak={streakData.longestStreak} /> : null}
        {lastSession ? (
          <Card>
            <CardHeader>
              <CardTitle className="text-sm">Last Session</CardTitle>
            </CardHeader>
            <CardContent>
              <p className="text-xs text-muted-foreground">{new Date(lastSession.completedAt ?? lastSession.startedAt).toLocaleDateString()}</p>
            </CardContent>
          </Card>
        ) : null}
      </div>

      <div className="flex gap-3">
        <Button asChild>
          <Link to="/session">Start session</Link>
        </Button>
        <Button asChild variant="outline">
          <Link to="/history">View history</Link>
        </Button>
      </div>
    </div>
  )
}
