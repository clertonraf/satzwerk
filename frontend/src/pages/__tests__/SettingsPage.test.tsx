import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import axios from 'axios'
import SettingsPage from '../SettingsPage'
import { exportService } from '@/services/exportService'
import { partnerGrantsApi } from '@/services/partnerGrantsApi'
import { personalApiTokenService } from '@/services/personalApiTokenService'

vi.mock('@/services/exportService', () => ({
  exportService: {
    downloadExport: vi.fn(),
    importData: vi.fn(),
  },
}))

vi.mock('@/services/partnerGrantsApi', () => ({
  partnerGrantsApi: {
    listActiveGrants: vi.fn(),
    revokeGrant: vi.fn(),
  },
}))

vi.mock('@/services/personalApiTokenService', () => ({
  personalApiTokenService: {
    list: vi.fn(),
    create: vi.fn(),
    revoke: vi.fn(),
  },
  ALL_SCOPES: [
    'analytics:read',
    'exercises:read',
    'exercises:write',
    'measurements:read',
    'measurements:write',
    'medications:read',
    'medications:write',
    'plans:read',
    'plans:write',
    'sessions:read',
    'sessions:write',
  ],
}))

const mockDownload = exportService.downloadExport as ReturnType<typeof vi.fn>
const mockImport = exportService.importData as ReturnType<typeof vi.fn>
const mockList = personalApiTokenService.list as ReturnType<typeof vi.fn>
const mockCreate = personalApiTokenService.create as ReturnType<typeof vi.fn>
const mockRevoke = personalApiTokenService.revoke as ReturnType<typeof vi.fn>
const mockListGrants = partnerGrantsApi.listActiveGrants as ReturnType<typeof vi.fn>
const mockRevokeGrant = partnerGrantsApi.revokeGrant as ReturnType<typeof vi.fn>

const IMPORT_SUMMARY = {
  importedExercises: 5,
  importedWorkoutPlans: 2,
  importedWorkoutSessions: 10,
  importedSetLogs: 80,
  reusedExercises: 1,
  importedMedications: 3,
  reusedMedications: 1,
  importedMedicationLogs: 15,
}

const ACTIVE_TOKENS = [
  {
    id: 'token-1',
    name: 'My Script',
    scopes: ['analytics:read'],
    createdAt: '2026-01-01T00:00:00Z',
    lastUsedAt: null,
  },
  {
    id: 'token-2',
    name: 'CI Runner',
    scopes: ['exercises:read', 'sessions:read'],
    createdAt: '2026-02-01T00:00:00Z',
    lastUsedAt: '2026-08-01T00:00:00Z',
  },
]

function makeQueryClient() {
  return new QueryClient({ defaultOptions: { queries: { retry: false } } })
}

function renderPage(queryClient = makeQueryClient()) {
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <SettingsPage />
      </MemoryRouter>
    </QueryClientProvider>
  )
}

