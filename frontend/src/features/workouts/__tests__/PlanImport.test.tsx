import { afterEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import PlansPage from '../PlansPage'
import { planService } from '@/services/planService'

vi.mock('@/services/planService', () => ({
  planService: {
    list: vi.fn().mockResolvedValue([]),
    importFromFile: vi.fn().mockResolvedValue({
      id: '1',
      name: 'Plan',
      source: 'IMPORTED',
      isActive: false,
      createdAt: '',
      updatedAt: '',
    }),
    create: vi.fn(),
    activate: vi.fn(),
    delete: vi.fn(),
  },
}))

function createTestQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })
}

function renderPlansPage(queryClient = createTestQueryClient()) {
  const view = render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <PlansPage />
      </MemoryRouter>
    </QueryClientProvider>
  )

  return { ...view, queryClient }
}

describe('Plan import', () => {
  afterEach(() => {
    vi.clearAllMocks()
    vi.mocked(planService.list).mockResolvedValue([])
    vi.mocked(planService.importFromFile).mockResolvedValue({
      id: '1',
      name: 'Plan',
      source: 'IMPORTED',
      isActive: false,
      createdAt: '',
      updatedAt: '',
    })
  })

  it('renders Import xlsx button on PlansPage', async () => {
    renderPlansPage()

    expect(await screen.findByRole('button', { name: /import xlsx/i })).toBeInTheDocument()
  })

  it('importFromFile is called when xlsx file is selected', async () => {
    const file = new File(['xlsx'], 'import.xlsx', {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    })
    const { container } = renderPlansPage()

    const input = container.querySelector('input[type="file"]') as HTMLInputElement
    fireEvent.change(input, { target: { files: [file] } })

    await waitFor(() => expect(planService.importFromFile).toHaveBeenCalledWith(file))
  })

  it('plan list is refreshed after successful import', async () => {
    const file = new File(['xlsx'], 'import.xlsx', {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    })
    const { container, queryClient } = renderPlansPage()
    const invalidateQueriesSpy = vi.spyOn(queryClient, 'invalidateQueries')

    const input = container.querySelector('input[type="file"]') as HTMLInputElement
    fireEvent.change(input, { target: { files: [file] } })

    await waitFor(() => expect(invalidateQueriesSpy).toHaveBeenCalledWith({ queryKey: ['plans'] }))
  })

  it('shows error message when import fails', async () => {
    const file = new File(['xlsx'], 'import.xlsx', {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    })
    vi.mocked(planService.importFromFile).mockRejectedValueOnce(new Error('Import failed'))
    const { container } = renderPlansPage()

    const input = container.querySelector('input[type="file"]') as HTMLInputElement
    fireEvent.change(input, { target: { files: [file] } })

    expect(await screen.findByText(/import failed\. check the file format\./i)).toBeInTheDocument()
  })
})
