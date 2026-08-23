import { describe, expect, it } from 'vitest'
import { advancedTechniqueMetadataFixture } from '@/test/advancedTechniqueMetadata'
import {
  buildAdvancedTechniqueOptions,
  formatAdvancedTechnique,
  getAdvancedTechniqueDescription,
  getAdvancedTechniqueRestSeconds,
} from '../advancedTechnique'

const advancedTechniques = advancedTechniqueMetadataFixture

describe('buildAdvancedTechniqueOptions', () => {
  it('prepends the empty option and reuses backend labels', () => {
    expect(buildAdvancedTechniqueOptions(advancedTechniques)).toEqual([
      { value: '', label: 'None' },
      { value: 'SST', label: 'SST' },
      { value: 'REST_PAUSE', label: 'REST PAUSE' },
      { value: 'GVT', label: 'GVT' },
      { value: 'FST_7', label: 'FST-7' },
      { value: 'GIRONDA', label: 'GIRONDA' },
    ])
  })
})

describe('formatAdvancedTechnique', () => {
  it('returns null for null input', () => {
    expect(formatAdvancedTechnique(advancedTechniques, null)).toBeNull()
  })

  it('returns null for undefined input', () => {
    expect(formatAdvancedTechnique(advancedTechniques, undefined)).toBeNull()
  })

  it('returns null for empty string', () => {
    expect(formatAdvancedTechnique(advancedTechniques, '')).toBeNull()
  })

  it.each([
    ['SST', 'SST'],
    ['REST_PAUSE', 'REST PAUSE'],
    ['GVT', 'GVT'],
    ['FST_7', 'FST-7'],
    ['GIRONDA', 'GIRONDA'],
  ])('formats value "%s" as label "%s"', (value, expected) => {
    expect(formatAdvancedTechnique(advancedTechniques, value)).toBe(expected)
  })

  it('falls back to the raw value for unknown techniques', () => {
    expect(formatAdvancedTechnique(advancedTechniques, 'UNKNOWN')).toBe('UNKNOWN')
  })

  it('falls back to the raw value for prototype property names', () => {
    expect(formatAdvancedTechnique(advancedTechniques, 'toString')).toBe('toString')
  })
})

describe('getAdvancedTechniqueDescription', () => {
  it('returns null for null input', () => {
    expect(getAdvancedTechniqueDescription(advancedTechniques, null)).toBeNull()
  })

  it('returns null for undefined input', () => {
    expect(getAdvancedTechniqueDescription(advancedTechniques, undefined)).toBeNull()
  })

  it('returns null for empty string', () => {
    expect(getAdvancedTechniqueDescription(advancedTechniques, '')).toBeNull()
  })

  it('returns null for unknown technique values', () => {
    expect(getAdvancedTechniqueDescription(advancedTechniques, 'UNKNOWN')).toBeNull()
  })

  it('returns null for prototype property names', () => {
    expect(getAdvancedTechniqueDescription(advancedTechniques, 'toString')).toBeNull()
  })

  it('returns description for SST', () => {
    const description = getAdvancedTechniqueDescription(advancedTechniques, 'SST')
    expect(description).not.toBeNull()
    expect(description).toMatch(/drop the load/i)
  })

  it('returns description for REST_PAUSE', () => {
    const description = getAdvancedTechniqueDescription(advancedTechniques, 'REST_PAUSE')
    expect(description).not.toBeNull()
    expect(description).toMatch(/15 to 20 seconds/i)
  })

  it('returns description for GVT', () => {
    const description = getAdvancedTechniqueDescription(advancedTechniques, 'GVT')
    expect(description).not.toBeNull()
    expect(description).toMatch(/10 sets of 10/i)
  })

  it('returns description for FST_7', () => {
    const description = getAdvancedTechniqueDescription(advancedTechniques, 'FST_7')
    expect(description).not.toBeNull()
    expect(description).toMatch(/7 high-intensity sets/i)
  })

  it('returns description for GIRONDA', () => {
    const description = getAdvancedTechniqueDescription(advancedTechniques, 'GIRONDA')
    expect(description).not.toBeNull()
    expect(description).toMatch(/8 sets of 8/i)
  })
})

describe('getAdvancedTechniqueRestSeconds', () => {
  it('returns null for null input', () => {
    expect(getAdvancedTechniqueRestSeconds(advancedTechniques, null)).toBeNull()
  })

  it('returns null for undefined input', () => {
    expect(getAdvancedTechniqueRestSeconds(advancedTechniques, undefined)).toBeNull()
  })

  it('returns null for empty string', () => {
    expect(getAdvancedTechniqueRestSeconds(advancedTechniques, '')).toBeNull()
  })

  it('returns null for unknown technique', () => {
    expect(getAdvancedTechniqueRestSeconds(advancedTechniques, 'UNKNOWN')).toBeNull()
  })

  it('returns null for toString (prototype-chain safety)', () => {
    expect(getAdvancedTechniqueRestSeconds(advancedTechniques, 'toString')).toBeNull()
  })

  it.each([
    ['GVT', 60],
    ['FST_7', 30],
    ['GIRONDA', 30],
    ['REST_PAUSE', 20],
    ['SST', 0],
  ] as const)('%s returns %i seconds', (technique, expected) => {
    expect(getAdvancedTechniqueRestSeconds(advancedTechniques, technique)).toBe(expected)
  })
})
