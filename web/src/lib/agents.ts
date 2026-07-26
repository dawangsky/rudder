/** 智能体列表/创建共用类型与展示工具。 */

export type Agent = {
  id: string
  name: string
  avatar?: string
  description?: string
  instructions?: string
  provider: string
  runtimeId?: string | null
  maxConcurrency?: number
  status?: string
  skillIds?: string[]
  createdAt?: string
  updatedAt?: string
}

export type AgentFilter = 'mine' | 'all' | 'archived'

export function ownerDisplayName(email: string) {
  if (!email) return '—'
  const local = email.split('@')[0] || email
  return local.replace(/[._-]+/g, ' ').trim() || local
}

export function ownerInitials(email: string) {
  const name = ownerDisplayName(email)
  if (name === '—') return '?'
  const parts = name.split(/\s+/).filter(Boolean)
  if (parts.length >= 2) return (parts[0][0] + parts[1][0]).toUpperCase()
  return name.slice(0, 2).toUpperCase()
}

export function formatRelative(iso?: string) {
  if (!iso) return '—'
  // Java LocalDateTime 可能无时区，按本地解析
  const normalized = iso.includes('T') ? iso : iso.replace(' ', 'T')
  const t = Date.parse(normalized)
  if (Number.isNaN(t)) return iso
  const sec = Math.max(0, Math.floor((Date.now() - t) / 1000))
  if (sec < 60) return '刚刚'
  const min = Math.floor(sec / 60)
  if (min < 60) return `${min} 分钟前`
  const hr = Math.floor(min / 60)
  if (hr < 24) return `${hr} 小时前`
  const day = Math.floor(hr / 24)
  if (day < 30) return `${day} 天前`
  return new Date(t).toLocaleDateString()
}

export function agentStatusLabel(status?: string, runtimeOnline?: boolean) {
  if ((status || '').toLowerCase() === 'archived') return '已归档'
  if (runtimeOnline === false) return '离线'
  if (status === 'busy' || status === 'running') return '忙碌'
  if (runtimeOnline === true || status === 'idle' || status === 'online') return '在线'
  return status || '未知'
}

/** 详情页状态文案：在线 · 空闲 / 在线 · 忙碌 / 离线 / 已归档 */
export function agentDetailStatus(status?: string, runtimeOnline?: boolean) {
  if ((status || '').toLowerCase() === 'archived') return '已归档'
  if (runtimeOnline === false) return '离线'
  if (status === 'busy' || status === 'running') return '在线 · 忙碌'
  if (runtimeOnline === true || status === 'idle' || status === 'online') return '在线 · 空闲'
  return status || '未知'
}

export type AgentDetailTab = 'overview' | 'work' | 'skills' | 'settings'
export type AgentSettingsSection = 'general' | 'access' | 'env' | 'params'
