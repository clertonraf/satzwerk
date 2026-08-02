import { useState } from 'react'
import { format, subDays } from 'date-fns'
import { useQuery } from '@tanstack/react-query'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { medicationsApi } from '@/services/medicationsApi'
import { queryKeys } from '@/services/queryKeys'
import type { MedicationJournalEntry } from './types'

const DEFAULT_DAYS = 30

function toDateString(date: Date): string {
  return date.toISOString().slice(0, 10)
}

function groupByDate(entries: MedicationJournalEntry[]): Map<string, MedicationJournalEntry[]> {
  const groups = new Map<string, MedicationJournalEntry[]>()
  for (const entry of entries) {
    const date = entry.takenAt.slice(0, 10)
    const bucket = groups.get(date)
    if (bucket) {
      bucket.push(entry)
    } else {
      groups.set(date, [entry])
    }
  }
  return groups
}

function formatDose(entry: MedicationJournalEntry): string {
  const amount = entry.doseAmount ?? entry.dosageAmount
  return `${amount} ${entry.dosageUnit.toLowerCase()}`
}

export default function MedicationJournal() {
  const [days, setDays] = useState(DEFAULT_DAYS)

  const today = new Date()
  const from = toDateString(subDays(today, days))
  const to = toDateString(today)

  const { data: entries = [], isLoading } = useQuery({
    queryKey: queryKeys.medications.journal(from, to),
    queryFn: () => medicationsApi.getJournal(from, to),
  })

  const groups = groupByDate(entries)
  const sortedDates = [...groups.keys()].sort((a, b) => b.localeCompare(a))

  return (
    <section className="space-y-4">
      <h2 className="text-base font-semibold">Journal</h2>

      {isLoading && <p className="text-sm text-muted-foreground">Loading journal…</p>}

      {!isLoading && entries.length === 0 && (
        <p className="text-sm text-muted-foreground">No medication logs in the last {days} days.</p>
      )}

      {sortedDates.map((date) => (
        <div key={date}>
          <p className="mb-2 text-sm font-medium text-muted-foreground">
            {format(new Date(`${date}T12:00:00`), 'EEEE, MMMM d, yyyy')}
          </p>
          <ul className="space-y-2">
            {(groups.get(date) ?? []).map((entry) => (
              <li key={entry.id} className="rounded-lg border border-border px-3 py-2 text-sm">
                <div className="flex items-start justify-between gap-2">
                  <div className="space-y-0.5">
                    <p className="font-medium">{entry.medicationName}</p>
                    <p className="text-xs text-muted-foreground">
                      {formatDose(entry)} · {format(new Date(entry.takenAt), 'HH:mm')}
                    </p>
                    {entry.notes ? (
                      <p className="text-xs text-muted-foreground italic">{entry.notes}</p>
                    ) : null}
                  </div>
                  <Badge variant={entry.taken ? 'default' : 'secondary'}>
                    {entry.taken ? 'Taken' : 'Skipped'}
                  </Badge>
                </div>
              </li>
            ))}
          </ul>
        </div>
      ))}

      {!isLoading && (
        <Button type="button" variant="outline" size="sm" onClick={() => setDays((d) => d + DEFAULT_DAYS)}>
          Load more ({days} days shown)
        </Button>
      )}
    </section>
  )
}
