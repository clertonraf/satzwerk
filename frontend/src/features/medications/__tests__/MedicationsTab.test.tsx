import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { medicationsApi } from '@/services/medicationsApi'
import type { Medication } from '../types'
import MedicationsTab from '../MedicationsTab'

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

const mockGetAll = vi.mocked(medicationsApi.getAll)
const mockCreate = vi.mocked(medicationsApi.create)

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

function createTestQueryClient() {
  return new QueryClient({ defaultOptions: { queries: { retry: false } } })
}

function renderTab() {
  const queryClient = createTestQueryClient()
  render(
    <MemoryRouter>
      <QueryClientProvider client={queryClient}>
        <MedicationsTab />
      </QueryClientProvider>
    </MemoryRouter>,
  )
}

describe('MedicationsTab', () => {
  beforeEach(() => {
    mockGetAll.mockReset()
    mockCreate.mockReset()
  })

  it('shows loading state then medication list', async () => {
    mockGetAll.mockResolvedValue([ACTIVE_MED])
    renderTab()
    await waitFor(() => expect(screen.getByText('Vitamin D')).toBeInTheDocument())
    expect(screen.getByText(/1000 IU/)).toBeInTheDocument()
  })

  it('shows streak badge when streak > 0', async () => {
    mockGetAll.mockResolvedValue([ACTIVE_MED])
    renderTab()
    await waitFor(() => expect(screen.getByText(/🔥 5d/)).toBeInTheDocument())
  })

  it('shows empty state when no medications', async () => {
    mockGetAll.mockResolvedValue([])
    renderTab()
    await waitFor(() =>
      expect(screen.getByText(/No medications yet/i)).toBeInTheDocument(),
    )
  })

  it('opens add form when + Add Medication is clicked', async () => {
    const user = userEvent.setup()
    mockGetAll.mockResolvedValue([])
    renderTab()
    await waitFor(() => screen.getByRole('button', { name: /add medication/i }))
    await user.click(screen.getByRole('button', { name: /add medication/i }))
    expect(screen.getByLabelText(/name/i)).toBeInTheDocument()
  })

  it('submits create form with correct data', async () => {
    const user = userEvent.setup()
    mockGetAll.mockResolvedValue([])
    mockCreate.mockResolvedValue({ ...ACTIVE_MED, name: 'Magnesium', currentStreak: 0 })
    renderTab()
    await waitFor(() => screen.getByRole('button', { name: /add medication/i }))
    await user.click(screen.getByRole('button', { name: /add medication/i }))

    await user.type(screen.getByLabelText(/^name$/i), 'Magnesium')
    await user.clear(screen.getByLabelText(/dosage amount/i))
    await user.type(screen.getByLabelText(/dosage amount/i), '400')

    await user.click(screen.getByRole('button', { name: /^add medication$/i }))

    await waitFor(() =>
      expect(mockCreate).toHaveBeenCalledWith(
        expect.objectContaining({ name: 'Magnesium', dosageAmount: 400 }),
      ),
    )
  })
})
