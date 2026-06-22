import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import ForfeitSessionModal from '@/features/sessions/ForfeitSessionModal'
import ResumeDiscardModal from '@/features/sessions/ResumeDiscardModal'
import SessionStartup from '@/features/sessions/SessionStartup'
import SessionWorkout from '@/features/sessions/SessionWorkout'
import WorkoutGroupPreviewModal from '@/features/sessions/WorkoutGroupPreviewModal'
import { formatSessionDate } from '@/features/sessions/sessionHelpers'
import { useSessionQueries } from '@/features/sessions/useSessionQueries'
import { useWorkoutSession } from '@/features/sessions/useWorkoutSession'
import { useOnlineStatus } from '@/hooks/useOnlineStatus'
import type { WorkoutGroupDetail } from '@/services/planService'

export default function SessionPage() {
  const navigate = useNavigate()
  const isOnline = useOnlineStatus()
  const [isForfeitModalOpen, setIsForfeitModalOpen] = useState(false)
  const [previewGroup, setPreviewGroup] = useState<{ group: WorkoutGroupDetail; planName: string } | null>(null)

  const {
    session,
    conflictSession,
    stalePlanError,
    isSessionLoading,
    handleStartSession,
    handleLogSet,
    handleUpdateSetLog,
    handleCompleteSession,
    handleForfeitSession,
    handleDiscardConflict,
    clearConflictState,
    isStartPending,
    isAddSetPending,
    isUpdateSetPending,
    isCompletePending,
    isForfeitPending,
  } = useWorkoutSession({
    onComplete: () => navigate('/history'),
    onForfeit: () => navigate('/session'),
  })

  const [unitState, setUnitState] = useState<{ sessionId: string | undefined; units: Record<string, 'kg' | 'lb'> }>({
    sessionId: undefined,
    units: {},
  })
  // Derive per-exercise units scoped to the current session — automatically
  // resets to {} when session?.id changes (new session or session ended).
  const exerciseUnits = unitState.sessionId === session?.id ? unitState.units : {}
  const setExerciseUnit = (exerciseId: string, unit: 'kg' | 'lb') =>
    setUnitState((prev) => {
      const current = prev.sessionId === session?.id ? prev.units : {}
      return { sessionId: session?.id, units: { ...current, [exerciseId]: unit } }
    })

  const {
    startOptionsQuery,
    historyQuery,
    referenceWeightsQuery,
    groupOptions,
    groupCatalog,
    groupStatsMap,
    exercisesById,
    referenceWeightsMap,
    isCatalogLoading,
    queryError,
  } = useSessionQueries({ session, isSessionLoading })

  const currentGroupEntry = session ? groupCatalog[session.workoutGroupId] : undefined

  if (queryError) {
    return <p className="text-sm text-destructive">Could not load workout session data.</p>
  }

  if (!session && isSessionLoading) {
    return <p className="text-sm text-muted-foreground">Loading workout session...</p>
  }

  return (
    <div className="space-y-6">
      {conflictSession ? (
        <ResumeDiscardModal onResume={clearConflictState} onDiscard={() => void handleDiscardConflict()} />
      ) : null}

      {isForfeitModalOpen ? (
        <ForfeitSessionModal
          onConfirm={() => {
            setIsForfeitModalOpen(false)
            void handleForfeitSession()
          }}
          onCancel={() => setIsForfeitModalOpen(false)}
        />
      ) : null}

      {previewGroup ? (
        <WorkoutGroupPreviewModal
          group={previewGroup.group}
          planName={previewGroup.planName}
          onClose={() => setPreviewGroup(null)}
        />
      ) : null}

      <Card className="border-border bg-card/90 shadow-sm">
        <CardHeader className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
          <div className="space-y-1.5">
            <CardTitle>
              {session ? (currentGroupEntry?.group.title ?? 'Workout Session') : 'Start workout session'}
            </CardTitle>
            <CardDescription>
              {session
                ? `${currentGroupEntry?.plan.name ?? 'Workout plan'} · Started ${formatSessionDate(session.startedAt)}`
                : 'Choose a workout group to begin training.'}
            </CardDescription>
          </div>
        </CardHeader>
        <CardContent>
          {!session ? (
            <SessionStartup
              groupOptions={groupOptions}
              groupStatsMap={groupStatsMap}
              isHistoryLoading={historyQuery.isLoading}
              isHistoryAvailable={historyQuery.data !== undefined}
              startOptionsData={startOptionsQuery.data}
              isCatalogLoading={isCatalogLoading}
              isOnline={isOnline}
              stalePlanError={stalePlanError}
              isStartPending={isStartPending}
              onStart={(groupId) => void handleStartSession(groupId)}
              onPreview={(group, planName) => setPreviewGroup({ group, planName })}
            />
          ) : (
            <SessionWorkout
              session={session}
              currentGroupEntry={currentGroupEntry}
              exercisesById={exercisesById}
              referenceWeightsMap={referenceWeightsMap}
              isReferenceWeightsLoading={referenceWeightsQuery.isLoading}
              isCatalogLoading={isCatalogLoading}
              isOnline={isOnline}
              exerciseUnits={exerciseUnits}
              isAddSetPending={isAddSetPending}
              isUpdateSetPending={isUpdateSetPending}
              isCompletePending={isCompletePending}
              isForfeitPending={isForfeitPending}
              onLogSet={(exerciseId, setNumber, weight, reps, unit) =>
                void handleLogSet(exerciseId, setNumber, weight, reps, unit)
              }
              onUpdateSetLog={handleUpdateSetLog}
              onSetExerciseUnit={setExerciseUnit}
              onComplete={() => void handleCompleteSession()}
              onForfeit={() => setIsForfeitModalOpen(true)}
            />
          )}
        </CardContent>
      </Card>
    </div>
  )
}
