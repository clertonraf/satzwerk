import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import PlanCard from '../PlanCard'

const plan = { id: '1', name: 'PPL', source: 'MANUAL', isActive: false, createdAt: '', updatedAt: '' }

describe('PlanCard', () => {
  it('renders plan name', () => {
    render(
      <MemoryRouter>
        <PlanCard plan={plan} onActivate={vi.fn()} onDelete={vi.fn()} />
      </MemoryRouter>
    )

    expect(screen.getByText('PPL')).toBeInTheDocument()
  })

  it('shows "Active" badge when plan is active', () => {
    render(
      <MemoryRouter>
        <PlanCard plan={{ ...plan, isActive: true }} onActivate={vi.fn()} onDelete={vi.fn()} />
      </MemoryRouter>
    )

    expect(screen.getByText(/active/i)).toBeInTheDocument()
  })

  it('calls onActivate when activate button clicked', async () => {
    const user = userEvent.setup()
    const onActivate = vi.fn()

    render(
      <MemoryRouter>
        <PlanCard plan={plan} onActivate={onActivate} onDelete={vi.fn()} />
      </MemoryRouter>
    )

    await user.click(screen.getByRole('button', { name: /activate/i }))

    expect(onActivate).toHaveBeenCalledWith('1')
  })

  it('shows Imported badge when plan source is IMPORTED', () => {
    render(
      <MemoryRouter>
        <PlanCard plan={{ ...plan, source: 'IMPORTED' }} onActivate={vi.fn()} onDelete={vi.fn()} />
      </MemoryRouter>
    )

    expect(screen.getByText('Imported')).toBeInTheDocument()
  })
})
