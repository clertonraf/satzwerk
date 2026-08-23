import { http } from './api'
import type {
  AdherenceHeatmap,
  BarChartGranularity,
  CreateMedicationPayload,
  LogDosePayload,
  MedicationJournalView,
  Medication,
  MedicationLog,
  MedicationTodayView,
  PerMedicationAnalytics,
  UpdateMedicationPayload,
} from '@/features/medications/types'

export const medicationsApi = {
  getAll: (): Promise<Medication[]> => http.get<Medication[]>('/medications'),

  getOne: (id: string): Promise<Medication> => http.get<Medication>(`/medications/${id}`),

  create: (payload: CreateMedicationPayload): Promise<Medication> =>
    http.post<Medication>('/medications', payload),

  update: (payload: UpdateMedicationPayload): Promise<Medication> => {
    const { id, ...body } = payload
    return http.put<Medication>(`/medications/${id}`, body)
  },

  deactivate: (id: string): Promise<void> => http.delete(`/medications/${id}`),

  getToday: (): Promise<MedicationTodayView> => http.get<MedicationTodayView>('/medications/today'),

  logDose: (medicationId: string, payload: LogDosePayload): Promise<MedicationLog> =>
    http.post<MedicationLog>(`/medications/${medicationId}/logs`, payload),

  getLogs: (medicationId: string, from: string, to: string): Promise<MedicationLog[]> => {
    const params = new URLSearchParams({ from, to })
    return http.get<MedicationLog[]>(`/medications/${medicationId}/logs?${params.toString()}`)
  },

  getJournal: (from: string, to: string, timezoneOffsetMinutes: number): Promise<MedicationJournalView> => {
    const params = new URLSearchParams({ from, to, timezoneOffsetMinutes: String(timezoneOffsetMinutes) })
    return http.get<MedicationJournalView>(`/medications/logs?${params.toString()}`)
  },

  getAggregateHeatmap: (weeks = 52): Promise<AdherenceHeatmap> =>
    http.get<AdherenceHeatmap>(`/medications/analytics/heatmap?weeks=${weeks}`),

  getPerMedicationAnalytics: (id: string, granularity: BarChartGranularity = 'WEEKLY'): Promise<PerMedicationAnalytics> =>
    http.get<PerMedicationAnalytics>(`/medications/${id}/analytics?granularity=${granularity}`),
}
