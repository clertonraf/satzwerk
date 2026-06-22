export function convertWeightHint(rawInput: string, unit: 'kg' | 'lb'): string | null {
  const value = Number(rawInput.replace(',', '.'))
  if (!Number.isFinite(value) || value <= 0) return null
  if (unit === 'kg') {
    return `≈ ${Number((value * 2.20462).toFixed(3))} lb`
  }
  return `≈ ${Number((value / 2.20462).toFixed(3))} kg`
}

export function formatDisplayWeight(weight: number, unit: 'kg' | 'lb') {
  const displayWeight = unit === 'kg' ? weight : weight * 2.20462
  return `${Number(displayWeight.toFixed(1))} ${unit}`
}
