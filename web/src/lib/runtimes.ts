/** 运行时列表共用的展示与分组工具。 */

export type Runtime = {
  id: string
  provider: string
  status: string
  hostName?: string
  lastHeartbeatAt?: string
  profile?: string
  daemonId?: string
}

export type MachineGroup = {
  key: string
  daemonId: string
  hostName: string
  profile: string
  isLocal: boolean
  online: boolean
  runtimes: Runtime[]
  latestHeartbeat?: string
}

/** Desktop 本机提示：即使 0 个 Provider 也要保留电脑卡片。 */
export type LocalMachineHint = {
  daemonId: string
  hostName?: string
  profile?: string
  online?: boolean
}

export const PROVIDERS = [
  { value: 'cursor', label: 'Cursor', short: 'Cursor' },
  { value: 'claude_code', label: 'Claude Code', short: 'Claude' },
  { value: 'codex', label: 'Codex', short: 'Codex' },
  { value: 'stub', label: 'Stub', short: 'Stub' },
] as const

export function providerMeta(code: string) {
  return PROVIDERS.find((p) => p.value === code) || { value: code, label: code, short: code }
}

export function displayName(r: Pick<Runtime, 'provider'>) {
  return providerMeta(r.provider).short
}

export function providerLabel(r: Pick<Runtime, 'provider'>) {
  return providerMeta(r.provider).label
}

export function formatHeartbeat(iso?: string) {
  if (!iso) return '—'
  const t = Date.parse(iso)
  if (Number.isNaN(t)) return iso
  const sec = Math.max(0, Math.floor((Date.now() - t) / 1000))
  if (sec < 15) return '刚刚'
  if (sec < 60) return `${sec} 秒前`
  const min = Math.floor(sec / 60)
  if (min < 60) return `${min} 分钟前`
  const hr = Math.floor(min / 60)
  if (hr < 24) return `${hr} 小时前`
  return new Date(t).toLocaleString()
}

export function looksLikeIp(s: string) {
  return /^\d{1,3}(\.\d{1,3}){3}$/.test(s)
}

export function groupMachines(
  runtimes: Runtime[],
  localDaemonId: string,
  localHint?: LocalMachineHint | null,
): MachineGroup[] {
  const map = new Map<string, MachineGroup>()
  for (const r of runtimes) {
    const daemonId = r.daemonId || 'unknown'
    const hostName = r.hostName || '未知主机'
    const key = daemonId
    let g = map.get(key)
    if (!g) {
      g = {
        key,
        daemonId,
        hostName,
        profile: r.profile || '',
        isLocal: !!(localDaemonId && daemonId === localDaemonId),
        online: false,
        runtimes: [],
        latestHeartbeat: undefined,
      }
      map.set(key, g)
    }
    g.runtimes.push(r)
    if (r.hostName && (!g.hostName || g.hostName === '未知主机')) g.hostName = r.hostName
    if (r.profile && !g.profile) g.profile = r.profile
    if (r.status === 'online') g.online = true
    if (r.lastHeartbeatAt) {
      if (!g.latestHeartbeat || Date.parse(r.lastHeartbeatAt) > Date.parse(g.latestHeartbeat)) {
        g.latestHeartbeat = r.lastHeartbeatAt
      }
    }
  }

  // Provider 全删后服务端无 runtime 行；本机 Daemon 仍应作为电脑存在
  const localId = localHint?.daemonId || localDaemonId
  if (localId) {
    let g = map.get(localId)
    if (!g) {
      g = {
        key: localId,
        daemonId: localId,
        hostName: localHint?.hostName || '本机',
        profile: localHint?.profile || 'desktop',
        isLocal: true,
        online: !!localHint?.online,
        runtimes: [],
        latestHeartbeat: undefined,
      }
      map.set(localId, g)
    } else {
      g.isLocal = true
      if (localHint?.hostName && (!g.hostName || g.hostName === '未知主机' || !g.runtimes.length)) {
        g.hostName = localHint.hostName
      }
      if (localHint?.profile && !g.profile) g.profile = localHint.profile
      // 本机在线以 Desktop Daemon 进程为准（删光 Provider 后仍可显示在线/离线）
      if (localHint?.online != null) g.online = !!localHint.online
    }
  }

  return [...map.values()].sort((a, b) => {
    if (a.isLocal !== b.isLocal) return a.isLocal ? -1 : 1
    return (b.latestHeartbeat || '').localeCompare(a.latestHeartbeat || '')
  })
}

export function runtimeTitle(r: Runtime) {
  const host = r.hostName || '本机'
  return `${displayName(r)} (${host})`
}
