import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import RestTimer from '../RestTimer'

describe('RestTimer', () => {
  it('renders nothing when defaultSeconds is 0', () => {
    const { container } = render(<RestTimer defaultSeconds={0} />)
    expect(container).toBeEmptyDOMElement()
  })

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

  it('uses internal 90-second default when no defaultSeconds prop is provided', async () => {
    const user = userEvent.setup()

    render(<RestTimer />)

    await user.click(screen.getByRole('button', { name: /start rest/i }))

    expect(screen.getByText(/1:30/i)).toBeInTheDocument()
  })

  it('stops timer when defaultSeconds becomes 0 and does not auto-resume later', async () => {
    const user = userEvent.setup()
    const { rerender } = render(<RestTimer defaultSeconds={20} />)

    await user.click(screen.getByRole('button', { name: /start rest/i }))
    expect(screen.getByText(/0:20/i)).toBeInTheDocument()

    rerender(<RestTimer defaultSeconds={0} />)
    expect(screen.queryByRole('button', { name: /start rest/i })).not.toBeInTheDocument()

    rerender(<RestTimer defaultSeconds={20} />)
    expect(screen.getByRole('button', { name: /start rest/i })).toBeInTheDocument()
    expect(screen.queryByText(/0:20/i)).not.toBeInTheDocument()
  })

  it('shows a stop button with aria-label when timer is running', async () => {
    const user = userEvent.setup()
    render(<RestTimer defaultSeconds={60} />)
    await user.click(screen.getByRole('button', { name: /start rest/i }))
    expect(screen.getByRole('button', { name: /stop rest timer/i })).toBeInTheDocument()
  })
})
