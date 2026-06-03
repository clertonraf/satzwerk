import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import ContributionHeatmap from '../ContributionHeatmap'

const FROM = '2025-10-01'
const TO = '2026-01-31'

const makeEntries = (n: number) =>
  Array.from({ length: n }, (_, i) => ({
    date: `2026-01-${String(i + 1).padStart(2, '0')}`,
    count: i,
    intensity: Math.min(10, Math.floor(i / 4)),
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

  it('SVG fills its container width to be fully responsive', () => {
    render(<ContributionHeatmap entries={makeEntries(7)} from={FROM} to={TO} />)
    const svg = document.querySelector('svg') as SVGSVGElement | null
    // width="100%" makes the SVG fill the container so it scales proportionally
    // on all screen sizes without adding a vertical scrollbar.
    expect(svg?.getAttribute('width')).toBe('100%')
  })

  it('SVG has no inline minWidth that would force horizontal overflow', () => {
    render(<ContributionHeatmap entries={makeEntries(7)} from={FROM} to={TO} />)
    const svg = document.querySelector('svg') as SVGSVGElement | null
    expect(svg?.style.minWidth).toBeFalsy()
  })

  it('renders all month labels visible in the date range', () => {
    render(<ContributionHeatmap entries={makeEntries(7)} from={FROM} to={TO} />)
    // Sep, Oct, Nov, Dec, Jan are all visible in the Oct–Jan range
    const texts = Array.from(document.querySelectorAll('text'))
    const labels = texts.map((t) => t.textContent)
    expect(labels).toContain('Oct')
    expect(labels).toContain('Nov')
    expect(labels).toContain('Dec')
    expect(labels).toContain('Jan')
  })

  it('month label text uses SVG fontSize attribute so it scales with the grid', () => {
    render(<ContributionHeatmap entries={makeEntries(7)} from={FROM} to={TO} />)
    const firstText = document.querySelector('text')
    // fontSize must be set as an SVG attribute (not CSS class) so it scales
    // proportionally when the SVG is displayed at different sizes.
    expect(firstText?.getAttribute('font-size')).toBeTruthy()
  })

  it('applies a colour for intensity level 10 (37+ sets)', () => {
    const highEntry = [{ date: '2026-01-01', count: 40, intensity: 10 }]
    render(<ContributionHeatmap entries={highEntry} from='2026-01-01' to='2026-01-07' />)
    const rects = Array.from(document.querySelectorAll('rect'))
    const activeRect = rects.find((r) => r.getAttribute('fill') !== '#1e293b')
    // intensity 10 must map to a distinct non-zero-activity colour
    expect(activeRect).toBeTruthy()
  })
})

