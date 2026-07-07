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
import { useWorkoutSessionMachine } from '@/features/sessions/useWorkoutSessionMachine'
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
    dispatch,
    isStartPending,
    isAddSetPending,
    isUpdateSetPending,
    isDeleteSetPending,
    isCompletePending,
    isForfeitPending,
  } = useWorkoutSessionMachine({
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
    groupOptions,
    groupCatalog,
    groupStatsMap,
    exercisesById,
    referenceWeightsMap,
    isCatalogLoading,
    queryError,
    isHistoryLoading,
    isHistoryAvailable,
    startOptionsData,
    isReferenceWeightsLoading,
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
        <ResumeDiscardModal
          onResume={() => void dispatch({ type: 'RESUME' })}
          onDiscard={() => void dispatch({ type: 'DISCARD' })}
        />
      ) : null}

      {isForfeitModalOpen ? (
        <ForfeitSessionModal
          onConfirm={() => {
            setIsForfeitModalOpen(false)
            void dispatch({ type: 'FORFEIT' })
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
              isHistoryLoading={isHistoryLoading}
              isHistoryAvailable={isHistoryAvailable}
              startOptionsData={startOptionsData}
              isCatalogLoading={isCatalogLoading}
              isOnline={isOnline}
              stalePlanError={stalePlanError}
              isStartPending={isStartPending}
              onStart={(groupId) => void dispatch({ type: 'START', workoutGroupId: groupId })}
              onPreview={(group, planName) => setPreviewGroup({ group, planName })}
            />
          ) : (
            <SessionWorkout
              session={session}
              currentGroupEntry={currentGroupEntry}
              exercisesById={exercisesById}
              referenceWeightsMap={referenceWeightsMap}
              isReferenceWeightsLoading={isReferenceWeightsLoading}
              isCatalogLoading={isCatalogLoading}
              isOnline={isOnline}
              exerciseUnits={exerciseUnits}
              isAddSetPending={isAddSetPending}
              isUpdateSetPending={isUpdateSetPending}
              isDeleteSetPending={isDeleteSetPending}
              isCompletePending={isCompletePending}
              isForfeitPending={isForfeitPending}
              onLogSet={(exerciseId, setNumber, weight, reps, unit) =>
                void dispatch({ type: 'LOG_SET', exerciseId, setNumber, weight, reps, unit })
              }
              onUpdateSetLog={(setLogId, weight, reps, unit) =>
                dispatch({ type: 'UPDATE_SET', setLogId, weight, reps, unit })
              }
              onDeleteSetLog={(setLogId) => void dispatch({ type: 'DELETE_SET', setLogId })}
              onSetExerciseUnit={setExerciseUnit}
              onComplete={() => void dispatch({ type: 'COMPLETE' })}
              onForfeit={() => setIsForfeitModalOpen(true)}
            />
          )}
        </CardContent>
      </Card>
    </div>
  )
}
