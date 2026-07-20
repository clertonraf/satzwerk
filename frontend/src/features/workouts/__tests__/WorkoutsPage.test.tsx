import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import WorkoutsPage from '../WorkoutsPage'

vi.mock('../PlansPage', () => ({
  default: () => <div>Plans content</div>,
}))

vi.mock('../ExercisesPage', () => ({
  default: () => <div>Exercises content</div>,
}))

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/workouts" element={<WorkoutsPage />} />
        <Route path="/workouts/exercises" element={<WorkoutsPage />} />
      </Routes>
    </MemoryRouter>
  )
}

describe('WorkoutsPage', () => {
  it('shows Plans tab as active by default on /workouts', () => {
    renderAt('/workouts')
    expect(screen.getByRole('tab', { name: /plans/i })).toHaveAttribute('data-state', 'active')
  })

  it('renders Plans content when on /workouts', () => {
    renderAt('/workouts')
    expect(screen.getByText('Plans content')).toBeInTheDocument()
  })

  it('shows Exercises tab as active when on /workouts/exercises', () => {
    renderAt('/workouts/exercises')
    expect(screen.getByRole('tab', { name: /exercises/i })).toHaveAttribute('data-state', 'active')
  })

  it('renders Exercises content when on /workouts/exercises', () => {
    renderAt('/workouts/exercises')
    expect(screen.getByText('Exercises content')).toBeInTheDocument()
  })

  it('both tabs are always visible', () => {
    renderAt('/workouts')
    expect(screen.getByRole('tab', { name: /plans/i })).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: /exercises/i })).toBeInTheDocument()
  })

  it('clicking Exercises tab navigates to /workouts/exercises', async () => {
    const user = userEvent.setup()
    renderAt('/workouts')
    await user.click(screen.getByRole('tab', { name: /exercises/i }))
    expect(screen.getByText('Exercises content')).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: /exercises/i })).toHaveAttribute('data-state', 'active')
  })

  it('clicking Plans tab from Exercises shows Plans content', async () => {
    const user = userEvent.setup()
    renderAt('/workouts/exercises')
    await user.click(screen.getByRole('tab', { name: /plans/i }))
    expect(screen.getByText('Plans content')).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: /plans/i })).toHaveAttribute('data-state', 'active')
  })
})
