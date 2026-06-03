import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import AdvancedTechniqueBadge from '../AdvancedTechniqueBadge'

describe('AdvancedTechniqueBadge', () => {
  it('renders nothing when technique is null', () => {
    const { container } = render(<AdvancedTechniqueBadge technique={null} />)
    expect(container).toBeEmptyDOMElement()
  })

  it('renders nothing when technique is undefined', () => {
    const { container } = render(<AdvancedTechniqueBadge technique={undefined} />)
    expect(container).toBeEmptyDOMElement()
  })

  it('renders a button with the technique label', () => {
    render(<AdvancedTechniqueBadge technique="SST" />)
    expect(screen.getByRole('button', { name: /SST/i })).toBeInTheDocument()
  })

  it.each([
    ['SST', 'SST'],
    ['REST_PAUSE', 'REST PAUSE'],
    ['GVT', 'GVT'],
    ['FST_7', 'FST-7'],
    ['GIRONDA', 'GIRONDA'],
  ])('renders technique "%s" with label "%s"', async (technique, label) => {
    render(<AdvancedTechniqueBadge technique={technique} />)
    expect(screen.getByRole('button', { name: new RegExp(label, 'i') })).toBeInTheDocument()
  })

  it('opens a popover with the description when clicked', async () => {
    const user = userEvent.setup()
    render(<AdvancedTechniqueBadge technique="GVT" />)

    await user.click(screen.getByRole('button', { name: /GVT/i }))

    expect(screen.getByText(/10 sets of 10/i)).toBeInTheDocument()
  })

  it('closes the popover when Escape is pressed', async () => {
    const user = userEvent.setup()
    render(<AdvancedTechniqueBadge technique="GVT" />)

    await user.click(screen.getByRole('button', { name: /GVT/i }))
    expect(screen.getByText(/10 sets of 10/i)).toBeInTheDocument()

    await user.keyboard('{Escape}')
    expect(screen.queryByText(/10 sets of 10/i)).not.toBeInTheDocument()
  })

  it('renders a plain non-interactive badge for an unknown technique value', () => {
    render(<AdvancedTechniqueBadge technique="UNKNOWN_FUTURE" />)

    // Unknown technique: label is shown as a span, not a button (no popover)
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
    expect(screen.getByText('UNKNOWN_FUTURE')).toBeInTheDocument()
  })

  it('can be activated by keyboard (Enter key)', async () => {
    const user = userEvent.setup()
    render(<AdvancedTechniqueBadge technique="SST" />)

    screen.getByRole('button', { name: /SST/i }).focus()
    await user.keyboard('{Enter}')

    expect(screen.getByText(/drop the load/i)).toBeInTheDocument()
  })
})
