import type { ExerciseProgressResponse } from '@/services/analyticsService'

interface ExerciseProgressChartProps {
  progress: ExerciseProgressResponse | null
  isLoading: boolean
}

export default function ExerciseProgressChart({ progress, isLoading }: ExerciseProgressChartProps) {
  if (isLoading) return null
  if (!progress) return null

  return (
    <div>
      <p className="text-sm font-medium">Top set progression</p>
      <p className="text-xs text-muted-foreground">{progress.exerciseName}</p>
    </div>
  )
}
