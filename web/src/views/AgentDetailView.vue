<script setup lang="ts">
/**
 * 智能体详情（对齐 Multica）：概览 / 工作 / 能力 / 设置。
 */
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { apiFetch } from '@/lib/api'
import {
  agentDetailStatus,
  formatRelative,
  modelOptionsForProvider,
  ownerDisplayName,
  ownerInitials,
  THINKING_OPTIONS,
  type Agent,
  type AgentDetailTab,
  type AgentSettingsSection,
} from '@/lib/agents'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import ActionIcon from '@/components/ActionIcon.vue'
import AgentAvatar from '@/components/AgentAvatar.vue'
import AvatarPicker from '@/components/AvatarPicker.vue'
import { pickRandomAvatar } from '@/lib/agentAvatars'
import {
  displayName,
  providerLabel,
  type Runtime,
} from '@/lib/runtimes'
import { getSessionEmail } from '@/lib/session'

type Task = {
  id: string
  agentId: string
  status: string
  prompt?: string
  resultSummary?: string
  triggerSource?: string
  chatSessionId?: string | null
  issueId?: string | null
}
type Skill = { id: string; name: string; description?: string }

const route = useRoute()
const router = useRouter()

const agent = ref<Agent | null>(null)
const runtimes = ref<Runtime[]>([])
const tasks = ref<Task[]>([])
const skills = ref<Skill[]>([])
const err = ref('')
const saving = ref(false)
const okMsg = ref('')
/** 设置页自动保存状态 */
const saveStatus = ref<'idle' | 'saving' | 'saved' | 'error'>('idle')
let autoSaveTimer: number | undefined
let hydratingForm = false
const showMore = ref(false)
const showArchive = ref(false)
const showDelete = ref(false)
const ackDelete = ref(false)
const deleting = ref(false)
const archiving = ref(false)
let timer: number | undefined

const agentId = computed(() => String(route.params.agentId || ''))
const isArchived = computed(() => (agent.value?.status || '').toLowerCase() === 'archived')

const tab = computed<AgentDetailTab>(() => {
  const t = String(route.query.tab || 'overview')
  if (t === 'work' || t === 'skills' || t === 'settings') return t
  return 'overview'
})

const settingsSection = computed<AgentSettingsSection>(() => {
  const s = String(route.query.section || 'general')
  if (s === 'access' || s === 'env' || s === 'params') return s
  return 'general'
})

const email = computed(() => getSessionEmail())
const ownerName = computed(() => ownerDisplayName(email.value))
const ownerAv = computed(() => ownerInitials(email.value))

const form = ref({
  name: '',
  description: '',
  avatar: '',
  instructions: '',
  runtimeId: '',
  model: 'default',
  thinkingMode: 'cli',
  maxConcurrency: 1,
  skillIds: [] as string[],
})
const showAvatarPicker = ref(false)

const runtime = computed(() => {
  if (!agent.value) return undefined
  if (agent.value.runtimeId) {
    return runtimes.value.find((r) => r.id === agent.value!.runtimeId)
  }
  return runtimes.value.find((r) => r.provider === agent.value!.provider)
})

const runtimeOnline = computed(() => runtime.value?.status === 'online')
const detailStatus = computed(() =>
  agentDetailStatus(agent.value?.status, runtime.value ? runtimeOnline.value : undefined),
)
const statusOn = computed(() => detailStatus.value.startsWith('在线'))

const runtimeLabelText = computed(() => {
  const rt = runtime.value
  if (!rt) return providerLabel(agent.value || { provider: '' })
  const host = rt.hostName || ''
  return host ? `${displayName(rt)} (${host})` : displayName(rt)
})

/** Issue 分配/提及产生的工作；chat 会话不算「工作」 */
function isIssueWorkTask(t: Task) {
  if (t.issueId) return true
  const src = (t.triggerSource || '').toLowerCase()
  return src === 'assign' || src === 'mention'
}

const agentTasks = computed(() =>
  tasks.value.filter((t) => t.agentId === agentId.value && isIssueWorkTask(t)),
)

const activeTasks = computed(() =>
  agentTasks.value.filter((t) =>
    ['queued', 'dispatched', 'running'].includes((t.status || '').toLowerCase()),
  ),
)

const recentTasks = computed(() =>
  agentTasks.value.filter((t) =>
    ['completed', 'failed', 'cancelled'].includes((t.status || '').toLowerCase()),
  ),
)

const stats = computed(() => {
  const done = agentTasks.value.filter((t) =>
    ['completed', 'failed', 'cancelled'].includes((t.status || '').toLowerCase()),
  )
  const ok = done.filter((t) => t.status === 'completed').length
  const fail = done.filter((t) => t.status === 'failed').length
  const rate = done.length ? Math.round((ok / done.length) * 100) : 0
  return {
    runs: done.length,
    successRate: done.length ? `${rate}%` : '—',
    fails: fail,
    avgDuration: '—',
  }
})