describe('SettingsPage', () => {
  beforeEach(() => {
    mockDownload.mockReset()
    mockImport.mockReset()
    mockList.mockReset()
    mockCreate.mockReset()
    mockRevoke.mockReset()
    mockListGrants.mockReset()
    mockRevokeGrant.mockReset()
    mockList.mockResolvedValue([])
    mockListGrants.mockResolvedValue([])
  })

  it('renders export button and file input', () => {
    renderPage()
    expect(screen.getByRole('button', { name: /export my data/i })).toBeInTheDocument()
    const fileInput = document.querySelector('input[type="file"]')
    expect(fileInput).toBeInTheDocument()
  })

  it('does not show Body Measurements card', () => {
    renderPage()
    expect(screen.queryByText(/body measurements/i)).not.toBeInTheDocument()
  })

  it('does not show Medications card', () => {
    renderPage()
    expect(screen.queryByText(/view medications/i)).not.toBeInTheDocument()
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
    expect(await screen.findByText(/3 medications imported/i)).toBeInTheDocument()
    expect(await screen.findByText(/15 medication logs/i)).toBeInTheDocument()
  })

  it('clears previous error and summary when a new file is selected', async () => {
    const user = userEvent.setup()
    mockImport.mockRejectedValueOnce(new Error('Server error'))
    const file = new File(['{"version":1}'], 'test.json', { type: 'application/json' })

    renderPage()
    const input = document.querySelector('input[type="file"]') as HTMLInputElement
    await user.upload(input, file)
    await user.click(await screen.findByRole('button', { name: /confirm/i }))
    expect(await screen.findByText(/failed to import/i)).toBeInTheDocument()

    const file2 = new File(['{"version":1}'], 'test2.json', { type: 'application/json' })
    await user.upload(input, file2)
    expect(screen.queryByText(/failed to import/i)).not.toBeInTheDocument()
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

  // ── Personal API Tokens ───────────────────────────────────────────────────

  describe('Personal API Tokens section', () => {
    it('renders the token section heading and create button', async () => {
      mockList.mockResolvedValueOnce([])
      renderPage()
      expect(screen.getByText(/personal api tokens/i)).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /create token/i })).toBeInTheDocument()
    })

    it('shows empty state when no tokens exist', async () => {
      mockList.mockResolvedValueOnce([])
      renderPage()
      expect(await screen.findByText(/no active tokens/i)).toBeInTheDocument()
    })

    it('renders active token list with names and scope badges', async () => {
      mockList.mockResolvedValueOnce(ACTIVE_TOKENS)
      renderPage()
      expect(await screen.findByText('My Script')).toBeInTheDocument()
      expect(await screen.findByText('CI Runner')).toBeInTheDocument()
      expect(await screen.findByText('analytics:read')).toBeInTheDocument()
      expect(await screen.findByText('exercises:read')).toBeInTheDocument()
    })

    it('does not expose token value in the list', async () => {
      mockList.mockResolvedValueOnce(ACTIVE_TOKENS)
      renderPage()
      await screen.findByText('My Script')
      expect(screen.queryByText(/satzwerk_/)).not.toBeInTheDocument()
    })

    it('opens create dialog when Create token is clicked', async () => {
      const user = userEvent.setup()
      mockList.mockResolvedValueOnce([])
      renderPage()
      await user.click(screen.getByRole('button', { name: /create token/i }))
      expect(await screen.findByRole('dialog')).toBeInTheDocument()
      expect(screen.getByLabelText(/token name/i)).toBeInTheDocument()
      expect(screen.getByText('analytics:read')).toBeInTheDocument()
    })

    it('displays one-time token value after successful creation', async () => {
      const user = userEvent.setup()
      mockList.mockResolvedValueOnce([])
      mockCreate.mockResolvedValueOnce({
        id: 'new-token-id',
        name: 'Test Token',
        scopes: ['analytics:read'],
        createdAt: '2026-08-22T00:00:00Z',
        lastUsedAt: null,
        token: 'satzwerk_abcdef1234567890abcdef1234567890',
      })
      mockList.mockResolvedValueOnce([])
      renderPage()

      await user.click(screen.getByRole('button', { name: /create token/i }))
      const nameInput = await screen.findByLabelText(/token name/i)
      await user.type(nameInput, 'Test Token')
      await user.click(screen.getByLabelText('analytics:read'))
      await user.click(screen.getByRole('button', { name: /^create token$/i }))

      expect(await screen.findByText('satzwerk_abcdef1234567890abcdef1234567890')).toBeInTheDocument()
      expect(screen.getByText(/copy this token now/i)).toBeInTheDocument()
    })

    it('shows revoke confirmation dialog when Revoke is clicked', async () => {
      const user = userEvent.setup()
      mockList.mockResolvedValueOnce(ACTIVE_TOKENS)
      renderPage()

      const revokeButtons = await screen.findAllByRole('button', { name: /revoke token/i })
      await user.click(revokeButtons[0])

      expect(await screen.findByText(/this token will stop working immediately/i)).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /confirm revoke/i })).toBeInTheDocument()
    })

    it('calls revoke service and refreshes list on confirm', async () => {
      const user = userEvent.setup()
      mockList.mockResolvedValueOnce(ACTIVE_TOKENS)
      mockRevoke.mockResolvedValueOnce(undefined)
      mockList.mockResolvedValueOnce([ACTIVE_TOKENS[1]])
      renderPage()

      const revokeButtons = await screen.findAllByRole('button', { name: /revoke token/i })
      await user.click(revokeButtons[0])
      await user.click(await screen.findByRole('button', { name: /confirm revoke/i }))

      await waitFor(() => expect(mockRevoke).toHaveBeenCalledWith('token-1'))
    })

    it('validates that name is required before creating', async () => {
      const user = userEvent.setup()
      mockList.mockResolvedValueOnce([])
      renderPage()

      await user.click(screen.getByRole('button', { name: /create token/i }))
      await screen.findByRole('dialog')
      await user.click(screen.getByRole('button', { name: /^create token$/i }))

      expect(await screen.findByText(/token name is required/i)).toBeInTheDocument()
      expect(mockCreate).not.toHaveBeenCalled()
    })

    it('validates that at least one scope is required before creating', async () => {
      const user = userEvent.setup()
      mockList.mockResolvedValueOnce([])
      renderPage()

      await user.click(screen.getByRole('button', { name: /create token/i }))
      const nameInput = await screen.findByLabelText(/token name/i)
      await user.type(nameInput, 'No Scopes Token')
      await user.click(screen.getByRole('button', { name: /^create token$/i }))

      expect(await screen.findByText(/at least one scope/i)).toBeInTheDocument()
      expect(mockCreate).not.toHaveBeenCalled()
    })
  })
})

