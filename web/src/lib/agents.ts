/** 智能体列表/创建共用类型与展示工具。 */

export type Agent = {
  id: string
  name: string
  avatar?: string
  description?: string
  instructions?: string
  provider: string
  runtimeId?: string | null
  model?: string
  thinkingMode?: string
  maxConcurrency?: number
  status?: string
  skillIds?: string[]
  createdAt?: string
  updatedAt?: string
}

export type ModelOption = { value: string; label: string }

/** 按 Provider 提供可选模型列表 */
export function modelOptionsForProvider(provider?: string): ModelOption[] {
  const p = (provider || '').toLowerCase()
  if (p === 'claude_code' || p.startsWith('claude')) {
    return [
      { value: 'default', label: '默认' },
      { value: 'claude-sonnet-4', label: 'Claude Sonnet' },
      { value: 'claude-opus-4', label: 'Claude Opus' },
      { value: 'claude-haiku', label: 'Claude Haiku' },
    ]
  }
  if (p === 'cursor') {
    return [
      { value: 'default', label: '默认' },
      { value: 'composer', label: 'Composer' },
      { value: 'sonnet', label: 'Sonnet' },
      { value: 'gpt-5', label: 'GPT-5' },
    ]
  }
  if (p === 'codex' || p.startsWith('openai') || p === 'gpt' || p === 'copilot') {
    return [
      { value: 'default', label: '默认' },
      { value: 'gpt-5', label: 'GPT-5' },
      { value: 'o3', label: 'o3' },
      { value: 'gpt-4.1', label: 'GPT-4.1' },
    ]
  }
  if (p === 'opencode' || p === 'gemini') {
    return [
      { value: 'default', label: '默认' },
      { value: 'gemini-2.5-pro', label: 'Gemini 2.5 Pro' },
      { value: 'gemini-2.5-flash', label: 'Gemini 2.5 Flash' },
    ]
  }
  if (p === 'qwen' || p === 'qoder' || p === 'codebuddy') {
    return [
      { value: 'default', label: '默认' },
      { value: 'qwen3-coder', label: 'Qwen3 Coder' },
      { value: 'qwen-max', label: 'Qwen Max' },
    ]
  }
  if (p === 'kimi') {
    return [
      { value: 'default', label: '默认' },
      { value: 'kimi-k2', label: 'Kimi K2' },
      { value: 'moonshot-v1', label: 'Moonshot V1' },
    ]
  }
  if (p === 'traecli' || p === 'deveco') {
    return [
      { value: 'default', label: '默认' },
      { value: 'doubao', label: '豆包' },
      { value: 'deepseek', label: 'DeepSeek' },
    ]
  }
  return [{ value: 'default', label: '默认' }]
}

export const THINKING_OPTIONS: ModelOption[] = [
  { value: 'cli', label: '跟随 CLI 配置' },
  { value: 'low', label: '低' },
  { value: 'medium', label: '中' },
  { value: 'high', label: '高' },
]

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