const assignedSkills = computed(() => {
  const ids = new Set(agent.value?.skillIds || [])
  return skills.value.filter((s) => ids.has(s.id))
})

const onlineRuntimes = computed(() => runtimes.value.filter((r) => r.status === 'online'))

const runtimeOptions = computed(() => {
  // 设置里可选：当前绑定 + 全部在线（便于换机）
  const map = new Map<string, Runtime>()
  for (const r of onlineRuntimes.value) map.set(r.id, r)
  if (runtime.value) map.set(runtime.value.id, runtime.value)
  return [...map.values()]
})

const selectedRuntimeProvider = computed(() => {
  const rt = runtimeOptions.value.find((r) => r.id === form.value.runtimeId)
  return rt?.provider || agent.value?.provider || ''
})

const modelOptions = computed(() => modelOptionsForProvider(selectedRuntimeProvider.value))

const saveBadge = computed(() => {
  if (saveStatus.value === 'saving') return '保存中…'
  if (saveStatus.value === 'saved') return '已保存'
  if (saveStatus.value === 'error') return '保存失败'
  return ''
})

function setTab(t: AgentDetailTab) {
  const q: Record<string, string> = { tab: t }
  if (t === 'settings') q.section = settingsSection.value
  router.replace({ name: 'agent-detail', params: { agentId: agentId.value }, query: q })
}

function setSection(s: AgentSettingsSection) {
  router.replace({
    name: 'agent-detail',
    params: { agentId: agentId.value },
    query: { tab: 'settings', section: s },
  })
}

async function syncForm(a: Agent) {
  hydratingForm = true
  form.value = {
    name: a.name || '',
    description: a.description || '',
    avatar: a.avatar || '',
    instructions: a.instructions || '',
    runtimeId: a.runtimeId || '',
    model: a.model || 'default',
    thinkingMode: a.thinkingMode || 'cli',
    maxConcurrency: a.maxConcurrency ?? 1,
    skillIds: [...(a.skillIds || [])],
  }
  await nextTick()
  hydratingForm = false
}

async function load(soft = false) {
  if (!soft) err.value = ''
  try {
    const [list, rts, tks, sks] = await Promise.all([
      apiFetch<Agent[]>('/api/agents'),
      apiFetch<Runtime[]>('/api/runtimes'),
      apiFetch<Task[]>('/api/tasks'),
      apiFetch<Skill[]>('/api/skills'),
    ])
    runtimes.value = rts
    tasks.value = tks
    skills.value = sks
    const found = list.find((a) => a.id === agentId.value) || null
    agent.value = found
    // 轮询软刷新不覆盖表单，避免打断设置页编辑与自动保存
    if (found && !soft) await syncForm(found)
  } catch (e) {
    if (!soft) err.value = e instanceof Error ? e.message : '加载失败'
  }
}

function scheduleAutoSave() {
  if (hydratingForm || !agent.value || isArchived.value) return
  if (autoSaveTimer) window.clearTimeout(autoSaveTimer)
  autoSaveTimer = window.setTimeout(() => {
    void autoSaveSettings()
  }, 450)
}

async function autoSaveSettings() {
  if (!agent.value || isArchived.value || hydratingForm) return
  const name = form.value.name.trim()
  if (!name) {
    err.value = '名称不能为空'
    saveStatus.value = 'error'
    return
  }
  if (name.length > 64) {
    err.value = '名称最多 64 个字符'
    saveStatus.value = 'error'
    return
  }
  const conc = Number(form.value.maxConcurrency)
  if (!Number.isFinite(conc) || conc < 1 || conc > 50) {
    err.value = '并发须在 1–50 之间'
    saveStatus.value = 'error'
    return
  }
  err.value = ''
  saveStatus.value = 'saving'
  saving.value = true
  try {
    const rt = runtimeOptions.value.find((r) => r.id === form.value.runtimeId)
    const body: Record<string, unknown> = {
      name,
      description: form.value.description.trim().slice(0, 255),
      avatar: form.value.avatar || '',
      instructions: form.value.instructions,
      model: form.value.model || 'default',
      thinkingMode: form.value.thinkingMode || 'cli',
      maxConcurrency: conc,
    }
    if (form.value.runtimeId) {
      body.runtimeId = form.value.runtimeId
      if (rt) body.provider = rt.provider
    }
    const updated = await apiFetch<Agent>(`/api/agents/${agent.value.id}`, {
      method: 'PUT',
      body: JSON.stringify(body),
    })
    agent.value = updated
    await syncForm(updated)
    saveStatus.value = 'saved'
    okMsg.value = ''
  } catch (e) {
    err.value = e instanceof Error ? e.message : '保存失败'
    saveStatus.value = 'error'
  } finally {
    saving.value = false
  }
}

watch(
  () => ({
    name: form.value.name,
    description: form.value.description,
    avatar: form.value.avatar,
    instructions: form.value.instructions,
    runtimeId: form.value.runtimeId,
    model: form.value.model,
    thinkingMode: form.value.thinkingMode,
    maxConcurrency: form.value.maxConcurrency,
  }),
  () => scheduleAutoSave(),
  { deep: true },
)

