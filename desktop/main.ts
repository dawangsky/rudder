/**
 * Electron 主进程：薄壳窗口 + Desktop profile Daemon（对齐 Multica）。
 *
 * - 固定 --profile desktop → ~/.rudder/profiles/desktop/
 * - 与终端默认 CLI Daemon（~/.rudder/）隔离，可同机并存
 * - 启动时若有凭证且 autoStartOnLaunch 则自动拉起
 * - 登录后由渲染进程写入凭证并 restart
 */
import { app, BrowserWindow, ipcMain, dialog, type IpcMainInvokeEvent } from 'electron'
import path from 'path'
import fs from 'fs'
import os from 'os'
import { spawn, type ChildProcess } from 'child_process'
import { randomUUID } from 'crypto'
import { ensureDaemon, type EnsureDaemonResult } from './scripts/ensure-daemon'

const WEB_DEV_URL = process.env.RUDDER_WEB_URL || 'http://127.0.0.1:5173'
const DESKTOP_PROFILE = 'desktop'

type DesktopPrefs = {
  autoStartOnLaunch: boolean
  autoStopOnQuit: boolean
}

type Credentials = {
  email?: string
  daemonToken?: string
  server?: string
}

type CliResult = { code: number; out: string; err: string }

let mainWindow: BrowserWindow | null = null
let daemonChild: ChildProcess | null = null
/** 本次进程内记录的启动时间（用于 Uptime 展示） */
let daemonStartedAt: number | null = null
/** ensure-daemon 结果 */
let daemonEnsure: EnsureDaemonResult = {
  ok: false,
  rebuilt: false,
  version: '',
  message: '',
  binary: '',
}

function resolveCliPath(): string {
  if (process.env.RUDDER_CLI) return process.env.RUDDER_CLI
  // dist/main.js → ../../daemon/rudder
  return daemonEnsure.binary || path.join(__dirname, '..', '..', 'daemon', 'rudder')
}

function profileHome(): string {
  return path.join(os.homedir(), '.rudder', 'profiles', DESKTOP_PROFILE)
}

/** Desktop 子进程 PATH：补上 npm 全局等常见目录，否则探测不到 opencode 等 CLI。 */
function cliEnv(): NodeJS.ProcessEnv {
  const home = os.homedir()
  const extras = [
    path.join(home, '.npm-global', 'bin'),
    path.join(home, '.local', 'bin'),
    path.join(home, 'bin'),
    path.join(home, '.yarn', 'bin'),
    path.join(home, 'Library', 'pnpm'),
    path.join(home, '.bun', 'bin'),
    '/opt/homebrew/bin',
    '/usr/local/bin',
  ]
  const cur = process.env.PATH || ''
  const parts = cur.split(path.delimiter).filter(Boolean)
  const seen = new Set(parts)
  const prepend = extras.filter((d) => {
    if (seen.has(d)) return false
    try {
      return fs.statSync(d).isDirectory()
    } catch {
      return false
    }
  })
  return { ...process.env, PATH: [...prepend, ...parts].join(path.delimiter) }
}

function credentialsPath(): string {
  return path.join(profileHome(), 'credentials.json')
}

function instancePath(): string {
  return path.join(profileHome(), 'instance.json')
}

function prefsPath(): string {
  return path.join(profileHome(), 'desktop.json')
}

function defaultPrefs(): DesktopPrefs {
  return {
    autoStartOnLaunch: true,
    autoStopOnQuit: false,
  }
}

function readPrefs(): DesktopPrefs {
  try {
    return { ...defaultPrefs(), ...JSON.parse(fs.readFileSync(prefsPath(), 'utf8')) }
  } catch {
    return defaultPrefs()
  }
}

function writePrefs(partial: Partial<DesktopPrefs>): DesktopPrefs {
  const next = { ...readPrefs(), ...partial }
  fs.mkdirSync(profileHome(), { recursive: true, mode: 0o700 })
  fs.writeFileSync(prefsPath(), `${JSON.stringify(next, null, 2)}\n`, { mode: 0o600 })
  return next
}

function readCredentials(): Credentials | null {
  try {
    return JSON.parse(fs.readFileSync(credentialsPath(), 'utf8')) as Credentials
  } catch {
    return null
  }
}

function readPid(): number | null {
  try {
    const raw = fs.readFileSync(path.join(profileHome(), 'daemon.pid'), 'utf8').trim()
    const pid = Number(raw)
    return Number.isFinite(pid) && pid > 0 ? pid : null
  } catch {
    return null
  }
}

function readOrCreateInstanceId(): string {
  try {
    const f = JSON.parse(fs.readFileSync(instancePath(), 'utf8')) as { id?: string }
    if (f && f.id) return String(f.id)
  } catch {
    /* create below */
  }
  const id = randomUUID()
  fs.mkdirSync(profileHome(), { recursive: true, mode: 0o700 })
  fs.writeFileSync(
    instancePath(),
    `${JSON.stringify({ id, profile: DESKTOP_PROFILE }, null, 2)}\n`,
    { mode: 0o600 },
  )
  return id
}

