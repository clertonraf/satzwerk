import type { ExerciseReferenceWeights } from '@/services/sessionService'
import { formatDisplayWeight } from '@/lib/unitFormatters'

interface ExerciseReferenceRowProps {
  referenceWeights: ExerciseReferenceWeights | undefined
  isLoading: boolean
  unit: 'kg' | 'lb'
}

export default function ExerciseReferenceRow({
  referenceWeights,
  isLoading,
  unit,
}: ExerciseReferenceRowProps) {
  if (isLoading) {
    return (
      <div
        data-testid="reference-weights-loading"
        aria-label="Loading reference weights"
        className="h-4 w-48 animate-pulse rounded bg-muted"
      />
    )
  }

  if (!referenceWeights) {
    return null
  }

  const values = [
    referenceWeights.previousWeightKg != null
      ? `Previous: ${formatDisplayWeight(referenceWeights.previousWeightKg, unit)}`
      : null,
    referenceWeights.prWeightKg != null ? `PR: ${formatDisplayWeight(referenceWeights.prWeightKg, unit)}` : null,
    referenceWeights.estimatedOneRepMaxKg != null
      ? `Est. 1RM: ${formatDisplayWeight(referenceWeights.estimatedOneRepMaxKg, unit)}`
      : null,
    referenceWeights.suggestedWeightKg != null
      ? `Suggested: ${formatDisplayWeight(referenceWeights.suggestedWeightKg, unit)}`
      : null,
  ].filter((value): value is string => value !== null)

  if (values.length === 0) {
    return null
  }

  return (
    <p className="text-sm text-muted-foreground">
      {values.map((value, index) => (
        <span key={value}>
          {index > 0 ? ' · ' : null}
          <span>{value}</span>
        </span>
      ))}
    </p>
  )
}
