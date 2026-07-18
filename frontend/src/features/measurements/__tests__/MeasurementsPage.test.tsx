import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, useNavigate } from 'react-router-dom'
import { measurementsApi } from '@/services/measurementsApi'
import MeasurementsPage from '../MeasurementsPage'

vi.mock('@/services/measurementsApi', () => ({
  measurementsApi: {
    getAll: vi.fn(),
    upsert: vi.fn(),
    deleteByDate: vi.fn(),
  },
}))

vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom')
  return { ...actual, useNavigate: vi.fn() }
})

const mockGetAll = vi.mocked(measurementsApi.getAll)
const mockNavigate = vi.mocked(useNavigate)

function createTestQueryClient() {
  return new QueryClient({ defaultOptions: { queries: { retry: false } } })
}

function renderPage() {
  const queryClient = createTestQueryClient()
  const navigate = vi.fn()
  mockNavigate.mockReturnValue(navigate)
  render(
    <MemoryRouter>
      <QueryClientProvider client={queryClient}>
        <MeasurementsPage />
      </QueryClientProvider>
    </MemoryRouter>,
  )
  return { navigate }
}

describe('MeasurementsPage', () => {
  beforeEach(() => {
    mockGetAll.mockReset()
  })

  it('navigates to /profile when back button is clicked', async () => {
    const user = userEvent.setup()
    mockGetAll.mockResolvedValueOnce([])

    const { navigate } = renderPage()
    await user.click(screen.getByRole('button', { name: /back to profile/i }))

    expect(navigate).toHaveBeenCalledWith('/profile')
  })

  it('renders Log, History and Charts tabs', async () => {
    mockGetAll.mockResolvedValueOnce([])

    renderPage()

    expect(await screen.findByRole('tab', { name: /log/i })).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: /history/i })).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: /charts/i })).toBeInTheDocument()
  })

  it('switches to History tab on click', async () => {
    const user = userEvent.setup()
    mockGetAll.mockResolvedValueOnce([])

    renderPage()

    const historyTab = await screen.findByRole('tab', { name: /history/i })
    await user.click(historyTab)

    expect(await screen.findByText(/no measurements logged yet/i)).toBeInTheDocument()
  })
})
