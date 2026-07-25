/** 调用控制面 API 的轻量客户端。 */

import { getServerBaseUrl } from './config'

export type ApiError = { error?: string; message?: string }

export async function apiFetch<T>(path: string, options: RequestInit = {}): Promise<T> {
  const base = getServerBaseUrl().replace(/\/$/, '')
  const headers = new Headers(options.headers || {})
  if (!headers.has('Content-Type') && options.body) {
    headers.set('Content-Type', 'application/json')
  }
  const token = sessionStorage.getItem('rudder_session_token')
  if (token && token !== 'dev-placeholder-token') {
    headers.set('Authorization', `Bearer ${token}`)
  }

  const res = await fetch(`${base}${path}`, { ...options, headers })
  const text = await res.text()
  const data = text ? JSON.parse(text) : null
  if (!res.ok) {
    const err = (data || {}) as ApiError
    throw new Error(err.message || `请求失败 (${res.status})`)
  }
  return data as T
}
