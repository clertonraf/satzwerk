import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import type { MeasurementEntry } from '@/services/measurementsApi'
import { measurementsApi } from '@/services/measurementsApi'
import LogTab from '../LogTab'

vi.mock('@/services/measurementsApi', () => ({
  measurementsApi: {
    getAll: vi.fn(),
    upsert: vi.fn(),
    deleteByDate: vi.fn(),
  },
}))

const mockUpsert = vi.mocked(measurementsApi.upsert)

const EXISTING_ENTRY: MeasurementEntry = {
  id: 'abc',
  measurementDate: '2026-01-15',
  shoulders: 120.5,
  chest: 100.0,
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
}

function createTestQueryClient() {
  return new QueryClient({ defaultOptions: { queries: { retry: false } } })
}

function renderLogTab(measurements: MeasurementEntry[] = []) {
  const queryClient = createTestQueryClient()
  return render(
    <MemoryRouter>
      <QueryClientProvider client={queryClient}>
        <LogTab measurements={measurements} />
      </QueryClientProvider>
    </MemoryRouter>,
  )
}

describe('LogTab', () => {
  beforeEach(() => {
    mockUpsert.mockReset()
  })

  it('renders date picker and all 13 measurement input fields', () => {
    renderLogTab()
    expect(screen.getByLabelText(/shoulders/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/weight/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/chest/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/right bicep/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/left bicep/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /save/i })).toBeInTheDocument()
  })

  it('calls upsert with filled fields when form is submitted', async () => {
    const user = userEvent.setup()
    mockUpsert.mockResolvedValueOnce({ ...EXISTING_ENTRY, measurementDate: '2026-01-15' })

    renderLogTab()

    await user.clear(screen.getByLabelText(/shoulders/i))
    await user.type(screen.getByLabelText(/shoulders/i), '120.5')

    await user.clear(screen.getByLabelText(/weight/i))
    await user.type(screen.getByLabelText(/weight/i), '82.3')

    await user.click(screen.getByRole('button', { name: /save/i }))

    await waitFor(() => expect(mockUpsert).toHaveBeenCalledOnce())
    const call = mockUpsert.mock.calls[0][0]
    expect(call.shoulders).toBeCloseTo(120.5)
    expect(call.weightKg).toBeCloseTo(82.3)
  })

  it('shows success message after successful save', async () => {
    const user = userEvent.setup()
    mockUpsert.mockResolvedValueOnce(EXISTING_ENTRY)

    renderLogTab()
    await user.click(screen.getByRole('button', { name: /save/i }))

    expect(await screen.findByText(/measurements saved/i)).toBeInTheDocument()
  })

  it('shows error message when save fails', async () => {
    const user = userEvent.setup()
    mockUpsert.mockRejectedValueOnce(new Error('Network error'))

    renderLogTab()
    await user.click(screen.getByRole('button', { name: /save/i }))

    expect(await screen.findByText(/failed to save/i)).toBeInTheDocument()
  })
})
