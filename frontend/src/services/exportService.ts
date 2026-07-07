import { api } from './api'

export interface ImportSummaryResponse {
  importedExercises: number
  importedWorkoutPlans: number
  importedWorkoutSessions: number
  importedSetLogs: number
  reusedExercises: number
}

export const exportService = {
  async downloadExport(): Promise<void> {
    const response = await api.get('/export', { responseType: 'blob' })
    const url = URL.createObjectURL(response.data as Blob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'satzwerk-export.json'
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    // Delay revocation so the browser has time to start the download
    setTimeout(() => URL.revokeObjectURL(url), 100)
  },

  async importData(jsonContent: string): Promise<ImportSummaryResponse> {
    const body = JSON.parse(jsonContent) as unknown
    const response = await api.post<ImportSummaryResponse>('/import', body)
    return response.data
  },
}
