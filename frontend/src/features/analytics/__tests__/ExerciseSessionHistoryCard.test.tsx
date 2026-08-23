import { render, screen } from '@testing-library/react'
import ExerciseSessionHistoryCard from '../ExerciseSessionHistoryCard'

describe('ExerciseSessionHistoryCard', () => {
  it('shows loading placeholder while fetching', () => {
    render(<ExerciseSessionHistoryCard progress={null} isLoading={true} />)
    expect(screen.getByText('Loading history…')).toBeInTheDocument()
  })

  it('renders nothing when not loading and no data', () => {
    const { container } = render(
      <ExerciseSessionHistoryCard progress={null} isLoading={false} />,
    )
    expect(container).toBeEmptyDOMElement()
  })
})
