/**
 * 前端会话：登录态存在 sessionStorage；账号密码记忆见 rememberAuth。
 */

const SESSION_KEYS = [
  'rudder_session_token',
  'rudder_user_email',
  'rudder_workspace_id',
] as const

export function getSessionEmail(): string {
  return sessionStorage.getItem('rudder_user_email') || ''
}

/** 仅退出登录态，不影响「记住账号密码」。 */
export function clearSession(): void {
  for (const k of SESSION_KEYS) {
    sessionStorage.removeItem(k)
  }
}
