import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import ExerciseProgressChart from '../ExerciseProgressChart'
import type { ExerciseProgressResponse } from '@/services/analyticsService'

const makeProgress = (pointCount: number): ExerciseProgressResponse => ({
  exerciseId: 'ex-1',
  exerciseName: 'Bench Press',
  points: Array.from({ length: pointCount }, (_, i) => ({
    sessionId: `session-${i + 1}`,
    sessionDate: `2026-0${(i % 9) + 1}-01`,
    topSetWeightKg: 80 + i * 2.5,
    topSetReps: 5,
    estimatedOneRepMaxKg: 95 + i * 3,
  })),
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
})
