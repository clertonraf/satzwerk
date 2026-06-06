import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import type { WorkoutSession } from '@/services/sessionService'

interface LastSessionCardProps {
  session: WorkoutSession
}

export default function LastSessionCard({ session }: LastSessionCardProps) {
  const completedDate = session.completedAt ? new Date(session.completedAt) : null
  const startedDate = new Date(session.startedAt)

  const durationMinutes =
    completedDate
      ? Math.round((completedDate.getTime() - startedDate.getTime()) / 60_000)
      : null

  const displayDate = (completedDate ?? startedDate).toLocaleDateString(undefined, {
    weekday: 'short',
    month: 'short',
    day: 'numeric',
  })

  return (
    <Card>
      <CardHeader className="pb-2">
        <CardTitle className="text-sm">Last Session</CardTitle>
      </CardHeader>
      <CardContent className="space-y-1">
        <p className="font-medium">{session.workoutGroupTitle}</p>
        <p className="text-xs text-muted-foreground">{displayDate}</p>
        <div className="flex gap-3 text-xs text-muted-foreground">
          {durationMinutes !== null && <span>{durationMinutes} min</span>}
          <span>{session.setCount} sets</span>
        </div>
      </CardContent>
    </Card>
  )
}
