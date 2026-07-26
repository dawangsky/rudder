/**
 * preload：向渲染进程暴露窄接口 window.rudderHost，业务代码不直接依赖 Electron。
 */
const { contextBridge, ipcRenderer } = require('electron')

contextBridge.exposeInMainWorld('rudderHost', {
  getDaemonStatus: () => ipcRenderer.invoke('daemon:status'),
  startDaemon: () => ipcRenderer.invoke('daemon:start'),
  stopDaemon: () => ipcRenderer.invoke('daemon:stop'),
  restartDaemon: () => ipcRenderer.invoke('daemon:restart'),
  getDaemonPrefs: () => ipcRenderer.invoke('daemon:prefs'),
  setDaemonPrefs: (partial) => ipcRenderer.invoke('daemon:prefs', partial),
  applyDaemonCredentials: (payload) => ipcRenderer.invoke('daemon:apply-credentials', payload),
  getDaemonAccount: () => ipcRenderer.invoke('daemon:account'),
  detectRuntime: (provider) => ipcRenderer.invoke('runtime:detect', provider),
  enableRuntime: (provider) => ipcRenderer.invoke('runtime:enable', provider),
  addRuntime: (provider) => ipcRenderer.invoke('runtime:add', provider),
  removeRuntime: (provider) => ipcRenderer.invoke('runtime:remove', provider),
  validateCommand: (command) => ipcRenderer.invoke('runtime:validate-command', command),
  addCustomRuntime: (payload) => ipcRenderer.invoke('runtime:add-custom', payload),
})
