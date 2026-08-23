import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import type { ExerciseProgressResponse } from '@/services/analyticsService'

interface ExerciseSessionHistoryCardProps {
  progress: ExerciseProgressResponse | null
  isLoading: boolean
}

export default function ExerciseSessionHistoryCard({ progress, isLoading }: ExerciseSessionHistoryCardProps) {
  if (isLoading)
    return (
      <Card>
        <CardContent className="pt-6">
          <p className="text-sm text-muted-foreground">Loading recent sessions…</p>
        </CardContent>
      </Card>
    )
  if (!progress || progress.recentSessions.length === 0) return null

  return (
    <Card>
      <CardHeader>
        <CardTitle>Recent sessions</CardTitle>
      </CardHeader>
      <CardContent>
        <ul className="space-y-3">
          {progress.recentSessions.map((session) => (
            <li key={session.sessionId} className="flex items-center justify-between text-sm">
              <div>
                <p className="font-medium">{session.workoutGroupTitle}</p>
                <p className="text-muted-foreground">{session.sessionDate}</p>
              </div>
              <span className="text-muted-foreground">{session.topSetLabel}</span>
            </li>
          ))}
        </ul>
      </CardContent>
    </Card>
  )
}
