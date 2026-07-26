#!/usr/bin/env node
/**
 * 确保 Desktop 使用的 daemon/rudder 与 daemon/VERSION 一致。
 * 版本落后、二进制缺失或源码比二进制新时自动 go build。
 */
const { spawnSync } = require('child_process')
const fs = require('fs')
const path = require('path')
const os = require('os')

const daemonDir = path.resolve(__dirname, '..', '..', 'daemon')
const binaryPath = path.join(daemonDir, process.platform === 'win32' ? 'rudder.exe' : 'rudder')
const versionFile = path.join(daemonDir, 'VERSION')
const versionPkg = 'github.com/dawangsky/rudder/daemon/internal/version'

function readExpectedVersion() {
  try {
    return fs.readFileSync(versionFile, 'utf8').trim()
  } catch {
    return ''
  }
}

function readInstalledVersion() {
  if (!fs.existsSync(binaryPath)) return null
  const r = spawnSync(binaryPath, ['version', '--json'], {
    encoding: 'utf8',
    env: process.env,
  })
  if (r.status !== 0) {
    // 旧二进制无 --json
    const r2 = spawnSync(binaryPath, ['version'], { encoding: 'utf8', env: process.env })
    const m = String(r2.stdout || '').match(/rudder-cli\s+(\S+)/)
    return m ? m[1] : null
  }
  try {
    return JSON.parse(String(r.stdout || '{}')).version || null
  } catch {
    return null
  }
}

function latestSourceMtime(dir) {
  let latest = 0
  const skip = new Set(['rudder', 'rudder.exe', '.git'])
  function walk(d) {
    let entries
    try {
      entries = fs.readdirSync(d, { withFileTypes: true })
    } catch {
      return
    }
    for (const ent of entries) {
      if (skip.has(ent.name) || ent.name.startsWith('.')) continue
      const p = path.join(d, ent.name)
      if (ent.isDirectory()) {
        walk(p)
        continue
      }
      if (!/\.(go|mod|sum)$/.test(ent.name) && ent.name !== 'VERSION') continue
      try {
        const st = fs.statSync(p)
        if (st.mtimeMs > latest) latest = st.mtimeMs
      } catch {
        /* ignore */
      }
    }
  }
  walk(dir)
  return latest
}

function needsRebuild(expected, installed) {
  if (!fs.existsSync(binaryPath)) {
    return { yes: true, reason: '二进制不存在' }
  }
  if (!installed || installed !== expected) {
    return { yes: true, reason: `版本不匹配（当前 ${installed || 'unknown'}，期望 ${expected}）` }
  }
  try {
    const binMtime = fs.statSync(binaryPath).mtimeMs
    const srcMtime = latestSourceMtime(daemonDir)
    if (srcMtime > binMtime + 1000) {
      return { yes: true, reason: '源码比二进制更新' }
    }
  } catch {
    /* ignore */
  }
  return { yes: false, reason: '' }
}

function buildDaemon(expected) {
  const ldflags = `-X ${versionPkg}.Version=${expected} -X ${versionPkg}.BuiltAt=${new Date().toISOString()}`
  const args = ['build', `-ldflags=${ldflags}`, '-o', binaryPath, './cmd/rudder']
  const r = spawnSync('go', args, {
    cwd: daemonDir,
    encoding: 'utf8',
    env: {
      ...process.env,
      GOTOOLCHAIN: process.env.GOTOOLCHAIN || 'auto',
    },
  })
  if (r.status !== 0) {
    const err = (r.stderr || r.stdout || 'go build failed').trim()
    throw new Error(err)
  }
}

/**
 * @returns {{ ok: boolean, rebuilt: boolean, version: string, binary: string, message: string }}
 */
function ensureDaemon() {
  const expected = readExpectedVersion()
  if (!expected) {
    return {
      ok: false,
      rebuilt: false,
      version: '',
      binary: binaryPath,
      message: `缺少 ${versionFile}`,
    }
  }
  const installed = readInstalledVersion()
  const check = needsRebuild(expected, installed)
  if (!check.yes) {
    return {
      ok: true,
      rebuilt: false,
      version: expected,
      binary: binaryPath,
      message: `Daemon 已是最新 ${expected}`,
    }
  }
  try {
    console.log(`[ensure-daemon] ${check.reason}，正在编译 ${expected}…`)
    buildDaemon(expected)
    const after = readInstalledVersion()
    if (after !== expected) {
      return {
        ok: false,
        rebuilt: true,
        version: after || '',
        binary: binaryPath,
        message: `编译完成但版本仍为 ${after || 'unknown'}（期望 ${expected}）`,
      }
    }
    console.log(`[ensure-daemon] 已升级到 ${expected}`)
    return {
      ok: true,
      rebuilt: true,
      version: expected,
      binary: binaryPath,
      message: `已将 Daemon 升级到 ${expected}`,
    }
  } catch (e) {
    return {
      ok: false,
      rebuilt: false,
      version: installed || '',
      binary: binaryPath,
      message: `自动编译 Daemon 失败：${e.message || e}。请在 daemon/ 下执行 go build -o rudder ./cmd/rudder`,
    }
  }
}

if (require.main === module) {
  const result = ensureDaemon()
  console.log(result.message)
  process.exit(result.ok ? 0 : 1)
}

module.exports = { ensureDaemon, binaryPath, readExpectedVersion }
