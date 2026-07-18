import { type FormEvent, useState } from 'react'
import { format } from 'date-fns'
import { CalendarIcon } from 'lucide-react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Button } from '@/components/ui/button'
import { Calendar } from '@/components/ui/calendar'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { measurementsApi, type MeasurementEntry, type UpsertMeasurementPayload } from '@/services/measurementsApi'
import { queryKeys } from '@/services/queryKeys'
import { MEASUREMENT_FIELDS } from './measurementFields'

type FieldValues = Record<string, string>

function toFieldValues(entry: MeasurementEntry | undefined): FieldValues {
  if (!entry) return {}
  return Object.fromEntries(
    MEASUREMENT_FIELDS.map(({ key }) => {
      const value = entry[key as keyof MeasurementEntry]
      return [key, value != null ? String(value) : '']
    }),
  )
}

interface LogTabProps {
  measurements: MeasurementEntry[]
}

export default function LogTab({ measurements }: LogTabProps) {
  const today = new Date()
  const todayStr = format(today, 'yyyy-MM-dd')
  const [date, setDate] = useState<Date>(today)
  const [calendarOpen, setCalendarOpen] = useState(false)
  const [fields, setFields] = useState<FieldValues>(() =>
    toFieldValues(measurements.find((m) => m.measurementDate === todayStr)),
  )
  const [saveError, setSaveError] = useState<string | null>(null)
  const [saveSuccess, setSaveSuccess] = useState(false)
  const queryClient = useQueryClient()

  const dateStr = format(date, 'yyyy-MM-dd')
  const existing = measurements.find((m) => m.measurementDate === dateStr)

  function handleDateSelect(selected: Date | undefined) {
    if (!selected) return
    setDate(selected)
    setCalendarOpen(false)
    setSaveSuccess(false)
    setSaveError(null)
    setFields(toFieldValues(measurements.find((m) => m.measurementDate === format(selected, 'yyyy-MM-dd'))))
  }

  const upsertMutation = useMutation({
    mutationFn: (payload: UpsertMeasurementPayload) => measurementsApi.upsert(payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.measurements.all() })
      setSaveSuccess(true)
      setSaveError(null)
    },
    onError: () => {
      setSaveError('Failed to save measurements. Please try again.')
      setSaveSuccess(false)
    },
  })

  function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setSaveSuccess(false)
    setSaveError(null)

    const payload: UpsertMeasurementPayload = { measurementDate: dateStr }
    for (const { key } of MEASUREMENT_FIELDS) {
      const raw = fields[key]
      if (raw && raw.trim() !== '') {
        const num = parseFloat(raw)
        if (!isNaN(num)) {
          payload[key] = num
        }
      }
    }
    upsertMutation.mutate(payload)
  }

  return (
    <Card className="border-border bg-card/90 shadow-sm">
      <CardHeader>
        <CardTitle>Log Measurements</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="flex flex-col gap-1">
          <label className="text-sm font-medium">Date</label>
          <Popover open={calendarOpen} onOpenChange={setCalendarOpen}>
            <PopoverTrigger asChild>
              <Button
                variant="outline"
                className="w-[200px] justify-start text-left font-normal"
              >
                <CalendarIcon className="mr-2 h-4 w-4" />
                {format(date, 'PPP')}
              </Button>
            </PopoverTrigger>
            <PopoverContent className="w-auto p-0" align="start">
              <Calendar
                mode="single"
                selected={date}
                onSelect={handleDateSelect}
                disabled={{ after: new Date() }}
              />
            </PopoverContent>
          </Popover>
          {existing && <p className="text-xs text-muted-foreground">Pre-filled with existing data for this date.</p>}
        </div>

        <form onSubmit={handleSubmit} className="space-y-3">
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
            {MEASUREMENT_FIELDS.map(({ key, label, unit }) => (
              <div key={key} className="flex flex-col gap-1">
                <label htmlFor={key} className="text-xs font-medium">
                  {label} ({unit})
                </label>
                <Input
                  id={key}
                  type="number"
                  step="0.01"
                  min="0.01"
                  placeholder="—"
                  value={fields[key] ?? ''}
                  onChange={(e) => setFields((prev) => ({ ...prev, [key]: e.target.value }))}
                />
              </div>
            ))}
          </div>

          {saveError && <p className="text-sm text-destructive">{saveError}</p>}
          {saveSuccess && <p className="text-sm text-green-600">Measurements saved.</p>}

          <Button type="submit" disabled={upsertMutation.isPending}>
            {upsertMutation.isPending ? 'Saving…' : 'Save'}
          </Button>
        </form>
      </CardContent>
    </Card>
  )
}
