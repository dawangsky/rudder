/**
 * 确保 Electron 二进制完整（path.txt + Framework）。
 * 下载失败时请设置：export ELECTRON_MIRROR=https://npmmirror.com/mirrors/electron/
 */
import fs from 'fs'
import path from 'path'
import { spawnSync } from 'child_process'

/** desktop/ 包根：兼容源码 scripts/ 与编译后 dist/scripts/ */
function packageRoot(): string {
  const here = __dirname
  if (path.basename(path.dirname(here)) === 'dist') {
    return path.resolve(here, '..', '..')
  }
  return path.resolve(here, '..')
}

const electronDir = path.join(packageRoot(), 'node_modules', 'electron')
const pathFile = path.join(electronDir, 'path.txt')
const bin = path.join(electronDir, 'dist', 'Electron.app', 'Contents', 'MacOS', 'Electron')
const framework = path.join(
  electronDir,
  'dist',
  'Electron.app',
  'Contents',
  'Frameworks',
  'Electron Framework.framework',
)

function ok(): boolean {
  return fs.existsSync(pathFile) && fs.existsSync(bin) && fs.existsSync(framework)
}

if (ok()) {
  process.exit(0)
}

if (!process.env.ELECTRON_MIRROR) {
  process.env.ELECTRON_MIRROR = 'https://npmmirror.com/mirrors/electron/'
}

const installJs = path.join(electronDir, 'install.js')
if (!fs.existsSync(installJs)) {
  console.warn('[ensure-electron] electron package missing; run npm install first')
  process.exit(0)
}

console.log('[ensure-electron] repairing Electron binary via install.js ...')
const r = spawnSync(process.execPath, [installJs], {
  cwd: electronDir,
  env: process.env,
  stdio: 'inherit',
})
if (r.status !== 0 || !ok()) {
  // 兜底：若 Framework 已在但缺 path.txt
  if (fs.existsSync(bin) && fs.existsSync(framework) && !fs.existsSync(pathFile)) {
    fs.writeFileSync(pathFile, 'Electron.app/Contents/MacOS/Electron')
  }
}
if (!ok()) {
  console.error(
    '[ensure-electron] Electron 仍不完整。请手动：\n' +
      '  export ELECTRON_MIRROR=https://npmmirror.com/mirrors/electron/\n' +
      '  rm -rf node_modules/electron && npm install\n' +
      '  或从缓存 unzip electron-v*-darwin-*.zip 到 node_modules/electron/dist',
  )
  process.exit(1)
}
console.log('[ensure-electron] ok')
