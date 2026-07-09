import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import RestTimer from '../RestTimer'

describe('RestTimer', () => {
  it('renders a start button initially', () => {
    render(<RestTimer />)

    expect(screen.getByRole('button', { name: /start rest/i })).toBeInTheDocument()
  })

  it('shows countdown after start is clicked', async () => {
    const user = userEvent.setup()

    render(<RestTimer defaultSeconds={60} />)

    await user.click(screen.getByRole('button', { name: /start rest/i }))

    expect(screen.getByText(/1:00/i)).toBeInTheDocument()
  })

  it('starts countdown at the passed defaultSeconds when started', async () => {
    const user = userEvent.setup()

    render(<RestTimer defaultSeconds={20} />)

    await user.click(screen.getByRole('button', { name: /start rest/i }))

    expect(screen.getByText(/0:20/i)).toBeInTheDocument()
  })

  it('starts countdown at 90 seconds by default', async () => {
    const user = userEvent.setup()

    render(<RestTimer />)

    await user.click(screen.getByRole('button', { name: /start rest/i }))

    expect(screen.getByText(/1:30/i)).toBeInTheDocument()
  })
})
