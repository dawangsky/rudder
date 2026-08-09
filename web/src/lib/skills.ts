/** Skills 列表 / 新建 / 详情共用类型与模板。 */

export type SkillSourceType = 'manual' | 'url' | 'runtime'

export type SkillAgentBrief = {
  id: string
  name: string
  description?: string
  avatar?: string
  provider?: string
}

export type Skill = {
  id: string
  name: string
  description?: string
  content: string
  sourceType?: SkillSourceType | string
  sourceRef?: string
  createdByUserId?: string | null
  createdBy?: string
  agentCount?: number
  agents?: SkillAgentBrief[]
  createdAt?: string
  updatedAt?: string
  /** create/import 接口返回 */
  action?: 'created' | 'updated' | string
}

export type RuntimeSkill = {
  id: string
  name: string
  description?: string
  sourcePath: string
  /** 展示用路径（如 ~/.agents/skills/foo） */
  displayPath?: string
  contentHash?: string
  reportedAt?: string
  /** 来源根目录标签：claude / agents / cursor… */
  origin?: string
  fileCount?: number
  /** Desktop 本地扫描时带内容，可直接 POST /api/skills */
  content?: string
  /** local = 本机扫描；reported = Daemon 上报缓存 */
  source?: 'local' | 'reported'
}

export type SkillUrlPreview = {
  name: string
  description?: string
  content: string
  sourceUrl: string
}

export type ImportResultAction = 'created' | 'updated' | 'conflict' | 'skipped' | 'failed'

export type ImportResultItem = {
  name: string
  action: ImportResultAction
  message?: string
}

export type ImportResultSummary = {
  created: number
  updated: number
  conflict: number
  skipped: number
  failed: number
  items: ImportResultItem[]
}

export function emptyImportResult(): ImportResultSummary {
  return { created: 0, updated: 0, conflict: 0, skipped: 0, failed: 0, items: [] }
}

export function skillOriginFromPath(sourcePath?: string): string {
  if (!sourcePath) return 'local'
  const p = sourcePath.replace(/\\/g, '/').toLowerCase()
  if (p.includes('/.claude/')) return 'claude'
  if (p.includes('/.agents/')) return 'agents'
  // 仅 skills 目录；排除 skills-*（如 skills-cursor）
  if (/\/\.cursor\/skills(\/|$)/.test(p)) return 'cursor'
  if (p.includes('/.codex/')) return 'codex'
  if (p.includes('/.openclaw/')) return 'openclaw'
  return 'local'
}

/** 将绝对路径收成展示用目录（去掉 SKILL.md；尽量保留 ~/.…）。 */
export function formatSkillDisplayPath(sourcePath?: string): string {
  if (!sourcePath) return ''
  let p = sourcePath.replace(/\\/g, '/')
  if (p.toLowerCase().endsWith('/skill.md')) {
    p = p.slice(0, -'/skill.md'.length)
  }
  return p
}

export function defaultSkillMarkdown(name = 'my-skill') {
  return `---
name: ${name}
description: 简要说明这个 skill 做什么、何时使用。
---

# ${name}

## 何时使用

- …

## 步骤

1. …
2. …
`
}

export function sourceLabel(type?: string) {
  switch (type) {
    case 'url':
      return 'URL'
    case 'runtime':
      return '运行时'
    case 'manual':
    default:
      return '手动'
  }
}

export function formatSkillTime(iso?: string) {
  if (!iso) return '—'
  const normalized = iso.includes('T') ? iso : iso.replace(' ', 'T')
  const t = Date.parse(normalized)
  if (Number.isNaN(t)) return iso
  const sec = Math.max(0, Math.floor((Date.now() - t) / 1000))
  if (sec < 60) return '刚刚'
  if (sec < 3600) return `${Math.floor(sec / 60)} 分钟前`
  if (sec < 86400) return `${Math.floor(sec / 3600)} 小时前`
  if (sec < 86400 * 30) return `${Math.floor(sec / 86400)} 天前`
  return new Date(t).toLocaleDateString()
}

export function creatorInitials(name?: string) {
  const s = (name || '').trim()
  if (!s) return '?'
  const parts = s.split(/\s+/).filter(Boolean)
  if (parts.length >= 2) return (parts[0][0] + parts[1][0]).toUpperCase()
  return s.slice(0, 2).toUpperCase()
}
