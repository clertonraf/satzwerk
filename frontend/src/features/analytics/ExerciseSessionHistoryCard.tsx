import type { ExerciseProgressResponse } from '@/services/analyticsService'

interface ExerciseSessionHistoryCardProps {
  progress: ExerciseProgressResponse | null
  isLoading: boolean
}

export default function ExerciseSessionHistoryCard({ progress, isLoading }: ExerciseSessionHistoryCardProps) {
  if (isLoading) return null
  if (!progress) return null

  return (
    <ul>
      {progress.recentSessions.map((session) => (
        <li key={session.sessionId}>{session.topSetLabel}</li>
      ))}
    </ul>
  )
}
