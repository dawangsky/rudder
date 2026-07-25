/**
 * Electron 主进程：薄壳窗口，开发模式加载 Vite（web/），生产加载打包产物。
 * 不在此进程执行 Agent；仅窗口 + HostBridge 托管 Daemon。
 */
const { app, BrowserWindow, ipcMain } = require('electron')
const path = require('path')
const { spawn } = require('child_process')

/** 开发态默认加载 web Vite；可用环境变量覆盖。 */
const WEB_DEV_URL = process.env.RUDDER_WEB_URL || 'http://127.0.0.1:5173'
/** 本机 rudder CLI 路径（后续可做成配置）。 */
const RUDDER_CLI = process.env.RUDDER_CLI || path.join(__dirname, '..', 'daemon', 'rudder')

let mainWindow = null
/** 简单记录由壳拉起的 daemon 子进程（骨架）。 */
let daemonChild = null

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1280,
    height: 840,
    title: 'Rudder',
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
    },
  })

  // MVP 开发：直接打开 Vue 开发服务器
  mainWindow.loadURL(WEB_DEV_URL)
}

/**
 * 通过 CLI 查询/启停 Daemon（实现细节后续与真实 status 协议对齐）。
 */
function runCli(args) {
  return new Promise((resolve) => {
    const child = spawn(RUDDER_CLI, args, { shell: false })
    let out = ''
    let err = ''
    child.stdout.on('data', (d) => { out += d.toString() })
    child.stderr.on('data', (d) => { err += d.toString() })
    child.on('error', (e) => {
      resolve({ code: -1, out: '', err: e.message })
    })
    child.on('close', (code) => {
      resolve({ code: code ?? 0, out, err })
    })
  })
}

ipcMain.handle('daemon:status', async () => {
  const r = await runCli(['daemon', 'status'])
  const running = /running/i.test(r.out) && !/not running/i.test(r.out)
  return {
    running,
    message: (r.out || r.err || 'ok').trim(),
  }
})

ipcMain.handle('daemon:start', async () => {
  // 骨架：异步拉起；完整守护进程管理后续完善
  if (!daemonChild) {
    daemonChild = spawn(RUDDER_CLI, ['daemon', 'start'], {
      detached: true,
      stdio: 'ignore',
    })
    daemonChild.unref()
  }
  return { running: true, message: '已请求 daemon start' }
})

ipcMain.handle('daemon:stop', async () => {
  const r = await runCli(['daemon', 'stop'])
  daemonChild = null
  return { running: false, message: (r.out || r.err || 'stopped').trim() }
})

/** 手动添加运行时：CLI 会探测本机是否安装，未安装则非 0 退出。 */
ipcMain.handle('runtime:add', async (_evt, provider) => {
  const r = await runCli(['runtime', 'add', '--provider', String(provider || '')])
  const message = (r.err || r.out || '').trim() || (r.code === 0 ? 'ok' : '注册失败')
  return { ok: r.code === 0, message }
})

ipcMain.handle('runtime:remove', async (_evt, provider) => {
  const r = await runCli(['runtime', 'remove', '--provider', String(provider || '')])
  const message = (r.err || r.out || '').trim() || (r.code === 0 ? 'ok' : '移除失败')
  return { ok: r.code === 0, message }
})

app.whenReady().then(() => {
  createWindow()
  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow()
  })
})

app.on('window-all-closed', () => {
  // macOS 常见：关窗不退出应用；Daemon 本身也不应随窗退出
  if (process.platform !== 'darwin') app.quit()
})
