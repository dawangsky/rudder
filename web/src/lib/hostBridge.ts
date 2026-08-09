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
  closeAction?: CloseAction
}

export type CloseAction = 'ask' | 'quit' | 'minimize'

export type DaemonPrefs = {
  autoStartOnLaunch: boolean
  autoStopOnQuit: boolean
  closeAction: CloseAction
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

/** Desktop 本机扫描到的 skill（含完整 SKILL.md 内容，可直接导入）。 */
export type LocalScannedSkill = {
  id: string
  name: string
  description: string
  content: string
  sourcePath: string
  displayPath: string
  contentHash: string
  origin: string
  fileCount: number
}

export type ScanLocalSkillsResult = {
  ok: boolean
  skills: LocalScannedSkill[]
  message?: string
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
  scanLocalSkills: () => Promise<ScanLocalSkillsResult>
  onClosePrompt: (handler: () => void) => () => void
  resolveClosePrompt: (payload: {
    action: 'quit' | 'minimize' | 'cancel'
    askEveryTime: boolean
  }) => Promise<{ ok: boolean }>
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
      return { autoStartOnLaunch: true, autoStopOnQuit: false, closeAction: 'ask' as const }
    },
    async setDaemonPrefs(partial) {
      return {
        autoStartOnLaunch: true,
        autoStopOnQuit: false,
        closeAction: 'ask' as const,
        ...partial,
      }
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
    async scanLocalSkills() {
      return { ok: false, skills: [], message: '非 Desktop 环境：无法扫描本机 skill' }
    },
    onClosePrompt() {
      return () => undefined
    },
    async resolveClosePrompt() {
      return { ok: false }
    },
  }
}
