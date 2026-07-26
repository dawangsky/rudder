/** 运行时列表共用的展示与分组工具。 */

export type Runtime = {
  id: string
  provider: string
  status: string
  hostName?: string
  lastHeartbeatAt?: string
  profile?: string
  daemonId?: string
  kind?: string
  displayName?: string
  command?: string
  description?: string
  baseProvider?: string
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

export type ProviderMeta = {
  value: string
  label: string
  short: string
  /** intl | cn | test */
  region?: 'intl' | 'cn' | 'test'
  /** 自定义运行时命令占位示例 */
  commandHint?: string
}

/**
 * 与 Daemon detect.Catalog / Server WorkdirResolver 保持一致。
 * 含主流国际协议与国产 coding agent CLI。
 */
export const PROVIDERS: readonly ProviderMeta[] = [
  { value: 'claude_code', label: 'Claude Code', short: 'Claude', region: 'intl', commandHint: '例如：claude -p "{prompt}"' },
  { value: 'cursor', label: 'Cursor', short: 'Cursor', region: 'intl', commandHint: '例如：agent "{prompt}"' },
  { value: 'codex', label: 'Codex', short: 'Codex', region: 'intl', commandHint: '例如：codex exec "{prompt}"' },
  { value: 'opencode', label: 'OpenCode', short: 'OpenCode', region: 'intl', commandHint: '例如：opencode run "{prompt}"' },
  { value: 'gemini', label: 'Gemini CLI', short: 'Gemini', region: 'intl', commandHint: '例如：gemini -p "{prompt}"' },
  { value: 'copilot', label: 'GitHub Copilot', short: 'Copilot', region: 'intl', commandHint: '例如：copilot -p "{prompt}"' },
  { value: 'aider', label: 'Aider', short: 'Aider', region: 'intl', commandHint: '例如：aider --message "{prompt}" --yes-always' },
  { value: 'goose', label: 'Goose', short: 'Goose', region: 'intl', commandHint: '例如：goose run "{prompt}"' },
  { value: 'codebuddy', label: 'CodeBuddy', short: 'CodeBuddy', region: 'cn', commandHint: '例如：codebuddy -p "{prompt}"' },
  { value: 'qwen', label: 'Qwen Code', short: 'Qwen', region: 'cn', commandHint: '例如：qwen -p "{prompt}"' },
  { value: 'kimi', label: 'Kimi Code', short: 'Kimi', region: 'cn', commandHint: '例如：kimi -p "{prompt}"' },
  { value: 'qoder', label: 'Qoder', short: 'Qoder', region: 'cn', commandHint: '例如：qoder -p "{prompt}"' },
  { value: 'traecli', label: 'Trae CLI', short: 'Trae', region: 'cn', commandHint: '例如：traecli -p "{prompt}"' },
  { value: 'kiro', label: 'Kiro', short: 'Kiro', region: 'intl', commandHint: '例如：kiro -p "{prompt}"' },
  { value: 'grok', label: 'Grok', short: 'Grok', region: 'intl', commandHint: '例如：grok -p "{prompt}"' },
  { value: 'hermes', label: 'Hermes', short: 'Hermes', region: 'intl', commandHint: '例如：hermes -p "{prompt}"' },
  { value: 'pi', label: 'Pi', short: 'Pi', region: 'intl', commandHint: '例如：pi -p "{prompt}"' },
  { value: 'openclaw', label: 'OpenClaw', short: 'OpenClaw', region: 'intl', commandHint: '例如：openclaw -p "{prompt}"' },
  { value: 'antigravity', label: 'Antigravity', short: 'Antigravity', region: 'intl', commandHint: '例如：antigravity -p "{prompt}"' },
  { value: 'deveco', label: 'DevEco', short: 'DevEco', region: 'cn', commandHint: '例如：deveco -p "{prompt}"' },
  { value: 'stub', label: 'Stub', short: 'Stub', region: 'test', commandHint: '无需本机 CLI（测试用）' },
] as const

/** 基础协议 id（按长度降序，解析 custom_<base>_<hash>） */
export const BASE_PROVIDER_IDS = [...PROVIDERS.map((p) => p.value)].sort(
  (a, b) => b.length - a.length || a.localeCompare(b),
)

/** 添加自定义运行时可选协议（不含 stub） */
export const PROTOCOL_OPTIONS = PROVIDERS.filter((p) => p.value !== 'stub')

export function baseProviderOf(provider: string) {
  if (!provider?.startsWith('custom_')) return provider
  const rest = provider.slice('custom_'.length)
  for (const base of BASE_PROVIDER_IDS) {
    if (rest.startsWith(`${base}_`)) return base
  }
  return provider
}

export function providerMeta(code: string): ProviderMeta {
  const base = baseProviderOf(code)
  return PROVIDERS.find((p) => p.value === base) || { value: code, label: code, short: code }
}

export function displayName(r: Pick<Runtime, 'provider' | 'displayName'>) {
  if (r.displayName) return r.displayName
  return providerMeta(r.provider).short
}

export function providerLabel(r: Pick<Runtime, 'provider' | 'displayName'>) {
  if (r.displayName) return r.displayName
  return providerMeta(r.provider).label
}

export function isCustomRuntime(r: Pick<Runtime, 'provider' | 'kind'>) {
  return r.kind === 'custom' || r.provider?.startsWith('custom_')
}

export function iconProvider(r: Pick<Runtime, 'provider' | 'baseProvider'>) {
  return r.baseProvider || baseProviderOf(r.provider)
}

export function commandHintFor(provider: string) {
  return providerMeta(provider).commandHint || '例如：my-cli "{prompt}"'
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
