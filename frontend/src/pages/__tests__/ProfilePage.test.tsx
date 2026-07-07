import { afterEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import axios from 'axios'
import ProfilePage from '../ProfilePage'
import { exportService } from '@/services/exportService'

vi.mock('@/services/exportService', () => ({
  exportService: {
    downloadExport: vi.fn(),
    importData: vi.fn(),
  },
}))

const mockDownload = exportService.downloadExport as ReturnType<typeof vi.fn>
const mockImport = exportService.importData as ReturnType<typeof vi.fn>

const IMPORT_SUMMARY = {
  importedExercises: 5,
  importedWorkoutPlans: 2,
  importedWorkoutSessions: 10,
  importedSetLogs: 80,
  reusedExercises: 1,
}

function renderPage() {
  return render(
    <MemoryRouter>
      <ProfilePage />
    </MemoryRouter>
  )
}

describe('ProfilePage', () => {
  afterEach(() => {
    vi.clearAllMocks()
  })

  it('renders export button and file input', () => {
    renderPage()
    expect(screen.getByRole('button', { name: /export my data/i })).toBeInTheDocument()
    const fileInput = document.querySelector('input[type="file"]')
    expect(fileInput).toBeInTheDocument()
  })

  it('export button calls downloadExport', async () => {
    const user = userEvent.setup()
    mockDownload.mockResolvedValueOnce(undefined)

    renderPage()
    await user.click(screen.getByRole('button', { name: /export my data/i }))

    await waitFor(() => expect(mockDownload).toHaveBeenCalledTimes(1))
  })

  it('shows error message when export fails', async () => {
    const user = userEvent.setup()
    mockDownload.mockRejectedValueOnce(new Error('Network error'))

    renderPage()
    await user.click(screen.getByRole('button', { name: /export my data/i }))

    expect(await screen.findByText(/failed to export/i)).toBeInTheDocument()
  })

  it('shows confirmation dialog after file selection', async () => {
    const user = userEvent.setup()
    const file = new File(['{"version":1}'], 'test.json', { type: 'application/json' })

    renderPage()
    const input = document.querySelector('input[type="file"]') as HTMLInputElement
    await user.upload(input, file)

    expect(await screen.findByRole('dialog')).toBeInTheDocument()
    expect(screen.getByText(/existing data will not be deleted/i)).toBeInTheDocument()
  })

  it('calls importData and shows summary on confirmed import', async () => {
    const user = userEvent.setup()
    mockImport.mockResolvedValueOnce(IMPORT_SUMMARY)
    const file = new File(['{"version":1}'], 'test.json', { type: 'application/json' })

    renderPage()
    const input = document.querySelector('input[type="file"]') as HTMLInputElement
    await user.upload(input, file)

    const confirmButton = await screen.findByRole('button', { name: /confirm/i })
    await user.click(confirmButton)

    await waitFor(() => expect(mockImport).toHaveBeenCalledTimes(1))
    expect(await screen.findByText(/5 exercises/i)).toBeInTheDocument()
    expect(await screen.findByText(/2 workout plans/i)).toBeInTheDocument()
  })

  it('shows 409-specific message when import fails with conflict', async () => {
    const user = userEvent.setup()
    const conflictError = new axios.AxiosError('Conflict', undefined, undefined, {}, {
      status: 409,
      data: { message: 'Open session' },
    } as import('axios').AxiosResponse)
    mockImport.mockRejectedValueOnce(conflictError)
    const file = new File(['{"version":1}'], 'test.json', { type: 'application/json' })

    renderPage()
    const input = document.querySelector('input[type="file"]') as HTMLInputElement
    await user.upload(input, file)

    const confirmButton = await screen.findByRole('button', { name: /confirm/i })
    await user.click(confirmButton)

    expect(await screen.findByText(/open workout session/i)).toBeInTheDocument()
  })

  it('shows generic error on other import failures', async () => {
    const user = userEvent.setup()
    mockImport.mockRejectedValueOnce(new Error('Server error'))
    const file = new File(['{"version":1}'], 'test.json', { type: 'application/json' })

    renderPage()
    const input = document.querySelector('input[type="file"]') as HTMLInputElement
    await user.upload(input, file)

    const confirmButton = await screen.findByRole('button', { name: /confirm/i })
    await user.click(confirmButton)

    expect(await screen.findByText(/failed to import/i)).toBeInTheDocument()
  })
})
