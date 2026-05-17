import { useMemo } from 'react'
import { useQueries, useQuery } from '@tanstack/react-query'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { buildWorkoutGroupCatalog, formatSessionDate } from '@/features/sessions/sessionHelpers'
import { planService } from '@/services/planService'
import { sessionService } from '@/services/sessionService'

export default function HistoryPage() {
  const historyQuery = useQuery({
    queryKey: ['session-history'],
    queryFn: () => sessionService.history(),
  })
  const plansQuery = useQuery({
    queryKey: ['plans'],
    queryFn: () => planService.list(),
  })
  const planDetailsQueries = useQueries({
    queries: (plansQuery.data ?? []).map((plan) => ({
      queryKey: ['plans', plan.id],
      queryFn: () => planService.get(plan.id),
    })),
  })

  const planDetails = planDetailsQueries.flatMap((query) => (query.data ? [query.data] : []))
  const groupCatalog = useMemo(() => buildWorkoutGroupCatalog(planDetails), [planDetails])

  if (historyQuery.error || plansQuery.error || planDetailsQueries.some((query) => query.error)) {
    return <p className="text-sm text-destructive">Could not load workout history.</p>
  }

  return (
    <Card className="border-border bg-card/90 shadow-sm">
      <CardHeader>
        <CardTitle>History</CardTitle>
        <CardDescription>Review completed workout sessions.</CardDescription>
      </CardHeader>
      <CardContent>
        {historyQuery.isLoading ? <p className="text-sm text-muted-foreground">Loading workout history...</p> : null}

        {!historyQuery.isLoading && (historyQuery.data ?? []).length === 0 ? (
          <p className="text-sm text-muted-foreground">No completed workout sessions yet.</p>
        ) : null}

        {(historyQuery.data ?? []).length > 0 ? (
          <ul className="space-y-3">
            {historyQuery.data!.map((session) => {
              const groupEntry = groupCatalog[session.workoutGroupId]

              return (
                <li
                  key={session.id}
                  className="flex flex-col gap-1 rounded-lg border border-border px-4 py-4 sm:flex-row sm:items-center sm:justify-between"
                >
                  <div>
                    <p className="font-medium">{groupEntry?.group.title ?? 'Workout group'}</p>
                    <p className="text-sm text-muted-foreground">{groupEntry?.plan.name ?? 'Workout plan'}</p>
                  </div>
                  <p className="text-sm text-muted-foreground">{formatSessionDate(session.completedAt ?? session.startedAt)}</p>
                </li>
              )
            })}
          </ul>
        ) : null}
      </CardContent>
    </Card>
  )
}
