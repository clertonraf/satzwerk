import { api } from './api'

export interface AuthResponse {
  accessToken: string
  refreshToken: string
}

export interface RegisterRequest {
  email: string
  password: string
  displayName: string
}

export interface LoginRequest {
  email: string
  password: string
}

export const authService = {
  register: (data: RegisterRequest) => api.post<AuthResponse>('/auth/register', data).then((response) => response.data),
  login: (data: LoginRequest) => api.post<AuthResponse>('/auth/login', data).then((response) => response.data),
  refresh: (refreshToken: string) =>
    api.post<AuthResponse>('/auth/refresh', { refreshToken }).then((response) => response.data),
  logout: () => api.post('/auth/logout').catch(() => {}),
}
