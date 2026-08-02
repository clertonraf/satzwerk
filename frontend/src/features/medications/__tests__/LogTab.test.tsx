import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { medicationsApi } from '@/services/medicationsApi'
import type { Medication, ScheduledDose } from '../types'
import LogTab from '../LogTab'

vi.mock('@/services/medicationsApi', () => ({
  medicationsApi: {
    getAll: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    deactivate: vi.fn(),
    getToday: vi.fn(),
    logDose: vi.fn(),
    getJournal: vi.fn(),
    getAggregateHeatmap: vi.fn(),
    getPerMedicationAnalytics: vi.fn(),
  },
}))

const mockGetToday = vi.mocked(medicationsApi.getToday)
const mockGetAll = vi.mocked(medicationsApi.getAll)
const mockLogDose = vi.mocked(medicationsApi.logDose)
const mockGetJournal = vi.mocked(medicationsApi.getJournal)

const ACTIVE_MED: Medication = {
  id: 'med-1',
  name: 'Vitamin D',
  dosageAmount: 1000,
  dosageUnit: 'IU',
  frequency: { type: 'DAILY', timesPerDay: 1, times: [] },
  purpose: null,
  isActive: true,
  createdAt: '2024-01-01T00:00:00Z',
  currentStreak: 3,
}

const SCHEDULED_DOSE: ScheduledDose = {
  medication: ACTIVE_MED,
  scheduledCount: 1,
  logs: [],
}

function createTestQueryClient() {
  return new QueryClient({ defaultOptions: { queries: { retry: false } } })
}

function renderLogTab() {
  const queryClient = createTestQueryClient()
  render(
    <MemoryRouter>
      <QueryClientProvider client={queryClient}>
        <LogTab />
      </QueryClientProvider>
    </MemoryRouter>,
  )
}

describe('LogTab', () => {
  beforeEach(() => {
    mockGetToday.mockReset()
    mockGetAll.mockReset()
    mockLogDose.mockReset()
    mockGetJournal.mockReset()
    mockGetJournal.mockResolvedValue([])
  })

  it('shows empty state when no doses scheduled', async () => {
    mockGetToday.mockResolvedValue([])
    mockGetAll.mockResolvedValue([])
    renderLogTab()
    await waitFor(() =>
      expect(screen.getByText(/No doses scheduled for today/i)).toBeInTheDocument(),
    )
  })

  it('shows scheduled dose with medication name and quick log buttons', async () => {
    mockGetToday.mockResolvedValue([SCHEDULED_DOSE])
    mockGetAll.mockResolvedValue([ACTIVE_MED])
    renderLogTab()
    await waitFor(() => expect(screen.getByText('Vitamin D')).toBeInTheDocument())
    expect(screen.getByRole('button', { name: /taken/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /skip/i })).toBeInTheDocument()
  })

  it('calls logDose with taken=true when Taken button is clicked', async () => {
    const user = userEvent.setup()
    mockGetToday.mockResolvedValue([SCHEDULED_DOSE])
    mockGetAll.mockResolvedValue([ACTIVE_MED])
    mockLogDose.mockResolvedValue({
      id: 'log-1',
      medicationId: 'med-1',
      takenAt: new Date().toISOString(),
      taken: true,
      doseAmount: null,
      notes: null,
    })
    renderLogTab()
    await waitFor(() => screen.getByRole('button', { name: /taken/i }))
    await user.click(screen.getByRole('button', { name: /taken/i }))
    await waitFor(() =>
      expect(mockLogDose).toHaveBeenCalledWith(
        'med-1',
        expect.objectContaining({ taken: true }),
      ),
    )
  })

  it('shows manual log form when + Log a dose is clicked', async () => {
    const user = userEvent.setup()
    mockGetToday.mockResolvedValue([])
    mockGetAll.mockResolvedValue([ACTIVE_MED])
    renderLogTab()
    await waitFor(() => screen.getByRole('button', { name: /\+ Log a dose/i }))
    await user.click(screen.getByRole('button', { name: /\+ Log a dose/i }))
    expect(screen.getByText(/manual dose log/i)).toBeInTheDocument()
  })
})
