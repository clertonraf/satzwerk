import { afterEach, describe, expect, it } from 'vitest'
import { ALL_WIDGET_IDS, useDashboardPreferences } from '../dashboardPreferences'

afterEach(() => {
  useDashboardPreferences.setState({ visibleWidgets: {} })
  localStorage.removeItem('satzwerk-dashboard-prefs')
})

describe('useDashboardPreferences', () => {
  it('returns ALL_WIDGET_IDS when no preference is stored for a user', () => {
    const result = useDashboardPreferences.getState().getVisibleWidgets('user-1')
    expect(result).toEqual(ALL_WIDGET_IDS)
  })

  it('returns stored widgets after setVisibleWidgets is called', () => {
    useDashboardPreferences.getState().setVisibleWidgets('user-1', ['summary-grid'])
    const result = useDashboardPreferences.getState().getVisibleWidgets('user-1')
    expect(result).toEqual(['summary-grid'])
  })

  it('keeps preferences independent per userId', () => {
    useDashboardPreferences.getState().setVisibleWidgets('user-1', ['summary-grid'])
    useDashboardPreferences.getState().setVisibleWidgets('user-2', ['weekly-trend', 'recent-prs'])

    expect(useDashboardPreferences.getState().getVisibleWidgets('user-1')).toEqual(['summary-grid'])
    expect(useDashboardPreferences.getState().getVisibleWidgets('user-2')).toEqual(['weekly-trend', 'recent-prs'])
  })

  it('returns ALL_WIDGET_IDS for an unknown userId when another user has a preference', () => {
    useDashboardPreferences.getState().setVisibleWidgets('user-1', ['summary-grid'])

    expect(useDashboardPreferences.getState().getVisibleWidgets('unknown-user')).toEqual(ALL_WIDGET_IDS)
  })
})
