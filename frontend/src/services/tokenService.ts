const LEGACY_REFRESH_TOKEN_KEY = 'refreshToken'
const CSRF_COOKIE_NAME = 'refresh_csrf'

let accessToken: string | null = null
let csrfToken: string | null = null

purgeLegacyRefreshToken()

export const tokenService = {
  saveAccessToken: (token: string | null): void => {
    accessToken = token
  },
  getAccessToken: (): string | null => accessToken,
  saveCsrfToken: (token: string | null): void => {
    csrfToken = token
  },
  getCsrfToken: (): string | null => csrfToken ?? readCookie(CSRF_COOKIE_NAME),
  hasRefreshSession: (): boolean => tokenService.getCsrfToken() !== null,
  clearTokens: (): void => {
    accessToken = null
    csrfToken = null
    purgeLegacyRefreshToken()
    clearCookie(CSRF_COOKIE_NAME)
  },
}

function purgeLegacyRefreshToken(): void {
  localStorage.removeItem(LEGACY_REFRESH_TOKEN_KEY)
}

function readCookie(name: string): string | null {
  const prefix = `${name}=`
  const cookie = document.cookie
    .split(';')
    .map((entry) => entry.trim())
    .find((entry) => entry.startsWith(prefix))

  return cookie ? decodeURIComponent(cookie.slice(prefix.length)) : null
}

function clearCookie(name: string): void {
  document.cookie = `${name}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/`
  document.cookie = `${name}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/api/auth`
}
