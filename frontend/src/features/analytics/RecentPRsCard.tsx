import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import type { PersonalRecord } from '@/services/analyticsService'

interface RecentPRsCardProps {
  records: PersonalRecord[]
}

export default function RecentPRsCard({ records }: RecentPRsCardProps) {
  if (records.length === 0) {
    return (
      <Card>
        <CardHeader className="pb-2">
          <CardTitle className="text-sm">Recent PRs</CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-xs text-muted-foreground">No personal records yet.</p>
        </CardContent>
      </Card>
    )
  }

  return (
    <Card>
      <CardHeader className="pb-2">
        <CardTitle className="text-sm">Recent PRs</CardTitle>
      </CardHeader>
      <CardContent className="space-y-2">
        {records.map((pr) => (
          <div key={`${pr.exerciseId}-${pr.achievedAt}`} className="text-sm">
            <span className="font-medium">{pr.exerciseName}</span>
            <span className="text-muted-foreground">
              {' — '}
              {pr.weightKg} kg × {pr.reps} reps{pr.reps > 0 ? ` (ratio: ${(pr.weightKg / pr.reps).toFixed(1)})` : ''}
            </span>
          </div>
        ))}
      </CardContent>
    </Card>
  )
}
