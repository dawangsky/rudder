/**
 * 本机记住登录账号/密码（localStorage）。
 * 仅用于 Self-Host Desktop 本地便利；明文存储，勿用于不可信共享环境。
 */

const KEY = 'rudder_remember_auth'

export type RememberedAuth = {
  email: string
  password: string
}

export function loadRememberedAuth(): RememberedAuth | null {
  try {
    const raw = localStorage.getItem(KEY)
    if (!raw) return null
    const data = JSON.parse(raw) as RememberedAuth
    if (!data?.email || typeof data.password !== 'string') return null
    return { email: data.email, password: data.password }
  } catch {
    return null
  }
}

export function saveRememberedAuth(email: string, password: string): void {
  localStorage.setItem(KEY, JSON.stringify({ email, password }))
}

export function clearRememberedAuth(): void {
  localStorage.removeItem(KEY)
}
