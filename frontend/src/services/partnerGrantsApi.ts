import { http } from '@/services/api'

export interface ActiveGrant {
  grantId: string
  appId: string
  appName: string
  grantedScopes: string
  grantedAt: string
}

export const partnerGrantsApi = {
  listActiveGrants: (): Promise<ActiveGrant[]> => http.get<ActiveGrant[]>('/partner-grants'),
  revokeGrant: (grantId: string): Promise<void> => http.delete(`/partner-grants/${grantId}`),
}