function formatUptime(ms: number): string {
  if (!ms || ms < 0) return '—'
  const sec = Math.floor(ms / 1000)
  const h = Math.floor(sec / 3600)
  const m = Math.floor((sec % 3600) / 60)
  const s = sec % 60
  if (h > 0) return `${h}h ${m}m`
  if (m > 0) return `${m}m ${s}s`
  return `${s}s`
}

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
  void mainWindow.loadURL(WEB_DEV_URL)
}

function runCli(args: string[]): Promise<CliResult> {
  return new Promise((resolve) => {
    const child = spawn(resolveCliPath(), ['--profile', DESKTOP_PROFILE, ...args], {
      shell: false,
      env: cliEnv(),
    })
    let out = ''
    let err = ''
    child.stdout?.on('data', (d: Buffer) => {
      out += d.toString()
    })
    child.stderr?.on('data', (d: Buffer) => {
      err += d.toString()
    })
    child.on('error', (e: Error) => {
      resolve({ code: -1, out: '', err: e.message })
    })
    child.on('close', (code) => {
      resolve({ code: code ?? 0, out, err })
    })
  })
}

async function stopDaemonProcess() {
  const r = await runCli(['daemon', 'stop'])
  daemonChild = null
  daemonStartedAt = null
  return r
}

async function startDaemonProcess() {
  await stopDaemonProcess()
  if (!readCredentials()?.daemonToken) {
    return { running: false, message: 'Desktop Daemon 未启动：请先登录（将自动同步凭证）' }
  }
  const cli = resolveCliPath()
  if (!fs.existsSync(cli)) {
    return { running: false, message: `Daemon 二进制不存在：${cli}` }
  }
  daemonChild = spawn(cli, ['--profile', DESKTOP_PROFILE, 'daemon', 'start'], {
    detached: true,
    stdio: 'ignore',
    env: cliEnv(),
  })
  daemonChild.unref()
  daemonStartedAt = Date.now()
  return {
    running: true,
    message: `已启动 Desktop Daemon（profile=${DESKTOP_PROFILE} · v${daemonEnsure.version || '?'}）`,
  }
}

async function buildStatus() {
  const r = await runCli(['daemon', 'status'])
  const running = /running/i.test(r.out) && !/not running/i.test(r.out)
  const creds = readCredentials()
  const email = creds?.email || ''
  const prefs = readPrefs()
  const pid = readPid()
  const cli = resolveCliPath()
  const cliInstalled = fs.existsSync(cli)
  const ver = await runCli(['version', '--json'])
  let cliVersion = daemonEnsure.version || ''
  try {
    cliVersion = (JSON.parse(ver.out || '{}') as { version?: string }).version || cliVersion
  } catch {
    /* ignore */
  }
  return {
    running,
    email,
    profile: DESKTOP_PROFILE,
    pid: pid || null,
    daemonId: readOrCreateInstanceId(),
    server: creds?.server || '',
    deviceName: os.hostname(),
    uptime:
      running && daemonStartedAt
        ? formatUptime(Date.now() - daemonStartedAt)
        : running
          ? '—'
          : '—',
    cliInstalled,
    cliPath: cli,
    cliVersion,
    cliEnsureOk: !!daemonEnsure.ok,
    cliEnsureMessage: daemonEnsure.message || '',
    autoStartOnLaunch: !!prefs.autoStartOnLaunch,
    autoStopOnQuit: !!prefs.autoStopOnQuit,
    message: email
      ? running
        ? `Daemon 运行中 · desktop · v${cliVersion || '?'} · ${email}`
        : `Daemon 未运行 · desktop · 已绑定 ${email}`
      : (r.out || r.err || 'ok').trim(),
  }
}

ipcMain.handle('daemon:status', async () => buildStatus())

ipcMain.handle('daemon:start', async () => {
  await startDaemonProcess()
  return buildStatus()
})
ipcMain.handle('daemon:stop', async () => {
  await stopDaemonProcess()
  return buildStatus()
})
ipcMain.handle('daemon:restart', async () => {
  await startDaemonProcess()
  return buildStatus()
})

ipcMain.handle('daemon:prefs', async (_evt: IpcMainInvokeEvent, partial?: Partial<DesktopPrefs>) => {
  if (partial && typeof partial === 'object') {
    writePrefs(partial)
  }
  return readPrefs()
})

