import { describe, it, expect, afterEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import AppShell from '../AppShell'

describe('AppShell', () => {
  afterEach(() => {
    document.title = ''
    document.documentElement.classList.remove('dark')
  })

  it('shows Dashboard as page title in mobile header when on root route', () => {
    render(
      <MemoryRouter initialEntries={['/']}>
        <AppShell>
          <div>content</div>
        </AppShell>
      </MemoryRouter>
    )
    const header = screen.getByRole('banner')
    expect(header).toHaveTextContent('Dashboard')
  })

  it('shows Workouts as page title in mobile header when on /workouts/history', () => {
    render(
      <MemoryRouter initialEntries={['/workouts/history']}>
        <AppShell>
          <div>content</div>
        </AppShell>
      </MemoryRouter>
    )
    const header = screen.getByRole('banner')
    expect(header).toHaveTextContent('Workouts')
  })

  it('shows Workouts as page title in mobile header when on /workouts', () => {
    render(
      <MemoryRouter initialEntries={['/workouts']}>
        <AppShell>
          <div>content</div>
        </AppShell>
      </MemoryRouter>
    )
    const header = screen.getByRole('banner')
    expect(header).toHaveTextContent('Workouts')
  })

  it('shows Workouts as page title in mobile header when on /workouts/exercises', () => {
    render(
      <MemoryRouter initialEntries={['/workouts/exercises']}>
        <AppShell>
          <div>content</div>
        </AppShell>
      </MemoryRouter>
    )
    const header = screen.getByRole('banner')
    expect(header).toHaveTextContent('Workouts')
  })

  it('shows Health as page title in mobile header when on /health', () => {
    render(
      <MemoryRouter initialEntries={['/health']}>
        <AppShell>
          <div>content</div>
        </AppShell>
      </MemoryRouter>
    )
    const header = screen.getByRole('banner')
    expect(header).toHaveTextContent('Health')
  })

  it('shows Health as page title in mobile header when on /health/measurements', () => {
    render(
      <MemoryRouter initialEntries={['/health/measurements']}>
        <AppShell>
          <div>content</div>
        </AppShell>
      </MemoryRouter>
    )
    const header = screen.getByRole('banner')
    expect(header).toHaveTextContent('Health')
  })

  it('shows Settings as page title in mobile header when on /settings', () => {
    render(
      <MemoryRouter initialEntries={['/settings']}>
        <AppShell>
          <div>content</div>
        </AppShell>
      </MemoryRouter>
    )
    const header = screen.getByRole('banner')
    expect(header).toHaveTextContent('Settings')
  })

  it('shows Plan Builder as page title in mobile header when on /workouts/plans/:planId', () => {
    render(
      <MemoryRouter initialEntries={['/workouts/plans/abc-123']}>
        <AppShell>
          <div>content</div>
        </AppShell>
      </MemoryRouter>
    )
    const header = screen.getByRole('banner')
    expect(header).toHaveTextContent('Plan Builder')
  })

  it('sets document.title to "Dashboard | Satzwerk" on root route', () => {
    render(
      <MemoryRouter initialEntries={['/']}>
        <AppShell>
          <div>content</div>
        </AppShell>
      </MemoryRouter>
    )
    expect(document.title).toBe('Dashboard | Satzwerk')
  })

  it('sets document.title to "Workouts | Satzwerk" on /workouts/history', () => {
    render(
      <MemoryRouter initialEntries={['/workouts/history']}>
        <AppShell>
          <div>content</div>
        </AppShell>
      </MemoryRouter>
    )
    expect(document.title).toBe('Workouts | Satzwerk')
  })

  it('sets document.title to "Workouts | Satzwerk" on /workouts', () => {
    render(
      <MemoryRouter initialEntries={['/workouts']}>
        <AppShell>
          <div>content</div>
        </AppShell>
      </MemoryRouter>
    )
    expect(document.title).toBe('Workouts | Satzwerk')
  })

  it('sets document.title to "Workouts | Satzwerk" on /workouts/exercises', () => {
    render(
      <MemoryRouter initialEntries={['/workouts/exercises']}>
        <AppShell>
          <div>content</div>
        </AppShell>
      </MemoryRouter>
    )
    expect(document.title).toBe('Workouts | Satzwerk')
  })

  it('sets document.title to "Health | Satzwerk" on /health', () => {
    render(
      <MemoryRouter initialEntries={['/health']}>
        <AppShell>
          <div>content</div>
        </AppShell>
      </MemoryRouter>
    )
    expect(document.title).toBe('Health | Satzwerk')
  })

  it('sets document.title to "Health | Satzwerk" on /health/measurements', () => {
    render(
      <MemoryRouter initialEntries={['/health/measurements']}>
        <AppShell>
          <div>content</div>
        </AppShell>
      </MemoryRouter>
    )
    expect(document.title).toBe('Health | Satzwerk')
  })

  it('sets document.title to "Settings | Satzwerk" on /settings', () => {
    render(
      <MemoryRouter initialEntries={['/settings']}>
        <AppShell>
          <div>content</div>
        </AppShell>
      </MemoryRouter>
    )
    expect(document.title).toBe('Settings | Satzwerk')
  })

  it('sets document.title to "Plan Builder | Satzwerk" on /workouts/plans/:planId', () => {
    render(
      <MemoryRouter initialEntries={['/workouts/plans/abc-123']}>
        <AppShell>
          <div>content</div>
        </AppShell>
      </MemoryRouter>
    )
    expect(document.title).toBe('Plan Builder | Satzwerk')
  })

  it('resets document.title to "Satzwerk" when AppShell unmounts', () => {
    const { unmount } = render(
      <MemoryRouter initialEntries={['/workouts/history']}>
        <AppShell>
          <div>content</div>
        </AppShell>
      </MemoryRouter>
    )
    expect(document.title).toBe('Workouts | Satzwerk')
    unmount()
    expect(document.title).toBe('Satzwerk')
  })

  it('renders mobile bottom navigation with Workouts, Health, and Settings items', () => {
    render(
      <MemoryRouter>
        <AppShell>
          <div>content</div>
        </AppShell>
      </MemoryRouter>
    )
    const mobileNav = screen.getByRole('navigation', { name: /main navigation/i })
    expect(mobileNav).toHaveTextContent('Workouts')
    expect(mobileNav).toHaveTextContent('Health')
    expect(mobileNav).toHaveTextContent('Settings')
  })

  it('mobile bottom navigation does not show Plans, Exercises, or Profile items', () => {
    render(
      <MemoryRouter>
        <AppShell>
          <div>content</div>
        </AppShell>
      </MemoryRouter>
    )
    const mobileNav = screen.getByRole('navigation', { name: /main navigation/i })
    expect(mobileNav).not.toHaveTextContent('Plans')
    expect(mobileNav).not.toHaveTextContent('Exercises')
    expect(mobileNav).not.toHaveTextContent('Profile')
    expect(mobileNav).not.toHaveTextContent('History')
  })

  it('renders children', () => {
    render(
      <MemoryRouter>
        <AppShell>
          <div>test content</div>
        </AppShell>
      </MemoryRouter>
    )
    expect(screen.getByText('test content')).toBeInTheDocument()
  })

  it('bottom nav is hidden on md screens via md:hidden class', () => {
    render(
      <MemoryRouter>
        <AppShell>
          <div>content</div>
        </AppShell>
      </MemoryRouter>
    )
    const mobileNav = screen.getByRole('navigation', { name: /main navigation/i })
    expect(mobileNav.className).toMatch(/md:hidden/)
  })

  it('content area has min-w-0 to prevent flex overflow on mobile', () => {
    const { container } = render(
      <MemoryRouter>
        <AppShell>
          <div>content</div>
        </AppShell>
      </MemoryRouter>
    )
    // The flex content column must have min-w-0 so that oversized children
    // (e.g. the 689px heatmap SVG) are clipped by overflow-x-auto containers
    // instead of expanding the page width on mobile.
    const contentCol = container.querySelector('.flex.min-w-0.flex-1.flex-col')
    expect(contentCol).toBeInTheDocument()
  })

  it('desktop sidebar is hidden on mobile via hidden md:flex classes', () => {
    const { container } = render(
      <MemoryRouter>
        <AppShell>
          <div>content</div>
        </AppShell>
      </MemoryRouter>
    )
    const sidebar = container.querySelector('aside')
    expect(sidebar?.className).toMatch(/hidden/)
    expect(sidebar?.className).toMatch(/md:flex/)
  })

  it.each([
    { path: '/history', label: 'Workouts' },
    { path: '/plans', label: 'Workouts' },
    { path: '/exercises', label: 'Workouts' },
    { path: '/medications', label: 'Health' },
    { path: '/measurements', label: 'Health' },
    { path: '/profile', label: 'Settings' },
  ])('sets document.title to "$label | Satzwerk" on legacy redirect path $path', ({ path, label }) => {
    render(
      <MemoryRouter initialEntries={[path]}>
        <AppShell>
          <div>content</div>
        </AppShell>
      </MemoryRouter>
    )
    expect(document.title).toBe(`${label} | Satzwerk`)
  })
})
