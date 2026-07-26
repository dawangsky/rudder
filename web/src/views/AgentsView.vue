<script setup lang="ts">
/**
 * 智能体列表（对齐 Multica）：搜索、我的/全部/已归档、表格列。
 */
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { apiFetch } from '@/lib/api'
import {
  agentStatusLabel,
  formatRelative,
  ownerDisplayName,
  ownerInitials,
  type Agent,
  type AgentFilter,
} from '@/lib/agents'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import ProviderIcon from '@/components/ProviderIcon.vue'
import { getCustomProviderIcon } from '@/lib/providerIcons'
import { displayName, providerLabel, type Runtime } from '@/lib/runtimes'
import { getSessionEmail } from '@/lib/session'

const router = useRouter()
const agents = ref<Agent[]>([])
const runtimes = ref<Runtime[]>([])
const q = ref('')
const filter = ref<AgentFilter>('mine')
const err = ref('')
const loading = ref(false)
const busy = ref(false)

type PendingAction = { type: 'archive' | 'delete'; agent: Agent } | null
const pending = ref<PendingAction>(null)
const ackDelete = ref(false)

const email = computed(() => getSessionEmail())
const ownerName = computed(() => ownerDisplayName(email.value))
const ownerAv = computed(() => ownerInitials(email.value))

const runtimeById = computed(() => {
  const m = new Map<string, Runtime>()
  for (const r of runtimes.value) m.set(r.id, r)
  return m
})

function runtimeFor(a: Agent): Runtime | undefined {
  if (a.runtimeId && runtimeById.value.has(a.runtimeId)) {
    return runtimeById.value.get(a.runtimeId)
  }
  return runtimes.value.find((r) => r.provider === a.provider)
}

function runtimeLabel(a: Agent) {
  const rt = runtimeFor(a)
  if (!rt) return providerLabel(a)
  const host = rt.hostName || ''
  return host ? `${displayName(rt)} (${host})` : displayName(rt)
}

function statusOf(a: Agent) {
  const rt = runtimeFor(a)
  return agentStatusLabel(a.status, rt ? rt.status === 'online' : undefined)
}

function isArchived(a: Agent) {
  return (a.status || '').toLowerCase() === 'archived'
}

const activeAgents = computed(() => agents.value.filter((a) => !isArchived(a)))
const archivedAgents = computed(() => agents.value.filter(isArchived))

const filtered = computed(() => {
  let list = filter.value === 'archived' ? archivedAgents.value : activeAgents.value
  const s = q.value.trim().toLowerCase()
  if (s) {
    list = list.filter(
      (a) =>
        a.name.toLowerCase().includes(s) ||
        (a.description || '').toLowerCase().includes(s) ||
        a.provider.toLowerCase().includes(s),
    )
  }
  return list
})

const counts = computed(() => ({
  mine: activeAgents.value.length,
  all: activeAgents.value.length,
  archived: archivedAgents.value.length,
}))

