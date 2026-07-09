import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import type { TopExercise } from '@/services/analyticsService'

interface TopExercisesCardProps {
  exercises: TopExercise[]
}

export default function TopExercisesCard({ exercises }: TopExercisesCardProps) {
  return (
    <Card>
      <CardHeader className="pb-2">
        <CardTitle className="text-sm">Most trained exercises</CardTitle>
      </CardHeader>
      <CardContent>
        {exercises.length === 0 ? (
          <p className="text-xs text-muted-foreground">No sets logged yet.</p>
        ) : (
          <ul className="space-y-2">
            {exercises.map((ex) => (
              <li key={ex.exerciseId} className="flex items-center justify-between text-sm">
                <span className="font-medium">{ex.exerciseName}</span>
                <span className="text-muted-foreground">{ex.setCount} sets</span>
              </li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  )
}
