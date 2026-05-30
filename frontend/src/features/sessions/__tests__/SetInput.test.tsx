import { describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import SetInput from '../SetInput'

describe('SetInput', () => {
  it('renders weight and reps inputs', () => {
    render(<SetInput onLog={vi.fn()} setNumber={1} unit="kg" />)

    expect(screen.getByLabelText(/weight/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/reps/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /log set/i })).toBeInTheDocument()
  })

  it('calls onLog with weight and reps when submitted', async () => {
    const user = userEvent.setup()
    const onLog = vi.fn()

    render(<SetInput onLog={onLog} setNumber={1} unit="kg" />)

    await user.type(screen.getByLabelText(/weight/i), '80')
    await user.type(screen.getByLabelText(/reps/i), '5')
    await user.click(screen.getByRole('button', { name: /log set/i }))

    await waitFor(() =>
      expect(onLog).toHaveBeenCalledWith(
        expect.objectContaining({
          weight: 80,
          reps: 5,
          setNumber: 1,
        })
      )
    )
  })

  it('shows validation error when weight is empty', async () => {
    const user = userEvent.setup()

    render(<SetInput onLog={vi.fn()} setNumber={1} unit="kg" />)

    await user.click(screen.getByRole('button', { name: /log set/i }))

    expect(await screen.findByText(/weight is required/i)).toBeInTheDocument()
  })

  it('shows lb conversion hint when unit is kg', async () => {
    const user = userEvent.setup()

    render(<SetInput onLog={vi.fn()} setNumber={1} unit="kg" />)

    await user.type(screen.getByLabelText(/weight/i), '100')

    expect(await screen.findByText(/≈.*lb/i)).toBeInTheDocument()
  })

  it('shows kg conversion hint when unit is lb', async () => {
    const user = userEvent.setup()

    render(<SetInput onLog={vi.fn()} setNumber={1} unit="lb" />)

    await user.type(screen.getByLabelText(/weight/i), '100')

    expect(await screen.findByText(/≈.*kg/i)).toBeInTheDocument()
  })
})
