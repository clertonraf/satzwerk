import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import ResumeDiscardModal from '../ResumeDiscardModal'

describe('ResumeDiscardModal', () => {
  it('shows resume and discard buttons', () => {
    render(<ResumeDiscardModal onResume={vi.fn()} onDiscard={vi.fn()} />)

    expect(screen.getByRole('button', { name: /resume/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /discard/i })).toBeInTheDocument()
  })

  it('calls onResume when Resume clicked', async () => {
    const user = userEvent.setup()
    const onResume = vi.fn()

    render(<ResumeDiscardModal onResume={onResume} onDiscard={vi.fn()} />)

    await user.click(screen.getByRole('button', { name: /resume/i }))

    expect(onResume).toHaveBeenCalled()
  })
})
