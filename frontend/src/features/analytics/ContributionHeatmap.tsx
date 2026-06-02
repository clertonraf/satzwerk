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

const INTENSITY_COLORS = ['#1e293b', '#166534', '#15803d', '#16a34a', '#22c55e']
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

  const dayOfWeek = startDate.getUTCDay()
  const mondayOffset = dayOfWeek === 0 ? -6 : 1 - dayOfWeek
  const alignedStart = addUtcDays(startDate, mondayOffset)

  const totalDays = Math.round((endDate.getTime() - alignedStart.getTime()) / MS_PER_DAY) + 1
  const cols = Math.ceil(totalDays / ROWS)

  const cells: Array<{ dateStr: string; col: number; row: number; entry: HeatmapEntry | null }> = []
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

    cells.push({
      dateStr,
      col,
      row,
      entry: date > today ? null : (byDate.get(dateStr) ?? null),
    })
  }

  return (
    <svg
      aria-label="Contribution heatmap"
      role="img"
      viewBox={`0 0 ${cols * STEP} ${TOP_PAD + ROWS * STEP}`}
      style={{ minWidth: `${cols * STEP}px` }}
    >
      {monthLabels.map(({ key, label, x }) => (
        <text key={key} x={x} y={10} className="fill-muted-foreground text-[10px]">
          {label}
        </text>
      ))}
      {cells.map(({ dateStr, col, row, entry }) => (
        <rect
          key={dateStr}
          x={col * STEP}
          y={TOP_PAD + row * STEP}
          width={CELL}
          height={CELL}
          rx={2}
          fill={INTENSITY_COLORS[entry?.intensity ?? 0] ?? INTENSITY_COLORS[0]}
        >
          <title>{`${dateStr}: ${entry?.count ?? 0} sets`}</title>
        </rect>
      ))}
    </svg>
  )
}
