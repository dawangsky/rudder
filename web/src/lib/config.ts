/** 前端本地配置：Server Base URL（任意 Self-Host）。 */

const KEY = 'rudder_server_base_url'
const DEFAULT_URL = 'http://127.0.0.1:8080'

export function getServerBaseUrl(): string {
  return localStorage.getItem(KEY) || DEFAULT_URL
}

export function setServerBaseUrl(url: string): void {
  localStorage.setItem(KEY, url || DEFAULT_URL)
}