watch(
  () => selectedRuntimeProvider.value,
  () => {
    if (hydratingForm) return
    const opts = modelOptions.value
    if (!opts.some((o) => o.value === form.value.model)) {
      form.value.model = 'default'
    }
  },
)

function editName() {
  if (isArchived.value) return
  setTab('settings')
  setSection('general')
  // 下一帧聚焦名称输入
  requestAnimationFrame(() => {
    document.getElementById('agent-name-input')?.focus()
    ;(document.getElementById('agent-name-input') as HTMLInputElement | null)?.select()
  })
}

function toggleSkill(id: string) {
  const set = new Set(form.value.skillIds)
  if (set.has(id)) set.delete(id)
  else set.add(id)
  form.value.skillIds = [...set]
}

async function saveSkills() {
  if (!agent.value) return
  err.value = ''
  okMsg.value = ''
  saving.value = true
  try {
    const updated = await apiFetch<Agent>(`/api/agents/${agent.value.id}`, {
      method: 'PUT',
      body: JSON.stringify({ skillIds: form.value.skillIds }),
    })
    agent.value = updated
    await syncForm(updated)
    okMsg.value = '能力已更新'
  } catch (e) {
    err.value = e instanceof Error ? e.message : '保存失败'
  } finally {
    saving.value = false
  }
}

function goChat() {
  router.push({ name: 'chat' })
}

function goAssign() {
  router.push({ name: 'issues' })
}

function askArchive() {
  if (!agent.value || isArchived.value) return
  showMore.value = false
  showArchive.value = true
}

function closeArchive() {
  if (archiving.value) return
  showArchive.value = false
}

async function confirmArchive() {
  if (!agent.value || isArchived.value) return
  archiving.value = true
  err.value = ''
  try {
    const updated = await apiFetch<Agent>(`/api/agents/${agent.value.id}/archive`, { method: 'POST' })
    agent.value = updated
    okMsg.value = '已归档。可在列表「已归档」中恢复。'
    showArchive.value = false
  } catch (e) {
    err.value = e instanceof Error ? e.message : '归档失败'
  } finally {
    archiving.value = false
  }
}

async function restoreAgent() {
  if (!agent.value || !isArchived.value) return
  showMore.value = false
  err.value = ''
  try {
    const updated = await apiFetch<Agent>(`/api/agents/${agent.value.id}/restore`, { method: 'POST' })
    agent.value = updated
    okMsg.value = '已恢复'
  } catch (e) {
    err.value = e instanceof Error ? e.message : '恢复失败'
  }
}

function openDelete() {
  showMore.value = false
  showArchive.value = false
  ackDelete.value = false
  err.value = ''
  showDelete.value = true
}

function closeDelete() {
  if (deleting.value) return
  showDelete.value = false
  ackDelete.value = false
}

async function confirmDelete() {
  if (!agent.value || !ackDelete.value) return
  deleting.value = true
  err.value = ''
  try {
    await apiFetch(`/api/agents/${agent.value.id}`, { method: 'DELETE' })
    await router.replace({ name: 'agents' })
  } catch (e) {
    err.value = e instanceof Error ? e.message : '删除失败'
  } finally {
    deleting.value = false
    showDelete.value = false
  }
}

function taskTitle(t: Task) {
  const p = (t.prompt || '').trim()
  if (!p) return t.triggerSource || '任务'
  return p.length > 80 ? `${p.slice(0, 80)}…` : p
}

function taskStatusLabel(s: string) {
  const m: Record<string, string> = {
    queued: '排队中',
    dispatched: '已下发',
    running: '运行中',
    completed: '已完成',
    failed: '失败',
    cancelled: '已取消',
  }
  return m[(s || '').toLowerCase()] || s
}

function runtimeOptionLabel(r: Runtime) {
  const host = r.hostName || ''
  const name = displayName(r)
  return host ? `${name} · ${host}` : name
}

watch(agentId, () => {
  load()
})

onMounted(() => {
  void load(false)
  timer = window.setInterval(() => void load(true), 8000)
})
onUnmounted(() => {
  if (timer) window.clearInterval(timer)
  if (autoSaveTimer) window.clearTimeout(autoSaveTimer)
})
</script>

