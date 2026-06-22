import { useSessionLifecycle } from '@/features/sessions/useSessionLifecycle'
import { useConflictResolution } from '@/features/sessions/useConflictResolution'

export function useWorkoutSession({ onComplete, onForfeit }: { onComplete: () => void; onForfeit?: () => void }) {
  const lifecycle = useSessionLifecycle({ onComplete, onForfeit })
  const conflict = useConflictResolution({
    startMutateAsync: lifecycle.startMutateAsync,
    discardMutateAsync: lifecycle.discardMutateAsync,
  })

  return {
    session: lifecycle.session,
    conflictSession: conflict.conflictSession,
    stalePlanError: conflict.stalePlanError,
    isSessionLoading: lifecycle.isSessionLoading,
    handleStartSession: conflict.handleStartSession,
    handleLogSet: lifecycle.handleLogSet,
    handleUpdateSetLog: lifecycle.handleUpdateSetLog,
    handleDeleteSetLog: lifecycle.handleDeleteSetLog,
    handleCompleteSession: lifecycle.handleCompleteSession,
    handleForfeitSession: lifecycle.handleForfeitSession,
    handleDiscardConflict: conflict.handleDiscardConflict,
    clearConflictState: conflict.clearConflictState,
    isStartPending: lifecycle.isStartPending,
    isAddSetPending: lifecycle.isAddSetPending,
    isUpdateSetPending: lifecycle.isUpdateSetPending,
    isDeleteSetPending: lifecycle.isDeleteSetPending,
    isCompletePending: lifecycle.isCompletePending,
    isForfeitPending: lifecycle.isForfeitPending,
  }
}

