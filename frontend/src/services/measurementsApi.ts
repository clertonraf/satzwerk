import { http } from './api'

export interface MeasurementEntry {
  id: string
  measurementDate: string
  shoulders: number | null
  chest: number | null
  weightKg: number | null
  rightBicep: number | null
  leftBicep: number | null
  rightForearm: number | null
  leftForearm: number | null
  abdomen: number | null
  glutes: number | null
  rightThigh: number | null
  leftThigh: number | null
  rightCalf: number | null
  leftCalf: number | null
  createdAt: string
  updatedAt: string
}

export interface UpsertMeasurementPayload {
  measurementDate: string
  shoulders?: number
  chest?: number
  weightKg?: number
  rightBicep?: number
  leftBicep?: number
  rightForearm?: number
  leftForearm?: number
  abdomen?: number
  glutes?: number
  rightThigh?: number
  leftThigh?: number
  rightCalf?: number
  leftCalf?: number
}

export const measurementsApi = {
  getAll: (): Promise<MeasurementEntry[]> => http.get<MeasurementEntry[]>('/measurements'),

  upsert: (payload: UpsertMeasurementPayload): Promise<MeasurementEntry> =>
    http.post<MeasurementEntry>('/measurements', payload),

  deleteByDate: (date: string): Promise<void> => http.delete(`/measurements/${date}`),
}
