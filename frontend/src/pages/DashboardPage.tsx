import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import ContributionHeatmap from '@/features/analytics/ContributionHeatmap'
import StreakCard from '@/features/analytics/StreakCard'
import { analyticsService } from '@/services/analyticsService'
import { queryKeys } from '@/services/queryKeys'
import { sessionService } from '@/services/sessionService'

export default function DashboardPage() {
  const { data: heatmapEntries = [] } = useQuery({
    queryKey: queryKeys.analytics.heatmap(),
    queryFn: () => analyticsService.heatmap(),
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
          <ContributionHeatmap entries={heatmapEntries} />
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
