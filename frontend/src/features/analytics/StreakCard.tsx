import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'

interface StreakCardProps {
  currentStreak: number
  longestStreak: number
}

export default function StreakCard({ currentStreak, longestStreak }: StreakCardProps) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-sm">Streaks</CardTitle>
      </CardHeader>
      <CardContent className="grid grid-cols-2 gap-4">
        <div>
          <p className="text-sm text-muted-foreground">Current streak {currentStreak > 0 ? '🔥' : ''}</p>
          <p className="text-3xl font-semibold">{currentStreak}</p>
          <p className="text-xs text-muted-foreground">days</p>
        </div>
        <div>
          <p className="text-sm text-muted-foreground">Longest</p>
          <p className="text-3xl font-semibold">{longestStreak}</p>
          <p className="text-xs text-muted-foreground">days</p>
        </div>
      </CardContent>
    </Card>
  )
}
