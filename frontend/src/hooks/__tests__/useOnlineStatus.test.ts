import { act, renderHook } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { useOnlineStatus } from '../useOnlineStatus'

describe('useOnlineStatus', () => {
  it('returns true when navigator.onLine is true', () => {
    Object.defineProperty(navigator, 'onLine', { value: true, configurable: true })

    const { result } = renderHook(() => useOnlineStatus())

    expect(result.current).toBe(true)
  })

  it('returns false when navigator.onLine is false', () => {
    Object.defineProperty(navigator, 'onLine', { value: false, configurable: true })

    const { result } = renderHook(() => useOnlineStatus())

    expect(result.current).toBe(false)
  })

  it('updates when online event fires', () => {
    Object.defineProperty(navigator, 'onLine', { value: false, configurable: true })

    const { result } = renderHook(() => useOnlineStatus())

    expect(result.current).toBe(false)

    act(() => {
      Object.defineProperty(navigator, 'onLine', { value: true, configurable: true })
      window.dispatchEvent(new Event('online'))
    })

    expect(result.current).toBe(true)
  })
})
