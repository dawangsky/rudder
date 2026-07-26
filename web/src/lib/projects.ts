/** 项目列表 / 新建共用类型与标签。 */

export type ProjectStatus = 'planned' | 'in_progress' | 'completed' | 'canceled'
export type ProjectPriority = 'none' | 'low' | 'medium' | 'high' | 'urgent'

export type Project = {
  id: string
  name: string
  description?: string
  status?: ProjectStatus | string
  priority?: ProjectPriority | string
  assigneeUserId?: string | null
  localPath?: string | null
  repoUrl?: string
  startDate?: string | null
  dueDate?: string | null
  createdAt?: string
  updatedAt?: string
}

export type WorkspaceMember = {
  id: string
  email: string
  displayName: string
  role?: string
}

export const PROJECT_STATUSES: { value: ProjectStatus; label: string }[] = [
  { value: 'planned', label: '计划中' },
  { value: 'in_progress', label: '进行中' },
  { value: 'completed', label: '已完成' },
  { value: 'canceled', label: '已取消' },
]

export const PROJECT_PRIORITIES: { value: ProjectPriority; label: string }[] = [
  { value: 'urgent', label: '紧急' },
  { value: 'high', label: '高' },
  { value: 'medium', label: '中' },
  { value: 'low', label: '低' },
  { value: 'none', label: '无优先级' },
]

export function statusLabel(status?: string) {
  return PROJECT_STATUSES.find((s) => s.value === status)?.label || '计划中'
}

export function priorityLabel(priority?: string) {
  return PROJECT_PRIORITIES.find((p) => p.value === priority)?.label || '无优先级'
}

export function memberLabel(m?: WorkspaceMember | null) {
  if (!m) return '负责人'
  return m.displayName || m.email
}