<template>
  <section class="page" v-if="agent">
    <nav class="crumb">
      <router-link :to="{ name: 'agents' }">智能体</router-link>
      <span class="sep">/</span>
      <span>{{ agent.name }}</span>
    </nav>

    <header class="hero">
      <div class="hero-left">
        <div class="avatar" @click="!isArchived && (showAvatarPicker = true)" :class="{ clickable: !isArchived }">
          <AgentAvatar :src="form.avatar || agent.avatar" :provider="agent.provider" :size="56" />
        </div>
        <div class="hero-text">
          <div class="title-row">
            <h1
              class="agent-title"
              :class="{ editable: !isArchived }"
              :title="isArchived ? undefined : '点击修改名称'"
              @click="editName"
            >{{ agent.name }}</h1>
            <span class="live" :class="{ on: statusOn }">
              <i class="dot" />{{ detailStatus }}
            </span>
          </div>
          <p class="desc">{{ agent.description || '暂无描述' }}</p>
          <div class="meta">
            <span class="meta-item" title="模型">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                <path d="M12 3l8 4.5v9L12 21l-8-4.5v-9L12 3z" stroke="currentColor" stroke-width="1.5" />
              </svg>
              默认
            </span>
            <span class="meta-item" title="运行时">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                <rect x="3" y="5" width="18" height="14" rx="2" stroke="currentColor" stroke-width="1.5" />
                <path d="M7 15h4M13 15h4" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" />
              </svg>
              {{ runtimeLabelText }}
            </span>
            <span class="meta-item" title="访问">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                <circle cx="12" cy="9" r="3.5" stroke="currentColor" stroke-width="1.5" />
                <path d="M5 19c1.5-3 4-4.5 7-4.5S17.5 16 19 19" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" />
              </svg>
              个人
            </span>
            <span class="meta-item" title="更新时间">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                <circle cx="12" cy="12" r="8" stroke="currentColor" stroke-width="1.5" />
                <path d="M12 8v4.5L15 15" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" />
              </svg>
              {{ formatRelative(agent.updatedAt || agent.createdAt) }}更新
            </span>
          </div>
        </div>
      </div>
      <div class="hero-actions">
        <button v-if="!isArchived" type="button" class="btn-ghost" @click="goChat">
          <ActionIcon name="chat" />
          私信
        </button>
        <button v-if="!isArchived" type="button" class="btn-primary" @click="goAssign">
          <ActionIcon name="assign" />
          分配工作
        </button>
        <button v-if="isArchived" type="button" class="btn-primary" @click="restoreAgent">
          <ActionIcon name="restore" />
          恢复智能体
        </button>
        <div class="more-wrap">
          <button type="button" class="btn-icon" aria-label="更多" @click="showMore = !showMore">⋯</button>
          <div v-if="showMore" class="more-menu" @mouseleave="showMore = false">
            <button type="button" @click="setTab('settings'); showMore = false">
              <ActionIcon name="settings" />
              设置
            </button>
            <button v-if="!isArchived" type="button" @click="askArchive">
              <ActionIcon name="archive" />
              归档
            </button>
            <button v-else type="button" @click="restoreAgent">
              <ActionIcon name="restore" />
              恢复
            </button>
            <button type="button" class="danger" @click="openDelete">
              <ActionIcon name="delete" />
              删除
            </button>
          </div>
        </div>
      </div>
    </header>

    <div v-if="isArchived" class="archived-banner">
      此智能体已归档。历史 task 仍保留；恢复后可继续使用。永久删除将抹去相关记录。
    </div>

    <nav class="tabs" role="tablist">
      <button type="button" :class="{ on: tab === 'overview' }" @click="setTab('overview')">概览</button>
      <button type="button" :class="{ on: tab === 'work' }" @click="setTab('work')">工作</button>
      <button type="button" :class="{ on: tab === 'skills' }" @click="setTab('skills')">能力</button>
      <button type="button" :class="{ on: tab === 'settings' }" @click="setTab('settings')">设置</button>
    </nav>

    <p v-if="err" class="error">{{ err }}</p>
    <p v-if="okMsg" class="ok">{{ okMsg }}</p>

    <!-- 概览 -->
    <div v-if="tab === 'overview'" class="overview">
      <div class="ov-main">
        <section class="block">
          <h3 class="block-title">
            当前
            <span class="block-sub">{{ activeTasks.length ? `${activeTasks.length} 个进行中` : '无进行中的工作' }}</span>
          </h3>
          <ul v-if="activeTasks.length" class="task-list">
            <li v-for="t in activeTasks" :key="t.id">
              <span class="badge">{{ taskStatusLabel(t.status) }}</span>
              <span class="task-title">{{ taskTitle(t) }}</span>
            </li>
          </ul>
          <p v-else class="empty-lead">这个智能体当前没有在跑任何 task。</p>
        </section>

        <section class="block">
          <h3 class="block-title">
            最近工作
            <span class="block-sub">{{ recentTasks.length ? `${recentTasks.length} 条` : '还没有完成的 task' }}</span>
          </h3>
          <ul v-if="recentTasks.length" class="task-list">
            <li v-for="t in recentTasks.slice(0, 8)" :key="t.id">
              <span class="badge muted">{{ taskStatusLabel(t.status) }}</span>
              <span class="task-title">{{ taskTitle(t) }}</span>
            </li>
          </ul>
          <p v-else class="empty-lead">这个智能体还没有完成过任何 task。</p>
        </section>
      </div>

      <aside class="ov-side">
        <div class="side-card">
          <h4>智能体</h4>
          <dl>
            <div><dt>所有者</dt><dd><span class="owner"><i class="av">{{ ownerAv }}</i>{{ ownerName }}</span></dd></div>
            <div><dt>访问权限</dt><dd>🔒 个人</dd></div>
            <div>
              <dt>运行时</dt>
              <dd>
                <span class="rt">
                  <i class="dot" :class="{ on: runtimeOnline }" />
                  {{ runtimeLabelText }}
                </span>
              </dd>
            </div>
            <div><dt>模型</dt><dd>默认</dd></div>
            <div><dt>并发</dt><dd>{{ agent.maxConcurrency ?? 1 }}</dd></div>
          </dl>
        </div>

        <div class="side-card">
          <h4>Skills <span class="count">{{ assignedSkills.length }}</span></h4>
          <ul v-if="assignedSkills.length" class="skill-mini">
            <li v-for="s in assignedSkills" :key="s.id">{{ s.name }}</li>
          </ul>
          <p v-else class="muted">尚未分配 skill</p>
        </div>

        <div class="side-card">
          <h4>近 30 天</h4>
          <div class="stats">
            <div><strong>{{ stats.runs }}</strong><span>次运行</span></div>
            <div><strong>{{ stats.successRate }}</strong><span>成功</span></div>
            <div><strong>{{ stats.avgDuration }}</strong><span>平均耗时</span></div>
            <div><strong>{{ stats.fails }}</strong><span>失败</span></div>
          </div>
        </div>
      </aside>
    </div>

    <!-- 工作 -->
    <div v-else-if="tab === 'work'" class="panel">
      <h3 class="panel-title">全部工作</h3>
      <p v-if="!agentTasks.length" class="empty-lead">还没有与该智能体相关的 task。</p>
      <ul v-else class="task-list dense">
        <li v-for="t in agentTasks" :key="t.id">
          <span class="badge" :class="{ ok: t.status === 'completed', bad: t.status === 'failed' }">
            {{ taskStatusLabel(t.status) }}
          </span>
          <span class="task-title">{{ taskTitle(t) }}</span>
          <span class="task-meta">{{ t.triggerSource || '—' }}</span>
        </li>
      </ul>
    </div>

    <!-- 能力 -->
    <div v-else-if="tab === 'skills'" class="panel">
      <div class="panel-head">
        <div>
          <h3 class="panel-title">Skills</h3>
          <p class="panel-lead">为该智能体分配可调用的技能。</p>
        </div>
        <button type="button" class="btn-primary" :disabled="saving" @click="saveSkills">
          {{ saving ? '保存中…' : '保存' }}
        </button>
      </div>
      <p v-if="!skills.length" class="empty-lead">工作区还没有 Skill，请先到「Skills」创建。</p>
      <ul v-else class="skill-pick">
        <li v-for="s in skills" :key="s.id">
          <label>
            <input
              type="checkbox"
              :checked="form.skillIds.includes(s.id)"
              @change="toggleSkill(s.id)"
            />
            <span>
              <strong>{{ s.name }}</strong>
              <small>{{ s.description || '无描述' }}</small>
            </span>
          </label>
        </li>
      </ul>
    </div>

    <!-- 设置 -->
    <div v-else class="settings">
      <aside class="set-nav">
        <button type="button" :class="{ on: settingsSection === 'general' }" @click="setSection('general')">通用</button>
        <button type="button" :class="{ on: settingsSection === 'access' }" @click="setSection('access')">访问权限</button>
        <button type="button" :class="{ on: settingsSection === 'env' }" @click="setSection('env')">环境变量</button>
        <button type="button" :class="{ on: settingsSection === 'params' }" @click="setSection('params')">自定义参数</button>
      </aside>

      <div class="set-body" v-if="settingsSection === 'general'">
        <section class="set-card">
          <header class="set-card-head">
            <h3>资料</h3>
            <span
              v-if="saveBadge"
              class="save-badge"
              :class="{
                saving: saveStatus === 'saving',
                saved: saveStatus === 'saved',
                error: saveStatus === 'error',
              }"
            >
              <span v-if="saveStatus === 'saved'" class="save-check">✓</span>
              {{ saveBadge }}
            </span>
          </header>

          <div class="set-row">
            <div class="set-meta">
              <span class="label">头像</span>
              <p class="hint">用于任务指派、动态和聊天中的展示。</p>
            </div>
            <div class="set-control avatar-edit">
              <AgentAvatar :src="form.avatar" :provider="agent.provider" :size="48" />
              <button type="button" class="btn-ghost" :disabled="isArchived" @click="showAvatarPicker = true">
                更换
              </button>
              <button
                type="button"
                class="btn-ghost"
                :disabled="isArchived"
                @click="form.avatar = pickRandomAvatar(form.avatar)"
              >
                随机
              </button>
            </div>
          </div>

          <div class="set-row">
            <div class="set-meta">
              <span class="label">名称</span>
            </div>
            <div class="set-control">
              <input
                id="agent-name-input"
                v-model="form.name"
                type="text"
                maxlength="64"
                :disabled="isArchived"
                placeholder="智能体名称"
              />
            </div>
          </div>

          <div class="set-row top">
            <div class="set-meta">
              <span class="label">描述</span>
            </div>
            <div class="set-control">
              <textarea
                v-model="form.description"
                rows="3"
                maxlength="255"
                :disabled="isArchived"
                placeholder="这个智能体能做什么？"
              />
              <span class="counter">{{ form.description.length }} / 255</span>
            </div>
          </div>

          <div class="set-row">
            <div class="set-meta">
              <span class="label">标签</span>
              <p class="hint">用于工作区分类（二期）。</p>
            </div>
            <div class="set-control">
              <input type="text" disabled placeholder="添加标签…" />
            </div>
          </div>
        </section>

        <section class="set-card">
          <header class="set-card-head">
            <h3>执行配置</h3>
          </header>

          <div class="set-row">
            <div class="set-meta">
              <span class="label">运行时</span>
            </div>
            <div class="set-control">
              <select v-model="form.runtimeId" :disabled="isArchived">
                <option v-for="r in runtimeOptions" :key="r.id" :value="r.id">
                  {{ runtimeOptionLabel(r) }}{{ r.status === 'online' ? '' : '（离线）' }}
                </option>
              </select>
            </div>
          </div>

          <div class="set-row">
            <div class="set-meta">
              <span class="label">模型</span>
            </div>
            <div class="set-control">
              <select v-model="form.model" :disabled="isArchived">
                <option v-for="m in modelOptions" :key="m.value" :value="m.value">
                  {{ m.label }}
                </option>
              </select>
            </div>
          </div>

          <div class="set-row">
            <div class="set-meta">
              <span class="label">思考</span>
            </div>
            <div class="set-control">
              <select v-model="form.thinkingMode" :disabled="isArchived">
                <option v-for="t in THINKING_OPTIONS" :key="t.value" :value="t.value">
                  {{ t.label }}
                </option>
              </select>
            </div>
          </div>

          <div class="set-row">
            <div class="set-meta">
              <span class="label">并发</span>
              <p class="hint">最大并行 task 数 (1-50)</p>
            </div>
            <div class="set-control">
              <input
                v-model.number="form.maxConcurrency"
                type="number"
                min="1"
                max="50"
                :disabled="isArchived"
              />
            </div>
          </div>

          <div class="set-row top">
            <div class="set-meta">
              <span class="label">Instructions</span>
              <p class="hint">系统提示与行为准则。</p>
            </div>
            <div class="set-control">
              <textarea
                v-model="form.instructions"
                rows="5"
                :disabled="isArchived"
                placeholder="系统提示 / 行为准则"
              />
            </div>
          </div>
        </section>

        <section class="set-block danger-zone">
          <h3>危险操作</h3>
          <p class="panel-lead">归档可恢复且保留 task；删除将永久抹去该智能体及其 task、聊天等记录。</p>
          <div class="danger-actions">
            <button
              v-if="!isArchived"
              type="button"
              class="btn-warn"
              @click="askArchive"
            >
              <ActionIcon name="archive" />
              归档智能体
            </button>
            <button
              v-else
              type="button"
              class="btn-ghost"
              @click="restoreAgent"
            >
              <ActionIcon name="restore" />
              恢复智能体
            </button>
            <button type="button" class="btn-del" @click="openDelete">
              <ActionIcon name="delete" />
              永久删除
            </button>
          </div>
        </section>
      </div>

      <div class="set-body" v-else>
        <section class="set-block">
          <h3>
            {{
              settingsSection === 'access' ? '访问权限'
              : settingsSection === 'env' ? '环境变量'
              : '自定义参数'
            }}
          </h3>
          <p class="empty-lead">该分区将在二期开放。当前请使用「通用」管理基础配置。</p>
        </section>
      </div>
    </div>

    <AvatarPicker v-model="form.avatar" v-model:open="showAvatarPicker" />

    <ConfirmDialog
      :open="showArchive"
      :title="`归档智能体「${agent.name}」？`"
      description="归档后无法再领取新任务，历史 task 会保留，之后可恢复。"
      confirm-label="归档"
      tone="warn"
      :busy="archiving"
      @cancel="closeArchive"
      @confirm="confirmArchive"
    />
    <ConfirmDialog
      :open="showDelete"
      :title="`永久删除智能体「${agent.name}」？`"
      description="将删除该智能体及其全部相关记录：task、聊天会话与消息、Skills 绑定；Issue 上的指派会被清空。此操作不可恢复。"
      callout="若只需停用，请改用「归档」——历史 task 会保留，之后可恢复。"
      callout-tone="danger"
      confirm-label="永久删除"
      tone="danger"
      :busy="deleting"
      require-ack
      v-model:ack="ackDelete"
      ack-label="我已了解：删除将永久抹去该智能体及相关记录。"
      @cancel="closeDelete"
      @confirm="confirmDelete"
    >
      <p v-if="err" class="error dialog-err">{{ err }}</p>
    </ConfirmDialog>
  </section>

  <section v-else class="page">
    <nav class="crumb">
      <router-link :to="{ name: 'agents' }">智能体</router-link>
      <span class="sep">/</span>
      <span>未找到</span>
    </nav>
    <p class="muted">智能体不存在或已删除。</p>
    <p v-if="err" class="error">{{ err }}</p>
  </section>
