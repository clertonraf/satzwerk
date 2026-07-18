import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { measurementsApi, type MeasurementEntry } from '@/services/measurementsApi'
import { queryKeys } from '@/services/queryKeys'

const FIELD_LABELS: Record<string, string> = {
  shoulders: 'Shoulders',
  chest: 'Chest',
  weightKg: 'Weight',
  rightBicep: 'Right Bicep',
  leftBicep: 'Left Bicep',
  rightForearm: 'Right Forearm',
  leftForearm: 'Left Forearm',
  abdomen: 'Abdomen',
  glutes: 'Glutes',
  rightThigh: 'Right Thigh',
  leftThigh: 'Left Thigh',
  rightCalf: 'Right Calf',
  leftCalf: 'Left Calf',
}

const FIELD_UNITS: Record<string, string> = {
  shoulders: 'cm',
  chest: 'cm',
  weightKg: 'kg',
  rightBicep: 'cm',
  leftBicep: 'cm',
  rightForearm: 'cm',
  leftForearm: 'cm',
  abdomen: 'cm',
  glutes: 'cm',
  rightThigh: 'cm',
  leftThigh: 'cm',
  rightCalf: 'cm',
  leftCalf: 'cm',
}

const MEASUREMENT_KEYS = Object.keys(FIELD_LABELS) as (keyof MeasurementEntry)[]

function countNonNull(entry: MeasurementEntry): number {
  return MEASUREMENT_KEYS.filter((key) => entry[key] != null).length
}

interface HistoryTabProps {
  measurements: MeasurementEntry[]
}

export default function HistoryTab({ measurements }: HistoryTabProps) {
  const [expandedDates, setExpandedDates] = useState<Set<string>>(new Set())
  const queryClient = useQueryClient()

  const deleteMutation = useMutation({
    mutationFn: (date: string) => measurementsApi.deleteByDate(date),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.measurements.all() })
    },
  })

  function toggleExpand(date: string) {
    setExpandedDates((prev) => {
      const next = new Set(prev)
      if (next.has(date)) {
        next.delete(date)
      } else {
        next.add(date)
      }
      return next
    })
  }

  if (measurements.length === 0) {
    return (
      <Card className="border-border bg-card/90 shadow-sm">
        <CardContent className="pt-6">
          <p className="text-sm text-muted-foreground">No measurements logged yet.</p>
        </CardContent>
      </Card>
    )
  }

  return (
    <Card className="border-border bg-card/90 shadow-sm">
      <CardHeader>
        <CardTitle>History</CardTitle>
      </CardHeader>
      <CardContent className="space-y-2">
        {measurements.map((entry) => {
          const isExpanded = expandedDates.has(entry.measurementDate)
          const nonNullCount = countNonNull(entry)
          const nonNullFields = MEASUREMENT_KEYS.filter((key) => entry[key] != null)

          return (
            <div key={entry.measurementDate} className="rounded-md border border-border">
              <div className="flex items-center justify-between px-3 py-2">
                <button
                  type="button"
                  className="flex flex-1 items-center gap-2 text-left text-sm font-medium hover:underline"
                  onClick={() => toggleExpand(entry.measurementDate)}
                  aria-expanded={isExpanded}
                  aria-label={`Toggle entry for ${entry.measurementDate}`}
                >
                  <span>{entry.measurementDate}</span>
                  <span className="text-xs text-muted-foreground">
                    {nonNullCount} field{nonNullCount !== 1 ? 's' : ''} logged
                  </span>
                  <span className="ml-auto text-muted-foreground">{isExpanded ? '▲' : '▼'}</span>
                </button>
                <Button
                  variant="outline"
                  size="sm"
                  className="ml-3 text-destructive hover:bg-destructive hover:text-destructive-foreground"
                  onClick={() => deleteMutation.mutate(entry.measurementDate)}
                  disabled={deleteMutation.isPending && deleteMutation.variables === entry.measurementDate}
                  aria-label={`Delete entry for ${entry.measurementDate}`}
                >
                  Delete
                </Button>
              </div>

              {isExpanded && (
                <div className="border-t border-border px-3 py-2">
                  <dl className="grid grid-cols-2 gap-x-4 gap-y-1 sm:grid-cols-3 text-sm">
                    {nonNullFields.map((key) => (
                      <div key={String(key)}>
                        <dt className="text-xs text-muted-foreground">{FIELD_LABELS[String(key)]}</dt>
                        <dd className="font-medium">
                          {String(entry[key])} {FIELD_UNITS[String(key)]}
                        </dd>
                      </div>
                    ))}
                  </dl>
                </div>
              )}
            </div>
          )
        })}
      </CardContent>
    </Card>
  )
}
