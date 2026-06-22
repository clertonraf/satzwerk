import { Link } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { formatGroupStats } from '@/features/sessions/sessionHelpers'
import type { WorkoutGroupCatalogEntry } from '@/lib/domainBuilders'
import type { WorkoutPlanDetail, WorkoutGroupDetail } from '@/services/planService'

interface SessionStartupProps {
  groupOptions: WorkoutGroupCatalogEntry[]
  groupStatsMap: Map<string, { count: number; lastCompletedAt: string | null }>
  isHistoryLoading: boolean
  isHistoryAvailable: boolean
  startOptionsData: WorkoutPlanDetail | null | undefined
  isCatalogLoading: boolean
  isOnline: boolean
  stalePlanError: string | null
  isStartPending: boolean
  onStart: (groupId: string) => void
  onPreview: (group: WorkoutGroupDetail, planName: string) => void
}

export default function SessionStartup({
  groupOptions,
  groupStatsMap,
  isHistoryLoading,
  isHistoryAvailable,
  startOptionsData,
  isCatalogLoading,
  isOnline,
  stalePlanError,
  isStartPending,
  onStart,
  onPreview,
}: SessionStartupProps) {
  if (isCatalogLoading) {
    return <p className="text-sm text-muted-foreground">Loading workout groups...</p>
  }

  if (startOptionsData === null) {
    return (
      <p className="text-sm text-muted-foreground">
        No active plan. Activate a plan on the <Link className="underline" to="/plans">Plans</Link> page to start a session.
      </p>
    )
  }

  if (groupOptions.length === 0) {
    return (
      <p className="text-sm text-muted-foreground">
        No workout groups found yet. Build your training split on the <Link className="underline" to="/plans">Plans</Link> page.
      </p>
    )
  }

  return (
    <div className="space-y-3">
      {!isOnline ? (
        <p className="text-sm text-muted-foreground">
          Reconnect to start a new workout. Your current session data stays available offline.
        </p>
      ) : null}
      {stalePlanError ? <p className="text-sm text-destructive">{stalePlanError}</p> : null}
      {groupOptions.map(({ group, plan }) => {
        const stats = groupStatsMap.get(group.id)

        return (
          <div key={group.id} className="flex w-full items-center justify-between rounded-lg border border-border px-4 py-4">
            <span>
              <span className="block font-medium">{group.title}</span>
              <span className="block text-sm text-muted-foreground">
                {plan.name} · {group.exercises.length} exercises ·{' '}
                {isHistoryLoading
                  ? '…'
                  : isHistoryAvailable
                    ? formatGroupStats(stats?.count ?? 0, stats?.lastCompletedAt ?? null)
                    : 'Stats unavailable'}
              </span>
            </span>
            <span className="flex items-center gap-2">
              <Button type="button" size="sm" variant="ghost" onClick={() => onPreview(group, plan.name)}>
                Preview
              </Button>
              <Button
                type="button"
                size="sm"
                disabled={!isOnline || isStartPending}
                onClick={() => onStart(group.id)}
              >
                Start
              </Button>
            </span>
          </div>
        )
      })}
    </div>
  )
}