async function load() {
  loading.value = true
  err.value = ''
  try {
    const [a, r] = await Promise.all([
      apiFetch<Agent[]>('/api/agents'),
      apiFetch<Runtime[]>('/api/runtimes'),
    ])
    agents.value = a
    runtimes.value = r
  } catch (e) {
    err.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

function goCreate() {
  router.push({ name: 'agent-create' })
}

function openAgent(a: Agent) {
  router.push({ name: 'agent-detail', params: { agentId: a.id } })
}

async function restoreAgent(a: Agent) {
  err.value = ''
  busy.value = true
  try {
    await apiFetch(`/api/agents/${a.id}/restore`, { method: 'POST' })
    await load()
    filter.value = 'mine'
  } catch (e) {
    err.value = e instanceof Error ? e.message : '恢复失败'
  } finally {
    busy.value = false
  }
}

function askArchive(a: Agent) {
  ackDelete.value = false
  pending.value = { type: 'archive', agent: a }
}

function askDelete(a: Agent) {
  ackDelete.value = false
  pending.value = { type: 'delete', agent: a }
}

function closePending() {
  if (busy.value) return
  pending.value = null
  ackDelete.value = false
}

async function confirmPending() {
  if (!pending.value) return
  const { type, agent: a } = pending.value
  err.value = ''
  busy.value = true
  try {
    if (type === 'archive') {
      await apiFetch(`/api/agents/${a.id}/archive`, { method: 'POST' })
    } else {
      await apiFetch(`/api/agents/${a.id}`, { method: 'DELETE' })
    }
    pending.value = null
    ackDelete.value = false
    await load()
  } catch (e) {
    err.value = e instanceof Error ? e.message : type === 'archive' ? '归档失败' : '删除失败'
  } finally {
    busy.value = false
  }
}

onMounted(load)
</script>

<template>
  <section class="page">
    <header class="head">
      <div>
        <h2>智能体 <span class="count">{{ agents.length }}</span></h2>
        <p class="lead">
          能领取 issue、留下评论、推进状态的 AI 队友。
          <a class="more" href="https://github.com/dawangsky/rudder" target="_blank" rel="noreferrer">了解更多 →</a>
        </p>
      </div>
      <button type="button" class="btn-add" @click="goCreate">+ 新建智能体</button>
    </header>

    <p v-if="err" class="error">{{ err }}</p>

    <div class="toolbar">
      <label class="search">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" aria-hidden="true">
          <circle cx="11" cy="11" r="6.5" stroke="currentColor" stroke-width="1.6" />
          <path d="M16 16l4 4" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
        </svg>
        <input v-model="q" type="search" placeholder="搜索智能体..." />
      </label>
      <div class="seg" role="tablist">
        <button
          type="button"
          :class="{ on: filter === 'mine' }"
          @click="filter = 'mine'"
        >我的 {{ counts.mine }}</button>
        <button
          type="button"
          :class="{ on: filter === 'all' }"
          @click="filter = 'all'"
        >全部 {{ counts.all }}</button>
        <button
          type="button"
          :class="{ on: filter === 'archived' }"
          @click="filter = 'archived'"
        >已归档 {{ counts.archived }}</button>
      </div>
    </div>

    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th class="col-check"></th>
            <th>智能体</th>
            <th>状态</th>
            <th>Owner</th>
            <th>访问范围</th>
            <th>运行时</th>
            <th>最近活跃</th>
            <th class="num">运行次数</th>
            <th class="col-actions">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="9" class="empty">加载中…</td>
          </tr>
          <tr v-else-if="!filtered.length">
            <td colspan="9" class="empty">
              {{ filter === 'archived' ? '暂无已归档智能体' : '暂无智能体，点击「新建智能体」开始' }}
            </td>
          </tr>
          <tr
            v-for="a in filtered"
            :key="a.id"
            class="row-click"
            @click="openAgent(a)"
          >
            <td class="col-check" @click.stop>
              <input type="checkbox" disabled title="批量操作二期" />
            </td>
            <td>
              <div class="agent-cell">
                <span class="agent-av" aria-hidden="true">
                  <ProviderIcon
                    v-if="a.provider"
                    :provider="a.provider"
                    :custom-src="getCustomProviderIcon(runtimeFor(a)?.daemonId, a.provider)"
                    :size="28"
                  />
                  <span v-else class="bot">✦</span>
                </span>
                <span class="agent-text">
                  <span class="name-row">
                    <strong>{{ a.name }}</strong>
                    <span class="you">你</span>
                  </span>
                  <span class="desc">{{ a.description || a.instructions || '暂无描述' }}</span>
                </span>
              </div>
            </td>
            <td>
              <span class="status" :class="{ on: statusOf(a) === '在线', busy: statusOf(a) === '忙碌', archived: isArchived(a) }">
                <i class="dot" />{{ statusOf(a) }}
              </span>
            </td>
            <td>
              <span class="owner">
                <span class="owner-av">{{ ownerAv }}</span>
                {{ ownerName }}
              </span>
            </td>
            <td class="muted">工作区</td>
            <td class="muted">{{ runtimeLabel(a) }}</td>
            <td class="muted">{{ formatRelative(a.updatedAt || a.createdAt) }}</td>
            <td class="num muted">—</td>
            <td class="col-actions" @click.stop>
              <template v-if="isArchived(a)">
                <button type="button" class="link-btn" :disabled="busy" @click="restoreAgent(a)">恢复</button>
                <button type="button" class="link-btn danger" :disabled="busy" @click="askDelete(a)">删除</button>
              </template>
              <template v-else>
                <button type="button" class="link-btn" :disabled="busy" @click="askArchive(a)">归档</button>
                <button type="button" class="link-btn danger" :disabled="busy" @click="askDelete(a)">删除</button>
              </template>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <ConfirmDialog
      v-if="pending?.type === 'archive'"
      :open="true"
      :title="`归档智能体「${pending.agent.name}」？`"
      description="归档后无法领取新任务，历史 task 保留，之后可恢复。"
      confirm-label="归档"
      tone="warn"
      :busy="busy"
      @cancel="closePending"
      @confirm="confirmPending"
    />
    <ConfirmDialog
      v-else-if="pending?.type === 'delete'"
      :open="true"
      :title="`永久删除智能体「${pending.agent.name}」？`"
      description="将抹去其全部 task、聊天等记录，不可恢复。"
      callout="若只需停用，请改用「归档」——历史 task 会保留，之后可恢复。"
      callout-tone="danger"
      confirm-label="永久删除"
      tone="danger"
      :busy="busy"
      require-ack
      v-model:ack="ackDelete"
      ack-label="我已了解：删除将永久抹去该智能体及相关记录。"
      @cancel="closePending"
      @confirm="confirmPending"
    />
  </section>
</template>

<style scoped>
.page { max-width: 1100px; }
.head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 18px;
}
h2 { margin: 0; font-size: 22px; font-weight: 700; }
.count { color: var(--muted); font-weight: 600; margin-left: 4px; }
.lead { margin: 8px 0 0; font-size: 13px; color: var(--muted); line-height: 1.5; }
.more { color: #2563eb; text-decoration: none; }
.more:hover { text-decoration: underline; }
.btn-add {
  border: none;
  background: #1c2333;
  color: #fff;
  border-radius: 8px;
  padding: 9px 14px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  white-space: nowrap;
}
.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  margin-bottom: 12px;
}
.search {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  border: 1px solid var(--border);
  background: #fff;
  border-radius: 999px;
  padding: 7px 12px;
  color: var(--muted);
  min-width: 200px;
}
.search input {
  border: none;
  outline: none;
  font: inherit;
  font-size: 13px;
  width: 160px;
  background: transparent;
  color: var(--text);
}
.seg {
  display: inline-flex;
  border: 1px solid var(--border);
  border-radius: 8px;
  overflow: hidden;
  background: #fff;
}
.seg button {
  border: none;
  background: transparent;
  padding: 7px 12px;
  font-size: 13px;
  cursor: pointer;
  color: var(--muted);
}
.seg button.on {
  background: #f3f4f6;
  color: var(--text);
  font-weight: 600;
}
.table-wrap {
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: 12px;
  overflow: auto;
}
table { width: 100%; border-collapse: collapse; font-size: 13px; }
th, td {
  padding: 12px 14px;
  text-align: left;
  border-bottom: 1px solid var(--border);
  vertical-align: middle;
}
th {
  font-size: 12px;
  color: var(--muted);
  font-weight: 600;
  white-space: nowrap;
}
tr:last-child td { border-bottom: none; }
.row-click { cursor: pointer; }
.row-click:hover td { background: #f9fafb; }
.col-check { width: 36px; }
.num { text-align: right; }
.empty { text-align: center; color: var(--muted); padding: 36px !important; }
.agent-cell { display: flex; align-items: flex-start; gap: 10px; min-width: 220px; }
.agent-av {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.bot {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  background: #f3f4f6;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.agent-text { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.name-row { display: flex; align-items: center; gap: 6px; }
.name-row strong { font-weight: 650; }
.you {
  font-size: 11px;
  padding: 0 6px;
  border-radius: 999px;
  background: #eef2ff;
  color: #4338ca;
  font-weight: 600;
}
.desc {
  color: var(--muted);
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 320px;
}
.status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--muted);
}
.status .dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #9ca3af;
}
.status.on { color: #059669; }
.status.on .dot { background: #10b981; }
.status.busy { color: #d97706; }
.status.busy .dot { background: #f59e0b; }
.status.archived { color: #92400e; }
.status.archived .dot { background: #f59e0b; }
.col-actions {
  white-space: nowrap;
  text-align: right;
}
.link-btn {
  border: none;
  background: transparent;
  color: #2563eb;
  font-size: 12px;
  cursor: pointer;
  padding: 2px 6px;
}
.link-btn:hover { text-decoration: underline; }
.link-btn.danger { color: #b42318; }
.owner {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}
.owner-av {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: #ea580c;
  color: #fff;
  font-size: 10px;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.muted { color: var(--muted); }
.error { color: var(--danger); margin-bottom: 10px; }
</style>
