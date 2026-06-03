import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import ContributionHeatmap from '../ContributionHeatmap'

const FROM = '2025-10-01'
const TO = '2026-01-31'

const makeEntries = (n: number) =>
  Array.from({ length: n }, (_, i) => ({
    date: `2026-01-${String(i + 1).padStart(2, '0')}`,
    count: i,
    intensity: Math.min(4, Math.floor(i / 4)),
  }))

describe('ContributionHeatmap', () => {
  it('renders an SVG element', () => {
    render(<ContributionHeatmap entries={makeEntries(7)} from={FROM} to={TO} />)

    expect(document.querySelector('svg')).toBeInTheDocument()
  })

  it('renders one rect per day in the aligned date range', () => {
    // FROM=2025-10-01 (Wed) aligns back to Sep 29 (Mon); TO=2026-01-31 (Sat) aligns
    // forward to Feb 1 (Sun) → 126 days total (18 full weeks).
    render(<ContributionHeatmap entries={makeEntries(7)} from={FROM} to={TO} />)

    expect(document.querySelectorAll('rect').length).toBe(126)
  })

  it('renders empty state when no entries', () => {
    render(<ContributionHeatmap entries={[]} from={FROM} to={TO} />)

    expect(screen.getByText(/no activity/i)).toBeInTheDocument()
  })

  it('SVG has inline minWidth to preserve grid size for horizontal scrolling', () => {
    render(<ContributionHeatmap entries={makeEntries(7)} from={FROM} to={TO} />)
    const svg = document.querySelector('svg') as SVGSVGElement | null
    // minWidth must be set as an inline style (computed from cols * STEP) so it
    // adapts to any date range and prevents cells from shrinking below readable size.
    // Horizontal overflow is handled by the overflow-x-auto wrapper in DashboardPage.
    expect(svg?.style.minWidth).toMatch(/^\d+px$/)
  })
})

