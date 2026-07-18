import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import type { MeasurementEntry } from '@/services/measurementsApi'
import ChartsTab from '../ChartsTab'

const makeEntry = (overrides: Partial<MeasurementEntry> = {}): MeasurementEntry => ({
  id: '1',
  measurementDate: '2026-01-15',
  shoulders: null,
  chest: null,
  weightKg: null,
  rightBicep: null,
  leftBicep: null,
  rightForearm: null,
  leftForearm: null,
  abdomen: null,
  glutes: null,
  rightThigh: null,
  leftThigh: null,
  rightCalf: null,
  leftCalf: null,
  createdAt: '2026-01-15T10:00:00Z',
  updatedAt: '2026-01-15T10:00:00Z',
  ...overrides,
})

describe('ChartsTab', () => {
  it('shows empty state when no measurements provided', () => {
    render(<ChartsTab measurements={[]} />)
    expect(screen.getByText(/no measurements logged yet/i)).toBeInTheDocument()
  })

  it('shows empty state when measurements have no non-null fields', () => {
    render(<ChartsTab measurements={[makeEntry()]} />)
    expect(screen.getByText(/no measurements logged yet/i)).toBeInTheDocument()
  })

  it('renders a card for each measurement type that has data', () => {
    render(
      <ChartsTab
        measurements={[
          makeEntry({ measurementDate: '2026-01-10', weightKg: 80.0, shoulders: 120.0 }),
          makeEntry({ measurementDate: '2026-01-15', weightKg: 81.0 }),
        ]}
      />,
    )
    expect(screen.getByText(/weight/i)).toBeInTheDocument()
    expect(screen.getByText(/shoulders/i)).toBeInTheDocument()
    expect(screen.queryByText(/chest/i)).not.toBeInTheDocument()
  })

  it('does not render a chart for a field with no data across any entry', () => {
    render(<ChartsTab measurements={[makeEntry({ weightKg: 80.0 })]} />)
    expect(screen.queryByText(/chest/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/bicep/i)).not.toBeInTheDocument()
  })
})
