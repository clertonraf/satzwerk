import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import ExerciseProgressChart from '../ExerciseProgressChart'
import { formatExerciseProgressTooltipValue } from '../exerciseProgressChartTooltip'
import type { ExerciseProgressResponse } from '@/services/analyticsService'

const makeProgress = (pointCount: number): ExerciseProgressResponse => ({
  exerciseId: 'ex-1',
  exerciseName: 'Bench Press',
  points: Array.from({ length: pointCount }, (_, i) => {
    const month = String((i % 12) + 1).padStart(2, '0')
    return {
      sessionId: `session-${i + 1}`,
      sessionDate: `2026-${month}-01`,
      topSetWeightKg: 80 + i * 2.5,
      topSetReps: 5,
      estimatedOneRepMaxKg: 95 + i * 3,
    }
  }),
  recentSessions: [],
})

describe('ExerciseProgressChart', () => {
  it('renders loading state', () => {
    render(<ExerciseProgressChart progress={null} isLoading={true} />)

    expect(screen.getByText('Loading progress…')).toBeInTheDocument()
  })

  it('renders empty state when progress is null', () => {
    render(<ExerciseProgressChart progress={null} isLoading={false} />)

    expect(screen.getByText('No completed sessions for this Exercise yet.')).toBeInTheDocument()
  })

  it('renders empty state when points array is empty', () => {
    render(<ExerciseProgressChart progress={makeProgress(0)} isLoading={false} />)

    expect(screen.getByText('No completed sessions for this Exercise yet.')).toBeInTheDocument()
  })

  it('renders the card title when data is present', () => {
    render(<ExerciseProgressChart progress={makeProgress(3)} isLoading={false} />)

    expect(screen.getByText('Top set progression')).toBeInTheDocument()
  })

  it('renders an SVG chart when data is present', () => {
    render(<ExerciseProgressChart progress={makeProgress(3)} isLoading={false} />)

    expect(document.querySelector('svg')).toBeInTheDocument()
  })

  it('does not render loading text when data is present', () => {
    render(<ExerciseProgressChart progress={makeProgress(2)} isLoading={false} />)

    expect(screen.queryByText('Loading progress…')).not.toBeInTheDocument()
  })

  it('renders Estimated 1RM label when data is present', () => {
    render(<ExerciseProgressChart progress={makeProgress(3)} isLoading={false} />)

    expect(screen.getByText('Estimated 1RM')).toBeInTheDocument()
  })

  it('formats a missing Estimated 1RM tooltip value as not available', () => {
    expect(formatExerciseProgressTooltipValue(null, 'Estimated 1RM')).toEqual(['Not available', 'Estimated 1RM'])
  })

  it('formats top-set tooltip values in kg', () => {
    expect(formatExerciseProgressTooltipValue(85, 'Top set')).toEqual(['85 kg', 'Top set'])
  })
})
