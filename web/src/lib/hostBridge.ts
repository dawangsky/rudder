/** HostBridge：业务 UI 与 Electron 壳的窄接口（浏览器环境则为空实现）。 */

export type DaemonStatus = {
  running: boolean
  message: string
}

type HostBridgeApi = {
  getDaemonStatus: () => Promise<DaemonStatus>
  startDaemon: () => Promise<DaemonStatus>
  stopDaemon: () => Promise<DaemonStatus>
}

declare global {
  interface Window {
    rudderHost?: HostBridgeApi
  }
}

/** 获取宿主桥；纯浏览器预览时返回 stub。 */
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
  }
}
