/**
 * Desktop 登录后同步本机唯一 Daemon 账号，并重启 Daemon 加载新 Token。
 * 浏览器纯预览环境会跳过（仍可用 CLI rudder login）。
 */

import { apiFetch } from '@/lib/api'
import { getServerBaseUrl } from '@/lib/config'
import { getHostBridge, isDesktopHost } from '@/lib/hostBridge'

type DaemonLoginResponse = {
  daemonToken: string
}

/**
 * 用与 Desktop 相同的邮箱密码换取 daemonToken，写入 ~/.rudder/credentials.json，重启 Daemon。
 * @returns 人类可读结果；失败时抛错或返回 ok:false（调用方可决定是否阻断登录）
 */
export async function syncDaemonWithDesktopLogin(
  email: string,
  password: string,
): Promise<{ ok: boolean; message: string }> {
  if (!isDesktopHost()) {
    return { ok: true, message: '非 Desktop，跳过 Daemon 同步' }
  }
  const host = getHostBridge()
  const daemon = await apiFetch<DaemonLoginResponse>('/api/auth/daemon-login', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  })
  if (!daemon?.daemonToken) {
    return { ok: false, message: 'daemon-login 未返回 daemonToken' }
  }
  const applied = await host.applyDaemonCredentials({
    email: email.trim(),
    daemonToken: daemon.daemonToken,
    server: getServerBaseUrl(),
  })
  if (!applied.ok) return applied
  await host.restartDaemon()
  return { ok: true, message: applied.message || '已联动本机 Daemon' }
}
