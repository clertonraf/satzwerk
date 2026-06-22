import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import SessionStartup from '../SessionStartup'
import type { WorkoutPlanDetail } from '@/services/planService'
import type { WorkoutGroupCatalogEntry } from '@/lib/domainBuilders'

const makePlan = (overrides: Partial<WorkoutPlanDetail> = {}): WorkoutPlanDetail => ({
  id: 'plan-1',
  name: 'PPL Plan',
  source: 'MANUAL',
  isActive: true,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
  groups: [
    { id: 'group-1', title: 'Push Day', orderIndex: 0, exercises: [] },
    { id: 'group-2', title: 'Pull Day', orderIndex: 1, exercises: [] },
  ],
  ...overrides,
})

function makeGroupOptions(plan: WorkoutPlanDetail): WorkoutGroupCatalogEntry[] {
  return plan.groups.map((group) => ({ group, plan }))
}

const defaultProps = {
  groupOptions: makeGroupOptions(makePlan()),
  groupStatsMap: new Map(),
  isHistoryLoading: false,
  isHistoryAvailable: true,
  startOptionsData: makePlan(),
  isCatalogLoading: false,
  isOnline: true,
  stalePlanError: null,
  isStartPending: false,
  onStart: vi.fn(),
  onPreview: vi.fn(),
}

function renderStartup(props = {}) {
  render(
    <MemoryRouter>
      <SessionStartup {...defaultProps} {...props} />
    </MemoryRouter>
  )
}

describe('SessionStartup', () => {
  it('shows loading state when catalog is loading', () => {
    renderStartup({ isCatalogLoading: true })
    expect(screen.getByText('Loading workout groups...')).toBeInTheDocument()
  })

  it('shows no-plan message when startOptionsData is null', () => {
    renderStartup({ startOptionsData: null, groupOptions: [] })
    expect(screen.getByText(/no active plan/i)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Plans' })).toBeInTheDocument()
  })

  it('shows no-groups message when groupOptions is empty', () => {
    renderStartup({ groupOptions: [] })
    expect(screen.getByText(/no workout groups found yet/i)).toBeInTheDocument()
  })

  it('renders a row per workout group', () => {
    renderStartup()
    expect(screen.getByText('Push Day')).toBeInTheDocument()
    expect(screen.getByText('Pull Day')).toBeInTheDocument()
  })

  it('calls onStart with the group id when Start is clicked', async () => {
    const onStart = vi.fn()
    renderStartup({ onStart })
    await userEvent.click(screen.getAllByRole('button', { name: 'Start' })[0])
    expect(onStart).toHaveBeenCalledWith('group-1')
  })

  it('calls onPreview when Preview is clicked', async () => {
    const onPreview = vi.fn()
    renderStartup({ onPreview })
    await userEvent.click(screen.getAllByRole('button', { name: 'Preview' })[0])
    expect(onPreview).toHaveBeenCalledWith(
      expect.objectContaining({ id: 'group-1' }),
      'PPL Plan',
    )
  })

  it('disables Start buttons when offline', () => {
    renderStartup({ isOnline: false })
    const startButtons = screen.getAllByRole('button', { name: 'Start' })
    startButtons.forEach((btn) => expect(btn).toBeDisabled())
  })

  it('shows stale plan error when provided', () => {
    renderStartup({ stalePlanError: 'Plan has changed. Please review.' })
    expect(screen.getByText('Plan has changed. Please review.')).toBeInTheDocument()
  })
})
