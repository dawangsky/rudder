/** Skills 列表 / 新建共用类型与模板。 */

export type SkillSourceType = 'manual' | 'url' | 'runtime'

export type Skill = {
  id: string
  name: string
  description?: string
  content: string
  sourceType?: SkillSourceType | string
  sourceRef?: string
  createdAt?: string
  updatedAt?: string
}

export type RuntimeSkill = {
  id: string
  name: string
  description?: string
  sourcePath: string
  contentHash?: string
  reportedAt?: string
}

export type SkillUrlPreview = {
  name: string
  description?: string
  content: string
  sourceUrl: string
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
