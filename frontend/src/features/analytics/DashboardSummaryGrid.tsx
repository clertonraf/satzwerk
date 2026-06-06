import type { DashboardSummary } from '@/services/analyticsService'

interface StatTileProps {
  label: string
  value: number | string
}

function StatTile({ label, value }: StatTileProps) {
  return (
    <div className="flex flex-col gap-1 rounded-xl border border-border bg-card p-4">
      <span className="text-2xl font-bold tabular-nums">{value}</span>
      <span className="text-xs text-muted-foreground">{label}</span>
    </div>
  )
}

function SkeletonTile() {
  return <div className="h-20 animate-pulse rounded-xl bg-muted" />
}

interface DashboardSummaryGridProps {
  data: DashboardSummary | undefined
  isLoading: boolean
  isError?: boolean
}

export default function DashboardSummaryGrid({ data, isLoading, isError }: DashboardSummaryGridProps) {
  if (isLoading) {
    return (
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
        {Array.from({ length: 6 }).map((_, i) => (
          <SkeletonTile key={i} />
        ))}
      </div>
    )
  }

  if (isError || !data) {
    return (
      <div className="rounded-xl border border-border bg-card p-4">
        <p className="text-xs text-muted-foreground">Stats unavailable.</p>
      </div>
    )
  }

  return (
    <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
      <StatTile label="Current streak" value={`${data.currentStreak}d`} />
      <StatTile label="Sessions this month" value={data.sessionsThisMonth} />
      <StatTile label="PRs this month" value={data.prsThisMonth} />
      <StatTile label="Longest streak" value={`${data.longestStreak}d`} />
      <StatTile label="Total sessions" value={data.totalSessions} />
      <StatTile label="Sets this week" value={data.setsThisWeek} />
      {data.activePlanDays != null && (
        <StatTile label="Plan age" value={`${data.activePlanDays}d`} />
      )}
    </div>
  )
}
