import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import ContributionHeatmap from '../ContributionHeatmap'

const FROM = '2025-10-01'
const TO = '2026-01-31'

// Mirror the backend intensityTier formula: min(10, floor((count-1)/4)+1)
const toIntensity = (count: number) => (count === 0 ? 0 : Math.min(10, Math.floor((count - 1) / 4) + 1))

const makeEntries = (n: number) =>
  Array.from({ length: n }, (_, i) => ({
    date: `2026-01-${String(i + 1).padStart(2, '0')}`,
    count: i,
    intensity: toIntensity(i),
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
    expect(svg?.getAttribute('width')).toBe('100%')
  })

  it('SVG has no inline minWidth that would force horizontal overflow', () => {
    render(<ContributionHeatmap entries={makeEntries(7)} from={FROM} to={TO} />)
    const svg = document.querySelector('svg') as SVGSVGElement | null
    expect(svg?.style.minWidth).toBeFalsy()
  })

  it('SVG has a CSS aspectRatio style so height tracks the viewBox ratio in all layout contexts', () => {
    render(<ContributionHeatmap entries={makeEntries(7)} from={FROM} to={TO} />)
    const svg = document.querySelector('svg') as SVGSVGElement | null
    expect(svg?.style.aspectRatio).toBeTruthy()
  })

  it('renders month labels for months not suppressed by gap constraint', () => {
    // FROM=2025-10-01 aligns to Sep 29 (Mon) = col 0 → "Sep" label.
    // Oct first seen at col 1 (gap 1 < MIN_LABEL_GAP_COLS=3) → suppressed entirely.
    // Nov first seen at col 5 (gap 5-0=5 from Sep) → shown. Dec, Jan follow with sufficient gaps.
    render(<ContributionHeatmap entries={makeEntries(7)} from={FROM} to={TO} />)
    const texts = Array.from(document.querySelectorAll('text'))
    const labels = texts.map((t) => t.textContent)
    expect(labels).toContain('Sep')
    expect(labels).not.toContain('Oct')
    expect(labels).toContain('Nov')
    expect(labels).toContain('Dec')
    expect(labels).toContain('Jan')
  })

  it('month label text uses SVG fontSize attribute so it scales with the grid', () => {
    render(<ContributionHeatmap entries={makeEntries(7)} from={FROM} to={TO} />)
    const firstText = document.querySelector('text')
    expect(firstText?.getAttribute('font-size')).toBeTruthy()
  })

  it('applies the tier-10 colour (#f0fdf4) for intensity level 10 (37+ sets)', () => {
    const highEntry = [{ date: '2026-01-01', count: 40, intensity: 10 }]
    render(<ContributionHeatmap entries={highEntry} from='2026-01-01' to='2026-01-07' />)
    const rects = Array.from(document.querySelectorAll('rect'))
    const activeRect = rects.find((r) => r.getAttribute('fill') === '#f0fdf4')
    expect(activeRect).toBeTruthy()
  })

  it('suppresses month label when gap to previous label is fewer than 3 columns', () => {
    // 2025-03-28 to 2025-04-17:
    // alignedStart = 2025-03-24 (Mon), alignedEnd = 2025-04-20 (Sun)
    // "Mar" → col 0 (Mar 24). "Apr" first seen at col 2 (Apr 7), gap = 2 < 3 → suppress.
    // Apr 14 = col 3: same month key as Apr 7 → already seen, skip.
    // "Apr" must never appear.
    const entries = [{ date: '2025-03-28', count: 1, intensity: 1 }]
    render(<ContributionHeatmap entries={entries} from='2025-03-28' to='2025-04-17' />)
    const texts = Array.from(document.querySelectorAll('text'))
    const labels = texts.map((t) => t.textContent)
    expect(labels).toContain('Mar')
    expect(labels).not.toContain('Apr')
  })

  it('shows all three month labels when gaps are at least 3 columns', () => {
    // FROM=2025-01-06 (Mon) = alignedStart = col 0 → "Jan".
    // Feb 3 = col 4 (gap 4 ≥ 3) → shown. Mar 3 = col 8 (gap 4 ≥ 3) → shown.
    const entries = [{ date: '2025-01-06', count: 1, intensity: 1 }]
    render(<ContributionHeatmap entries={entries} from='2025-01-06' to='2025-03-31' />)
    const texts = Array.from(document.querySelectorAll('text'))
    const labels = texts.map((t) => t.textContent)
    expect(labels).toContain('Jan')
    expect(labels).toContain('Feb')
    expect(labels).toContain('Mar')
  })
})
