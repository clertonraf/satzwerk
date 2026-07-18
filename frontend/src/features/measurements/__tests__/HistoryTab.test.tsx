import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { MeasurementEntry } from '@/services/measurementsApi'
import { measurementsApi } from '@/services/measurementsApi'
import HistoryTab from '../HistoryTab'

vi.mock('@/services/measurementsApi', () => ({
  measurementsApi: {
    getAll: vi.fn(),
    upsert: vi.fn(),
    deleteByDate: vi.fn(),
  },
}))

const mockDelete = vi.mocked(measurementsApi.deleteByDate)

const makeEntry = (date: string, overrides: Partial<MeasurementEntry> = {}): MeasurementEntry => ({
  id: `id-${date}`,
  measurementDate: date,
  shoulders: 120.5,
  chest: null,
  weightKg: 82.3,
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

function createTestQueryClient() {
  return new QueryClient({ defaultOptions: { queries: { retry: false } } })
}

function renderHistoryTab(measurements: MeasurementEntry[]) {
  const queryClient = createTestQueryClient()
  return render(
    <QueryClientProvider client={queryClient}>
      <HistoryTab measurements={measurements} />
    </QueryClientProvider>,
  )
}

describe('HistoryTab', () => {
  beforeEach(() => {
    mockDelete.mockReset()
  })

  it('shows empty state when no measurements provided', () => {
    renderHistoryTab([])
    expect(screen.getByText(/no measurements logged yet/i)).toBeInTheDocument()
  })

  it('renders one row per measurement entry', () => {
    renderHistoryTab([makeEntry('2026-01-15'), makeEntry('2026-01-10')])
    expect(screen.getByText('2026-01-15')).toBeInTheDocument()
    expect(screen.getByText('2026-01-10')).toBeInTheDocument()
  })

  it('shows count of non-null fields per row', () => {
    renderHistoryTab([makeEntry('2026-01-15', { shoulders: 120.0, weightKg: 80.0, chest: null })])
    expect(screen.getByText(/2 fields/i)).toBeInTheDocument()
  })

  it('expands row to show field values on click', async () => {
    const user = userEvent.setup()
    renderHistoryTab([makeEntry('2026-01-15', { shoulders: 120.5, weightKg: 82.3 })])

    const rowButton = screen.getByRole('button', { name: /toggle entry for 2026-01-15/i })
    await user.click(rowButton)

    expect(screen.getByText('120.5 cm')).toBeInTheDocument()
    expect(screen.getByText('82.3 kg')).toBeInTheDocument()
  })

  it('collapses row on second click', async () => {
    const user = userEvent.setup()
    renderHistoryTab([makeEntry('2026-01-15', { shoulders: 120.5 })])

    const rowButton = screen.getByRole('button', { name: /toggle entry for 2026-01-15/i })
    await user.click(rowButton)
    expect(screen.getByText('120.5 cm')).toBeInTheDocument()

    await user.click(rowButton)
    expect(screen.queryByText('120.5 cm')).not.toBeInTheDocument()
  })

  it('calls deleteByDate when delete button clicked', async () => {
    const user = userEvent.setup()
    mockDelete.mockResolvedValueOnce(undefined)
    renderHistoryTab([makeEntry('2026-01-15')])

    const deleteBtn = screen.getByRole('button', { name: /delete entry for 2026-01-15/i })
    await user.click(deleteBtn)

    expect(mockDelete).toHaveBeenCalledWith('2026-01-15')
  })
})