ipcMain.handle(
  'daemon:apply-credentials',
  async (_evt: IpcMainInvokeEvent, payload?: Credentials) => {
    const email = String(payload?.email || '').trim()
    const daemonToken = String(payload?.daemonToken || '').trim()
    const server = String(payload?.server || 'http://127.0.0.1:8080').trim()
    if (!email || !daemonToken) {
      return { ok: false, message: '缺少 email 或 daemonToken' }
    }
    try {
      fs.mkdirSync(profileHome(), { recursive: true, mode: 0o700 })
      fs.writeFileSync(
        credentialsPath(),
        `${JSON.stringify({ server, email, daemonToken }, null, 2)}\n`,
        { mode: 0o600 },
      )
      readOrCreateInstanceId()
      return { ok: true, message: `已同步 Desktop Daemon 账号 ${email}` }
    } catch (e) {
      const msg = e instanceof Error ? e.message : '写入凭证失败'
      return { ok: false, message: msg }
    }
  },
)

ipcMain.handle('daemon:account', async () => {
  const creds = readCredentials()
  return {
    email: creds?.email || '',
    server: creds?.server || '',
    profile: DESKTOP_PROFILE,
    daemonId: readOrCreateInstanceId(),
  }
})

ipcMain.handle('runtime:add', async (_evt: IpcMainInvokeEvent, provider?: string) => {
  const r = await runCli(['runtime', 'add', '--provider', String(provider || '')])
  const message = (r.err || r.out || '').trim() || (r.code === 0 ? 'ok' : '注册失败')
  return { ok: r.code === 0, message }
})

ipcMain.handle('runtime:detect', async (_evt: IpcMainInvokeEvent, provider?: string) => {
  const r = await runCli(['runtime', 'detect', '--provider', String(provider || '')])
  const message = (r.err || r.out || '').trim() || (r.code === 0 ? 'ok' : '未安装')
  return { ok: r.code === 0, message }
})

ipcMain.handle('runtime:enable', async (_evt: IpcMainInvokeEvent, provider?: string) => {
  const r = await runCli(['runtime', 'enable', '--provider', String(provider || '')])
  const message = (r.err || r.out || '').trim() || (r.code === 0 ? 'ok' : '启用失败')
  return { ok: r.code === 0, message }
})

ipcMain.handle('runtime:remove', async (_evt: IpcMainInvokeEvent, provider?: string) => {
  const r = await runCli(['runtime', 'remove', '--provider', String(provider || '')])
  const message = (r.err || r.out || '').trim() || (r.code === 0 ? 'ok' : '移除失败')
  return { ok: r.code === 0, message }
})

ipcMain.handle('runtime:validate-command', async (_evt: IpcMainInvokeEvent, command?: string) => {
  const r = await runCli(['runtime', 'validate-command', '--command', String(command || '')])
  const message = (r.err || r.out || '').trim() || (r.code === 0 ? 'ok' : '命令无效')
  return { ok: r.code === 0, message }
})

ipcMain.handle(
  'runtime:add-custom',
  async (
    _evt: IpcMainInvokeEvent,
    payload?: { base?: string; name?: string; command?: string; description?: string },
  ) => {
    const base = String(payload?.base || '')
    const name = String(payload?.name || '')
    const command = String(payload?.command || '')
    const description = String(payload?.description || '')
    const args = ['runtime', 'add-custom', '--base', base, '--name', name, '--command', command]
    if (description) args.push('--description', description)
    const r = await runCli(args)
    const message = (r.err || r.out || '').trim() || (r.code === 0 ? 'ok' : '创建失败')
    return { ok: r.code === 0, message }
  },
)

ipcMain.handle('dialog:select-directory', async () => {
  const win = BrowserWindow.getFocusedWindow() || mainWindow
  const result = win
    ? await dialog.showOpenDialog(win, {
        properties: ['openDirectory', 'createDirectory'],
      })
    : await dialog.showOpenDialog({
        properties: ['openDirectory', 'createDirectory'],
      })
  if (result.canceled || !result.filePaths?.length) {
    return { ok: false, path: '' }
  }
  return { ok: true, path: result.filePaths[0] }
})

app.whenReady().then(async () => {
  // 启动前对齐 Daemon 版本，避免落后二进制导致协议探测等功能缺失
  daemonEnsure = ensureDaemon()
  if (!daemonEnsure.ok) {
    console.warn('[desktop]', daemonEnsure.message)
  } else if (daemonEnsure.rebuilt) {
    console.log('[desktop]', daemonEnsure.message)
  }

  createWindow()
  const prefs = readPrefs()
  if (prefs.autoStartOnLaunch) {
    try {
      // 版本刚升级时强制用新二进制重启
      await startDaemonProcess()
    } catch {
      /* ignore */
    }
  }
  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow()
  })
})

app.on('window-all-closed', async () => {
  const prefs = readPrefs()
  if (prefs.autoStopOnQuit) {
    try {
      await stopDaemonProcess()
    } catch {
      /* ignore */
    }
  }
  if (process.platform !== 'darwin') app.quit()
})

app.on('before-quit', async () => {
  const prefs = readPrefs()
  if (!prefs.autoStopOnQuit) return
  try {
    await stopDaemonProcess()
  } catch {
    /* ignore */
  }
})
