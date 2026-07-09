import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import LastSessionCard from '../LastSessionCard'
import type { WorkoutSession } from '@/services/sessionService'

const makeSession = (overrides: Partial<WorkoutSession> = {}): WorkoutSession => ({
  id: 'session-1',
  workoutGroupId: 'group-1',
  workoutGroupTitle: 'Push Day',
  startedAt: '2026-06-01T10:00:00Z',
  completedAt: '2026-06-01T11:00:00Z',
  notes: null,
  setLogs: [],
  setCount: 12,
  exerciseCount: 3,
  ...overrides,
})

describe('LastSessionCard', () => {
  it('renders the workout group title', () => {
    render(<LastSessionCard session={makeSession()} />)

    expect(screen.getByText('Push Day')).toBeInTheDocument()
  })

  it('renders the set count', () => {
    render(<LastSessionCard session={makeSession({ setCount: 18 })} />)

    expect(screen.getByText(/18 sets/i)).toBeInTheDocument()
  })

  it('calculates and renders duration when session is completed', () => {
    render(
      <LastSessionCard
        session={makeSession({
          startedAt: '2026-06-01T10:00:00Z',
          completedAt: '2026-06-01T11:15:00Z',
        })}
      />,
    )

    expect(screen.getByText(/75 min/i)).toBeInTheDocument()
  })

  it('omits duration for sessions without a completedAt', () => {
    render(<LastSessionCard session={makeSession({ completedAt: null })} />)

    expect(screen.queryByText(/min/i)).not.toBeInTheDocument()
    expect(screen.getByText(/12 sets/i)).toBeInTheDocument()
  })

  it('renders avg min per exercise when session is completed and has exercises', () => {
    render(
      <LastSessionCard
        session={makeSession({
          startedAt: '2026-06-01T10:00:00Z',
          completedAt: '2026-06-01T11:00:00Z',
          exerciseCount: 4,
        })}
      />,
    )

    // 60 min / 4 exercises = 15 min/exercise
    expect(screen.getByText(/15 min\/exercise/i)).toBeInTheDocument()
  })

  it('omits avg min per exercise when exerciseCount is 0', () => {
    render(<LastSessionCard session={makeSession({ exerciseCount: 0 })} />)

    expect(screen.queryByText(/min\/exercise/i)).not.toBeInTheDocument()
  })

  it('omits avg min per exercise when session has no completedAt', () => {
    render(<LastSessionCard session={makeSession({ completedAt: null, exerciseCount: 3 })} />)

    expect(screen.queryByText(/min\/exercise/i)).not.toBeInTheDocument()
  })
})
