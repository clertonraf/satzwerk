import type { AdvancedTechniqueMetadata } from '@/services/planService'

const EMPTY_ADVANCED_TECHNIQUE_OPTION = { value: '', label: 'None' } as const

function findAdvancedTechnique(
  advancedTechniques: AdvancedTechniqueMetadata[],
  value: string | null | undefined,
): AdvancedTechniqueMetadata | null {
  if (!value) {
    return null
  }

  return advancedTechniques.find((technique) => technique.value === value) ?? null
}

export function buildAdvancedTechniqueOptions(advancedTechniques: AdvancedTechniqueMetadata[]) {
  return [
    EMPTY_ADVANCED_TECHNIQUE_OPTION,
    ...advancedTechniques.map((technique) => ({ value: technique.value, label: technique.label })),
  ]
}

export function formatAdvancedTechnique(
  advancedTechniques: AdvancedTechniqueMetadata[],
  value: string | null | undefined,
) {
  if (!value) {
    return null
  }

  return findAdvancedTechnique(advancedTechniques, value)?.label ?? value
}

export function getAdvancedTechniqueDescription(
  advancedTechniques: AdvancedTechniqueMetadata[],
  value: string | null | undefined,
): string | null {
  return findAdvancedTechnique(advancedTechniques, value)?.description ?? null
}

export function getAdvancedTechniqueRestSeconds(
  advancedTechniques: AdvancedTechniqueMetadata[],
  value: string | null | undefined,
): number | null {
  return findAdvancedTechnique(advancedTechniques, value)?.restSeconds ?? null
}
