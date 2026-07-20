import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { measurementsApi } from '@/services/measurementsApi'
import MeasurementsPage from '../MeasurementsPage'

vi.mock('@/services/measurementsApi', () => ({
  measurementsApi: {
    getAll: vi.fn(),
    upsert: vi.fn(),
    deleteByDate: vi.fn(),
  },
}))

const mockGetAll = vi.mocked(measurementsApi.getAll)

function createTestQueryClient() {
  return new QueryClient({ defaultOptions: { queries: { retry: false } } })
}

function renderPage() {
  const queryClient = createTestQueryClient()
  render(
    <MemoryRouter>
      <QueryClientProvider client={queryClient}>
        <MeasurementsPage />
      </QueryClientProvider>
    </MemoryRouter>,
  )
}

describe('MeasurementsPage', () => {
  beforeEach(() => {
    mockGetAll.mockReset()
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

  it('does not render a back navigation button', async () => {
    mockGetAll.mockResolvedValueOnce([])

    renderPage()

    await screen.findByRole('tab', { name: /log/i })
    expect(screen.queryByRole('button', { name: /back/i })).not.toBeInTheDocument()
  })
})
