import { describe, expect, it } from 'vitest'
import {
  ADVANCED_TECHNIQUE_OPTIONS,
  formatAdvancedTechnique,
  getAdvancedTechniqueDescription,
} from '../advancedTechnique'

describe('ADVANCED_TECHNIQUE_OPTIONS completeness', () => {
  const nonEmptyOptions = ADVANCED_TECHNIQUE_OPTIONS.filter((o) => o.value !== '')

  it('every non-empty option has a non-null label via formatAdvancedTechnique', () => {
    for (const option of nonEmptyOptions) {
      expect(
        formatAdvancedTechnique(option.value),
        `Expected a label for technique "${option.value}"`,
      ).not.toBeNull()
    }
  })

  it('every non-empty option has a non-null description via getAdvancedTechniqueDescription', () => {
    for (const option of nonEmptyOptions) {
      expect(
        getAdvancedTechniqueDescription(option.value),
        `Expected a description for technique "${option.value}"`,
      ).not.toBeNull()
    }
  })
})


describe('formatAdvancedTechnique', () => {
  it('returns null for null input', () => {
    expect(formatAdvancedTechnique(null)).toBeNull()
  })

  it('returns null for undefined input', () => {
    expect(formatAdvancedTechnique(undefined)).toBeNull()
  })

  it('returns null for empty string', () => {
    expect(formatAdvancedTechnique('')).toBeNull()
  })

  it.each([
    ['SST', 'SST'],
    ['REST_PAUSE', 'REST PAUSE'],
    ['GVT', 'GVT'],
    ['FST_7', 'FST-7'],
    ['GIRONDA', 'GIRONDA'],
  ])('formats value "%s" as label "%s"', (value, expected) => {
    expect(formatAdvancedTechnique(value)).toBe(expected)
  })

  it('falls back to the raw value for unknown techniques', () => {
    expect(formatAdvancedTechnique('UNKNOWN')).toBe('UNKNOWN')
  })
})

describe('getAdvancedTechniqueDescription', () => {
  it('returns null for null input', () => {
    expect(getAdvancedTechniqueDescription(null)).toBeNull()
  })

  it('returns null for undefined input', () => {
    expect(getAdvancedTechniqueDescription(undefined)).toBeNull()
  })

  it('returns null for empty string', () => {
    expect(getAdvancedTechniqueDescription('')).toBeNull()
  })

  it('returns null for unknown technique values', () => {
    expect(getAdvancedTechniqueDescription('UNKNOWN')).toBeNull()
  })

  it('returns description for SST', () => {
    const description = getAdvancedTechniqueDescription('SST')
    expect(description).not.toBeNull()
    expect(description).toMatch(/drop the load/i)
  })

  it('returns description for REST_PAUSE', () => {
    const description = getAdvancedTechniqueDescription('REST_PAUSE')
    expect(description).not.toBeNull()
    expect(description).toMatch(/15 to 20 seconds/i)
  })

  it('returns description for GVT', () => {
    const description = getAdvancedTechniqueDescription('GVT')
    expect(description).not.toBeNull()
    expect(description).toMatch(/10 sets of 10/i)
  })

  it('returns description for FST_7', () => {
    const description = getAdvancedTechniqueDescription('FST_7')
    expect(description).not.toBeNull()
    expect(description).toMatch(/7 high-intensity sets/i)
  })

  it('returns description for GIRONDA', () => {
    const description = getAdvancedTechniqueDescription('GIRONDA')
    expect(description).not.toBeNull()
    expect(description).toMatch(/8 sets of 8/i)
  })
})
