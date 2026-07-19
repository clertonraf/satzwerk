import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { medicationsApi } from '@/services/medicationsApi'
import type { AdherenceHeatmap, Medication, PerMedicationAnalytics } from '../types'
import ChartsTab from '../ChartsTab'

vi.mock('@/services/medicationsApi', () => ({
  medicationsApi: {
    getAll: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    deactivate: vi.fn(),
    getToday: vi.fn(),
    logDose: vi.fn(),
    getAggregateHeatmap: vi.fn(),
    getPerMedicationAnalytics: vi.fn(),
  },
}))

vi.mock('@/features/analytics/ContributionHeatmap', () => ({
  default: ({ entries }: { entries: { date: string }[] }) => (
    <div data-testid="heatmap">{entries.length} days</div>
  ),
}))

const mockGetAll = vi.mocked(medicationsApi.getAll)
const mockGetHeatmap = vi.mocked(medicationsApi.getAggregateHeatmap)
const mockGetAnalytics = vi.mocked(medicationsApi.getPerMedicationAnalytics)

const ACTIVE_MED: Medication = {
  id: 'med-1',
  name: 'Vitamin D',
  dosageAmount: 1000,
  dosageUnit: 'IU',
  frequency: { type: 'DAILY', timesPerDay: 1, times: [] },
  purpose: null,
  isActive: true,
  createdAt: '2024-01-01T00:00:00Z',
  currentStreak: 5,
}

const AGGREGATE_HEATMAP: AdherenceHeatmap = {
  days: [
    { date: '2024-01-01', adherenceRatio: 1.0, takenCount: 1, scheduledCount: 1 },
    { date: '2024-01-02', adherenceRatio: 0.0, takenCount: 0, scheduledCount: 1 },
  ],
}

const PER_MED_ANALYTICS: PerMedicationAnalytics = {
  heatmap: AGGREGATE_HEATMAP,
  barChart: [{ period: '2024-W01', taken: 5, skipped: 2 }],
  currentStreak: 5,
}

function createTestQueryClient() {
  return new QueryClient({ defaultOptions: { queries: { retry: false } } })
}

function renderChartsTab() {
  const queryClient = createTestQueryClient()
  render(
    <MemoryRouter>
      <QueryClientProvider client={queryClient}>
        <ChartsTab />
      </QueryClientProvider>
    </MemoryRouter>,
  )
}

describe('ChartsTab', () => {
  beforeEach(() => {
    mockGetAll.mockReset()
    mockGetHeatmap.mockReset()
    mockGetAnalytics.mockReset()
  })

  it('shows aggregate heatmap when data is loaded', async () => {
    mockGetAll.mockResolvedValue([ACTIVE_MED])
    mockGetHeatmap.mockResolvedValue(AGGREGATE_HEATMAP)
    mockGetAnalytics.mockResolvedValue(PER_MED_ANALYTICS)
    renderChartsTab()
    await waitFor(() => expect(screen.getAllByTestId('heatmap')).toHaveLength(2))
    // aggregate + per-med heatmaps both rendered
    expect(screen.getAllByText(/2 days/)).toHaveLength(2)
  })

  it('shows loading state while heatmap is fetching', async () => {
    mockGetAll.mockResolvedValue([ACTIVE_MED])
    mockGetHeatmap.mockImplementation(() => new Promise(() => {}))
    mockGetAnalytics.mockImplementation(() => new Promise(() => {}))
    renderChartsTab()
    // Wait for getAll to resolve, which enables the heatmap query
    await waitFor(() => expect(mockGetAll).toHaveBeenCalled())
    // Loading indicator should be visible while heatmap is pending
    await waitFor(() => expect(screen.getAllByText(/Loading…/i).length).toBeGreaterThan(0))
  })

  it('shows no data message when heatmap is empty', async () => {
    mockGetAll.mockResolvedValue([ACTIVE_MED])
    mockGetHeatmap.mockResolvedValue({ days: [] })
    mockGetAnalytics.mockResolvedValue({ ...PER_MED_ANALYTICS, heatmap: { days: [] } })
    renderChartsTab()
    await waitFor(() => expect(screen.getAllByText(/No data yet/i).length).toBeGreaterThan(0))
  })
})
