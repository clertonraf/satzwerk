import { afterEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import PlanBuilderPage from '../PlanBuilderPage'
import { QueryClientWrapper } from '@/test/QueryClientWrapper'
import { planService } from '@/services/planService'
import { exerciseService } from '@/services/exerciseService'

vi.mock('@/services/planService', () => ({
  planService: {
    getMetadata: vi.fn(),
    getStructure: vi.fn(),
    getAdvancedTechniques: vi.fn(),
    update: vi.fn(),
  },
  workoutGroupService: {
    create: vi.fn(),
    update: vi.fn(),
    delete: vi.fn(),
  },
  workoutExerciseService: {
    create: vi.fn(),
    update: vi.fn(),
    reorder: vi.fn(),
    delete: vi.fn(),
  },
}))

vi.mock('@/services/exerciseService', () => ({
  exerciseService: {
    list: vi.fn(),
  },
}))

describe('PlanBuilderPage', () => {
  afterEach(() => {
    vi.clearAllMocks()
  })

  it('reads workout plan metadata separately from the workout plan structure', async () => {
    vi.mocked(planService.getMetadata).mockResolvedValue({
      id: 'plan-1',
      name: 'Push Pull Legs',
      source: 'MANUAL',
      isActive: true,
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
    })
    vi.mocked(planService.getStructure).mockResolvedValue({
      groups: [
        {
          id: 'group-1',
          title: 'Push Day',
          orderIndex: 0,
          exercises: [],
        },
      ],
    })
    vi.mocked(exerciseService.list).mockResolvedValue([])
    vi.mocked(planService.getAdvancedTechniques).mockResolvedValue([])

    render(
      <QueryClientWrapper>
        <MemoryRouter initialEntries={['/workouts/plans/plan-1']}>
          <Routes>
            <Route path="/workouts/plans/:planId" element={<PlanBuilderPage />} />
          </Routes>
        </MemoryRouter>
      </QueryClientWrapper>
    )

    expect(await screen.findByText('Push Pull Legs')).toBeInTheDocument()
    expect(await screen.findByText('Push Day')).toBeInTheDocument()
    expect(planService.getMetadata).toHaveBeenCalledWith('plan-1')
    expect(planService.getStructure).toHaveBeenCalledWith('plan-1')
  })
})
