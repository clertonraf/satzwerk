import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { Progress } from '../progress'

describe('Progress', () => {
  it('exposes the current determinate value on the progressbar element', () => {
    render(<Progress value={45} />)

    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '45')
  })

  it('keeps the computed fill transform even when custom indicator styles are provided', () => {
    render(<Progress value={45} indicatorStyle={{ transform: 'translateX(0%)' }} />)

    const indicator = screen.getByRole('progressbar').firstElementChild
    expect(indicator).toHaveAttribute('style', expect.stringContaining('translateX(-55%)'))
  })
})
