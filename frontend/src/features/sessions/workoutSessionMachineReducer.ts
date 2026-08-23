import type { PendingSetLog, WorkoutSession } from '@/services/sessionService'

export type SessionPhase = 'idle' | 'conflict' | 'open' | 'completing'

export interface WorkoutSessionMachineState {
  phase: SessionPhase
  conflictSession: WorkoutSession | null
  pendingGroupId: string | null
  stalePlanError: string | null
  pendingSetLogs: PendingSetLog[]
  trackedSessionId: string | null
  trackedServerSetCount: number
}

export type WorkoutSessionMachineAction =
  | { type: 'session-synced'; sessionId: string | null; serverSetCount: number }
  | { type: 'start-requested' }
  | { type: 'start-succeeded'; sessionId: string; serverSetCount: number }
  | { type: 'start-conflicted'; workoutGroupId: string; conflictSession: WorkoutSession }
  | { type: 'start-rejected-stale-plan'; message: string }
  | { type: 'resume-conflict' }
  | { type: 'conflict-discarded' }
  | { type: 'completion-started' }
  | { type: 'completion-finished' }
  | { type: 'forfeit-finished' }
  | { type: 'pending-set-log-recorded'; pendingSetLog: PendingSetLog }
  | { type: 'stale-plan-dismissed' }

function derivePhaseFromSessionId(sessionId: string | null): SessionPhase {
  return sessionId ? 'open' : 'idle'
}

export function createInitialWorkoutSessionMachineState(): WorkoutSessionMachineState {
  return {
    phase: 'idle',
    conflictSession: null,
    pendingGroupId: null,
    stalePlanError: null,
    pendingSetLogs: [],
    trackedSessionId: null,
    trackedServerSetCount: 0,
  }
}

export function workoutSessionMachineReducer(
  state: WorkoutSessionMachineState,
  action: WorkoutSessionMachineAction,
): WorkoutSessionMachineState {
  switch (action.type) {
    case 'session-synced': {
      const isSessionChanged = state.trackedSessionId !== action.sessionId
      const serverCountDelta = action.serverSetCount - state.trackedServerSetCount

      return {
        ...state,
        phase:
          state.phase === 'conflict' || state.phase === 'completing'
            ? state.phase
            : derivePhaseFromSessionId(action.sessionId),
        pendingSetLogs: isSessionChanged
          ? []
          : serverCountDelta > 0
            ? state.pendingSetLogs.slice(serverCountDelta)
            : state.pendingSetLogs,
        trackedSessionId: action.sessionId,
        trackedServerSetCount: action.serverSetCount,
      }
    }

    case 'start-requested':
      return {
        ...state,
        stalePlanError: null,
      }

    case 'start-succeeded':
      return {
        ...state,
        phase: 'open',
        conflictSession: null,
        pendingGroupId: null,
        stalePlanError: null,
        trackedSessionId: action.sessionId,
        trackedServerSetCount: action.serverSetCount,
      }

    case 'start-conflicted':
      return {
        ...state,
        phase: 'conflict',
        conflictSession: action.conflictSession,
        pendingGroupId: action.workoutGroupId,
        stalePlanError: null,
        trackedSessionId: action.conflictSession.id,
        trackedServerSetCount: action.conflictSession.setCount,
      }

    case 'start-rejected-stale-plan':
      return {
        ...state,
        stalePlanError: action.message,
      }

    case 'resume-conflict':
      return {
        ...state,
        phase: derivePhaseFromSessionId(state.trackedSessionId),
        conflictSession: null,
        pendingGroupId: null,
      }

    case 'conflict-discarded':
    case 'completion-finished':
    case 'forfeit-finished':
      return {
        ...state,
        phase: 'idle',
        conflictSession: null,
        pendingGroupId: null,
        pendingSetLogs: [],
        trackedSessionId: null,
        trackedServerSetCount: 0,
      }

    case 'completion-started':
      return {
        ...state,
        phase: 'completing',
      }

    case 'pending-set-log-recorded':
      return {
        ...state,
        pendingSetLogs: [...state.pendingSetLogs, action.pendingSetLog],
      }

    case 'stale-plan-dismissed':
      return {
        ...state,
        stalePlanError: null,
      }
  }
}
