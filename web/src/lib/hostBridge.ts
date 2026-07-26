/** HostBridge：业务 UI 与 Electron 壳的窄接口（浏览器环境则为空实现）。 */

export type DaemonStatus = {
  running: boolean
  message: string
  email?: string
  profile?: string
  pid?: number | null
  daemonId?: string
  server?: string
  deviceName?: string
  uptime?: string
  cliInstalled?: boolean
  cliPath?: string
  cliVersion?: string
  cliEnsureOk?: boolean
  cliEnsureMessage?: string
  autoStartOnLaunch?: boolean
  autoStopOnQuit?: boolean
}

export type DaemonPrefs = {
  autoStartOnLaunch: boolean
  autoStopOnQuit: boolean
}

export type DaemonAccount = {
  email: string
  server: string
  profile?: string
  daemonId?: string
}

export type RuntimeActionResult = {
  ok: boolean
  message: string
}

export type DaemonCredentialsPayload = {
  email: string
  daemonToken: string
  server: string
}

type HostBridgeApi = {
  getDaemonStatus: () => Promise<DaemonStatus>
  startDaemon: () => Promise<DaemonStatus>
  stopDaemon: () => Promise<DaemonStatus>
  restartDaemon: () => Promise<DaemonStatus>
  getDaemonPrefs: () => Promise<DaemonPrefs>
  setDaemonPrefs: (partial: Partial<DaemonPrefs>) => Promise<DaemonPrefs>
  applyDaemonCredentials: (payload: DaemonCredentialsPayload) => Promise<RuntimeActionResult>
  getDaemonAccount: () => Promise<DaemonAccount>
  detectRuntime: (provider: string) => Promise<RuntimeActionResult>
  enableRuntime: (provider: string) => Promise<RuntimeActionResult>
  addRuntime: (provider: string) => Promise<RuntimeActionResult>
  removeRuntime: (provider: string) => Promise<RuntimeActionResult>
  validateCommand: (command: string) => Promise<RuntimeActionResult>
  addCustomRuntime: (payload: {
    base: string
    name: string
    command: string
    description?: string
  }) => Promise<RuntimeActionResult>
  selectDirectory: () => Promise<{ ok: boolean; path: string }>
}

declare global {
  interface Window {
    rudderHost?: HostBridgeApi
  }
}

export function isDesktopHost(): boolean {
  return typeof window !== 'undefined' && !!window.rudderHost
}

function unsupported(action: string, provider?: string): RuntimeActionResult {
  const tip = provider
    ? `非 Desktop：请执行 rudder runtime ${action} --provider ${provider}`
    : `非 Desktop：请使用 CLI（rudder runtime ${action}）`
  return { ok: false, message: tip }
}

export function getHostBridge(): HostBridgeApi {
  if (window.rudderHost) return window.rudderHost
  return {
    async getDaemonStatus() {
      return { running: false, message: '非 Desktop 环境：请使用 CLI 管理 Daemon' }
    },
    async startDaemon() {
      return { running: false, message: '非 Desktop 环境：请执行 rudder daemon start' }
    },
    async stopDaemon() {
      return { running: false, message: '非 Desktop 环境' }
    },
    async restartDaemon() {
      return { running: false, message: '非 Desktop 环境' }
    },
    async getDaemonPrefs() {
      return { autoStartOnLaunch: true, autoStopOnQuit: false }
    },
    async setDaemonPrefs(partial) {
      return { autoStartOnLaunch: true, autoStopOnQuit: false, ...partial }
    },
    async applyDaemonCredentials() {
      return { ok: false, message: '非 Desktop 环境：请执行 rudder login' }
    },
    async getDaemonAccount() {
      return { email: '', server: '', profile: '', daemonId: '' }
    },
    async detectRuntime(provider: string) {
      return unsupported('detect', provider)
    },
    async enableRuntime(provider: string) {
      return unsupported('enable', provider)
    },
    async addRuntime(provider: string) {
      return unsupported('add', provider)
    },
    async removeRuntime(provider: string) {
      return unsupported('remove', provider)
    },
    async validateCommand() {
      return { ok: false, message: '非 Desktop：请在目标机用 rudder runtime validate-command' }
    },
    async addCustomRuntime() {
      return { ok: false, message: '非 Desktop：请执行 rudder runtime add-custom' }
    },
    async selectDirectory() {
      return { ok: false, path: '' }
    },
  }
}
