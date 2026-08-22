import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { Progress } from '../progress'

describe('Progress', () => {
  it('exposes the current determinate value on the progressbar element', () => {
    render(<Progress value={45} />)

    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '45')
  })

  it('scales clamping and fill transform to a custom max value', () => {
    render(<Progress value={5} max={20} />)

    const progressbar = screen.getByRole('progressbar')
    expect(progressbar).toHaveAttribute('aria-valuenow', '5')

    const indicator = progressbar.firstElementChild
    expect(indicator).toHaveAttribute('style', expect.stringContaining('translateX(-75%)'))
  })

  it('clamps the determinate value to the custom max value', () => {
    render(<Progress value={25} max={20} />)

    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '20')
  })

  it('keeps the computed fill transform even when custom indicator styles are provided', () => {
    render(<Progress value={45} indicatorStyle={{ transform: 'translateX(0%)' }} />)

    const indicator = screen.getByRole('progressbar').firstElementChild
    expect(indicator).toHaveAttribute('style', expect.stringContaining('translateX(-55%)'))
  })
})
