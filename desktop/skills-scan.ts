/**
 * 扫描本机常见 skill 根目录下的 SKILL.md（与 daemon/internal/skills/scan.go 对齐）。
 *
 * 忽略：软链、点目录（含 Codex `.system`）、不可读、超大 SKILL.md、超大目录。
 * 附属文件（references 等）单文件过大时跳过该文件计入，不整包丢弃。
 */
import crypto from 'crypto'
import fs from 'fs'
import os from 'os'
import path from 'path'

const MAX_DEPTH = 6
/** 导入用的 SKILL.md 正文上限 */
const MAX_SKILL_MD_BYTES = 1024 * 1024
/** 附属文件单文件上限：超过则跳过该文件，不否决整个 skill */
const MAX_ASSET_FILE_BYTES = 4 * 1024 * 1024
/** 单个 skill 目录体积上限（递归、不含软链与超限附属文件） */
const MAX_SKILL_DIR_BYTES = 16 * 1024 * 1024
/** 单个 skill 目录文件数上限 */
const MAX_SKILL_DIR_FILES = 200

export type LocalSkill = {
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

function skillRoots(home: string): Array<{ root: string; origin: string }> {
  const roots: Array<{ root: string; origin: string }> = [
    { root: path.join(home, '.agents', 'skills'), origin: 'agents' },
    { root: path.join(home, '.openclaw', 'skills'), origin: 'openclaw' },
    { root: path.join(home, '.claude', 'skills'), origin: 'claude' },
    { root: path.join(home, '.cursor', 'skills'), origin: 'cursor' },
  ]
  const codex = process.env.CODEX_HOME
  if (codex) {
    roots.push({ root: path.join(codex, 'skills'), origin: 'codex' })
  } else {
    roots.push({ root: path.join(home, '.codex', 'skills'), origin: 'codex' })
  }
  return roots
}

/** 点目录：.system（Codex 内置）、.git 等，不是用户可导入 skill。 */
function isSkippedDirName(name: string): boolean {
  return name === '.' || name === '..' || name.startsWith('.')
}

function unquote(s: string): string {
  if (s.length >= 2) {
    const q = s[0]
    if ((q === '"' || q === "'") && s[s.length - 1] === q) return s.slice(1, -1)
  }
  return s
}

function parseFrontmatter(content: string): { name: string; description: string } {
  const m = content.match(/^---\s*\r?\n([\s\S]*?)\r?\n---\s*\r?\n?/)
  if (!m) return { name: '', description: '' }
  let name = ''
  let description = ''
  for (const line of m[1].split('\n')) {
    const t = line.trim()
    if (t.startsWith('name:')) {
      name = unquote(t.slice('name:'.length).trim())
    } else if (t.startsWith('description:')) {
      description = unquote(t.slice('description:'.length).trim())
    }
  }
  return { name, description }
}

function toDisplayPath(absPath: string, home: string): string {
  const skillDir = path.dirname(absPath)
  if (skillDir === home || skillDir.startsWith(home + path.sep)) {
    return ('~' + skillDir.slice(home.length)).replace(/\\/g, '/')
  }
  return skillDir.replace(/\\/g, '/')
}

/** 统计 skill 目录；超限或不安全则返回 null。 */
function measureSkillDir(dir: string): { fileCount: number; bytes: number } | null {
  let fileCount = 0
  let bytes = 0
  const walk = (d: string, depth: number): boolean => {
    if (depth > MAX_DEPTH) return true
    let entries: fs.Dirent[]
    try {
      entries = fs.readdirSync(d, { withFileTypes: true })
    } catch {
      // 不可读子目录：跳过该层，不整包失败
      return true
    }
    for (const e of entries) {
      if (e.name === '.DS_Store') continue
      if (e.isSymbolicLink()) continue
      const p = path.join(d, e.name)
      if (e.isDirectory()) {
        if (isSkippedDirName(e.name)) continue
        if (!walk(p, depth + 1)) return false
        continue
      }
      if (!e.isFile()) continue
      let st: fs.Stats
      try {
        st = fs.lstatSync(p)
      } catch {
        continue
      }
      if (st.isSymbolicLink() || !st.isFile()) continue
      // 附属大文件（如 iconpark-index.json）跳过计入，不整包失败
      if (st.size > MAX_ASSET_FILE_BYTES) continue
      fileCount += 1
      bytes += st.size
      if (fileCount > MAX_SKILL_DIR_FILES || bytes > MAX_SKILL_DIR_BYTES) return false
    }
    return true
  }
  if (!walk(dir, 0)) return null
  return { fileCount: Math.max(1, fileCount), bytes }
}

function readSkill(skillMdPath: string, origin: string, home: string): LocalSkill | null {
  let st: fs.Stats
  try {
    st = fs.lstatSync(skillMdPath)
  } catch {
    return null
  }
  if (st.isSymbolicLink() || !st.isFile() || st.size <= 0 || st.size > MAX_SKILL_MD_BYTES) {
    return null
  }
  let content: string
  try {
    content = fs.readFileSync(skillMdPath, 'utf8')
  } catch {
    return null
  }
  if (!content) return null
  const skillDir = path.dirname(skillMdPath)
  const measured = measureSkillDir(skillDir)
  if (!measured) return null
  const { name: fmName, description } = parseFrontmatter(content)
  const name = fmName || path.basename(skillDir)
  const hash = crypto.createHash('sha256').update(content, 'utf8').digest('hex').slice(0, 16)
  return {
    id: `local:${hash}:${skillMdPath}`,
    name,
    description,
    content,
    sourcePath: skillMdPath,
    displayPath: toDisplayPath(skillMdPath, home),
    contentHash: hash,
    origin,
    fileCount: measured.fileCount,
  }
}

function walkSkillMd(
  root: string,
  origin: string,
  home: string,
  seen: Set<string>,
  out: LocalSkill[],
): void {
  const walk = (dir: string, depth: number) => {
    if (depth > MAX_DEPTH) return
    let entries: fs.Dirent[]
    try {
      entries = fs.readdirSync(dir, { withFileTypes: true })
    } catch {
      return
    }
    for (const e of entries) {
      if (e.isSymbolicLink()) continue
      const p = path.join(dir, e.name)
      if (e.isDirectory()) {
        if (isSkippedDirName(e.name)) continue
        walk(p, depth + 1)
        continue
      }
      if (!e.isFile()) continue
      if (e.name.toLowerCase() !== 'skill.md') continue
      if (seen.has(p)) continue
      const sk = readSkill(p, origin, home)
      if (!sk) continue
      seen.add(p)
      out.push(sk)
    }
  }
  walk(root, 0)
}

/** 扫描本机 skill；按名称排序。 */
export function scanLocalSkills(): LocalSkill[] {
  const home = os.homedir()
  if (!home) return []
  const seen = new Set<string>()
  const out: LocalSkill[] = []
  for (const { root, origin } of skillRoots(home)) {
    try {
      const st = fs.lstatSync(root)
      if (st.isSymbolicLink() || !st.isDirectory()) continue
    } catch {
      continue
    }
    walkSkillMd(root, origin, home, seen, out)
  }
  out.sort((a, b) => a.name.localeCompare(b.name) || a.displayPath.localeCompare(b.displayPath))
  return out
}
