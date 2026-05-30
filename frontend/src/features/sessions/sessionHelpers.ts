import type { WorkoutPlanDetail } from '@/services/planService'

export interface WorkoutGroupCatalogEntry {
  group: WorkoutPlanDetail['groups'][number]
  plan: WorkoutPlanDetail
}

export function buildWorkoutGroupCatalog(plans: WorkoutPlanDetail[]) {
  const catalog: Record<string, WorkoutGroupCatalogEntry> = {}

  plans.forEach((plan) => {
    plan.groups.forEach((group) => {
      catalog[group.id] = { group, plan }
    })
  })

  return catalog
}

export function formatSessionDate(value: string | null | undefined) {
  if (!value) {
    return 'In progress'
  }

  const date = new Date(value)

  if (Number.isNaN(date.getTime())) {
    return value
  }

  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(date)
}

export function toKilograms(weight: number, unit: 'kg' | 'lb') {
  if (unit === 'kg') {
    return weight
  }

  return Number((weight / 2.20462).toFixed(3))
}

export function convertWeightHint(rawInput: string, unit: 'kg' | 'lb'): string | null {
  const value = parseFloat(rawInput)
  if (isNaN(value) || value <= 0) return null
  if (unit === 'kg') {
    return `≈ ${Number((value * 2.20462).toFixed(3))} lb`
  }
  return `≈ ${Number((value / 2.20462).toFixed(3))} kg`
}

export function formatDisplayWeight(weight: number, unit: 'kg' | 'lb') {
  const displayWeight = unit === 'kg' ? weight : weight * 2.20462
  return `${Number(displayWeight.toFixed(1))} ${unit}`
}
