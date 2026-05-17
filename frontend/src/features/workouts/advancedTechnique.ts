export const ADVANCED_TECHNIQUE_OPTIONS = [
  { value: '', label: 'None' },
  { value: 'SST', label: 'SST' },
  { value: 'REST_PAUSE', label: 'REST PAUSE' },
  { value: 'GVT', label: 'GVT' },
  { value: 'FST_7', label: 'FST-7' },
  { value: 'GIRONDA', label: 'GIRONDA' },
] as const

const advancedTechniqueLabels: Record<string, string> = {
  SST: 'SST',
  REST_PAUSE: 'REST PAUSE',
  GVT: 'GVT',
  FST_7: 'FST-7',
  GIRONDA: 'GIRONDA',
}

export function formatAdvancedTechnique(value: string | null | undefined) {
  if (!value) {
    return null
  }

  return advancedTechniqueLabels[value] ?? value
}
