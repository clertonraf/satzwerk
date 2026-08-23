import { describe, expect, it } from 'vitest'
import type { PendingSetLog, WorkoutSession } from '@/services/sessionService'
import {
  createInitialWorkoutSessionMachineState,
  workoutSessionMachineReducer,
} from '../workoutSessionMachineReducer'

function buildSession(overrides: Partial<WorkoutSession> = {}): WorkoutSession {
  return {
    id: 'session-1',
    workoutGroupId: 'group-1',
    workoutGroupTitle: 'Push Day',
    startedAt: '2024-01-01T00:00:00Z',
    completedAt: null,
    notes: null,
    setLogs: [],
    setCount: 0,
    exerciseCount: 0,
    ...overrides,
  }
}

function makePendingSetLog(overrides: Partial<PendingSetLog> = {}): PendingSetLog {
  return {
    id: 'queued-session-1-exercise-1-1-123',
    exerciseId: 'exercise-1',
    setNumber: 1,
    weight: 80,
    reps: 5,
    rir: null,
    loggedAt: '2024-01-01T00:00:00Z',
    pending: true,
    ...overrides,
  }
}

describe('workoutSessionMachineReducer', () => {
  it('enters conflict state atomically when start collides with an existing open session', () => {
    const openSession = buildSession({ id: 'existing-session', workoutGroupId: 'group-2' })

    const state = workoutSessionMachineReducer(createInitialWorkoutSessionMachineState(), {
      type: 'start-conflicted',
      workoutGroupId: 'group-1',
      conflictSession: openSession,
    })

    expect(state.phase).toBe('conflict')
    expect(state.pendingGroupId).toBe('group-1')
    expect(state.conflictSession).toEqual(openSession)
    expect(state.stalePlanError).toBeNull()
  })

  it('returns to the open phase when the user resumes the conflicting session', () => {
    const openSession = buildSession({ id: 'existing-session' })
    const conflictState = workoutSessionMachineReducer(createInitialWorkoutSessionMachineState(), {
      type: 'start-conflicted',
      workoutGroupId: 'group-1',
      conflictSession: openSession,
    })
    const syncedState = workoutSessionMachineReducer(conflictState, {
      type: 'session-synced',
      sessionId: openSession.id,
      serverSetCount: openSession.setCount,
    })

    const resumedState = workoutSessionMachineReducer(syncedState, { type: 'resume-conflict' })

    expect(resumedState.phase).toBe('open')
    expect(resumedState.conflictSession).toBeNull()
    expect(resumedState.pendingGroupId).toBeNull()
  })

  it('tracks the completing phase until the session is finished and cleared', () => {
    const openState = workoutSessionMachineReducer(createInitialWorkoutSessionMachineState(), {
      type: 'session-synced',
      sessionId: 'session-1',
      serverSetCount: 0,
    })

    const completingState = workoutSessionMachineReducer(openState, { type: 'completion-started' })
    const completedState = workoutSessionMachineReducer(completingState, { type: 'completion-finished' })

    expect(completingState.phase).toBe('completing')
    expect(completedState.phase).toBe('idle')
    expect(completedState.conflictSession).toBeNull()
    expect(completedState.pendingGroupId).toBeNull()
  })

  it('captures and dismisses stale-plan errors without disturbing the current phase', () => {
    const openState = workoutSessionMachineReducer(createInitialWorkoutSessionMachineState(), {
      type: 'session-synced',
      sessionId: 'session-1',
      serverSetCount: 0,
    })

    const stalePlanState = workoutSessionMachineReducer(openState, {
      type: 'start-rejected-stale-plan',
      message: 'Your active plan changed. Please select a group again.',
    })
    const dismissedState = workoutSessionMachineReducer(stalePlanState, { type: 'stale-plan-dismissed' })

    expect(stalePlanState.phase).toBe('open')
    expect(stalePlanState.stalePlanError).toBe('Your active plan changed. Please select a group again.')
    expect(dismissedState.phase).toBe('open')
    expect(dismissedState.stalePlanError).toBeNull()
  })

  it('reconciles pending set logs against explicit sync confirmations', () => {
    const initialState = workoutSessionMachineReducer(createInitialWorkoutSessionMachineState(), {
      type: 'session-synced',
      sessionId: 'session-1',
      serverSetCount: 0,
    })
    const withPendingLogs = workoutSessionMachineReducer(initialState, {
      type: 'pending-set-log-recorded',
      pendingSetLog: makePendingSetLog({ setNumber: 1 }),
    })
    const morePendingLogs = workoutSessionMachineReducer(withPendingLogs, {
      type: 'pending-set-log-recorded',
      pendingSetLog: makePendingSetLog({ id: 'queued-2', setNumber: 2 }),
    })

    const reconciledState = workoutSessionMachineReducer(morePendingLogs, {
      type: 'pending-set-logs-confirmed',
      pendingSetLogIds: [makePendingSetLog({ setNumber: 1 }).id],
    })

    expect(reconciledState.pendingSetLogs).toEqual([makePendingSetLog({ id: 'queued-2', setNumber: 2 })])
  })
})
