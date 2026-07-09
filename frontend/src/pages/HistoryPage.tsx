import { useMemo, useState } from 'react'
import { ChevronDown, ChevronUp } from 'lucide-react'
import { useQueries, useQuery } from '@tanstack/react-query'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { buildWorkoutGroupCatalog } from '@/lib/domainBuilders'
import { computeSetCompletionPercentage, formatSessionDate } from '@/features/sessions/sessionHelpers'
import type { WorkoutSession, SetLog } from '@/services/sessionService'
import { exerciseService, type Exercise } from '@/services/exerciseService'
import { planService } from '@/services/planService'
import { queryKeys } from '@/services/queryKeys'
import { sessionService } from '@/services/sessionService'

function formatDuration(startedAt: string, completedAt: string | null): string | null {
  if (!completedAt) return null
  const ms = new Date(completedAt).getTime() - new Date(startedAt).getTime()
  const minutes = Math.round(ms / 60000)
  if (minutes < 60) return `${minutes} min`
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  return m > 0 ? `${h}h ${m}m` : `${h}h`
}

interface SessionHistoryItemProps {
  session: WorkoutSession
  groupTitle: string
  planName: string
  exerciseMap: Record<string, Exercise>
  totalTargetSets: number
}

function SessionHistoryItem({ session, groupTitle, planName, exerciseMap, totalTargetSets }: SessionHistoryItemProps) {
  const [isOpen, setIsOpen] = useState(false)

  const detailQuery = useQuery({
    queryKey: queryKeys.sessions.detail(session.id),
    queryFn: () => sessionService.getById(session.id),
    enabled: isOpen,
    staleTime: Infinity,
  })

  const groupedSetLogs = useMemo(() => {
    if (!detailQuery.data?.setLogs.length) return {} as Record<string, SetLog[]>
    return detailQuery.data.setLogs.reduce<Record<string, SetLog[]>>((acc, log) => {
      if (!acc[log.exerciseId]) acc[log.exerciseId] = []
      acc[log.exerciseId].push(log)
      return acc
    }, {})
  }, [detailQuery.data])

  const duration = formatDuration(session.startedAt, session.completedAt)
  const completionPct = computeSetCompletionPercentage(session.setCount, totalTargetSets)

  return (
    <li className="rounded-lg border border-border">
      <button
        type="button"
        className="flex w-full flex-col gap-1 px-4 py-4 text-left sm:flex-row sm:items-center sm:justify-between"
        onClick={() => setIsOpen((v) => !v)}
        aria-expanded={isOpen}
      >
        <div>
          <p className="font-medium">{groupTitle}</p>
          <p className="text-sm text-muted-foreground">{planName}</p>
        </div>
        <div className="flex items-center gap-3 sm:flex-col sm:items-end">
          <div className="text-right">
            <p className="text-sm text-muted-foreground">{formatSessionDate(session.completedAt ?? session.startedAt)}</p>
            {duration ? <p className="text-xs text-muted-foreground">{duration}</p> : null}
            {completionPct !== null ? (
              <p className="text-xs text-muted-foreground">{completionPct}% sets completed</p>
            ) : null}
          </div>
          {isOpen ? (
            <ChevronUp className="size-4 shrink-0 text-muted-foreground" aria-hidden="true" />
          ) : (
            <ChevronDown className="size-4 shrink-0 text-muted-foreground" aria-hidden="true" />
          )}
        </div>
      </button>

      {isOpen ? (
        <div className="border-t border-border px-4 py-4">
          {detailQuery.isLoading ? (
            <p className="text-sm text-muted-foreground">Loading sets...</p>
          ) : detailQuery.error ? (
            <p className="text-sm text-destructive">Could not load set details.</p>
          ) : (
            <>
              {session.notes ? (
                <p className="mb-3 text-sm italic text-muted-foreground">"{session.notes}"</p>
              ) : null}
              {Object.keys(groupedSetLogs).length === 0 ? (
                <p className="text-sm text-muted-foreground">No sets logged.</p>
              ) : (
                <div className="space-y-4">
                  {Object.entries(groupedSetLogs).map(([exerciseId, logs]) => (
                    <div key={exerciseId}>
                      <p className="mb-1 text-sm font-semibold">{exerciseMap[exerciseId]?.name ?? 'Unknown exercise'}</p>
                      <div className="space-y-1">
                        {logs.map((log) => (
                          <p key={log.id} className="text-sm text-muted-foreground">
                            Set {log.setNumber} — {log.weight} kg × {log.reps} reps
                          </p>
                        ))}
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </>
          )}
        </div>
      ) : null}
    </li>
  )
}

export default function HistoryPage() {
  const historyQuery = useQuery({
    queryKey: queryKeys.sessions.history(),
    queryFn: () => sessionService.history(),
  })
  const plansQuery = useQuery({
    queryKey: queryKeys.plans.all(),
    queryFn: () => planService.list(),
  })
  const planDetailsQueries = useQueries({
    queries: (plansQuery.data ?? []).map((plan) => ({
      queryKey: queryKeys.plans.detail(plan.id),
      queryFn: () => planService.get(plan.id),
    })),
  })
  const exercisesQuery = useQuery({
    queryKey: queryKeys.exercises.all(),
    queryFn: () => exerciseService.list(),
  })

  const planDetails = planDetailsQueries.flatMap((query) => (query.data ? [query.data] : []))
  const groupCatalog = useMemo(() => buildWorkoutGroupCatalog(planDetails), [planDetails])
  const exerciseMap = useMemo(
    () => Object.fromEntries((exercisesQuery.data ?? []).map((e) => [e.id, e])),
    [exercisesQuery.data],
  )
  const targetSetsMap = useMemo(() => {
    const map = new Map<string, number>()
    Object.values(groupCatalog).forEach(({ group }) => {
      map.set(group.id, group.exercises.reduce((sum, ex) => sum + ex.sets, 0))
    })
    return map
  }, [groupCatalog])

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
                <SessionHistoryItem
                  key={session.id}
                  session={session}
                  groupTitle={groupEntry?.group.title ?? 'Workout group'}
                  planName={groupEntry?.plan.name ?? 'Workout plan'}
                  exerciseMap={exerciseMap}
                  totalTargetSets={targetSetsMap.get(session.workoutGroupId) ?? 0}
                />
              )
            })}
          </ul>
        ) : null}
      </CardContent>
    </Card>
  )
}
