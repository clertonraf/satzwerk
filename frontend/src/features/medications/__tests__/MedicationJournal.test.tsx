import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { medicationsApi } from '@/services/medicationsApi'
import type { MedicationJournalEntry } from '../types'
import MedicationJournal from '../MedicationJournal'

vi.mock('@/services/medicationsApi', () => ({
  medicationsApi: {
    getJournal: vi.fn(),
  },
}))

const mockGetJournal = vi.mocked(medicationsApi.getJournal)

function makeEntry(overrides: Partial<MedicationJournalEntry> = {}): MedicationJournalEntry {
  return {
    id: 'log-1',
    medicationId: 'med-1',
    medicationName: 'Vitamin D',
    takenAt: '2026-08-02T08:00:00Z',
    taken: true,
    doseAmount: null,
    dosageAmount: 1000,
    dosageUnit: 'MG',
    notes: null,
    ...overrides,
  }
}

function renderJournal() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <MedicationJournal />
    </QueryClientProvider>,
  )
}

describe('MedicationJournal', () => {
  beforeEach(() => {
    vi.mocked(medicationsApi.getJournal).mockReset()
  })

  it('shows empty state when no entries', async () => {
    mockGetJournal.mockResolvedValue([])
    renderJournal()
    expect(await screen.findByText(/no medication logs/i)).toBeInTheDocument()
  })

  it('renders journal entries grouped by date', async () => {
    mockGetJournal.mockResolvedValue([
      makeEntry({ taken: true, takenAt: '2026-08-02T08:00:00Z', medicationName: 'Vitamin D' }),
      makeEntry({ id: 'log-2', taken: false, takenAt: '2026-08-01T09:00:00Z', medicationName: 'Magnesium' }),
    ])
    renderJournal()
    expect(await screen.findByText('Vitamin D')).toBeInTheDocument()
    expect(screen.getByText('Magnesium')).toBeInTheDocument()
    expect(screen.getByText('Taken')).toBeInTheDocument()
    expect(screen.getByText('Skipped')).toBeInTheDocument()
  })

  it('shows medication name and dosage info', async () => {
    mockGetJournal.mockResolvedValue([makeEntry({ dosageAmount: 500, dosageUnit: 'MG' })])
    renderJournal()
    expect(await screen.findByText('Vitamin D')).toBeInTheDocument()
    expect(screen.getByText(/500 MG/)).toBeInTheDocument()
  })

  it('shows notes when present', async () => {
    mockGetJournal.mockResolvedValue([makeEntry({ notes: 'With food' })])
    renderJournal()
    expect(await screen.findByText('With food')).toBeInTheDocument()
  })

  it('shows "Load more" button and extends range on click', async () => {
    mockGetJournal.mockResolvedValue([])
    renderJournal()
    const loadMore = await screen.findByRole('button', { name: /load more/i })
    expect(loadMore).toBeInTheDocument()
    await userEvent.click(loadMore)
    expect(screen.getByText(/60 days shown/i)).toBeInTheDocument()
  })
})
