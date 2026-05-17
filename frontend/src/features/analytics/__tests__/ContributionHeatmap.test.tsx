import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import ContributionHeatmap from '../ContributionHeatmap'

const makeEntries = (n: number) =>
  Array.from({ length: n }, (_, i) => ({
    date: `2026-01-${String(i + 1).padStart(2, '0')}`,
    count: i,
    intensity: Math.min(4, Math.floor(i / 4)),
  }))

describe('ContributionHeatmap', () => {
  it('renders an SVG element', () => {
    render(<ContributionHeatmap entries={makeEntries(7)} />)

    expect(document.querySelector('svg')).toBeInTheDocument()
  })

  it('renders one rect per entry', () => {
    const entries = makeEntries(7)

    render(<ContributionHeatmap entries={entries} />)

    expect(document.querySelectorAll('rect').length).toBeGreaterThanOrEqual(7)
  })

  it('renders empty state when no entries', () => {
    render(<ContributionHeatmap entries={[]} />)

    expect(screen.getByText(/no activity/i)).toBeInTheDocument()
  })

  it('SVG has min-w to preserve grid size for horizontal scrolling', () => {
    render(<ContributionHeatmap entries={makeEntries(7)} />)
    const svg = document.querySelector('svg')
    // The SVG must have a fixed minimum width so the grid cells remain readable.
    // Horizontal overflow is handled by the overflow-x-auto wrapper in DashboardPage,
    // not by shrinking the SVG. This test ensures the min-w class is not accidentally removed.
    // SVGAnimatedString.baseVal gives the actual class string in jsdom.
    expect(svg?.className.baseVal).toMatch(/min-w-/)
  })
})

