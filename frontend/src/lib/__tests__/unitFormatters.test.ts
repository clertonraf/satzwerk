import { describe, expect, it } from 'vitest'
import { convertWeightHint, formatDisplayWeight } from '../unitFormatters'

describe('convertWeightHint', () => {
  it('returns null for empty input', () => {
    expect(convertWeightHint('', 'kg')).toBeNull()
  })

  it('returns null for non-numeric input', () => {
    expect(convertWeightHint('abc', 'kg')).toBeNull()
  })

  it('returns null for zero', () => {
    expect(convertWeightHint('0', 'kg')).toBeNull()
  })

  it('returns null for negative values', () => {
    expect(convertWeightHint('-5', 'kg')).toBeNull()
  })

  it('shows lb hint when unit is kg', () => {
    expect(convertWeightHint('100', 'kg')).toBe('≈ 220.462 lb')
  })

  it('shows kg hint when unit is lb', () => {
    expect(convertWeightHint('220.462', 'lb')).toBe('≈ 100 kg')
  })

  it('accepts comma as decimal separator', () => {
    expect(convertWeightHint('100,5', 'kg')).toBe('≈ 221.564 lb')
  })
})

describe('formatDisplayWeight', () => {
  it('formats kg weight with one decimal', () => {
    expect(formatDisplayWeight(100, 'kg')).toBe('100 kg')
  })

  it('formats lb weight by converting from kg and one decimal', () => {
    expect(formatDisplayWeight(100, 'lb')).toBe('220.5 lb')
  })

  it('rounds to one decimal place', () => {
    expect(formatDisplayWeight(1.005, 'kg')).toBe('1 kg')
  })
})
