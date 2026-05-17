import { describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import ExerciseForm from '../ExerciseForm'

describe('ExerciseForm', () => {
  it('renders name and muscle group fields', () => {
    render(
      <MemoryRouter>
        <ExerciseForm onSubmit={vi.fn()} />
      </MemoryRouter>
    )

    expect(screen.getByLabelText(/name/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/muscle group/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /save/i })).toBeInTheDocument()
  })

  it('shows validation error when name is empty', async () => {
    const user = userEvent.setup()

    render(
      <MemoryRouter>
        <ExerciseForm onSubmit={vi.fn()} />
      </MemoryRouter>
    )

    await user.click(screen.getByRole('button', { name: /save/i }))

    expect(await screen.findByText(/name is required/i)).toBeInTheDocument()
  })

  it('calls onSubmit with form data when valid', async () => {
    const user = userEvent.setup()
    const onSubmit = vi.fn()

    render(
      <MemoryRouter>
        <ExerciseForm onSubmit={onSubmit} />
      </MemoryRouter>
    )

    await user.type(screen.getByLabelText(/name/i), 'Bench Press')
    await user.type(screen.getByLabelText(/muscle group/i), 'CHEST')
    await user.click(screen.getByRole('button', { name: /save/i }))

    await waitFor(() =>
      expect(onSubmit).toHaveBeenCalledWith(
        expect.objectContaining({
          name: 'Bench Press',
          muscleGroup: 'CHEST',
        })
      )
    )
  })
})
