import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import DashboardSummaryGrid from '../DashboardSummaryGrid'
import type { DashboardSummary } from '@/services/analyticsService'

const mockData: DashboardSummary = {
  currentStreak: 5,
  longestStreak: 14,
  sessionsThisMonth: 8,
  prsThisMonth: 3,
  totalSessions: 42,
  setsThisWeek: 24,
  activePlanDays: null,
  avgSessionDurationMinutes: null,
}

describe('DashboardSummaryGrid', () => {
  it('renders all 6 stat tiles with correct values', () => {
    render(<DashboardSummaryGrid data={mockData} isLoading={false} />)

    expect(screen.getByText('5d')).toBeInTheDocument()
    expect(screen.getByText('14d')).toBeInTheDocument()
    expect(screen.getByText('8')).toBeInTheDocument()
    expect(screen.getByText('3')).toBeInTheDocument()
    expect(screen.getByText('42')).toBeInTheDocument()
    expect(screen.getByText('24')).toBeInTheDocument()
  })

  it('renders 7 skeleton tiles while loading', () => {
    render(<DashboardSummaryGrid data={undefined} isLoading={true} />)

    const skeletons = document.querySelectorAll('.animate-pulse')
    expect(skeletons.length).toBe(7)
    expect(screen.queryByText(/streak/i)).not.toBeInTheDocument()
  })

  it('renders error state when isError is true', () => {
    render(<DashboardSummaryGrid data={undefined} isLoading={false} isError={true} />)

    expect(screen.getByText(/stats unavailable/i)).toBeInTheDocument()
  })

  it('renders error state when data is undefined after loading', () => {
    render(<DashboardSummaryGrid data={undefined} isLoading={false} />)

    expect(screen.getByText(/stats unavailable/i)).toBeInTheDocument()
  })

  it('renders plan age tile when activePlanDays is present', () => {
    render(<DashboardSummaryGrid data={{ ...mockData, activePlanDays: 30 }} isLoading={false} />)

    expect(screen.getByText('30d')).toBeInTheDocument()
    expect(screen.getByText(/plan age/i)).toBeInTheDocument()
  })

  it('hides plan age tile when activePlanDays is null', () => {
    render(<DashboardSummaryGrid data={{ ...mockData, activePlanDays: null }} isLoading={false} />)

    expect(screen.queryByText(/plan age/i)).not.toBeInTheDocument()
  })

  it('renders avg session tile with duration in minutes when avgSessionDurationMinutes is set', () => {
    render(<DashboardSummaryGrid data={{ ...mockData, avgSessionDurationMinutes: 47 }} isLoading={false} />)

    expect(screen.getByText('47m')).toBeInTheDocument()
    expect(screen.getByText(/avg session/i)).toBeInTheDocument()
  })

  it('renders avg session tile with dash when avgSessionDurationMinutes is null', () => {
    render(<DashboardSummaryGrid data={{ ...mockData, avgSessionDurationMinutes: null }} isLoading={false} />)

    expect(screen.getByText('—')).toBeInTheDocument()
    expect(screen.getByText(/avg session/i)).toBeInTheDocument()
  })
})
