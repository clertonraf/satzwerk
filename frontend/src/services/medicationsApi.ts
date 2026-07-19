import { http } from './api'
import type {
  AdherenceHeatmap,
  BarChartGranularity,
  CreateMedicationPayload,
  LogDosePayload,
  Medication,
  MedicationLog,
  PerMedicationAnalytics,
  ScheduledDose,
  UpdateMedicationPayload,
} from '@/features/medications/types'

export const medicationsApi = {
  getAll: (): Promise<Medication[]> => http.get<Medication[]>('/medications'),

  getOne: (id: string): Promise<Medication> => http.get<Medication>(`/medications/${id}`),

  create: (payload: CreateMedicationPayload): Promise<Medication> =>
    http.post<Medication>('/medications', payload),

  update: (payload: UpdateMedicationPayload): Promise<Medication> =>
    http.put<Medication>(`/medications/${payload.id}`, payload),

  deactivate: (id: string): Promise<void> => http.delete(`/medications/${id}`),

  getToday: (): Promise<ScheduledDose[]> => http.get<ScheduledDose[]>('/medications/today'),

  logDose: (medicationId: string, payload: LogDosePayload): Promise<MedicationLog> =>
    http.post<MedicationLog>(`/medications/${medicationId}/logs`, payload),

  getLogs: (medicationId: string, from: string, to: string): Promise<MedicationLog[]> =>
    http.get<MedicationLog[]>(`/medications/${medicationId}/logs?from=${from}&to=${to}`),

  getAggregateHeatmap: (weeks = 52): Promise<AdherenceHeatmap> =>
    http.get<AdherenceHeatmap>(`/medications/analytics/heatmap?weeks=${weeks}`),

  getPerMedicationAnalytics: (id: string, granularity: BarChartGranularity = 'WEEKLY'): Promise<PerMedicationAnalytics> =>
    http.get<PerMedicationAnalytics>(`/medications/${id}/analytics?granularity=${granularity}`),
}
