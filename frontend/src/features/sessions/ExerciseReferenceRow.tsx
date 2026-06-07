import type { ExerciseReferenceWeights } from '@/services/sessionService'
import { toPounds } from './sessionHelpers'

interface ExerciseReferenceRowProps {
  referenceWeights: ExerciseReferenceWeights | undefined
  isLoading: boolean
  unit: 'kg' | 'lb'
}

function formatWeight(weightKg: number, unit: 'kg' | 'lb') {
  return String(unit === 'kg' ? weightKg : toPounds(weightKg))
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
      ? `Previous: ${formatWeight(referenceWeights.previousWeightKg, unit)} ${unit}`
      : null,
    referenceWeights.prWeightKg != null ? `PR: ${formatWeight(referenceWeights.prWeightKg, unit)} ${unit}` : null,
    referenceWeights.estimatedOneRepMaxKg != null
      ? `Est. 1RM: ${formatWeight(referenceWeights.estimatedOneRepMaxKg, unit)} ${unit}`
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
