export const ADVANCED_TECHNIQUE_OPTIONS = [
  { value: '', label: 'None' },
  { value: 'SST', label: 'SST' },
  { value: 'REST_PAUSE', label: 'REST PAUSE' },
  { value: 'GVT', label: 'GVT' },
  { value: 'FST_7', label: 'FST-7' },
  { value: 'GIRONDA', label: 'GIRONDA' },
] as const

/**
 * Union of all known AdvancedTechnique enum values (non-empty options only).
 * Derived from ADVANCED_TECHNIQUE_OPTIONS to keep the two in sync.
 * When a new technique is added to ADVANCED_TECHNIQUE_OPTIONS, this type automatically
 * widens — any label/description maps that don't cover the new value produce TS errors.
 */
export type AdvancedTechniqueValue = Exclude<
  (typeof ADVANCED_TECHNIQUE_OPTIONS)[number]['value'],
  ''
>

const advancedTechniqueLabels: Record<AdvancedTechniqueValue, string> = {
  SST: 'SST',
  REST_PAUSE: 'REST PAUSE',
  GVT: 'GVT',
  FST_7: 'FST-7',
  GIRONDA: 'GIRONDA',
}

const advancedTechniqueDescriptions: Record<AdvancedTechniqueValue, string> = {
  SST: 'Perform until muscular failure, then immediately drop the load by 20–30% three consecutive times with zero rest to push the muscle past its normal limits.',
  REST_PAUSE:
    'Perform until muscular failure, resting for a brief 15 to 20 seconds, and then immediately performing a few more reps with the same weight to maximize high-intensity muscle stimulation.',
  GVT: 'Perform a massive workload of 10 sets of 10 repetitions for a single exercise using a strict 60-second rest interval between sets. The weight remains identical for all 100 total reps, typically set at about 60% of your one-repetition maximum to trigger extreme hypertrophy.',
  FST_7:
    'Perform 7 high-intensity sets of 10 to 12 repetitions for your final exercise, restricting your rest periods to a strict 30 to 45 seconds between sets.',
  GIRONDA:
    'Perform 8 sets of 8 repetitions for an exercise while aggressively keeping rest intervals down to just 15 to 30 seconds.',
}

export function formatAdvancedTechnique(value: string | null | undefined) {
  if (!value) {
    return null
  }

  return (advancedTechniqueLabels as Record<string, string>)[value] ?? value
}

export function getAdvancedTechniqueDescription(value: string | null | undefined): string | null {
  if (!value) {
    return null
  }

  return (advancedTechniqueDescriptions as Record<string, string>)[value] ?? null
}

const advancedTechniqueRestSeconds: Record<AdvancedTechniqueValue, number> = {
  GVT: 60,
  FST_7: 30,
  GIRONDA: 30,
  REST_PAUSE: 20,
  SST: 0,
}

export function getAdvancedTechniqueRestSeconds(value: string | null | undefined): number | null {
  if (!value) return null
  return (advancedTechniqueRestSeconds as Record<string, number>)[value] ?? null
}
