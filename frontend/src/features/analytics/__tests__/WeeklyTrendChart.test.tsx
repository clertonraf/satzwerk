import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import WeeklyTrendChart from '../WeeklyTrendChart'
import type { WeeklyTrendEntry } from '@/services/analyticsService'

const makeEntries = (n: number): WeeklyTrendEntry[] =>
  Array.from({ length: n }, (_, i) => ({
    week: `2026-W${String(i + 1).padStart(2, '0')}`,
    setCount: (i + 1) * 5,
    sessionCount: i + 1,
  }))

describe('WeeklyTrendChart', () => {
  it('renders an SVG when entries are provided', () => {
    render(<WeeklyTrendChart entries={makeEntries(4)} />)

    expect(document.querySelector('svg')).toBeInTheDocument()
  })

  it('renders empty state when no entries', () => {
    render(<WeeklyTrendChart entries={[]} />)

    expect(screen.getByText(/no data/i)).toBeInTheDocument()
    expect(document.querySelector('svg')).not.toBeInTheDocument()
  })

  it('renders one bar rect per entry', () => {
    render(<WeeklyTrendChart entries={makeEntries(6)} />)

    expect(document.querySelectorAll('rect').length).toBe(6)
  })

  it('renders week labels for each entry', () => {
    render(<WeeklyTrendChart entries={makeEntries(3)} />)

    expect(screen.getByText('W01')).toBeInTheDocument()
    expect(screen.getByText('W02')).toBeInTheDocument()
    expect(screen.getByText('W03')).toBeInTheDocument()
  })

  it('SVG uses width="100%" for responsive sizing', () => {
    render(<WeeklyTrendChart entries={makeEntries(4)} />)

    expect(document.querySelector('svg')?.getAttribute('width')).toBe('100%')
  })

  it('SVG has aspectRatio style to avoid intrinsic height issues', () => {
    render(<WeeklyTrendChart entries={makeEntries(4)} />)

    expect(document.querySelector('svg')?.style.aspectRatio).toBeTruthy()
  })
})
