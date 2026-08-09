/**
 * preload：向渲染进程暴露窄接口 window.rudderHost，业务代码不直接依赖 Electron。
 */
import { contextBridge, ipcRenderer, type IpcRendererEvent } from 'electron'

contextBridge.exposeInMainWorld('rudderHost', {
  getDaemonStatus: () => ipcRenderer.invoke('daemon:status'),
  startDaemon: () => ipcRenderer.invoke('daemon:start'),
  stopDaemon: () => ipcRenderer.invoke('daemon:stop'),
  restartDaemon: () => ipcRenderer.invoke('daemon:restart'),
  getDaemonPrefs: () => ipcRenderer.invoke('daemon:prefs'),
  setDaemonPrefs: (partial: Record<string, unknown>) =>
    ipcRenderer.invoke('daemon:prefs', partial),
  applyDaemonCredentials: (payload: {
    email?: string
    daemonToken?: string
    server?: string
  }) => ipcRenderer.invoke('daemon:apply-credentials', payload),
  getDaemonAccount: () => ipcRenderer.invoke('daemon:account'),
  detectRuntime: (provider: string) => ipcRenderer.invoke('runtime:detect', provider),
  enableRuntime: (provider: string) => ipcRenderer.invoke('runtime:enable', provider),
  addRuntime: (provider: string) => ipcRenderer.invoke('runtime:add', provider),
  removeRuntime: (provider: string) => ipcRenderer.invoke('runtime:remove', provider),
  validateCommand: (command: string) =>
    ipcRenderer.invoke('runtime:validate-command', command),
  addCustomRuntime: (payload: {
    base?: string
    name?: string
    command?: string
    description?: string
  }) => ipcRenderer.invoke('runtime:add-custom', payload),
  selectDirectory: () => ipcRenderer.invoke('dialog:select-directory'),
  scanLocalSkills: () => ipcRenderer.invoke('skills:scan-local'),
  onClosePrompt: (handler: () => void) => {
    const listener = (_evt: IpcRendererEvent) => handler()
    ipcRenderer.on('app:close-prompt', listener)
    return () => ipcRenderer.removeListener('app:close-prompt', listener)
  },
  resolveClosePrompt: (payload: { action: string; askEveryTime: boolean }) =>
    ipcRenderer.invoke('app:close-decision', payload),
})
