import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import HealthPage from '../HealthPage'

vi.mock('@/features/medications/MedicationsPage', () => ({
  default: () => <div>Medications content</div>,
}))

vi.mock('@/features/measurements/MeasurementsPage', () => ({
  default: () => <div>Measurements content</div>,
}))

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/health" element={<HealthPage />} />
        <Route path="/health/measurements" element={<HealthPage />} />
      </Routes>
    </MemoryRouter>
  )
}

describe('HealthPage', () => {
  it('shows Medications tab as active by default on /health', () => {
    renderAt('/health')
    expect(screen.getByRole('tab', { name: /medications/i })).toHaveAttribute('data-state', 'active')
  })

  it('renders Medications content when on /health', () => {
    renderAt('/health')
    expect(screen.getByText('Medications content')).toBeInTheDocument()
  })

  it('shows Measurements tab as active when on /health/measurements', () => {
    renderAt('/health/measurements')
    expect(screen.getByRole('tab', { name: /measurements/i })).toHaveAttribute('data-state', 'active')
  })

  it('renders Measurements content when on /health/measurements', () => {
    renderAt('/health/measurements')
    expect(screen.getByText('Measurements content')).toBeInTheDocument()
  })

  it('both tabs are always visible', () => {
    renderAt('/health')
    expect(screen.getByRole('tab', { name: /medications/i })).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: /measurements/i })).toBeInTheDocument()
  })

  it('clicking Measurements tab navigates to /health/measurements', async () => {
    const user = userEvent.setup()
    renderAt('/health')
    await user.click(screen.getByRole('tab', { name: /measurements/i }))
    expect(screen.getByText('Measurements content')).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: /measurements/i })).toHaveAttribute('data-state', 'active')
  })

  it('clicking Medications tab from Measurements shows Medications content', async () => {
    const user = userEvent.setup()
    renderAt('/health/measurements')
    await user.click(screen.getByRole('tab', { name: /medications/i }))
    expect(screen.getByText('Medications content')).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: /medications/i })).toHaveAttribute('data-state', 'active')
  })
})
