/** Issue 看板状态与展示辅助。 */

export type IssueStatus =
  | 'backlog'
  | 'todo'
  | 'in_progress'
  | 'in_review'
  | 'done'

export type IssuePriority = 'low' | 'medium' | 'high' | 'urgent' | string

export type Issue = {
  id: string
  title: string
  description?: string
  status: string
  priority: string
  assigneeType?: string | null
  assigneeId?: string | null
  projectId?: string | null
  createdAt?: string
  updatedAt?: string
}

export type IssueColumn = {
  id: IssueStatus
  label: string
  /** 列头图标色调 */
  tone: 'muted' | 'amber' | 'green' | 'blue'
}

export const ISSUE_COLUMNS: IssueColumn[] = [
  { id: 'backlog', label: '待规划', tone: 'muted' },
  { id: 'todo', label: '待办', tone: 'muted' },
  { id: 'in_progress', label: '进行中', tone: 'amber' },
  { id: 'in_review', label: '审核中', tone: 'green' },
  { id: 'done', label: '已完成', tone: 'blue' },
]

const STATUS_ALIASES: Record<string, IssueStatus> = {
  doing: 'in_progress',
  active: 'in_progress',
  'in-progress': 'in_progress',
  review: 'in_review',
  planned: 'backlog',
  planning: 'backlog',
}

export function normalizeIssueStatus(raw?: string | null): IssueStatus {
  if (!raw) return 'todo'
  const s = raw.trim().toLowerCase()
  if ((ISSUE_COLUMNS as { id: string }[]).some((c) => c.id === s)) return s as IssueStatus
  return STATUS_ALIASES[s] || 'todo'
}

export function issueKey(prefix: string, id: string) {
  const p = (prefix || 'WS').toUpperCase()
  return `${p}-${id}`
}

export function priorityLabel(p?: string) {
  const m: Record<string, string> = {
    low: '低',
    medium: '中',
    high: '高',
    urgent: '紧急',
  }
  return m[(p || '').toLowerCase()] || p || '中'
}

export function snippet(text?: string | null, max = 96) {
  const t = (text || '').replace(/\s+/g, ' ').trim()
  if (!t) return ''
  return t.length > max ? `${t.slice(0, max)}…` : t
}
