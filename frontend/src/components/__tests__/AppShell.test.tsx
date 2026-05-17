import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import AppShell from '../AppShell'

describe('AppShell', () => {
  it('renders mobile bottom navigation', () => {
    render(
      <MemoryRouter>
        <AppShell>
          <div>content</div>
        </AppShell>
      </MemoryRouter>
    )
    expect(screen.getByRole('navigation', { name: /main navigation/i })).toBeInTheDocument()
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
})

