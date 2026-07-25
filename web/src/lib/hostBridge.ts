/** HostBridge：业务 UI 与 Electron 壳的窄接口（浏览器环境则为空实现）。 */

export type DaemonStatus = {
  running: boolean
  message: string
}

export type RuntimeActionResult = {
  ok: boolean
  message: string
}

type HostBridgeApi = {
  getDaemonStatus: () => Promise<DaemonStatus>
  startDaemon: () => Promise<DaemonStatus>
  stopDaemon: () => Promise<DaemonStatus>
  /** 探测本机 CLI 并注册运行时；未安装则 ok=false */
  addRuntime: (provider: string) => Promise<RuntimeActionResult>
  removeRuntime: (provider: string) => Promise<RuntimeActionResult>
}

declare global {
  interface Window {
    rudderHost?: HostBridgeApi
  }
}

/** 获取宿主桥；纯浏览器预览时返回 stub（需用 CLI 添加运行时）。 */
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
    async addRuntime(provider: string) {
      return {
        ok: false,
        message: `非 Desktop 环境：请执行 rudder runtime add --provider ${provider}`,
      }
    },
    async removeRuntime(provider: string) {
      return {
        ok: false,
        message: `非 Desktop 环境：请执行 rudder runtime remove --provider ${provider}`,
      }
    },
  }
}
