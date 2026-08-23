import type { AdvancedTechniqueMetadata } from '@/services/planService'

export const advancedTechniqueMetadataFixture: AdvancedTechniqueMetadata[] = [
  {
    value: 'SST',
    label: 'SST',
    description:
      'Perform until muscular failure, then immediately drop the load by 20-30% three consecutive times with zero rest to push the muscle past its normal limits.',
    restSeconds: 0,
  },
  {
    value: 'REST_PAUSE',
    label: 'REST PAUSE',
    description:
      'Perform until muscular failure, resting for a brief 15 to 20 seconds, and then immediately performing a few more reps with the same weight to maximize high-intensity muscle stimulation.',
    restSeconds: 20,
  },
  {
    value: 'GVT',
    label: 'GVT',
    description:
      'Perform a massive workload of 10 sets of 10 repetitions for a single exercise using a strict 60-second rest interval between sets. The weight remains identical for all 100 total reps, typically set at about 60% of your one-repetition maximum to trigger extreme hypertrophy.',
    restSeconds: 60,
  },
  {
    value: 'FST_7',
    label: 'FST-7',
    description:
      'Perform 7 high-intensity sets of 10 to 12 repetitions for your final exercise, restricting your rest periods to a strict 30 to 45 seconds between sets.',
    restSeconds: 30,
  },
  {
    value: 'GIRONDA',
    label: 'GIRONDA',
    description:
      'Perform 8 sets of 8 repetitions for an exercise while aggressively keeping rest intervals down to just 15 to 30 seconds.',
    restSeconds: 30,
  },
]
