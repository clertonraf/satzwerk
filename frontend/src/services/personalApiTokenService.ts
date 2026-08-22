import { http } from './api'

export const ALL_SCOPES = [
  'analytics:read',
  'exercises:read',
  'exercises:write',
  'measurements:read',
  'measurements:write',
  'medications:read',
  'medications:write',
  'plans:read',
  'plans:write',
  'sessions:read',
  'sessions:write',
] as const

export type TokenScope = (typeof ALL_SCOPES)[number]

export interface PersonalApiToken {
  id: string
  name: string
  scopes: TokenScope[]
  createdAt: string
  lastUsedAt: string | null
}

export interface CreatedPersonalApiToken extends PersonalApiToken {
  token: string
}

export interface CreateTokenRequest {
  name: string
  scopes: TokenScope[]
}

export const personalApiTokenService = {
  list: (): Promise<PersonalApiToken[]> => http.get<PersonalApiToken[]>('/tokens'),

  create: (req: CreateTokenRequest): Promise<CreatedPersonalApiToken> =>
    http.post<CreatedPersonalApiToken>('/tokens', req),

  revoke: (id: string): Promise<void> => http.delete(`/tokens/${id}`),
}
