import { type FormEvent, useState } from 'react'
import { format } from 'date-fns'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { medicationsApi } from '@/services/medicationsApi'
import { queryKeys } from '@/services/queryKeys'
import type { LogDosePayload, MedicationLog, ScheduledDose } from './types'

export default function LogTab() {
  const queryClient = useQueryClient()
  const [showManualForm, setShowManualForm] = useState(false)
  const [manualMedId, setManualMedId] = useState<string>('')
  const [manualTaken, setManualTaken] = useState<boolean>(true)
  const [manualDatetime, setManualDatetime] = useState(() => {
    // datetime-local inputs expect a local YYYY-MM-DDTHH:mm value, not UTC
    const now = new Date()
    const offset = now.getTimezoneOffset() * 60000
    return new Date(now.getTime() - offset).toISOString().slice(0, 16)
  })
  const [manualDose, setManualDose] = useState('')
  const [manualNotes, setManualNotes] = useState('')
  const [manualError, setManualError] = useState<string | null>(null)

  const { data: scheduledDoses = [], isLoading } = useQuery({
    queryKey: queryKeys.medications.today(),
    queryFn: () => medicationsApi.getToday(),
  })

  const { data: allMedications = [] } = useQuery({
    queryKey: queryKeys.medications.all(),
    queryFn: () => medicationsApi.getAll(),
  })

  const logMutation = useMutation({
    mutationFn: ({ medicationId, payload }: { medicationId: string; payload: LogDosePayload }) =>
      medicationsApi.logDose(medicationId, payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.medications.today() })
    },
  })

  const manualLogMutation = useMutation({
    mutationFn: ({ medicationId, payload }: { medicationId: string; payload: LogDosePayload }) =>
      medicationsApi.logDose(medicationId, payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.medications.today() })
      setShowManualForm(false)
      setManualMedId('')
      setManualDose('')
      setManualNotes('')
      setManualError(null)
    },
    onError: () => setManualError('Failed to log dose. Please try again.'),
  })

  function handleQuickLog(medicationId: string, taken: boolean) {
    logMutation.mutate({
      medicationId,
      payload: { takenAt: new Date().toISOString(), taken },
    })
  }

  function handleManualSubmit(e: FormEvent) {
    e.preventDefault()
    setManualError(null)
    if (!manualMedId) {
      setManualError('Please select a medication.')
      return
    }
    const payload: LogDosePayload = {
      takenAt: new Date(manualDatetime).toISOString(),
      taken: manualTaken,
      doseAmount: manualDose ? parseFloat(manualDose) : null,
      notes: manualNotes.trim() || null,
    }
    manualLogMutation.mutate({ medicationId: manualMedId, payload })
  }

  const activeMedications = allMedications.filter((m) => m.isActive)

  return (
    <div className="space-y-4">
      <p className="text-sm text-muted-foreground font-medium">{format(new Date(), 'EEEE, MMMM d, yyyy')}</p>

      <section>
        <h2 className="text-base font-semibold mb-2">Scheduled Today</h2>
        {isLoading && <p className="text-sm text-muted-foreground">Loading…</p>}
        {!isLoading && scheduledDoses.length === 0 && (
          <p className="text-sm text-muted-foreground">No doses scheduled for today.</p>
        )}
        {scheduledDoses.map((dose) => (
          <ScheduledDoseCard
            key={dose.medication.id}
            dose={dose}
            onLog={(taken) => handleQuickLog(dose.medication.id, taken)}
            isPending={logMutation.isPending}
          />
        ))}
      </section>

      <section>
        <h2 className="text-base font-semibold mb-2">Log Manually</h2>
        {!showManualForm && (
          <Button size="sm" variant="outline" onClick={() => setShowManualForm(true)}>
            + Log a dose
          </Button>
        )}
        {showManualForm && (
          <Card className="border-border bg-card/90 shadow-sm">
            <CardHeader>
              <CardTitle className="text-sm">Manual Dose Log</CardTitle>
            </CardHeader>
            <CardContent>
              <form onSubmit={handleManualSubmit} className="space-y-3">
                <div className="flex flex-col gap-1">
                  <label className="text-sm font-medium">Medication</label>
                  <Select value={manualMedId} onValueChange={setManualMedId}>
                    <SelectTrigger>
                      <SelectValue placeholder="Select medication…" />
                    </SelectTrigger>
                    <SelectContent>
                      {activeMedications.map((m) => (
                        <SelectItem key={m.id} value={m.id}>{m.name}</SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>

                <div className="flex flex-col gap-1">
                  <label className="text-sm font-medium" htmlFor="manual-datetime">Date & Time</label>
                  <Input
                    id="manual-datetime"
                    type="datetime-local"
                    value={manualDatetime}
                    onChange={(e) => setManualDatetime(e.target.value)}
                  />
                </div>

                <div className="flex gap-2">
                  <Button
                    type="button"
                    size="sm"
                    variant={manualTaken ? 'default' : 'outline'}
                    onClick={() => setManualTaken(true)}
                  >
                    Taken
                  </Button>
                  <Button
                    type="button"
                    size="sm"
                    variant={!manualTaken ? 'default' : 'outline'}
                    onClick={() => setManualTaken(false)}
                  >
                    Skipped
                  </Button>
                </div>

                <div className="flex flex-col gap-1">
                  <label className="text-sm font-medium" htmlFor="manual-dose">
                    Actual dose <span className="text-muted-foreground">(optional override)</span>
                  </label>
                  <Input
                    id="manual-dose"
                    type="number"
                    step="0.0001"
                    min="0.0001"
                    value={manualDose}
                    onChange={(e) => setManualDose(e.target.value)}
                    placeholder="—"
                  />
                </div>

                <div className="flex flex-col gap-1">
                  <label className="text-sm font-medium" htmlFor="manual-notes">
                    Notes <span className="text-muted-foreground">(optional)</span>
                  </label>
                  <Input
                    id="manual-notes"
                    value={manualNotes}
                    onChange={(e) => setManualNotes(e.target.value)}
                    placeholder="Any notes…"
                  />
                </div>

                {manualError && <p className="text-sm text-destructive">{manualError}</p>}

                <div className="flex gap-2">
                  <Button type="submit" size="sm" disabled={manualLogMutation.isPending}>
                    {manualLogMutation.isPending ? 'Saving…' : 'Log Dose'}
                  </Button>
                  <Button type="button" size="sm" variant="ghost" onClick={() => setShowManualForm(false)}>
                    Cancel
                  </Button>
                </div>
              </form>
            </CardContent>
          </Card>
        )}
      </section>
    </div>
  )
}

interface ScheduledDoseCardProps {
  dose: ScheduledDose
  onLog: (taken: boolean) => void
  isPending: boolean
}

function ScheduledDoseCard({ dose, onLog, isPending }: ScheduledDoseCardProps) {
  const { medication, logs } = dose
  const takenCount = logs.filter((l) => l.taken).length

  return (
    <Card className="border-border bg-card/90 shadow-sm mb-2">
      <CardContent className="pt-4 space-y-2">
        <div className="flex items-center justify-between gap-3">
          <div>
            <p className="font-medium">{medication.name}</p>
            <p className="text-sm text-muted-foreground">
              {medication.dosageAmount} {medication.dosageUnit} · {takenCount}/{dose.scheduledCount} logged
            </p>
          </div>
          <div className="flex gap-2">
            <Button size="sm" onClick={() => onLog(true)} disabled={isPending}>✓ Taken</Button>
            <Button size="sm" variant="outline" onClick={() => onLog(false)} disabled={isPending}>✕ Skip</Button>
          </div>
        </div>
        {logs.length > 0 && (
          <div className="space-y-1">
            {logs.map((log) => (
              <LogEntry key={log.id} log={log} />
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  )
}

function LogEntry({ log }: { log: MedicationLog }) {
  const time = new Date(log.takenAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
  return (
    <div className="flex items-center gap-2 text-sm text-muted-foreground">
      <Badge variant={log.taken ? 'default' : 'secondary'} className="text-xs">
        {log.taken ? 'Taken' : 'Skipped'}
      </Badge>
      <span>{time}</span>
      {log.doseAmount != null && <span>{log.doseAmount} actual</span>}
      {log.notes && <span>· {log.notes}</span>}
    </div>
  )
}