</template>

<style scoped>
.page { max-width: 1100px; }
.crumb {
  font-size: 13px;
  color: var(--muted);
  margin-bottom: 16px;
}
.crumb a { color: var(--muted); text-decoration: none; }
.crumb a:hover { color: var(--text); }
.sep { margin: 0 6px; }

.hero {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
  margin-bottom: 18px;
}
.hero-left { display: flex; gap: 14px; min-width: 0; }
.avatar {
  width: 56px;
  height: 56px;
  border-radius: 14px;
  border: 1px solid var(--border);
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  overflow: hidden;
}
.avatar.clickable { cursor: pointer; }
.avatar.clickable:hover { box-shadow: 0 0 0 2px #d1d5db; }
.avatar-edit {
  display: flex;
  align-items: center;
  gap: 10px;
}
.avatar.sm { width: 44px; height: 44px; border-radius: 12px; }
.title-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
h1 { margin: 0; font-size: 24px; }
.agent-title.editable {
  cursor: pointer;
  border-radius: 6px;
  padding: 0 4px;
  margin: 0 -4px;
}
.agent-title.editable:hover {
  background: #f3f4f6;
}
.live {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--muted);
}
.live.on { color: #059669; }
.dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #d1d5db;
  display: inline-block;
}
.live.on .dot,
.dot.on { background: #10b981; }
.desc {
  margin: 6px 0 10px;
  color: var(--muted);
  font-size: 14px;
}
.meta {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  font-size: 12px;
  color: var(--muted);
}
.meta-item {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}
.hero-actions {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-shrink: 0;
}
.btn-ghost {
  border: 1px solid var(--border);
  background: #fff;
  border-radius: 8px;
  padding: 8px 12px;
  font-size: 13px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.btn-primary {
  border: none;
  background: #111827;
  color: #fff;
  border-radius: 8px;
  padding: 8px 12px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.btn-primary:disabled { opacity: 0.5; cursor: not-allowed; }
.btn-icon {
  border: 1px solid var(--border);
  background: #fff;
  border-radius: 8px;
  width: 34px;
  height: 34px;
  cursor: pointer;
  font-size: 16px;
  line-height: 1;
}
.more-wrap { position: relative; }
.more-menu {
  position: absolute;
  right: 0;
  top: calc(100% + 4px);
  background: #fff;
  border: 1px solid var(--border);
  border-radius: 8px;
  min-width: 120px;
  box-shadow: 0 8px 24px rgba(0,0,0,.08);
  z-index: 20;
  padding: 4px;
}
.more-menu button {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  text-align: left;
  border: none;
  background: transparent;
  padding: 8px 10px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
}
.more-menu button:hover { background: #f3f4f6; }
.more-menu .danger { color: #b42318; }

.tabs {
  display: flex;
  gap: 4px;
  border-bottom: 1px solid var(--border);
  margin-bottom: 20px;
}
.tabs button {
  border: none;
  background: transparent;
  padding: 10px 14px;
  font-size: 14px;
  color: var(--muted);
  cursor: pointer;
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
}
.tabs button.on {
  color: var(--text);
  font-weight: 600;
  border-bottom-color: #111827;
}

.overview {
  display: grid;
  grid-template-columns: 1fr 280px;
  gap: 20px;
  align-items: start;
}
.block { margin-bottom: 28px; }
.block-title {
  margin: 0 0 8px;
  font-size: 15px;
  display: flex;
  gap: 8px;
  align-items: baseline;
}
.block-sub { font-weight: 500; color: var(--muted); font-size: 13px; }
.empty-lead { color: var(--muted); font-size: 14px; margin: 0; line-height: 1.5; }

.side-card {
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 14px 16px;
  margin-bottom: 12px;
}
.side-card h4 {
  margin: 0 0 12px;
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 6px;
}
.side-card .count {
  background: #f3f4f6;
  border-radius: 999px;
  padding: 1px 7px;
  font-size: 11px;
  color: var(--muted);
}
.side-card dl { margin: 0; display: flex; flex-direction: column; gap: 10px; }
.side-card dl > div {
  display: grid;
  grid-template-columns: 72px 1fr;
  gap: 8px;
  font-size: 13px;
}
.side-card dt { color: var(--muted); margin: 0; }
.side-card dd { margin: 0; }
.owner { display: inline-flex; align-items: center; gap: 6px; }
.av {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: #f59e0b;
  color: #fff;
  font-size: 9px;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-style: normal;
}
.rt { display: inline-flex; align-items: center; gap: 6px; }
.stats {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.stats div { display: flex; flex-direction: column; gap: 2px; }
.stats strong { font-size: 18px; }
.stats span { font-size: 12px; color: var(--muted); }
.skill-mini { list-style: none; margin: 0; padding: 0; font-size: 13px; }
.skill-mini li { padding: 4px 0; }

.panel { max-width: 760px; }
.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 16px;
}
.panel-title { margin: 0 0 4px; font-size: 16px; }
.panel-lead { margin: 0 0 16px; color: var(--muted); font-size: 13px; }

.task-list { list-style: none; margin: 0; padding: 0; }
.task-list li {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 0;
  border-bottom: 1px solid var(--border);
  font-size: 13px;
}
.task-list.dense li { padding: 12px 0; }
.task-title { flex: 1; min-width: 0; }
.task-meta { color: var(--muted); font-size: 12px; }
.badge {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 999px;
  background: #ecfdf5;
  color: #047857;
  flex-shrink: 0;
}
.badge.muted { background: #f3f4f6; color: #6b7280; }
.badge.ok { background: #ecfdf5; color: #047857; }
.badge.bad { background: #fef2f2; color: #b91c1c; }

.skill-pick { list-style: none; margin: 0; padding: 0; }
.skill-pick li {
  border: 1px solid var(--border);
  border-radius: 10px;
  margin-bottom: 8px;
  background: var(--panel);
}
.skill-pick label {
  display: flex;
  gap: 10px;
  padding: 12px 14px;
  cursor: pointer;
  align-items: flex-start;
}
.skill-pick strong { display: block; font-size: 14px; }
.skill-pick small { color: var(--muted); font-size: 12px; }

.settings {
  display: grid;
  grid-template-columns: 160px 1fr;
  gap: 24px;
  align-items: start;
}
.set-nav {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.set-nav button {
  text-align: left;
  border: none;
  background: transparent;
  padding: 8px 10px;
  border-radius: 8px;
  font-size: 13px;
  color: var(--muted);
  cursor: pointer;
}
.set-nav button.on {
  background: #f3f4f6;
  color: var(--text);
  font-weight: 600;
}
.set-body { max-width: 720px; display: flex; flex-direction: column; gap: 16px; }
.set-card {
  background: #fff;
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 18px 20px 8px;
}
.set-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}
.set-card-head h3 { margin: 0; font-size: 16px; }
.save-badge {
  font-size: 12px;
  color: var(--muted);
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.save-badge.saved { color: #047857; }
.save-badge.saving { color: var(--muted); }
.save-badge.error { color: var(--danger); }
.save-check {
  display: inline-flex;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: #d1fae5;
  color: #047857;
  font-size: 10px;
  align-items: center;
  justify-content: center;
  font-weight: 700;
}
.set-row {
  display: grid;
  grid-template-columns: minmax(140px, 32%) 1fr;
  gap: 16px 24px;
  align-items: center;
  padding: 14px 0;
  border-top: 1px solid #f3f4f6;
}
.set-row.top { align-items: flex-start; }
.set-meta .label {
  font-weight: 600;
  font-size: 13px;
  color: var(--text);
}
.set-meta .hint,
.set-control .hint,
.set-control .counter {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--muted);
}
.set-control {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.set-control.avatar-edit {
  flex-direction: row;
  align-items: center;
  gap: 10px;
}
.set-control input,
.set-control textarea,
.set-control select {
  width: 100%;
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 8px 10px;
  font: inherit;
  background: #fff;
  box-sizing: border-box;
}
.set-control textarea { resize: vertical; min-height: 72px; }
.set-control input[type='number'] { max-width: 120px; }
.set-block {
  background: #fff;
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 18px 20px;
}
.set-block h3 { margin: 0 0 4px; font-size: 16px; }
.set-actions { margin-top: 8px; }
@media (max-width: 700px) {
  .set-row { grid-template-columns: 1fr; gap: 8px; }
}

.error { color: var(--danger); }
.ok { color: #047857; font-size: 13px; }
.muted { color: var(--muted); }

.archived-banner {
  background: #fffbeb;
  border: 1px solid #f6d98a;
  color: #78350f;
  border-radius: 10px;
  padding: 10px 14px;
  font-size: 13px;
  margin-bottom: 16px;
  line-height: 1.45;
}
.danger-zone h3 { color: #b42318; }
.danger-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.btn-warn {
  border: 1px solid #f6d98a;
  background: #fffbeb;
  color: #92400e;
  border-radius: 8px;
  padding: 8px 12px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.btn-del {
  border: none;
  background: #fce8e6;
  color: #b42318;
  border-radius: 8px;
  padding: 8px 12px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.btn-del:disabled { opacity: 0.45; cursor: not-allowed; }
.dialog-err { margin: 0 0 8px; }

@media (max-width: 900px) {
  .overview, .settings { grid-template-columns: 1fr; }
  .hero { flex-direction: column; }
}
</style>
