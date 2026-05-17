import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import StreakCard from '../StreakCard'

describe('StreakCard', () => {
  it('displays current and longest streak', () => {
    render(<StreakCard currentStreak={7} longestStreak={21} />)

    expect(screen.getByText('7')).toBeInTheDocument()
    expect(screen.getByText('21')).toBeInTheDocument()
    expect(screen.getByText(/current streak/i)).toBeInTheDocument()
    expect(screen.getByText(/longest/i)).toBeInTheDocument()
  })

  it('shows zero streaks gracefully', () => {
    render(<StreakCard currentStreak={0} longestStreak={0} />)

    expect(screen.getAllByText('0').length).toBeGreaterThanOrEqual(2)
  })
})
