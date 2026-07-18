import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import type { MeasurementEntry } from '@/services/measurementsApi'
import { MEASUREMENT_FIELDS } from './measurementFields'

interface ChartsTabProps {
  measurements: MeasurementEntry[]
}

export default function ChartsTab({ measurements }: ChartsTabProps) {
  const fieldsWithData = MEASUREMENT_FIELDS.filter(({ key }) => measurements.some((m) => m[key] != null))

  if (fieldsWithData.length === 0) {
    return (
      <Card className="border-border bg-card/90 shadow-sm">
        <CardContent className="pt-6">
          <p className="text-sm text-muted-foreground">No measurements logged yet.</p>
        </CardContent>
      </Card>
    )
  }

  // Measurements arrive sorted date DESC from API; charts display oldest→newest
  const sortedAsc = [...measurements].reverse()

  return (
    <div className="space-y-6">
      {fieldsWithData.map(({ key, label, unit }) => {
        const chartData = sortedAsc
          .filter((m) => m[key] != null)
          .map((m) => ({ date: m.measurementDate, value: Number(m[key]) }))

        return (
          <Card key={String(key)} className="border-border bg-card/90 shadow-sm">
            <CardHeader>
              <CardTitle className="text-sm font-medium">
                {label} ({unit})
              </CardTitle>
            </CardHeader>
            <CardContent>
              <ResponsiveContainer width="100%" height={160}>
                <LineChart data={chartData} margin={{ top: 4, right: 8, left: 0, bottom: 4 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="hsl(var(--border))" />
                  <XAxis dataKey="date" tick={{ fontSize: 10 }} tickLine={false} />
                  <YAxis tick={{ fontSize: 10 }} tickLine={false} axisLine={false} width={40} />
                  <Tooltip
                    formatter={(value) => [`${String(value)} ${unit}`, label]}
                    contentStyle={{ fontSize: '0.75rem' }}
                  />
                  <Line
                    type="monotone"
                    dataKey="value"
                    stroke="hsl(var(--primary))"
                    strokeWidth={2}
                    dot={{ r: 3 }}
                    activeDot={{ r: 5 }}
                  />
                </LineChart>
              </ResponsiveContainer>
            </CardContent>
          </Card>
        )
      })}
    </div>
  )
}
