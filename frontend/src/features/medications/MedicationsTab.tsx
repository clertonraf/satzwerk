import { type FormEvent, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { medicationsApi } from '@/services/medicationsApi'
import { queryKeys } from '@/services/queryKeys'
import type {
  CreateMedicationPayload,
  DosageUnit,
  FrequencySpec,
  Medication,
  UpdateMedicationPayload,
} from './types'
import { formatFrequency } from './types'

const DOSAGE_UNITS: DosageUnit[] = ['MG', 'G', 'MCG', 'IU', 'ML', 'UNITS']
const WEEKDAY_NAMES = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']

interface MedicationFormState {
  name: string
  dosageAmount: string
  dosageUnit: DosageUnit
  frequencyType: 'DAILY' | 'WEEKLY' | 'MONTHLY'
  timesPerDay: string
  timesPerWeek: string
  timesPerMonth: string
  weekdays: number[]
  daysOfMonth: number[]
  purpose: string
}

function defaultFormState(): MedicationFormState {
  return {
    name: '',
    dosageAmount: '',
    dosageUnit: 'MG',
    frequencyType: 'DAILY',
    timesPerDay: '1',
    timesPerWeek: '1',
    timesPerMonth: '1',
    weekdays: [],
    daysOfMonth: [],
    purpose: '',
  }
}

function formStateFromMedication(med: Medication): MedicationFormState {
  const freq = med.frequency
  return {
    name: med.name,
    dosageAmount: String(med.dosageAmount),
    dosageUnit: med.dosageUnit,
    frequencyType: freq.type,
    timesPerDay: freq.type === 'DAILY' ? String(freq.timesPerDay) : '1',
    timesPerWeek: freq.type === 'WEEKLY' ? String(freq.timesPerWeek) : '1',
    timesPerMonth: freq.type === 'MONTHLY' ? String(freq.timesPerMonth) : '1',
    weekdays: freq.type === 'WEEKLY' ? (freq.weekdays ?? []) : [],
    daysOfMonth: freq.type === 'MONTHLY' ? (freq.daysOfMonth ?? []) : [],
    purpose: med.purpose ?? '',
  }
}

function buildFrequency(form: MedicationFormState): FrequencySpec {
  if (form.frequencyType === 'DAILY') {
    return { type: 'DAILY', timesPerDay: parseInt(form.timesPerDay, 10) || 1, times: [] }
  }
  if (form.frequencyType === 'WEEKLY') {
    return { type: 'WEEKLY', timesPerWeek: parseInt(form.timesPerWeek, 10) || 1, weekdays: form.weekdays }
  }
  return { type: 'MONTHLY', timesPerMonth: parseInt(form.timesPerMonth, 10) || 1, daysOfMonth: form.daysOfMonth }
}

export default function MedicationsTab() {
  const queryClient = useQueryClient()
  const [showForm, setShowForm] = useState(false)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [form, setForm] = useState<MedicationFormState>(defaultFormState)
  const [formError, setFormError] = useState<string | null>(null)

  const { data: medications = [], isLoading } = useQuery({
    queryKey: queryKeys.medications.all(),
    queryFn: () => medicationsApi.getAll(),
  })

  const createMutation = useMutation({
    mutationFn: (payload: CreateMedicationPayload) => medicationsApi.create(payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.medications.all() })
      void queryClient.invalidateQueries({ queryKey: queryKeys.medications.today() })
      void queryClient.invalidateQueries({ queryKey: queryKeys.medications.journal() })
      resetForm()
    },
    onError: (err: { response?: { status?: number } }) => {
      if (err.response?.status === 409) {
        setFormError('A medication with this name already exists.')
      } else {
        setFormError('Failed to save medication. Please try again.')
      }
    },
  })

  const updateMutation = useMutation({
    mutationFn: (payload: UpdateMedicationPayload) => medicationsApi.update(payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.medications.all() })
      void queryClient.invalidateQueries({ queryKey: queryKeys.medications.today() })
      void queryClient.invalidateQueries({ queryKey: queryKeys.medications.journal() })
      resetForm()
    },
    onError: () => setFormError('Failed to update medication. Please try again.'),
  })

  const deactivateMutation = useMutation({
    mutationFn: (id: string) => medicationsApi.deactivate(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.medications.all() })
      void queryClient.invalidateQueries({ queryKey: queryKeys.medications.today() })
      void queryClient.invalidateQueries({ queryKey: queryKeys.medications.journal() })
    },
  })

  function resetForm() {
    setShowForm(false)
    setEditingId(null)
    setForm(defaultFormState())
    setFormError(null)
  }

  function startEdit(med: Medication) {
    setForm(formStateFromMedication(med))
    setEditingId(med.id)
    setShowForm(true)
    setFormError(null)
  }

  function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setFormError(null)
    const frequency = buildFrequency(form)
    const dosageAmount = parseFloat(form.dosageAmount)
    if (isNaN(dosageAmount) || dosageAmount <= 0) {
      setFormError('Dosage amount must be a positive number.')
      return
    }
    const base = {
      name: form.name.trim(),
      dosageAmount,
      dosageUnit: form.dosageUnit,
      frequency,
      purpose: form.purpose.trim() || null,
    }
    if (editingId) {
      updateMutation.mutate({ ...base, id: editingId })
    } else {
      createMutation.mutate(base)
    }
  }

  function toggleWeekday(day: number) {
    setForm((f) => ({
      ...f,
      weekdays: f.weekdays.includes(day) ? f.weekdays.filter((d) => d !== day) : [...f.weekdays, day],
    }))
  }

  function toggleDayOfMonth(day: number) {
    setForm((f) => ({
      ...f,
      daysOfMonth: f.daysOfMonth.includes(day) ? f.daysOfMonth.filter((d) => d !== day) : [...f.daysOfMonth, day],
    }))
  }

  const isPending = createMutation.isPending || updateMutation.isPending

  const active = medications.filter((m) => m.isActive)
  const inactive = medications.filter((m) => !m.isActive)

  return (
    <div className="space-y-4">
      {!showForm && (
        <Button size="sm" onClick={() => { setShowForm(true); setEditingId(null); setForm(defaultFormState()) }}>
          + Add Medication
        </Button>
      )}

      {showForm && (
        <Card className="border-border bg-card/90 shadow-sm">
          <CardHeader>
            <CardTitle>{editingId ? 'Edit Medication' : 'Add Medication'}</CardTitle>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleSubmit} className="space-y-3">
              <div className="flex flex-col gap-1">
                <label className="text-sm font-medium" htmlFor="med-name">Name</label>
                <Input
                  id="med-name"
                  required
                  value={form.name}
                  onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
                  placeholder="e.g. Vitamin D"
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div className="flex flex-col gap-1">
                  <label className="text-sm font-medium" htmlFor="dosage-amount">Dosage Amount</label>
                  <Input
                    id="dosage-amount"
                    type="number"
                    step="0.0001"
                    min="0.0001"
                    required
                    value={form.dosageAmount}
                    onChange={(e) => setForm((f) => ({ ...f, dosageAmount: e.target.value }))}
                  />
                </div>
                <div className="flex flex-col gap-1">
                  <label className="text-sm font-medium">Unit</label>
                  <Select
                    value={form.dosageUnit}
                    onValueChange={(v) => setForm((f) => ({ ...f, dosageUnit: v as DosageUnit }))}
                  >
                    <SelectTrigger>
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {DOSAGE_UNITS.map((u) => (
                        <SelectItem key={u} value={u}>{u}</SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
              </div>

              <div className="flex flex-col gap-1">
                <label className="text-sm font-medium">Frequency Type</label>
                <Select
                  value={form.frequencyType}
                  onValueChange={(v) =>
                    setForm((f) => ({ ...f, frequencyType: v as 'DAILY' | 'WEEKLY' | 'MONTHLY' }))
                  }
                >
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="DAILY">Daily</SelectItem>
                    <SelectItem value="WEEKLY">Weekly</SelectItem>
                    <SelectItem value="MONTHLY">Monthly</SelectItem>
                  </SelectContent>
                </Select>
              </div>

              {form.frequencyType === 'DAILY' && (
                <div className="flex flex-col gap-1">
                  <label className="text-sm font-medium" htmlFor="times-per-day">Times per day</label>
                  <Input
                    id="times-per-day"
                    type="number"
                    min="1"
                    value={form.timesPerDay}
                    onChange={(e) => setForm((f) => ({ ...f, timesPerDay: e.target.value }))}
                  />
                </div>
              )}

              {form.frequencyType === 'WEEKLY' && (
                <div className="space-y-2">
                  <div className="flex flex-col gap-1">
                    <label className="text-sm font-medium" htmlFor="times-per-week">Times per week</label>
                    <Input
                      id="times-per-week"
                      type="number"
                      min="1"
                      value={form.timesPerWeek}
                      onChange={(e) => setForm((f) => ({ ...f, timesPerWeek: e.target.value }))}
                    />
                  </div>
                  <div>
                    <p className="text-sm font-medium mb-1">Days</p>
                    <div className="flex flex-wrap gap-2">
                      {WEEKDAY_NAMES.map((name, i) => {
                        const day = i + 1
                        return (
                          <button
                            key={day}
                            type="button"
                            onClick={() => toggleWeekday(day)}
                            className={`rounded px-2 py-1 text-xs border ${
                              form.weekdays.includes(day)
                                ? 'bg-primary text-primary-foreground border-primary'
                                : 'border-border'
                            }`}
                          >
                            {name}
                          </button>
                        )
                      })}
                    </div>
                  </div>
                </div>
              )}

              {form.frequencyType === 'MONTHLY' && (
                <div className="space-y-2">
                  <div className="flex flex-col gap-1">
                    <label className="text-sm font-medium" htmlFor="times-per-month">Times per month</label>
                    <Input
                      id="times-per-month"
                      type="number"
                      min="1"
                      value={form.timesPerMonth}
                      onChange={(e) => setForm((f) => ({ ...f, timesPerMonth: e.target.value }))}
                    />
                  </div>
                  <div>
                    <p className="text-sm font-medium mb-1">Days of month</p>
                    <div className="flex flex-wrap gap-1">
                      {Array.from({ length: 31 }, (_, i) => i + 1).map((day) => (
                        <button
                          key={day}
                          type="button"
                          onClick={() => toggleDayOfMonth(day)}
                          className={`rounded px-2 py-1 text-xs border ${
                            form.daysOfMonth.includes(day)
                              ? 'bg-primary text-primary-foreground border-primary'
                              : 'border-border'
                          }`}
                        >
                          {day}
                        </button>
                      ))}
                    </div>
                  </div>
                </div>
              )}

              <div className="flex flex-col gap-1">
                <label className="text-sm font-medium" htmlFor="med-purpose">
                  Purpose <span className="text-muted-foreground">(optional)</span>
                </label>
                <Input
                  id="med-purpose"
                  value={form.purpose}
                  onChange={(e) => setForm((f) => ({ ...f, purpose: e.target.value }))}
                  placeholder="e.g. recovery, performance"
                />
              </div>

              {formError && <p className="text-sm text-destructive">{formError}</p>}

              <div className="flex gap-2">
                <Button type="submit" disabled={isPending}>
                  {isPending ? 'Saving…' : editingId ? 'Save Changes' : 'Add Medication'}
                </Button>
                <Button type="button" variant="ghost" onClick={resetForm}>
                  Cancel
                </Button>
              </div>
            </form>
          </CardContent>
        </Card>
      )}

      {isLoading && <p className="text-sm text-muted-foreground">Loading…</p>}

      {!isLoading && medications.length === 0 && (
        <p className="text-sm text-muted-foreground">No medications yet. Add your first one above.</p>
      )}

      {active.length > 0 && (
        <div className="space-y-2">
          {active.map((med) => (
            <MedicationRow
              key={med.id}
              med={med}
              onEdit={() => startEdit(med)}
              onDeactivate={() => deactivateMutation.mutate(med.id)}
            />
          ))}
        </div>
      )}

      {inactive.length > 0 && (
        <div className="space-y-2 opacity-60">
          <p className="text-xs text-muted-foreground uppercase tracking-wide">Inactive</p>
          {inactive.map((med) => (
            <MedicationRow key={med.id} med={med} onEdit={() => startEdit(med)} />
          ))}
        </div>
      )}
    </div>
  )
}

interface MedicationRowProps {
  med: Medication
  onEdit: () => void
  onDeactivate?: () => void
}

function MedicationRow({ med, onEdit, onDeactivate }: MedicationRowProps) {
  return (
    <Card className="border-border bg-card/90 shadow-sm">
      <CardContent className="pt-4 flex items-start justify-between gap-3">
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 flex-wrap">
            <span className="font-medium">{med.name}</span>
            {med.currentStreak > 0 && (
              <Badge variant="outline" className="text-green-600 border-green-600">
                🔥 {med.currentStreak}d
              </Badge>
            )}
            {!med.isActive && <Badge variant="secondary">Inactive</Badge>}
          </div>
          <p className="text-sm text-muted-foreground">
            {med.dosageAmount} {med.dosageUnit} · {formatFrequency(med.frequency)}
          </p>
          {med.purpose && <p className="text-xs text-muted-foreground">{med.purpose}</p>}
        </div>
        <div className="flex gap-1 shrink-0">
          <Button size="sm" variant="ghost" onClick={onEdit}>Edit</Button>
          {onDeactivate && med.isActive && (
            <Button size="sm" variant="ghost" onClick={onDeactivate} className="text-muted-foreground">
              Deactivate
            </Button>
          )}
        </div>
      </CardContent>
    </Card>
  )
}
