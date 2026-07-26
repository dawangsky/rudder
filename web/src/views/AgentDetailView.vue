<script setup lang="ts">
/**
 * 智能体详情（对齐 Multica）：概览 / 工作 / 能力 / 设置。
 */
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { apiFetch } from '@/lib/api'
import {
  agentDetailStatus,
  formatRelative,
  ownerDisplayName,
  ownerInitials,
  type Agent,
  type AgentDetailTab,
  type AgentSettingsSection,
} from '@/lib/agents'
import ProviderIcon from '@/components/ProviderIcon.vue'
import { getCustomProviderIcon } from '@/lib/providerIcons'
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
const showMore = ref(false)
let timer: number | undefined

const agentId = computed(() => String(route.params.agentId || ''))

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
  instructions: '',
  runtimeId: '',
  maxConcurrency: 1,
  skillIds: [] as string[],
})

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

const agentTasks = computed(() =>
  tasks.value.filter((t) => t.agentId === agentId.value),
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

function syncForm(a: Agent) {
  form.value = {
    name: a.name || '',
    description: a.description || '',
    instructions: a.instructions || '',
    runtimeId: a.runtimeId || '',
    maxConcurrency: a.maxConcurrency ?? 1,
    skillIds: [...(a.skillIds || [])],
  }
}

async function load() {
  err.value = ''
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
    if (found) syncForm(found)
  } catch (e) {
    err.value = e instanceof Error ? e.message : '加载失败'
  }
}

async function saveGeneral() {
  if (!agent.value) return
  err.value = ''
  okMsg.value = ''
  if (!form.value.name.trim()) {
    err.value = '名称不能为空'
    return
  }
  const conc = Number(form.value.maxConcurrency)
  if (!Number.isFinite(conc) || conc < 1 || conc > 50) {
    err.value = '并发须在 1–50 之间'
    return
  }
  saving.value = true
  try {
    const rt = runtimeOptions.value.find((r) => r.id === form.value.runtimeId)
    const body: Record<string, unknown> = {
      name: form.value.name.trim(),
      description: form.value.description.trim().slice(0, 255),
      instructions: form.value.instructions,
      maxConcurrency: conc,
      skillIds: form.value.skillIds,
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
    syncForm(updated)
    okMsg.value = '已保存'
  } catch (e) {
    err.value = e instanceof Error ? e.message : '保存失败'
  } finally {
    saving.value = false
  }
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
    syncForm(updated)
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

async function archiveAgent() {
  if (!agent.value) return
  if (!confirm(`归档智能体「${agent.value.name}」？归档后无法再领取新任务。`)) return
  showMore.value = false
  try {
    await apiFetch(`/api/agents/${agent.value.id}`, {
      method: 'PUT',
      body: JSON.stringify({ status: 'archived' }),
    })
    await router.replace({ name: 'agents' })
  } catch (e) {
    err.value = e instanceof Error ? e.message : '归档失败'
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
  load()
  timer = window.setInterval(load, 8000)
})
onUnmounted(() => {
  if (timer) window.clearInterval(timer)
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
        <div class="avatar">
          <ProviderIcon
            :provider="agent.provider"
            :custom-src="getCustomProviderIcon(runtime?.daemonId, agent.provider)"
            :size="52"
          />
        </div>
        <div class="hero-text">
          <div class="title-row">
            <h1>{{ agent.name }}</h1>
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
        <button type="button" class="btn-ghost" @click="goChat">私信</button>
        <button type="button" class="btn-primary" @click="goAssign">+ 分配工作</button>
        <div class="more-wrap">
          <button type="button" class="btn-icon" aria-label="更多" @click="showMore = !showMore">⋯</button>
          <div v-if="showMore" class="more-menu" @mouseleave="showMore = false">
            <button type="button" @click="setTab('settings')">设置</button>
            <button type="button" class="danger" @click="archiveAgent">归档</button>
          </div>
        </div>
      </div>
    </header>

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
        <section class="set-block">
          <h3>资料</h3>
          <p class="panel-lead">管理该智能体在工作区内的展示方式。</p>

          <div class="field-row avatar-row">
            <span class="label">头像</span>
            <div class="avatar sm">
              <ProviderIcon :provider="agent.provider" :size="40" />
            </div>
          </div>
          <label class="field">
            <span class="label">名称</span>
            <input v-model="form.name" type="text" maxlength="64" />
          </label>
          <label class="field">
            <span class="label">描述</span>
            <textarea
              v-model="form.description"
              rows="3"
              maxlength="255"
              placeholder="这个智能体能做什么？"
            />
            <span class="counter">{{ form.description.length }} / 255</span>
          </label>
          <label class="field">
            <span class="label">标签</span>
            <input type="text" disabled placeholder="用于分类这个智能体的工作区标签。（二期）" />
          </label>
        </section>

        <section class="set-block">
          <h3>执行配置</h3>
          <p class="panel-lead">选择运行时、模型、思考强度、速度和并行任务上限。</p>

          <label class="field">
            <span class="label">运行时</span>
            <select v-model="form.runtimeId">
              <option v-for="r in runtimeOptions" :key="r.id" :value="r.id">
                {{ runtimeOptionLabel(r) }}{{ r.status === 'online' ? '' : '（离线）' }}
              </option>
            </select>
          </label>
          <label class="field">
            <span class="label">模型</span>
            <select disabled>
              <option>默认</option>
            </select>
          </label>
          <label class="field">
            <span class="label">并发</span>
            <input v-model.number="form.maxConcurrency" type="number" min="1" max="50" />
            <span class="hint">最大并行 task 数 (1-50)</span>
          </label>
          <label class="field">
            <span class="label">Instructions</span>
            <textarea v-model="form.instructions" rows="6" placeholder="系统提示 / 行为准则" />
          </label>

          <div class="set-actions">
            <button type="button" class="btn-primary" :disabled="saving" @click="saveGeneral">
              {{ saving ? '保存中…' : '保存更改' }}
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
}
.avatar.sm { width: 44px; height: 44px; border-radius: 12px; }
.title-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
h1 { margin: 0; font-size: 24px; }
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
  display: block;
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
.set-body { max-width: 560px; }
.set-block {
  margin-bottom: 28px;
  padding-bottom: 24px;
  border-bottom: 1px solid var(--border);
}
.set-block:last-child { border-bottom: none; }
.set-block h3 { margin: 0 0 4px; font-size: 16px; }
.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 14px;
  font-size: 13px;
}
.field .label,
.field-row .label {
  font-weight: 600;
  color: var(--text);
}
.field input,
.field textarea,
.field select {
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 8px 10px;
  font: inherit;
  background: #fff;
}
.field textarea { resize: vertical; }
.counter, .hint { font-size: 12px; color: var(--muted); }
.field-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
}
.set-actions { margin-top: 8px; }

.error { color: var(--danger); }
.ok { color: #047857; font-size: 13px; }
.muted { color: var(--muted); }

@media (max-width: 900px) {
  .overview, .settings { grid-template-columns: 1fr; }
  .hero { flex-direction: column; }
}
</style>
