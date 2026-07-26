/** 工作区运行时协议目录（设置页管理，运行时/智能体消费）。 */

import { computed, ref } from 'vue'
import { apiFetch } from '@/lib/api'
import {
  DEFAULT_PROVIDERS,
  setProviderCatalog,
  type ProviderMeta,
} from '@/lib/runtimes'

export type ProtocolRecord = ProviderMeta & {
  id?: string
  bins?: string[]
  enabled?: boolean
  builtin?: boolean
  sortOrder?: number
  updatedAt?: string
}

const allProtocols = ref<ProtocolRecord[]>(
  DEFAULT_PROVIDERS.map((p) => ({ ...p, enabled: true, builtin: true, bins: [] })),
)

let loading: Promise<void> | null = null

function compareByLabel(a: ProtocolRecord, b: ProtocolRecord) {
  const la = (a.label || a.value || '').trim()
  const lb = (b.label || b.value || '').trim()
  return la.localeCompare(lb, 'zh-Hans-CN', { sensitivity: 'base', numeric: true })
}

export const protocols = computed(() =>
  [...allProtocols.value].sort(compareByLabel),
)

/** 已启用协议（创建运行时 / 智能体用） */
export const enabledProtocols = computed(() =>
  allProtocols.value.filter((p) => p.enabled !== false).sort(compareByLabel),
)

/** 不含 stub 的已启用协议（自定义运行时基础协议选择） */
export const protocolOptions = computed(() =>
  enabledProtocols.value.filter((p) => p.value !== 'stub'),
)

export function baseProviderIdsFrom(list: { value: string }[]) {
  return [...list.map((p) => p.value)].sort((a, b) => b.length - a.length || a.localeCompare(b))
}

export async function loadProtocols(force = false): Promise<ProtocolRecord[]> {
  if (loading && !force) {
    await loading
    return allProtocols.value
  }
  loading = (async () => {
    try {
      const list = await apiFetch<ProtocolRecord[]>('/api/protocols')
      if (Array.isArray(list) && list.length) {
        allProtocols.value = list.map(normalizeRecord)
        setProviderCatalog(allProtocols.value)
      }
    } catch {
      // 离线/未登录：保留 DEFAULT
      if (!allProtocols.value.length) {
        allProtocols.value = DEFAULT_PROVIDERS.map((p) => ({
          ...p,
          enabled: true,
          builtin: true,
          bins: [],
        }))
      }
    } finally {
      loading = null
    }
  })()
  await loading
  return allProtocols.value
}

export async function createProtocol(body: {
  code: string
  label: string
  short?: string
  bins?: string[] | string
  commandHint?: string
  region?: string
  enabled?: boolean
}) {
  const created = await apiFetch<ProtocolRecord>('/api/protocols', {
    method: 'POST',
    body: JSON.stringify(body),
  })
  await loadProtocols(true)
  return created
}

export async function updateProtocol(
  code: string,
  body: Partial<{
    label: string
    short: string
    bins: string[] | string
    commandHint: string
    region: string
    enabled: boolean
    sortOrder: number
  }>,
) {
  const updated = await apiFetch<ProtocolRecord>(`/api/protocols/${encodeURIComponent(code)}`, {
    method: 'PUT',
    body: JSON.stringify(body),
  })
  await loadProtocols(true)
  return updated
}

export async function deleteProtocol(code: string) {
  await apiFetch(`/api/protocols/${encodeURIComponent(code)}`, { method: 'DELETE' })
  await loadProtocols(true)
}

function normalizeRecord(r: ProtocolRecord): ProtocolRecord {
  const value = r.value || (r as { code?: string }).code || ''
  return {
    ...r,
    value,
    label: r.label || value,
    short: r.short || r.label || value,
    bins: Array.isArray(r.bins) ? r.bins : [],
    enabled: r.enabled !== false,
    builtin: !!r.builtin,
    region: r.region || 'intl',
    commandHint: r.commandHint || '',
  }
}
