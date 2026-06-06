import type { WeeklyTrendEntry } from '@/services/analyticsService'

const CHART_HEIGHT = 80
const BAR_GAP = 4
const LABEL_HEIGHT = 16

interface WeeklyTrendChartProps {
  entries: WeeklyTrendEntry[]
}

function shortWeekLabel(week: string): string {
  // "2026-W22" → "W22"
  const match = week.match(/W(\d+)$/)
  return match ? `W${match[1]}` : week
}

export default function WeeklyTrendChart({ entries }: WeeklyTrendChartProps) {
  if (entries.length === 0) {
    return <p className="text-xs text-muted-foreground">No data yet.</p>
  }

  const maxSets = Math.max(...entries.map((e) => e.setCount), 1)
  const barCount = entries.length
  const svgWidth = 600

  const barWidth = Math.floor((svgWidth - BAR_GAP * (barCount + 1)) / barCount)
  const totalHeight = CHART_HEIGHT + LABEL_HEIGHT + 8

  return (
    <div className="w-full overflow-hidden">
      <svg
        viewBox={`0 0 ${svgWidth} ${totalHeight}`}
        preserveAspectRatio="none"
        className="w-full"
        style={{ height: totalHeight }}
        aria-label="Weekly sets trend"
      >
        {entries.map((entry, i) => {
          const x = BAR_GAP + i * (barWidth + BAR_GAP)
          const barH = Math.max(2, Math.round((entry.setCount / maxSets) * CHART_HEIGHT))
          const y = CHART_HEIGHT - barH

          return (
            <g key={entry.week}>
              <rect
                x={x}
                y={y}
                width={barWidth}
                height={barH}
                rx={2}
                fill="#22c55e"
                opacity={entry.setCount === 0 ? 0.15 : 0.9}
              />
              {entry.sessionCount > 0 && (
                <text
                  x={x + barWidth / 2}
                  y={y - 3}
                  textAnchor="middle"
                  fontSize={9}
                  fill="currentColor"
                  className="fill-muted-foreground"
                >
                  {entry.sessionCount}
                </text>
              )}
              <text
                x={x + barWidth / 2}
                y={CHART_HEIGHT + LABEL_HEIGHT}
                textAnchor="middle"
                fontSize={9}
                fill="currentColor"
                className="fill-muted-foreground"
              >
                {shortWeekLabel(entry.week)}
              </text>
            </g>
          )
        })}
      </svg>
      <p className="mt-1 text-center text-[10px] text-muted-foreground">
        Bars = sets logged · Numbers above bars = sessions
      </p>
    </div>
  )
}
