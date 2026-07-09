import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import ExerciseReferenceRow from '../ExerciseReferenceRow'
import { formatDisplayWeight } from '@/lib/unitFormatters'

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
          estimatedOneRepMaxMinKg: null,
          estimatedOneRepMaxMaxKg: null,
          suggestedWeightKg: null,
        }}
        isLoading={false}
        unit="kg"
      />
    )

    expect(screen.queryByText(/previous/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/^pr$/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/est\. 1rm/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/suggested/i)).not.toBeInTheDocument()
  })

  it('shows a loading skeleton when reference weights are loading', () => {
    render(<ExerciseReferenceRow referenceWeights={undefined} isLoading={true} unit="kg" />)

    expect(screen.getByTestId('reference-weights-loading')).toHaveAttribute('aria-label', 'Loading reference weights')
  })

  it('shows estimated 1RM as range when both min and max are non-null in kg', () => {
    render(
      <ExerciseReferenceRow
        referenceWeights={{
          exerciseId: 'exercise-1',
          previousWeightKg: 80,
          prWeightKg: 100,
          estimatedOneRepMaxMinKg: 112.5,
          estimatedOneRepMaxMaxKg: 116.67,
          suggestedWeightKg: null,
        }}
        isLoading={false}
        unit="kg"
      />
    )

    expect(screen.getByText(`Previous: ${formatDisplayWeight(80, 'kg')}`)).toBeInTheDocument()
    expect(screen.getByText(`PR: ${formatDisplayWeight(100, 'kg')}`)).toBeInTheDocument()
    expect(
      screen.getByText(
        `Est. 1RM: ${formatDisplayWeight(112.5, 'kg')} – ${formatDisplayWeight(116.67, 'kg')}`
      )
    ).toBeInTheDocument()
  })

  it('shows estimated 1RM as range when both min and max are non-null in lb', () => {
    render(
      <ExerciseReferenceRow
        referenceWeights={{
          exerciseId: 'exercise-1',
          previousWeightKg: 80,
          prWeightKg: 100,
          estimatedOneRepMaxMinKg: 112.5,
          estimatedOneRepMaxMaxKg: 116.67,
          suggestedWeightKg: null,
        }}
        isLoading={false}
        unit="lb"
      />
    )

    expect(screen.getByText(`Previous: ${formatDisplayWeight(80, 'lb')}`)).toBeInTheDocument()
    expect(screen.getByText(`PR: ${formatDisplayWeight(100, 'lb')}`)).toBeInTheDocument()
    expect(
      screen.getByText(
        `Est. 1RM: ${formatDisplayWeight(112.5, 'lb')} – ${formatDisplayWeight(116.67, 'lb')}`
      )
    ).toBeInTheDocument()
  })

  it('shows estimated 1RM as single value when min equals max', () => {
    render(
      <ExerciseReferenceRow
        referenceWeights={{
          exerciseId: 'exercise-1',
          previousWeightKg: null,
          prWeightKg: null,
          estimatedOneRepMaxMinKg: 116.67,
          estimatedOneRepMaxMaxKg: 116.67,
          suggestedWeightKg: null,
        }}
        isLoading={false}
        unit="kg"
      />
    )

    expect(
      screen.getByText(
        `Est. 1RM: ${formatDisplayWeight(116.67, 'kg')} – ${formatDisplayWeight(116.67, 'kg')}`
      )
    ).toBeInTheDocument()
  })

  it('shows estimated 1RM as single value when only min is non-null', () => {
    render(
      <ExerciseReferenceRow
        referenceWeights={{
          exerciseId: 'exercise-1',
          previousWeightKg: null,
          prWeightKg: null,
          estimatedOneRepMaxMinKg: 112.5,
          estimatedOneRepMaxMaxKg: null,
          suggestedWeightKg: null,
        }}
        isLoading={false}
        unit="kg"
      />
    )

    expect(screen.getByText(`Est. 1RM: ${formatDisplayWeight(112.5, 'kg')}`)).toBeInTheDocument()
  })

  it('shows estimated 1RM as single value when only max is non-null', () => {
    render(
      <ExerciseReferenceRow
        referenceWeights={{
          exerciseId: 'exercise-1',
          previousWeightKg: null,
          prWeightKg: null,
          estimatedOneRepMaxMinKg: null,
          estimatedOneRepMaxMaxKg: 116.67,
          suggestedWeightKg: null,
        }}
        isLoading={false}
        unit="kg"
      />
    )

    expect(screen.getByText(`Est. 1RM: ${formatDisplayWeight(116.67, 'kg')}`)).toBeInTheDocument()
  })

  it('omits estimated 1RM when both bounds are null', () => {
    render(
      <ExerciseReferenceRow
        referenceWeights={{
          exerciseId: 'exercise-1',
          previousWeightKg: null,
          prWeightKg: null,
          estimatedOneRepMaxMinKg: null,
          estimatedOneRepMaxMaxKg: null,
          suggestedWeightKg: null,
        }}
        isLoading={false}
        unit="kg"
      />
    )

    expect(screen.queryByText(/est\. 1rm/i)).not.toBeInTheDocument()
  })

  it('shows only available reference weight fields', () => {
    render(
      <ExerciseReferenceRow
        referenceWeights={{
          exerciseId: 'exercise-1',
          previousWeightKg: null,
          prWeightKg: 100,
          estimatedOneRepMaxMinKg: 112.5,
          estimatedOneRepMaxMaxKg: 116.67,
          suggestedWeightKg: null,
        }}
        isLoading={false}
        unit="kg"
      />
    )

    expect(screen.queryByText(/previous/i)).not.toBeInTheDocument()
    expect(screen.getByText(`PR: ${formatDisplayWeight(100, 'kg')}`)).toBeInTheDocument()
    expect(
      screen.getByText(
        `Est. 1RM: ${formatDisplayWeight(112.5, 'kg')} – ${formatDisplayWeight(116.67, 'kg')}`
      )
    ).toBeInTheDocument()
  })

  it('shows suggested weight in kg when suggestedWeightKg is non-null', () => {
    render(
      <ExerciseReferenceRow
        referenceWeights={{
          exerciseId: 'exercise-1',
          previousWeightKg: null,
          prWeightKg: null,
          estimatedOneRepMaxMinKg: null,
          estimatedOneRepMaxMaxKg: null,
          suggestedWeightKg: 88.82,
        }}
        isLoading={false}
        unit="kg"
      />
    )

    expect(screen.getByText(`Suggested: ${formatDisplayWeight(88.82, 'kg')}`)).toBeInTheDocument()
  })

  it('converts suggested weight to pounds when unit is lb', () => {
    render(
      <ExerciseReferenceRow
        referenceWeights={{
          exerciseId: 'exercise-1',
          previousWeightKg: null,
          prWeightKg: null,
          estimatedOneRepMaxMinKg: null,
          estimatedOneRepMaxMaxKg: null,
          suggestedWeightKg: 88.82,
        }}
        isLoading={false}
        unit="lb"
      />
    )

    expect(screen.getByText(`Suggested: ${formatDisplayWeight(88.82, 'lb')}`)).toBeInTheDocument()
  })

  it('does not show suggested weight when suggestedWeightKg is null', () => {
    render(
      <ExerciseReferenceRow
        referenceWeights={{
          exerciseId: 'exercise-1',
          previousWeightKg: null,
          prWeightKg: null,
          estimatedOneRepMaxMinKg: null,
          estimatedOneRepMaxMaxKg: null,
          suggestedWeightKg: null,
        }}
        isLoading={false}
        unit="kg"
      />
    )

    expect(screen.queryByText(/suggested/i)).not.toBeInTheDocument()
  })
})
