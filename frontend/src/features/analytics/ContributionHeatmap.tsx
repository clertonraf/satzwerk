const CELL = 11
const GAP = 2
const STEP = CELL + GAP
const ROWS = 7
const TOP_PAD = 20
const MS_PER_DAY = 1000 * 60 * 60 * 24

interface HeatmapEntry {
  date: string
  count: number
  intensity: number
}

const INTENSITY_COLORS = [
  '#1e293b', // 0: no activity
  '#14532d', // 1: 1-4 sets
  '#166534', // 2: 5-8 sets
  '#15803d', // 3: 9-12 sets
  '#16a34a', // 4: 13-16 sets
  '#22c55e', // 5: 17-20 sets
  '#4ade80', // 6: 21-24 sets
  '#86efac', // 7: 25-28 sets
  '#bbf7d0', // 8: 29-32 sets
  '#dcfce7', // 9: 33-36 sets
  '#f0fdf4', // 10: 37+ sets
]
const MONTH_LABELS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']

const formatDate = (date: Date) => {
  const year = date.getUTCFullYear()
  const month = String(date.getUTCMonth() + 1).padStart(2, '0')
  const day = String(date.getUTCDate()).padStart(2, '0')

  return `${year}-${month}-${day}`
}

const startOfUtcDay = (date: Date) => new Date(Date.UTC(date.getUTCFullYear(), date.getUTCMonth(), date.getUTCDate()))

const addUtcDays = (date: Date, days: number) => {
  const copy = new Date(date)
  copy.setUTCDate(copy.getUTCDate() + days)
  return copy
}

export default function ContributionHeatmap({ entries, from, to }: { entries: HeatmapEntry[]; from: string; to: string }) {
  if (entries.length === 0) {
    return <p className="text-sm text-muted-foreground">No activity yet.</p>
  }

  const byDate = new Map(entries.map((entry) => [entry.date, entry]))
  const today = startOfUtcDay(new Date())
  const startDate = startOfUtcDay(new Date(`${from}T00:00:00Z`))
  const endDate = startOfUtcDay(new Date(`${to}T00:00:00Z`))

  // Align start back to Monday
  const dayOfWeek = startDate.getUTCDay()
  const mondayOffset = dayOfWeek === 0 ? -6 : 1 - dayOfWeek
  const alignedStart = addUtcDays(startDate, mondayOffset)

  // Align end forward to Sunday so every column is a complete week
  const endDayOfWeek = endDate.getUTCDay()
  const sundayOffset = endDayOfWeek === 0 ? 0 : 7 - endDayOfWeek
  const alignedEnd = addUtcDays(endDate, sundayOffset)

  // Clamp today to endDate: cells beyond `to` are out-of-range, not "0 sets"
  const cutoff = today < endDate ? today : endDate

  const totalDays = Math.round((alignedEnd.getTime() - alignedStart.getTime()) / MS_PER_DAY) + 1
  const cols = totalDays / ROWS

  const cells: Array<{ dateStr: string; col: number; row: number; entry: HeatmapEntry | null; outOfRange: boolean }> = []
  const monthLabels: Array<{ key: string; label: string; x: number }> = []

  for (let index = 0; index < totalDays; index += 1) {
    const date = addUtcDays(alignedStart, index)
    const dateStr = formatDate(date)
    const col = Math.floor(index / ROWS)
    const row = index % ROWS

    if (row === 0) {
      const label = MONTH_LABELS[date.getUTCMonth()]
      const previous = monthLabels.at(-1)

      if (!previous || previous.key !== `${date.getUTCFullYear()}-${date.getUTCMonth()}`) {
        monthLabels.push({
          key: `${date.getUTCFullYear()}-${date.getUTCMonth()}`,
          label,
          x: col * STEP,
        })
      }
    }

    const outOfRange = date > cutoff
    cells.push({
      dateStr,
      col,
      row,
      entry: outOfRange ? null : (byDate.get(dateStr) ?? null),
      outOfRange,
    })
  }

  return (
    <svg
      aria-label="Contribution heatmap"
      role="img"
      width="100%"
      viewBox={`0 0 ${cols * STEP} ${TOP_PAD + ROWS * STEP}`}
    >
      {monthLabels.map(({ key, label, x }) => (
        <text key={key} x={x} y={10} className="fill-muted-foreground" fontSize={10}>
          {label}
        </text>
      ))}
      {cells.map(({ dateStr, col, row, entry, outOfRange }) => (
        <rect
          key={dateStr}
          x={col * STEP}
          y={TOP_PAD + row * STEP}
          width={CELL}
          height={CELL}
          rx={2}
          fill={INTENSITY_COLORS[entry?.intensity ?? 0] ?? INTENSITY_COLORS[0]}
        >
          <title>{outOfRange ? dateStr : `${dateStr}: ${entry?.count ?? 0} sets`}</title>
        </rect>
      ))}
    </svg>
  )
}
