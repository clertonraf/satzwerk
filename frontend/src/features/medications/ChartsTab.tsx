import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import ContributionHeatmap from '@/features/analytics/ContributionHeatmap'
import { medicationsApi } from '@/services/medicationsApi'
import { queryKeys } from '@/services/queryKeys'
import type { AdherenceHeatmapDay, BarChartGranularity } from './types'

const WEEKS = 52

function heatmapEntries(days: AdherenceHeatmapDay[]) {
  return days.map((d) => ({
    date: d.date,
    count: d.takenCount,
    intensity: Math.round(d.adherenceRatio * 10),
  }))
}

function heatmapFromTo(days: AdherenceHeatmapDay[]) {
  if (days.length === 0) return { from: '', to: '' }
  return { from: days[0].date, to: days[days.length - 1].date }
}

export default function ChartsTab() {
  const [selectedMedId, setSelectedMedId] = useState<string>('')
  const [granularity, setGranularity] = useState<BarChartGranularity>('WEEKLY')

  const { data: medications = [] } = useQuery({
    queryKey: queryKeys.medications.all(),
    queryFn: () => medicationsApi.getAll(),
  })

  const activeMedications = medications.filter((m) => m.isActive)
  const firstActiveId = activeMedications[0]?.id ?? ''
  const effectiveMedId = selectedMedId || firstActiveId

  const { data: aggregateHeatmap, isLoading: heatmapLoading } = useQuery({
    queryKey: queryKeys.medications.heatmap(WEEKS),
    queryFn: () => medicationsApi.getAggregateHeatmap(WEEKS),
    enabled: medications.length > 0,
  })

  const { data: perMedAnalytics, isLoading: analyticsLoading } = useQuery({
    queryKey: queryKeys.medications.analytics(effectiveMedId, granularity),
    queryFn: () => medicationsApi.getPerMedicationAnalytics(effectiveMedId, granularity),
    enabled: !!effectiveMedId,
  })

  const aggEntries = aggregateHeatmap ? heatmapEntries(aggregateHeatmap.days) : []
  const aggFromTo = aggregateHeatmap ? heatmapFromTo(aggregateHeatmap.days) : { from: '', to: '' }

  const perMedEntries = perMedAnalytics ? heatmapEntries(perMedAnalytics.heatmap.days) : []
  const perMedFromTo = perMedAnalytics ? heatmapFromTo(perMedAnalytics.heatmap.days) : { from: '', to: '' }

  const barData = (perMedAnalytics?.barChart ?? []).map((p) => ({
    period: p.period,
    taken: p.taken,
    skipped: p.skipped,
  }))

  return (
    <div className="space-y-6">
      <Card className="border-border bg-card/90 shadow-sm">
        <CardHeader>
          <CardTitle className="text-sm font-medium">Overall Adherence (52 weeks)</CardTitle>
        </CardHeader>
        <CardContent>
          {heatmapLoading && <p className="text-sm text-muted-foreground">Loading…</p>}
          {!heatmapLoading && aggEntries.length > 0 && (
            <ContributionHeatmap entries={aggEntries} from={aggFromTo.from} to={aggFromTo.to} unit="doses" />
          )}
          {!heatmapLoading && aggEntries.length === 0 && (
            <p className="text-sm text-muted-foreground">No data yet.</p>
          )}
        </CardContent>
      </Card>

      {activeMedications.length > 0 && (
        <>
          <div className="flex items-center gap-3">
            <label className="text-sm font-medium shrink-0">Medication</label>
            <Select value={effectiveMedId} onValueChange={setSelectedMedId}>
              <SelectTrigger className="w-48">
                <SelectValue placeholder="Select…" />
              </SelectTrigger>
              <SelectContent>
                {activeMedications.map((m) => (
                  <SelectItem key={m.id} value={m.id}>{m.name}</SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <Card className="border-border bg-card/90 shadow-sm">
            <CardHeader>
              <CardTitle className="text-sm font-medium">Per-Medication Adherence</CardTitle>
            </CardHeader>
            <CardContent>
              {analyticsLoading && <p className="text-sm text-muted-foreground">Loading…</p>}
              {!analyticsLoading && perMedEntries.length > 0 && (
                <ContributionHeatmap entries={perMedEntries} from={perMedFromTo.from} to={perMedFromTo.to} unit="doses" />
              )}
              {!analyticsLoading && perMedEntries.length === 0 && (
                <p className="text-sm text-muted-foreground">No data yet.</p>
              )}
            </CardContent>
          </Card>

          <Card className="border-border bg-card/90 shadow-sm">
            <CardHeader className="flex flex-row items-center justify-between">
              <CardTitle className="text-sm font-medium">Taken vs Skipped</CardTitle>
              <Select value={granularity} onValueChange={(v) => setGranularity(v as BarChartGranularity)}>
                <SelectTrigger className="w-32 h-7 text-xs">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="WEEKLY">Weekly</SelectItem>
                  <SelectItem value="MONTHLY">Monthly</SelectItem>
                </SelectContent>
              </Select>
            </CardHeader>
            <CardContent>
              {barData.length === 0 ? (
                <p className="text-sm text-muted-foreground">No dose logs yet.</p>
              ) : (
                <ResponsiveContainer width="100%" height={200}>
                  <BarChart data={barData} margin={{ top: 4, right: 4, left: -20, bottom: 4 }}>
                    <CartesianGrid strokeDasharray="3 3" stroke="hsl(var(--border))" />
                    <XAxis dataKey="period" tick={{ fontSize: 10 }} />
                    <YAxis tick={{ fontSize: 10 }} />
                    <Tooltip
                      contentStyle={{
                        background: 'hsl(var(--card))',
                        border: '1px solid hsl(var(--border))',
                        fontSize: 12,
                      }}
                    />
                    <Bar dataKey="taken" name="Taken" stackId="a" fill="#22c55e" />
                    <Bar dataKey="skipped" name="Skipped" stackId="a" fill="#ef4444" />
                  </BarChart>
                </ResponsiveContainer>
              )}
            </CardContent>
          </Card>
        </>
      )}
    </div>
  )
}
