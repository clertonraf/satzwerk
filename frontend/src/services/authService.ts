import axios, { type AxiosRequestConfig } from 'axios'
import { tokenService } from './tokenService'

export interface AuthResponse {
  accessToken: string
  csrfToken: string
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

const authApi = axios.create({
  baseURL: '/api/auth',
  withCredentials: true,
})

export const authService = {
  register: (data: RegisterRequest) => authApi.post<AuthResponse>('/register', data).then((response) => response.data),
  login: (data: LoginRequest) => authApi.post<AuthResponse>('/login', data).then((response) => response.data),
  refresh: () => authApi.post<AuthResponse>('/refresh', undefined, csrfHeaderConfig()).then((response) => response.data),
  logout: () => authApi.post('/logout').then(() => undefined),
}

function csrfHeaderConfig(): AxiosRequestConfig | undefined {
  const csrfToken = tokenService.getCsrfToken()
  if (!csrfToken) {
    return undefined
  }

  return {
    headers: {
      'X-CSRF-Token': csrfToken,
    },
  }
}
