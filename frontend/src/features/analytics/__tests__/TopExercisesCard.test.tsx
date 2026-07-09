import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import TopExercisesCard from '../TopExercisesCard'
import type { TopExercise } from '@/services/analyticsService'

const makeExercise = (overrides: Partial<TopExercise> = {}): TopExercise => ({
  exerciseId: 'ex-1',
  exerciseName: 'Bench Press',
  setCount: 42,
  ...overrides,
})

describe('TopExercisesCard', () => {
  it('renders the card title', () => {
    render(<TopExercisesCard exercises={[]} />)

    expect(screen.getByText('Most trained exercises')).toBeInTheDocument()
  })

  it('renders the empty state when no exercises are provided', () => {
    render(<TopExercisesCard exercises={[]} />)

    expect(screen.getByText('No sets logged yet.')).toBeInTheDocument()
  })

  it('renders exercise names and set counts', () => {
    const exercises = [
      makeExercise({ exerciseName: 'Bench Press', setCount: 42 }),
      makeExercise({ exerciseId: 'ex-2', exerciseName: 'Squat', setCount: 35 }),
    ]

    render(<TopExercisesCard exercises={exercises} />)

    expect(screen.getByText('Bench Press')).toBeInTheDocument()
    expect(screen.getByText('42 sets')).toBeInTheDocument()
    expect(screen.getByText('Squat')).toBeInTheDocument()
    expect(screen.getByText('35 sets')).toBeInTheDocument()
  })

  it('does not render the empty state when exercises are provided', () => {
    render(<TopExercisesCard exercises={[makeExercise()]} />)

    expect(screen.queryByText('No sets logged yet.')).not.toBeInTheDocument()
  })
})
