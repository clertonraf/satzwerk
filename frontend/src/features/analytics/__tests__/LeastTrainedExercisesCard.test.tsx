import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import LeastTrainedExercisesCard from '../LeastTrainedExercisesCard'
import type { TopExercise } from '@/services/analyticsService'

const makeExercise = (overrides: Partial<TopExercise> = {}): TopExercise => ({
  exerciseId: 'ex-1',
  exerciseName: 'Calf Raises',
  setCount: 2,
  ...overrides,
})

describe('LeastTrainedExercisesCard', () => {
  it('renders the card title', () => {
    render(<LeastTrainedExercisesCard exercises={[]} />)
    expect(screen.getByText('Least trained exercises')).toBeInTheDocument()
  })

  it('renders the empty state when no exercises are provided', () => {
    render(<LeastTrainedExercisesCard exercises={[]} />)
    expect(screen.getByText('No sets logged yet.')).toBeInTheDocument()
  })

  it('renders exercise names and set counts', () => {
    const exercises = [
      makeExercise({ exerciseName: 'Calf Raises', setCount: 2 }),
      makeExercise({ exerciseId: 'ex-2', exerciseName: 'Hip Thrust', setCount: 1 }),
    ]
    render(<LeastTrainedExercisesCard exercises={exercises} />)
    expect(screen.getByText('Calf Raises')).toBeInTheDocument()
    expect(screen.getByText('2 sets')).toBeInTheDocument()
    expect(screen.getByText('Hip Thrust')).toBeInTheDocument()
    expect(screen.getByText('1 set')).toBeInTheDocument()
  })

  it('does not render the empty state when exercises are provided', () => {
    render(<LeastTrainedExercisesCard exercises={[makeExercise()]} />)
    expect(screen.queryByText('No sets logged yet.')).not.toBeInTheDocument()
  })
})
