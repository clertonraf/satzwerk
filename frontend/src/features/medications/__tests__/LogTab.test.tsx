import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { medicationsApi } from '@/services/medicationsApi'
import { queryKeys } from '@/services/queryKeys'
import type { Medication, MedicationTodayView, ScheduledDose } from '../types'
import LogTab from '../LogTab'

vi.mock('@/services/medicationsApi', () => ({
  medicationsApi: {
    getToday: vi.fn(),
    logDose: vi.fn(),
    getJournal: vi.fn(),
  },
}))

const mockGetToday = vi.mocked(medicationsApi.getToday)
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

function makeTodayView(overrides: Partial<MedicationTodayView> = {}): MedicationTodayView {
  return {
    scheduledDoses: [],
    availableMedications: [{ id: ACTIVE_MED.id, name: ACTIVE_MED.name }],
    ...overrides,
  }
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
  return queryClient
}

describe('LogTab', () => {
  beforeEach(() => {
    mockGetToday.mockReset()
    mockLogDose.mockReset()
    mockGetJournal.mockReset()
    mockGetJournal.mockResolvedValue({ days: [] })
  })

  it('shows empty state when no doses scheduled', async () => {
    mockGetToday.mockResolvedValue(makeTodayView())
    renderLogTab()
    await waitFor(() =>
      expect(screen.getByText(/No doses scheduled for today/i)).toBeInTheDocument(),
    )
  })

  it('shows scheduled dose with medication name and quick log buttons', async () => {
    mockGetToday.mockResolvedValue(makeTodayView({ scheduledDoses: [SCHEDULED_DOSE] }))
    renderLogTab()
    await waitFor(() => expect(screen.getByText('Vitamin D')).toBeInTheDocument())
    expect(screen.getByRole('button', { name: /taken/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /skip/i })).toBeInTheDocument()
  })

  it('calls logDose with taken=true when Taken button is clicked', async () => {
    const user = userEvent.setup()
    mockGetToday.mockResolvedValue(makeTodayView({ scheduledDoses: [SCHEDULED_DOSE] }))
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
    mockGetToday.mockResolvedValue(makeTodayView())
    renderLogTab()
    await waitFor(() => screen.getByRole('button', { name: /\+ Log a dose/i }))
    await user.click(screen.getByRole('button', { name: /\+ Log a dose/i }))
    expect(screen.getByText(/manual dose log/i)).toBeInTheDocument()
  })

  it('invalidates today, medications, and journal caches after logging a dose', async () => {
    const user = userEvent.setup()
    mockGetToday.mockResolvedValue(makeTodayView({ scheduledDoses: [SCHEDULED_DOSE] }))
    mockLogDose.mockResolvedValue({
      id: 'log-1',
      medicationId: 'med-1',
      takenAt: new Date().toISOString(),
      taken: true,
      doseAmount: null,
      notes: null,
    })
    const queryClient = renderLogTab()
    const invalidateQueries = vi.spyOn(queryClient, 'invalidateQueries').mockResolvedValue()

    await waitFor(() => screen.getByRole('button', { name: /taken/i }))
    await user.click(screen.getByRole('button', { name: /taken/i }))

    await waitFor(() =>
      expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: queryKeys.medications.today() }),
    )
    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: queryKeys.medications.all() })
    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: queryKeys.medications.journal() })
  })
})
