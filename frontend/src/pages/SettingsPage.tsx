import { type ChangeEvent, useRef, useState } from 'react'
import { isAxiosError } from 'axios'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { exportService, type ImportSummaryResponse } from '@/services/exportService'
import {
  ALL_SCOPES,
  type CreatedPersonalApiToken,
  personalApiTokenService,
  type TokenScope,
} from '@/services/personalApiTokenService'
import { queryKeys } from '@/services/queryKeys'

export default function SettingsPage() {
  const fileInputRef = useRef<HTMLInputElement>(null)
  const [exportError, setExportError] = useState<string | null>(null)
  const [exportLoading, setExportLoading] = useState(false)
  const [pendingFileContent, setPendingFileContent] = useState<string | null>(null)
  const [importLoading, setImportLoading] = useState(false)
  const [importError, setImportError] = useState<string | null>(null)
  const [importSummary, setImportSummary] = useState<ImportSummaryResponse | null>(null)

  // Token management state
  const [createDialogOpen, setCreateDialogOpen] = useState(false)
  const [newTokenName, setNewTokenName] = useState('')
  const [selectedScopes, setSelectedScopes] = useState<Set<TokenScope>>(new Set())
  const [createdToken, setCreatedToken] = useState<CreatedPersonalApiToken | null>(null)
  const [revokeConfirmId, setRevokeConfirmId] = useState<string | null>(null)
  const [tokenError, setTokenError] = useState<string | null>(null)

  const queryClient = useQueryClient()

  const tokensQuery = useQuery({
    queryKey: queryKeys.tokens.all(),
    queryFn: () => personalApiTokenService.list(),
  })

  const createMutation = useMutation({
    mutationFn: (req: { name: string; scopes: TokenScope[] }) => personalApiTokenService.create(req),
    onSuccess: (created) => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.tokens.all() })
      setCreatedToken(created)
      setCreateDialogOpen(false)
      setNewTokenName('')
      setSelectedScopes(new Set())
      setTokenError(null)
    },
    onError: () => {
      setTokenError('Failed to create token. Please try again.')
    },
  })

  const revokeMutation = useMutation({
    mutationFn: (id: string) => personalApiTokenService.revoke(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.tokens.all() })
      setRevokeConfirmId(null)
    },
    onError: () => {
      setTokenError('Failed to revoke token. Please try again.')
      setRevokeConfirmId(null)
    },
  })

  function toggleScope(scope: TokenScope) {
    setSelectedScopes((prev) => {
      const next = new Set(prev)
      if (next.has(scope)) {
        next.delete(scope)
      } else {
        next.add(scope)
      }
      return next
    })
  }

  function handleOpenCreateDialog() {
    setNewTokenName('')
    setSelectedScopes(new Set())
    setTokenError(null)
    setCreateDialogOpen(true)
  }

  function handleCreateSubmit() {
    if (!newTokenName.trim()) {
      setTokenError('Token name is required.')
      return
    }
    if (selectedScopes.size === 0) {
      setTokenError('At least one scope must be selected.')
      return
    }
    setTokenError(null)
    createMutation.mutate({ name: newTokenName.trim(), scopes: [...selectedScopes] })
  }

  async function handleExport() {
    setExportError(null)
    setExportLoading(true)
    try {
      await exportService.downloadExport()
    } catch {
      setExportError('Failed to export data. Please try again.')
    } finally {
      setExportLoading(false)
    }
  }

  function handleFileChange(e: ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]
    if (!file) return

    setImportError(null)
    setImportSummary(null)

    const reader = new FileReader()
    reader.onload = (event) => {
      const content = event.target?.result as string
      setPendingFileContent(content)
    }
    reader.readAsText(file)
    if (fileInputRef.current) {
      fileInputRef.current.value = ''
    }
  }

  async function handleConfirmImport() {
    if (!pendingFileContent) return
    setImportLoading(true)
    setImportError(null)
    try {
      const summary = await exportService.importData(pendingFileContent)
      setImportSummary(summary)
      setPendingFileContent(null)
    } catch (err) {
      setPendingFileContent(null)
      if (isAxiosError(err) && err.response?.status === 409) {
        setImportError('Import failed: please finish or discard your open workout session first.')
      } else {
        setImportError('Failed to import data. Please check the file and try again.')
      }
    } finally {
      setImportLoading(false)
    }
  }

  return (
    <>
      <div className="space-y-4">
        <Card className="border-border bg-card/90 shadow-sm">
          <CardHeader>
            <CardTitle>Export Data</CardTitle>
            <CardDescription>Download all your Satzwerk data as a JSON file.</CardDescription>
          </CardHeader>
          <CardContent className="space-y-2">
            <Button onClick={() => void handleExport()} disabled={exportLoading}>
              {exportLoading ? 'Exporting…' : 'Export my data'}
            </Button>
            {exportError && <p className="text-sm text-destructive">{exportError}</p>}
          </CardContent>
        </Card>

        <Card className="border-border bg-card/90 shadow-sm">
          <CardHeader>
            <CardTitle>Import Data</CardTitle>
            <CardDescription>Restore data from a previously exported JSON file.</CardDescription>
          </CardHeader>
          <CardContent className="space-y-2">
            <input
              ref={fileInputRef}
              type="file"
              accept=".json,application/json"
              aria-label="Import data file"
              onChange={handleFileChange}
            />
            {importError && <p className="text-sm text-destructive">{importError}</p>}
            {importSummary && (
              <div className="text-sm space-y-1">
                <p className="font-medium">Import complete:</p>
                <ul className="list-disc pl-4 space-y-0.5">
                  <li>{importSummary.importedExercises} exercises imported</li>
                  <li>{importSummary.reusedExercises} exercises reused (already existed)</li>
                  <li>{importSummary.importedWorkoutPlans} workout plans imported</li>
                  <li>{importSummary.importedWorkoutSessions} workout sessions imported</li>
                  <li>{importSummary.importedSetLogs} set logs imported</li>
                  <li>{importSummary.importedMedications} medications imported</li>
                  <li>{importSummary.reusedMedications} medications reused (already existed)</li>
                  <li>{importSummary.importedMedicationLogs} medication logs imported</li>
                </ul>
              </div>
            )}
          </CardContent>
        </Card>

        <Card className="border-border bg-card/90 shadow-sm">
          <CardHeader>
            <CardTitle>Personal API Tokens</CardTitle>
            <CardDescription>
              Create tokens to access your Satzwerk data from scripts and automation. Each token has
              explicit scopes and can be revoked at any time.
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <Button onClick={handleOpenCreateDialog}>Create token</Button>
            {tokensQuery.isLoading && <p className="text-sm text-muted-foreground">Loading tokens…</p>}
            {tokensQuery.data && tokensQuery.data.length === 0 && (
              <p className="text-sm text-muted-foreground">No active tokens.</p>
            )}
            {tokensQuery.data && tokensQuery.data.length > 0 && (
              <ul className="space-y-2" aria-label="Active tokens">
                {tokensQuery.data.map((token) => (
                  <li key={token.id} className="flex items-start justify-between gap-2 rounded-md border p-3">
                    <div className="space-y-1 min-w-0">
                      <p className="font-medium text-sm truncate">{token.name}</p>
                      <div className="flex flex-wrap gap-1">
                        {token.scopes.map((scope) => (
                          <Badge key={scope} variant="secondary" className="text-xs">
                            {scope}
                          </Badge>
                        ))}
                      </div>
                      <p className="text-xs text-muted-foreground">
                        Created {new Date(token.createdAt).toLocaleDateString()}
                        {token.lastUsedAt && ` · Last used ${new Date(token.lastUsedAt).toLocaleDateString()}`}
                      </p>
                    </div>
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => setRevokeConfirmId(token.id)}
                      aria-label={`Revoke token ${token.name}`}
                    >
                      Revoke
                    </Button>
                  </li>
                ))}
              </ul>
            )}
          </CardContent>
        </Card>
      </div>

      {/* Import confirmation dialog */}
      <Dialog open={pendingFileContent !== null} onOpenChange={(open) => !open && setPendingFileContent(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Confirm Import</DialogTitle>
            <DialogDescription>
              Existing data will not be deleted — imported items will be merged. Are you sure you want to
              import?
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setPendingFileContent(null)} disabled={importLoading}>
              Cancel
            </Button>
            <Button onClick={() => void handleConfirmImport()} disabled={importLoading}>
              {importLoading ? 'Importing…' : 'Confirm'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Create token dialog */}
      <Dialog open={createDialogOpen} onOpenChange={(open) => !open && setCreateDialogOpen(false)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Create Personal API Token</DialogTitle>
            <DialogDescription>
              Give your token a name and select the scopes it can access. The raw token value is shown
              only once after creation.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4 py-2">
            <div className="space-y-1">
              <label htmlFor="token-name" className="text-sm font-medium">
                Token name
              </label>
              <Input
                id="token-name"
                placeholder="e.g. My analytics script"
                value={newTokenName}
                onChange={(e) => setNewTokenName(e.target.value)}
              />
            </div>
            <fieldset className="space-y-2">
              <legend className="text-sm font-medium">Scopes</legend>
              <div className="grid grid-cols-2 gap-1">
                {ALL_SCOPES.map((scope) => (
                  <label key={scope} className="flex items-center gap-2 text-sm cursor-pointer">
                    <input
                      type="checkbox"
                      checked={selectedScopes.has(scope)}
                      onChange={() => toggleScope(scope)}
                      className="rounded"
                    />
                    {scope}
                  </label>
                ))}
              </div>
            </fieldset>
            {tokenError && <p className="text-sm text-destructive">{tokenError}</p>}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setCreateDialogOpen(false)} disabled={createMutation.isPending}>
              Cancel
            </Button>
            <Button onClick={handleCreateSubmit} disabled={createMutation.isPending}>
              {createMutation.isPending ? 'Creating…' : 'Create token'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* One-time token display dialog */}
      <Dialog open={createdToken !== null} onOpenChange={(open) => !open && setCreatedToken(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Token created</DialogTitle>
            <DialogDescription>
              Copy this token now. It will not be shown again.
            </DialogDescription>
          </DialogHeader>
          {createdToken && (
            <div className="space-y-3 py-2">
              <code
                className="block break-all rounded bg-muted px-3 py-2 text-sm font-mono select-all"
                aria-label="Personal API token value"
              >
                {createdToken.token}
              </code>
              <div className="flex flex-wrap gap-1">
                {createdToken.scopes.map((scope) => (
                  <Badge key={scope} variant="secondary" className="text-xs">
                    {scope}
                  </Badge>
                ))}
              </div>
            </div>
          )}
          <DialogFooter>
            <Button onClick={() => setCreatedToken(null)}>Done</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Revoke confirmation dialog */}
      <Dialog open={revokeConfirmId !== null} onOpenChange={(open) => !open && setRevokeConfirmId(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Revoke token</DialogTitle>
            <DialogDescription>
              This token will stop working immediately and cannot be recovered. Are you sure?
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => setRevokeConfirmId(null)}
              disabled={revokeMutation.isPending}
            >
              Cancel
            </Button>
            <Button
              variant="outline"
              onClick={() => revokeConfirmId && revokeMutation.mutate(revokeConfirmId)}
              disabled={revokeMutation.isPending}
              aria-label="Confirm revoke"
            >
              {revokeMutation.isPending ? 'Revoking…' : 'Revoke'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  )
}
