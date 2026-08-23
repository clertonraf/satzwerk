type TooltipValue = string | number | null | undefined | readonly (string | number)[]

export function formatExerciseProgressTooltipValue(value: TooltipValue, name: string) {
  const seriesName = name === 'Estimated 1RM' ? 'Estimated 1RM' : 'Top set'
  const scalarValue = Array.isArray(value) ? value[0] : value
  if (scalarValue == null && seriesName === 'Estimated 1RM') {
    return ['Not available', seriesName] as const
  }
  return [`${scalarValue} kg`, seriesName] as const
}
