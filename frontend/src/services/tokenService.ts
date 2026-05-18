const REFRESH_TOKEN_KEY = 'refreshToken'

export const tokenService = {
  saveRefreshToken: (token: string): void => {
    localStorage.setItem(REFRESH_TOKEN_KEY, token)
  },
  getRefreshToken: (): string | null => localStorage.getItem(REFRESH_TOKEN_KEY),
  clearRefreshToken: (): void => {
    localStorage.removeItem(REFRESH_TOKEN_KEY)
  },
}
