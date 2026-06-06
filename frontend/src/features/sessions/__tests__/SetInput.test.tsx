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

  it('pre-fills weight and reps when defaultWeight and defaultReps are provided', () => {
    render(<SetInput onLog={vi.fn()} setNumber={2} unit="kg" defaultWeight={80} defaultReps={5} />)

    expect(screen.getByLabelText(/weight/i)).toHaveValue('80')
    expect(screen.getByLabelText(/reps/i)).toHaveValue(5)
  })

  it('renders cancel button and calls onCancel when clicked', async () => {
    const user = userEvent.setup()
    const onCancel = vi.fn()

    render(<SetInput onLog={vi.fn()} setNumber={1} unit="kg" onCancel={onCancel} />)

    const cancelButton = screen.getByRole('button', { name: /cancel/i })
    expect(cancelButton).toBeInTheDocument()

    await user.click(cancelButton)

    expect(onCancel).toHaveBeenCalledOnce()
  })

  it('does not render cancel button when onCancel is not provided', () => {
    render(<SetInput onLog={vi.fn()} setNumber={1} unit="kg" />)

    expect(screen.queryByRole('button', { name: /cancel/i })).not.toBeInTheDocument()
  })

  it('keeps form values after submit when resetOnSubmit is false', async () => {
    const user = userEvent.setup()
    const onLog = vi.fn()

    render(<SetInput onLog={onLog} setNumber={1} unit="kg" resetOnSubmit={false} submitLabel="Save" />)

    await user.type(screen.getByLabelText(/weight/i), '90')
    await user.type(screen.getByLabelText(/reps/i), '8')
    await user.click(screen.getByRole('button', { name: /save/i }))

    await waitFor(() => expect(onLog).toHaveBeenCalled())

    expect(screen.getByLabelText(/weight/i)).toHaveValue('90')
    expect(screen.getByLabelText(/reps/i)).toHaveValue(8)
  })

  it('clears form values after submit when resetOnSubmit is true (default)', async () => {
    const user = userEvent.setup()
    const onLog = vi.fn()

    render(<SetInput onLog={onLog} setNumber={1} unit="kg" />)

    await user.type(screen.getByLabelText(/weight/i), '90')
    await user.type(screen.getByLabelText(/reps/i), '8')
    await user.click(screen.getByRole('button', { name: /log set/i }))

    await waitFor(() => expect(onLog).toHaveBeenCalled())

    expect(screen.getByLabelText(/weight/i)).toHaveValue('')
    expect(screen.getByLabelText(/reps/i)).toHaveValue(null)
  })

  it('accepts comma as decimal separator and submits correct numeric weight', async () => {
    const user = userEvent.setup()
    const onLog = vi.fn()

    render(<SetInput onLog={onLog} setNumber={1} unit="kg" />)

    await user.type(screen.getByLabelText(/weight/i), '10,5')
    await user.type(screen.getByLabelText(/reps/i), '5')
    await user.click(screen.getByRole('button', { name: /log set/i }))

    await waitFor(() =>
      expect(onLog).toHaveBeenCalledWith(
        expect.objectContaining({
          weight: 10.5,
          reps: 5,
          setNumber: 1,
        })
      )
    )
  })

  it('shows conversion hint when comma decimal separator is used', async () => {
    const user = userEvent.setup()

    render(<SetInput onLog={vi.fn()} setNumber={1} unit="kg" />)

    await user.type(screen.getByLabelText(/weight/i), '10,5')

    expect(await screen.findByText(/≈.*lb/i)).toBeInTheDocument()
  })

  it('shows validation error when a negative weight is entered', async () => {
    const user = userEvent.setup()

    render(<SetInput onLog={vi.fn()} setNumber={1} unit="kg" />)

    await user.type(screen.getByLabelText(/weight/i), '-5')
    await user.click(screen.getByRole('button', { name: /log set/i }))

    expect(await screen.findByText(/non-negative/i)).toBeInTheDocument()
  })

  it('shows validation error for malformed comma input', async () => {
    const user = userEvent.setup()

    render(<SetInput onLog={vi.fn()} setNumber={1} unit="kg" />)

    await user.type(screen.getByLabelText(/weight/i), '10,,5')
    await user.click(screen.getByRole('button', { name: /log set/i }))

    expect(await screen.findByText(/non-negative/i)).toBeInTheDocument()
  })
})
