import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import ExerciseReferenceRow from '../ExerciseReferenceRow'
import { toPounds } from '../sessionHelpers'

describe('ExerciseReferenceRow', () => {
  it('renders nothing when reference weights are undefined and not loading', () => {
    render(<ExerciseReferenceRow referenceWeights={undefined} isLoading={false} unit="kg" />)

    expect(screen.queryByText(/previous/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/^pr$/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/est\. 1rm/i)).not.toBeInTheDocument()
  })

  it('renders nothing when all reference weight fields are null and not loading', () => {
    render(
      <ExerciseReferenceRow
        referenceWeights={{
          exerciseId: 'exercise-1',
          previousWeightKg: null,
          prWeightKg: null,
          estimatedOneRepMaxKg: null,
        }}
        isLoading={false}
        unit="kg"
      />
    )

    expect(screen.queryByText(/previous/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/^pr$/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/est\. 1rm/i)).not.toBeInTheDocument()
  })

  it('shows a loading skeleton when reference weights are loading', () => {
    render(<ExerciseReferenceRow referenceWeights={undefined} isLoading={true} unit="kg" />)

    expect(screen.getByTestId('reference-weights-loading')).toHaveAttribute('aria-label', 'Loading reference weights')
  })

  it('shows all reference weights in kilograms', () => {
    render(
      <ExerciseReferenceRow
        referenceWeights={{
          exerciseId: 'exercise-1',
          previousWeightKg: 80,
          prWeightKg: 100,
          estimatedOneRepMaxKg: 116.67,
        }}
        isLoading={false}
        unit="kg"
      />
    )

    expect(screen.getByText('Previous: 80 kg')).toBeInTheDocument()
    expect(screen.getByText('PR: 100 kg')).toBeInTheDocument()
    expect(screen.getByText('Est. 1RM: 116.67 kg')).toBeInTheDocument()
  })

  it('converts reference weights to pounds when unit is lb', () => {
    render(
      <ExerciseReferenceRow
        referenceWeights={{
          exerciseId: 'exercise-1',
          previousWeightKg: 80,
          prWeightKg: 100,
          estimatedOneRepMaxKg: 116.67,
        }}
        isLoading={false}
        unit="lb"
      />
    )

    expect(screen.getByText(`Previous: ${toPounds(80)} lb`)).toBeInTheDocument()
    expect(screen.getByText(`PR: ${toPounds(100)} lb`)).toBeInTheDocument()
    expect(screen.getByText(`Est. 1RM: ${toPounds(116.67)} lb`)).toBeInTheDocument()
  })

  it('shows only available reference weight fields', () => {
    render(
      <ExerciseReferenceRow
        referenceWeights={{
          exerciseId: 'exercise-1',
          previousWeightKg: null,
          prWeightKg: 100,
          estimatedOneRepMaxKg: 116.67,
        }}
        isLoading={false}
        unit="kg"
      />
    )

    expect(screen.queryByText(/previous/i)).not.toBeInTheDocument()
    expect(screen.getByText('PR: 100 kg')).toBeInTheDocument()
    expect(screen.getByText('Est. 1RM: 116.67 kg')).toBeInTheDocument()
  })
})
