import { afterEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import ExercisesPage from '../ExercisesPage'
import { QueryClientWrapper } from '@/test/QueryClientWrapper'
import { exerciseService } from '@/services/exerciseService'

vi.mock('@/services/exerciseService', () => ({
  exerciseService: {
    list: vi.fn(),
    delete: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
  },
}))

describe('ExercisesPage', () => {
  afterEach(() => {
    vi.clearAllMocks()
  })

  it('renders a list of exercises', async () => {
    vi.mocked(exerciseService.list).mockResolvedValue([
      {
        id: '1',
        name: 'Bench Press',
        muscleGroup: 'CHEST',
        description: null,
        videoUrl: null,
        equipment: null,
        createdAt: '',
        updatedAt: '',
      },
    ])

    render(
      <QueryClientWrapper>
        <MemoryRouter>
          <ExercisesPage />
        </MemoryRouter>
      </QueryClientWrapper>
    )

    expect(await screen.findByText('Bench Press')).toBeInTheDocument()
  })

  it('shows empty state when no exercises', async () => {
    vi.mocked(exerciseService.list).mockResolvedValue([])

    render(
      <QueryClientWrapper>
        <MemoryRouter>
          <ExercisesPage />
        </MemoryRouter>
      </QueryClientWrapper>
    )

    expect(await screen.findByText(/no exercises/i)).toBeInTheDocument()
  })

  it('removes exercise from list after delete', async () => {
    const user = userEvent.setup()

    vi.mocked(exerciseService.list).mockResolvedValue([
      {
        id: '1',
        name: 'Squat',
        muscleGroup: 'LEGS',
        description: null,
        videoUrl: null,
        equipment: null,
        createdAt: '',
        updatedAt: '',
      },
    ])
    vi.mocked(exerciseService.delete).mockResolvedValue(undefined)

    render(
      <QueryClientWrapper>
        <MemoryRouter>
          <ExercisesPage />
        </MemoryRouter>
      </QueryClientWrapper>
    )

    await user.click(await screen.findByRole('button', { name: /delete/i }))
    await user.click(screen.getByRole('button', { name: /confirm/i }))

    await waitFor(() => expect(exerciseService.delete).toHaveBeenCalledWith('1'))
  })
})
