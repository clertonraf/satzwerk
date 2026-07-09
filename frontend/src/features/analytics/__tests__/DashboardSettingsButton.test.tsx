import { afterEach, describe, expect, it, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import DashboardSettingsButton from '../DashboardSettingsButton'
import { ALL_WIDGET_IDS, type DashboardWidgetId } from '@/store/dashboardPreferences'

describe('DashboardSettingsButton', () => {
  afterEach(() => {
    vi.clearAllMocks()
  })

  it('renders a settings button', () => {
    render(<DashboardSettingsButton visibleWidgets={ALL_WIDGET_IDS} onToggle={vi.fn()} />)

    expect(screen.getByRole('button', { name: /dashboard settings/i })).toBeInTheDocument()
  })

  it('opens the settings popover when the button is clicked', async () => {
    render(<DashboardSettingsButton visibleWidgets={ALL_WIDGET_IDS} onToggle={vi.fn()} />)

    fireEvent.click(screen.getByRole('button', { name: /dashboard settings/i }))

    expect(await screen.findByText('Stats overview')).toBeInTheDocument()
    expect(screen.getByText('Activity heatmap')).toBeInTheDocument()
    expect(screen.getByText('Last session')).toBeInTheDocument()
    expect(screen.getByText('Recent PRs')).toBeInTheDocument()
    expect(screen.getByText('Weekly trend')).toBeInTheDocument()
  })

  it('renders checkboxes reflecting current preferences', async () => {
    const visible: DashboardWidgetId[] = ['summary-grid', 'recent-prs']
    render(<DashboardSettingsButton visibleWidgets={visible} onToggle={vi.fn()} />)

    fireEvent.click(screen.getByRole('button', { name: /dashboard settings/i }))

    expect(await screen.findByRole('checkbox', { name: /stats overview/i })).toBeChecked()
    expect(screen.getByRole('checkbox', { name: /recent prs/i })).toBeChecked()
    expect(screen.getByRole('checkbox', { name: /activity heatmap/i })).not.toBeChecked()
  })

  it('calls onToggle with widgetId and false when unchecking a checked widget', async () => {
    const onToggle = vi.fn()
    render(<DashboardSettingsButton visibleWidgets={ALL_WIDGET_IDS} onToggle={onToggle} />)

    fireEvent.click(screen.getByRole('button', { name: /dashboard settings/i }))

    const checkbox = await screen.findByRole('checkbox', { name: /last session/i })
    fireEvent.click(checkbox)

    expect(onToggle).toHaveBeenCalledWith('last-session', false)
  })
})
