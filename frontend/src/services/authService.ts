import { http } from './api'

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
  register: (data: RegisterRequest) => http.post<AuthResponse>('/auth/register', data),
  login: (data: LoginRequest) => http.post<AuthResponse>('/auth/login', data),
  refresh: (refreshToken: string) => http.post<AuthResponse>('/auth/refresh', { refreshToken }),
  logout: () => http.post('/auth/logout').catch(() => {}),
}
