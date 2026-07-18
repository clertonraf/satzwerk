import { type ChangeEvent, useRef, useState } from 'react'
import { isAxiosError } from 'axios'
import { useNavigate } from 'react-router-dom'
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
import { exportService, type ImportSummaryResponse } from '@/services/exportService'

export default function ProfilePage() {
  const navigate = useNavigate()
  const fileInputRef = useRef<HTMLInputElement>(null)
  const [exportError, setExportError] = useState<string | null>(null)
  const [exportLoading, setExportLoading] = useState(false)
  const [pendingFileContent, setPendingFileContent] = useState<string | null>(null)
  const [importLoading, setImportLoading] = useState(false)
  const [importError, setImportError] = useState<string | null>(null)
  const [importSummary, setImportSummary] = useState<ImportSummaryResponse | null>(null)

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
                </ul>
              </div>
            )}
          </CardContent>
        </Card>

        <Card className="border-border bg-card/90 shadow-sm">
          <CardHeader>
            <CardTitle>Body Measurements</CardTitle>
            <CardDescription>Log and track your body measurements over time.</CardDescription>
          </CardHeader>
          <CardContent>
            <Button onClick={() => navigate('/measurements')}>View Measurements</Button>
          </CardContent>
        </Card>
      </div>

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
    </>
  )
}
