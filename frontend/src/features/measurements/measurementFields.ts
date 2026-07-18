import type { MeasurementEntry } from '@/services/measurementsApi'

export type MeasurementFieldKey = Exclude<
  keyof MeasurementEntry,
  'id' | 'measurementDate' | 'createdAt' | 'updatedAt'
>

export interface MeasurementField {
  key: MeasurementFieldKey
  label: string
  unit: string
}

export const MEASUREMENT_FIELDS: MeasurementField[] = [
  { key: 'shoulders', label: 'Shoulders', unit: 'cm' },
  { key: 'chest', label: 'Chest', unit: 'cm' },
  { key: 'weightKg', label: 'Weight', unit: 'kg' },
  { key: 'rightBicep', label: 'Right Bicep', unit: 'cm' },
  { key: 'leftBicep', label: 'Left Bicep', unit: 'cm' },
  { key: 'rightForearm', label: 'Right Forearm', unit: 'cm' },
  { key: 'leftForearm', label: 'Left Forearm', unit: 'cm' },
  { key: 'abdomen', label: 'Abdomen', unit: 'cm' },
  { key: 'glutes', label: 'Glutes', unit: 'cm' },
  { key: 'rightThigh', label: 'Right Thigh', unit: 'cm' },
  { key: 'leftThigh', label: 'Left Thigh', unit: 'cm' },
  { key: 'rightCalf', label: 'Right Calf', unit: 'cm' },
  { key: 'leftCalf', label: 'Left Calf', unit: 'cm' },
]
