import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import ExerciseSessionHistoryCard from '../ExerciseSessionHistoryCard'

describe('ExerciseSessionHistoryCard', () => {
  it('shows loading placeholder while fetching', () => {
    render(<ExerciseSessionHistoryCard progress={null} isLoading={true} />)
    expect(screen.getByText('Loading recent sessions…')).toBeInTheDocument()
  })

  it('renders nothing when not loading and no data', () => {
    const { container } = render(
      <ExerciseSessionHistoryCard progress={null} isLoading={false} />,
    )
    expect(container).toBeEmptyDOMElement()
  })
})