// ── Connected Apps ────────────────────────────────────────────────────────────

describe('SettingsPage — Connected Apps', () => {
  beforeEach(() => {
    mockDownload.mockReset()
    mockImport.mockReset()
    mockList.mockReset()
    mockCreate.mockReset()
    mockRevoke.mockReset()
    mockListGrants.mockReset()
    mockRevokeGrant.mockReset()
    mockList.mockResolvedValue([])
    mockListGrants.mockResolvedValue([])
  })

  it('shows "No connected apps" when the grant list is empty', async () => {
    renderPage()
    expect(await screen.findByText(/no connected apps/i)).toBeInTheDocument()
  })

  it('renders active grants with app name, scopes, and revoke button', async () => {
    mockListGrants.mockResolvedValue([
      {
        grantId: 'grant-1',
        appId: 'app-1',
        appName: 'My Partner App',
        grantedScopes: 'exercises:read',
        grantedAt: '2024-01-01T00:00:00Z',
      },
    ])
    renderPage()
    expect(await screen.findByText('My Partner App')).toBeInTheDocument()
    expect(screen.getByText('exercises:read')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /revoke access for my partner app/i })).toBeInTheDocument()
  })

  it('calls revokeGrant and refreshes list on revoke', async () => {
    const user = userEvent.setup()
    mockListGrants.mockResolvedValue([
      {
        grantId: 'grant-1',
        appId: 'app-1',
        appName: 'My Partner App',
        grantedScopes: 'exercises:read',
        grantedAt: '2024-01-01T00:00:00Z',
      },
    ])
    mockRevokeGrant.mockResolvedValue(undefined)

    renderPage()
    await user.click(await screen.findByRole('button', { name: /revoke access for my partner app/i }))

    await waitFor(() => expect(mockRevokeGrant).toHaveBeenCalledWith('grant-1'))
  })

  it('shows error message when revoke fails', async () => {
    const user = userEvent.setup()
    mockListGrants.mockResolvedValue([
      {
        grantId: 'grant-1',
        appId: 'app-1',
        appName: 'Broken App',
        grantedScopes: 'plans:read',
        grantedAt: '2024-01-01T00:00:00Z',
      },
    ])
    mockRevokeGrant.mockRejectedValue(new Error('Network error'))

    renderPage()
    await user.click(await screen.findByRole('button', { name: /revoke access for broken app/i }))

    expect(await screen.findByText(/failed to revoke access/i)).toBeInTheDocument()
  })
})
