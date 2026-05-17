import { afterEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import PlansPage from '../PlansPage'
import { QueryClientWrapper } from '@/test/QueryClientWrapper'
import { planService } from '@/services/planService'

vi.mock('@/services/planService', () => ({
  planService: {
    list: vi.fn(),
    create: vi.fn(),
    activate: vi.fn(),
    delete: vi.fn(),
  },
}))

describe('PlansPage', () => {
  afterEach(() => {
    vi.clearAllMocks()
  })

  it('renders list of plans', async () => {
    vi.mocked(planService.list).mockResolvedValue([
      { id: '1', name: 'PPL', source: 'MANUAL', isActive: true, createdAt: '', updatedAt: '' },
    ])

    render(
      <QueryClientWrapper>
        <MemoryRouter>
          <PlansPage />
        </MemoryRouter>
      </QueryClientWrapper>
    )

    expect(await screen.findByText('PPL')).toBeInTheDocument()
  })

  it('shows empty state when no plans', async () => {
    vi.mocked(planService.list).mockResolvedValue([])

    render(
      <QueryClientWrapper>
        <MemoryRouter>
          <PlansPage />
        </MemoryRouter>
      </QueryClientWrapper>
    )

    expect(await screen.findByText(/no workout plans/i)).toBeInTheDocument()
  })
})
