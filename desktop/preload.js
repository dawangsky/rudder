/**
 * preload：向渲染进程暴露窄接口 window.rudderHost，业务代码不直接依赖 Electron。
 */
const { contextBridge, ipcRenderer } = require('electron')

contextBridge.exposeInMainWorld('rudderHost', {
  getDaemonStatus: () => ipcRenderer.invoke('daemon:status'),
  startDaemon: () => ipcRenderer.invoke('daemon:start'),
  stopDaemon: () => ipcRenderer.invoke('daemon:stop'),
  addRuntime: (provider) => ipcRenderer.invoke('runtime:add', provider),
  removeRuntime: (provider) => ipcRenderer.invoke('runtime:remove', provider),
})
