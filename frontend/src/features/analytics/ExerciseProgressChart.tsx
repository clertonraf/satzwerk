import { CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import type { ExerciseProgressResponse } from '@/services/analyticsService'

interface ExerciseProgressChartProps {
  progress: ExerciseProgressResponse | null
  isLoading: boolean
}

export default function ExerciseProgressChart({ progress, isLoading }: ExerciseProgressChartProps) {
  if (isLoading)
    return (
      <Card>
        <CardContent className="pt-6">
          <p className="text-sm text-muted-foreground">Loading progress…</p>
        </CardContent>
      </Card>
    )
  if (!progress || progress.points.length === 0)
    return (
      <Card>
        <CardContent className="pt-6">
          <p className="text-sm text-muted-foreground">No completed sessions for this Exercise yet.</p>
        </CardContent>
      </Card>
    )

  return (
    <Card>
      <CardHeader>
        <CardTitle>Top set progression</CardTitle>
      </CardHeader>
      <CardContent>
        <div className="sr-only">
          <p>{progress.exerciseName} progress data</p>
          <ul>
            {progress.points.map((point) => (
              <li key={point.sessionId}>
                {point.sessionDate}: top set {point.topSetWeightKg} kg for {point.topSetReps} reps; estimated 1RM{' '}
                {point.estimatedOneRepMaxKg == null ? 'not available' : `${point.estimatedOneRepMaxKg} kg`}
              </li>
            ))}
          </ul>
        </div>
        <div aria-hidden="true">
          <ResponsiveContainer width="100%" height={260}>
            <LineChart data={progress.points} margin={{ top: 8, right: 16, left: 0, bottom: 8 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
              <XAxis dataKey="sessionDate" tick={{ fontSize: 10 }} tickLine={false} />
              <YAxis yAxisId="left" tick={{ fontSize: 10 }} tickLine={false} axisLine={false} width={42} />
              <YAxis yAxisId="right" orientation="right" tick={{ fontSize: 10 }} tickLine={false} axisLine={false} width={42} />
              <Tooltip
                formatter={(value, name) =>
                  name === 'Estimated 1RM' ? [`${value} kg`, 'Estimated 1RM'] : [`${value} kg`, 'Top set']
                }
                labelFormatter={(value) => `Session ${value}`}
              />
              <Legend />
              <Line
                yAxisId="left"
                type="monotone"
                dataKey="topSetWeightKg"
                name="Top set"
                stroke="var(--primary)"
                strokeWidth={2}
                dot={{ r: 3 }}
              />
              <Line
                yAxisId="right"
                type="monotone"
                dataKey="estimatedOneRepMaxKg"
                name="Estimated 1RM"
                stroke="var(--muted-foreground)"
                strokeWidth={2}
                dot={{ r: 3 }}
                connectNulls
              />
            </LineChart>
          </ResponsiveContainer>
        </div>
      </CardContent>
    </Card>
  )
}
