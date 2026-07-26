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

export function getSessionToken(): string {
  return sessionStorage.getItem('rudder_session_token') || ''
}

export function getWorkspaceId(): string {
  return sessionStorage.getItem('rudder_workspace_id') || ''
}

export function setWorkspaceId(id: string) {
  if (id) sessionStorage.setItem('rudder_workspace_id', id)
  else sessionStorage.removeItem('rudder_workspace_id')
}

export function hasWorkspace(): boolean {
  return !!getWorkspaceId()
}

/** 登录/注册成功后写入会话。workspace 为空表示需引导创建。 */
export function applyAuthSession(data: {
  sessionToken?: string
  user?: { email?: string }
  workspace?: { id?: string } | null
}) {
  if (data.sessionToken) {
    sessionStorage.setItem('rudder_session_token', data.sessionToken)
  }
  if (data.user?.email) {
    sessionStorage.setItem('rudder_user_email', data.user.email)
  }
  if (data.workspace?.id) {
    setWorkspaceId(String(data.workspace.id))
  } else {
    setWorkspaceId('')
  }
}

/** 仅退出登录态，不影响「记住账号密码」。 */
export function clearSession(): void {
  for (const k of SESSION_KEYS) {
    sessionStorage.removeItem(k)
  }
}
