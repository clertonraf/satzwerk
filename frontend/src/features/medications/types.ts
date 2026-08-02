export type DosageUnit = 'MG' | 'G' | 'MCG' | 'IU' | 'ML' | 'UNITS'

export type DailyFrequency = { type: 'DAILY'; timesPerDay: number; times?: string[] }
export type WeeklyFrequency = { type: 'WEEKLY'; timesPerWeek: number; weekdays?: number[] }
export type MonthlyFrequency = { type: 'MONTHLY'; timesPerMonth: number; daysOfMonth?: number[] }
export type FrequencySpec = DailyFrequency | WeeklyFrequency | MonthlyFrequency

export interface Medication {
  id: string
  name: string
  dosageAmount: number
  dosageUnit: DosageUnit
  frequency: FrequencySpec
  purpose: string | null
  isActive: boolean
  createdAt: string
  currentStreak: number
}

export interface MedicationLog {
  id: string
  medicationId: string
  takenAt: string
  taken: boolean
  doseAmount: number | null
  notes: string | null
}

export interface MedicationJournalEntry {
  id: string
  medicationId: string
  medicationName: string
  takenAt: string
  taken: boolean
  doseAmount: number | null
  dosageAmount: number
  dosageUnit: DosageUnit
  notes: string | null
}

export interface ScheduledDose {
  medication: Medication
  scheduledCount: number
  logs: MedicationLog[]
}

export interface AdherenceHeatmapDay {
  date: string
  adherenceRatio: number
  takenCount: number
  scheduledCount: number
}

export interface AdherenceHeatmap {
  days: AdherenceHeatmapDay[]
}

export interface BarChartPeriod {
  period: string
  taken: number
  skipped: number
}

export interface PerMedicationAnalytics {
  heatmap: AdherenceHeatmap
  barChart: BarChartPeriod[]
  currentStreak: number
}

export type BarChartGranularity = 'WEEKLY' | 'MONTHLY'

export interface CreateMedicationPayload {
  name: string
  dosageAmount: number
  dosageUnit: DosageUnit
  frequency: FrequencySpec
  purpose?: string | null
}

export interface UpdateMedicationPayload extends CreateMedicationPayload {
  id: string
}

export interface LogDosePayload {
  takenAt: string
  taken: boolean
  doseAmount?: number | null
  notes?: string | null
}

/** Returns a human-readable summary of a FrequencySpec */
export function formatFrequency(spec: FrequencySpec): string {
  const WEEKDAY_NAMES = ['', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']
  if (spec.type === 'DAILY') {
    return spec.timesPerDay === 1 ? 'Once daily' : `${spec.timesPerDay}× daily`
  }
  if (spec.type === 'WEEKLY') {
    if (spec.weekdays && spec.weekdays.length > 0) {
      return spec.weekdays.map((d) => WEEKDAY_NAMES[d] ?? d).join(' / ')
    }
    return `${spec.timesPerWeek}× per week`
  }
  if (spec.daysOfMonth && spec.daysOfMonth.length > 0) {
    return `Days ${spec.daysOfMonth.join(', ')} of month`
  }
  return `${spec.timesPerMonth}× per month`
}
