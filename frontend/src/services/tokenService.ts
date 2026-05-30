const REFRESH_TOKEN_KEY = 'refreshToken'
let _accessToken: string | null = null

export const tokenService = {
  saveAccessToken: (token: string | null): void => {
    _accessToken = token
  },
  getAccessToken: (): string | null => _accessToken,
  saveRefreshToken: (token: string): void => {
    localStorage.setItem(REFRESH_TOKEN_KEY, token)
  },
  getRefreshToken: (): string | null => localStorage.getItem(REFRESH_TOKEN_KEY),
  clearTokens: (): void => {
    _accessToken = null
    localStorage.removeItem(REFRESH_TOKEN_KEY)
  },
}
