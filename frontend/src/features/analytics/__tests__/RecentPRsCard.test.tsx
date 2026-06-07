import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import RecentPRsCard from '../RecentPRsCard'
import type { PersonalRecord } from '@/services/analyticsService'

type PersonalRecordWithReps = PersonalRecord & {
  reps: number
}

describe('RecentPRsCard', () => {
  it('renders exercise name, weight, reps and ratio for each PR', () => {
    const mockData: PersonalRecordWithReps[] = [
      {
        exerciseId: 'ex-1',
        exerciseName: 'Bench Press',
        weightKg: 100,
        reps: 5,
        achievedAt: '2026-06-01T10:00:00Z',
      },
    ]

    render(<RecentPRsCard records={mockData} />)

    expect(screen.getByText(/Bench Press/i)).toBeInTheDocument()
    expect(screen.getByText(/100(?:\.0)? kg × 5 reps/i)).toBeInTheDocument()
    expect(screen.getByText(/ratio: 20\.0/i)).toBeInTheDocument()
  })

  it('renders correct ratio rounded to one decimal place', () => {
    const mockData: PersonalRecordWithReps[] = [
      {
        exerciseId: 'ex-2',
        exerciseName: 'Deadlift',
        weightKg: 102.5,
        reps: 5,
        achievedAt: '2026-06-01T10:00:00Z',
      },
    ]

    render(<RecentPRsCard records={mockData} />)

    expect(screen.getByText(/ratio: 20\.5/i)).toBeInTheDocument()
  })

  it('renders empty state when no personal records', () => {
    render(<RecentPRsCard records={[]} />)

    expect(screen.getByText(/no personal records yet\./i)).toBeInTheDocument()
    expect(screen.queryByText(/Bench Press/i)).not.toBeInTheDocument()
  })

  it('renders multiple personal records', () => {
    const mockData: PersonalRecordWithReps[] = [
      {
        exerciseId: 'ex-1',
        exerciseName: 'Bench Press',
        weightKg: 100,
        reps: 5,
        achievedAt: '2026-06-01T10:00:00Z',
      },
      {
        exerciseId: 'ex-2',
        exerciseName: 'Deadlift',
        weightKg: 102.5,
        reps: 5,
        achievedAt: '2026-06-02T10:00:00Z',
      },
    ]

    render(<RecentPRsCard records={mockData} />)

    expect(screen.getByText(/Bench Press/i)).toBeInTheDocument()
    expect(screen.getByText(/Deadlift/i)).toBeInTheDocument()
  })
})
