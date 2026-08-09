<script setup lang="ts">
/**
 * Issues 看板：按状态分列，拖拽流转；对齐 Multica 密度与结构。
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { apiFetch } from '@/lib/api'
import { formatRelative, type Agent } from '@/lib/agents'
import AgentAvatar from '@/components/AgentAvatar.vue'
import {
  ISSUE_COLUMNS,
  issueKey,
  normalizeIssueStatus,
  snippet,
  type Issue,
  type IssueStatus,
} from '@/lib/issues'
import { getWorkspaceId } from '@/lib/session'

type WorkspaceInfo = { id: string; name?: string; slug?: string; issuePrefix?: string }
type IssueDetail = Issue & {
  comments?: { id: string; content: string; authorType?: string; createdAt?: string }[]
  tasks?: { id: string; status: string; triggerSource?: string }[]
}

type ScopeTab = 'all' | 'members' | 'agents'

const route = useRoute()
const router = useRouter()

const issues = ref<Issue[]>([])
const agents = ref<Agent[]>([])
const workspace = ref<WorkspaceInfo | null>(null)
const loading = ref(false)
const err = ref('')
const busy = ref(false)

const scope = ref<ScopeTab>('all')
const draggingId = ref('')
const dropTarget = ref<IssueStatus | ''>('')

const showCreate = ref(false)
const createStatus = ref<IssueStatus>('todo')
const createTitle = ref('')
const createDesc = ref('')
const createAssignee = ref('')
const createPriority = ref('medium')

const selected = ref<IssueDetail | null>(null)
const comment = ref('')
const detailBusy = ref(false)

const prefix = computed(() => (workspace.value?.issuePrefix || 'WS').toUpperCase())

const agentById = computed(() => {
  const m = new Map<string, Agent>()
  for (const a of agents.value) m.set(a.id, a)
  return m
})

const filteredIssues = computed(() => {
  let list = issues.value.map((i) => ({
    ...i,
    status: normalizeIssueStatus(i.status),
  }))
  if (scope.value === 'agents') {
    list = list.filter((i) => i.assigneeType === 'agent')
  } else if (scope.value === 'members') {
    list = list.filter((i) => i.assigneeType === 'user' || !i.assigneeType)
  }
  return list
})

const columns = computed(() =>
  ISSUE_COLUMNS.map((col) => ({
    ...col,
    items: filteredIssues.value.filter((i) => i.status === col.id),
  })),
)

const agentsWorking = computed(() => {
  const ids = new Set(
    issues.value
      .filter((i) => normalizeIssueStatus(i.status) === 'in_progress' && i.assigneeType === 'agent' && i.assigneeId)
      .map((i) => i.assigneeId!),
  )
  return ids.size
})

function keyOf(i: Issue) {
  return issueKey(prefix.value, i.id)
}

function assigneeLabel(i: Issue) {
  if (i.assigneeType === 'agent' && i.assigneeId) {
    return agentById.value.get(i.assigneeId)?.name || '智能体'
  }
  return ''
}

async function load() {
  loading.value = true
  err.value = ''
  try {
    const [iss, ag, wss] = await Promise.all([
      apiFetch<Issue[]>('/api/issues'),
      apiFetch<Agent[]>('/api/agents'),
      apiFetch<WorkspaceInfo[]>('/api/auth/workspaces'),
    ])
    issues.value = iss
    agents.value = ag
    const wid = getWorkspaceId()
    workspace.value =
      wss.find((w) => String(w.id) === String(wid)) || wss[0] || null
  } catch (e) {
    err.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

function openCreate(status: IssueStatus = 'todo') {
  createStatus.value = status
  createTitle.value = ''
  createDesc.value = ''
  createAssignee.value = ''
  createPriority.value = 'medium'
  showCreate.value = true
}

function closeCreate() {
  if (busy.value) return
  showCreate.value = false
  if (route.query.new) {
    router.replace({ path: '/issues' })
  }
}

async function submitCreate() {
  const title = createTitle.value.trim()
  if (!title) {
    err.value = '标题不能为空'
    return
  }
  busy.value = true
  err.value = ''
  try {
    const body: Record<string, string> = {
      title,
      description: createDesc.value.trim(),
      status: createStatus.value,
      priority: createPriority.value,
    }
    if (createAssignee.value) {
      body.assigneeType = 'agent'
      body.assigneeId = createAssignee.value
    }
    await apiFetch('/api/issues', { method: 'POST', body: JSON.stringify(body) })
    showCreate.value = false
    if (route.query.new) router.replace({ path: '/issues' })
    await load()
  } catch (e) {
    err.value = e instanceof Error ? e.message : '创建失败'
  } finally {
    busy.value = false
  }
}

async function moveIssue(id: string, status: IssueStatus) {
  const cur = issues.value.find((i) => i.id === id)
  if (!cur || normalizeIssueStatus(cur.status) === status) return
  const prev = cur.status
  cur.status = status
  try {
    await apiFetch(`/api/issues/${id}`, {
      method: 'PUT',
      body: JSON.stringify({ status }),
    })
  } catch (e) {
    cur.status = prev
    err.value = e instanceof Error ? e.message : '状态更新失败'
  }
}

function onDragStart(ev: DragEvent, id: string) {
  draggingId.value = id
  if (ev.dataTransfer) {
    ev.dataTransfer.effectAllowed = 'move'
    ev.dataTransfer.setData('text/plain', id)
  }
}

function onDragEnd() {
  draggingId.value = ''
  dropTarget.value = ''
}

function onDragOver(ev: DragEvent, status: IssueStatus) {
  ev.preventDefault()
  dropTarget.value = status
  if (ev.dataTransfer) ev.dataTransfer.dropEffect = 'move'
}

function onDragLeave(status: IssueStatus) {
  if (dropTarget.value === status) dropTarget.value = ''
}

async function onDrop(ev: DragEvent, status: IssueStatus) {
  ev.preventDefault()
  const id = ev.dataTransfer?.getData('text/plain') || draggingId.value
  dropTarget.value = ''
  draggingId.value = ''
  if (id) await moveIssue(id, status)
}

async function openIssue(id: string) {
  detailBusy.value = true
  err.value = ''
  try {
    selected.value = await apiFetch<IssueDetail>(`/api/issues/${id}`)
  } catch (e) {
    err.value = e instanceof Error ? e.message : '加载详情失败'
  } finally {
    detailBusy.value = false
  }
}

function closeDetail() {
  selected.value = null
  comment.value = ''
}

async function changeSelectedStatus(status: IssueStatus) {
  if (!selected.value) return
  await moveIssue(selected.value.id, status)
  selected.value = { ...selected.value, status }
  await load()
}

async function sendComment() {
  if (!selected.value || !comment.value.trim()) return
  detailBusy.value = true
  try {
    await apiFetch(`/api/issues/${selected.value.id}/comments`, {
      method: 'POST',
      body: JSON.stringify({ content: comment.value.trim() }),
    })
    comment.value = ''
    await openIssue(selected.value.id)
    await load()
  } catch (e) {
    err.value = e instanceof Error ? e.message : '发送失败'
  } finally {
    detailBusy.value = false
  }
}

watch(
  () => route.query.new,
  (v) => {
    if (v === '1') openCreate('todo')
  },
)

onMounted(() => {
  void load()
  if (route.query.new === '1') openCreate('todo')
})
</script>

<template>
  <section class="issues-shell">
    <header class="toolbar">
      <div class="tabs" role="tablist">
        <button type="button" :class="{ on: scope === 'all' }" @click="scope = 'all'">全部</button>
        <button type="button" :class="{ on: scope === 'members' }" @click="scope = 'members'">成员</button>
        <button type="button" :class="{ on: scope === 'agents' }" @click="scope = 'agents'">智能体</button>
      </div>
      <div class="tools">
        <span class="working">{{ agentsWorking }} 个智能体工作中</span>
        <button type="button" class="tool" disabled title="筛选即将支持">筛选</button>
        <button type="button" class="tool" disabled title="显示即将支持">显示</button>
        <button type="button" class="tool view on" title="看板视图">看板</button>
      </div>
    </header>

    <p v-if="err" class="error">{{ err }}</p>

    <div class="board-wrap">
      <div v-if="loading && !issues.length" class="board-empty">加载中…</div>
      <div v-else class="board">
        <div
          v-for="col in columns"
          :key="col.id"
          class="col"
          :class="{ drop: dropTarget === col.id }"
          @dragover="onDragOver($event, col.id)"
          @dragleave="onDragLeave(col.id)"
          @drop="onDrop($event, col.id)"
        >
          <div class="col-head">
            <span class="col-title">
              <i class="st" :class="col.tone" aria-hidden="true">
                <svg v-if="col.id === 'in_progress'" viewBox="0 0 16 16" width="14" height="14">
                  <circle cx="8" cy="8" r="6" fill="none" stroke="currentColor" stroke-width="1.6" />
                  <path d="M8 4.5v3.7l2.4 1.4" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
                </svg>
                <svg v-else-if="col.id === 'in_review' || col.id === 'done'" viewBox="0 0 16 16" width="14" height="14">
                  <circle cx="8" cy="8" r="6" fill="none" stroke="currentColor" stroke-width="1.6" />
                  <path d="M5.2 8.1 7.1 10l3.7-4" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" />
                </svg>
                <svg v-else viewBox="0 0 16 16" width="14" height="14">
                  <circle cx="8" cy="8" r="6" fill="none" stroke="currentColor" stroke-width="1.6" />
                </svg>
              </i>
              <strong>{{ col.label }}</strong>
              <span class="cnt">{{ col.items.length }}</span>
            </span>
            <div class="col-acts">
              <button type="button" class="icon-btn" title="在此列新建" @click="openCreate(col.id)">+</button>
            </div>
          </div>

          <div class="col-body">
            <article
              v-for="i in col.items"
              :key="i.id"
              class="card"
              :class="{ dragging: draggingId === i.id }"
              draggable="true"
              @dragstart="onDragStart($event, i.id)"
              @dragend="onDragEnd"
              @click="openIssue(i.id)"
            >
              <div class="card-top">
                <span class="ikey">{{ keyOf(i) }}</span>
                <span class="prio" :class="(i.priority || 'medium').toLowerCase()" :title="i.priority || 'medium'">
                  <svg viewBox="0 0 12 12" width="12" height="12" aria-hidden="true">
                    <rect x="1" y="7" width="2.2" height="3.5" rx="0.4" fill="currentColor" opacity="0.35" />
                    <rect x="4.9" y="4.5" width="2.2" height="6" rx="0.4" fill="currentColor" opacity="0.65" />
                    <rect x="8.8" y="2" width="2.2" height="8.5" rx="0.4" fill="currentColor" />
                  </svg>
                </span>
              </div>
              <h3 class="card-title">{{ i.title }}</h3>
              <p v-if="snippet(i.description)" class="card-desc">{{ snippet(i.description) }}</p>
              <div class="card-foot">
                <span v-if="i.assigneeType === 'agent' && i.assigneeId" class="who" :title="assigneeLabel(i)">
                  <AgentAvatar
                    :src="agentById.get(i.assigneeId)?.avatar"
                    :provider="agentById.get(i.assigneeId)?.provider"
                    :size="22"
                    :rounded="999"
                  />
                </span>
                <span v-else class="who muted-av">未指派</span>
                <span class="when">{{ formatRelative(i.updatedAt || i.createdAt) }}更新</span>
              </div>
            </article>

            <p v-if="!col.items.length" class="col-empty">暂无 issue</p>
          </div>
        </div>
      </div>
    </div>

    <!-- 新建 -->
    <div v-if="showCreate" class="backdrop" @click.self="closeCreate">
      <div class="modal" role="dialog" aria-modal="true" aria-labelledby="create-issue-title">
        <h3 id="create-issue-title">新建 Issue</h3>
        <label>
          标题
          <input v-model="createTitle" type="text" placeholder="简要描述要做的事" autofocus @keydown.enter.prevent="submitCreate" />
        </label>
        <label>
          描述
          <textarea v-model="createDesc" rows="4" placeholder="补充上下文（可选）" />
        </label>
        <div class="row2">
          <label>
            状态
            <select v-model="createStatus">
              <option v-for="c in ISSUE_COLUMNS" :key="c.id" :value="c.id">{{ c.label }}</option>
            </select>
          </label>
          <label>
            优先级
            <select v-model="createPriority">
              <option value="low">低</option>
              <option value="medium">中</option>
              <option value="high">高</option>
              <option value="urgent">紧急</option>
            </select>
          </label>
        </div>
        <label>
          指派智能体
          <select v-model="createAssignee">
            <option value="">暂不指派</option>
            <option v-for="a in agents" :key="a.id" :value="a.id">{{ a.name }}</option>
          </select>
        </label>
        <div class="modal-acts">
          <button type="button" class="ghost" :disabled="busy" @click="closeCreate">取消</button>
          <button type="button" class="solid" :disabled="busy" @click="submitCreate">
            {{ busy ? '创建中…' : '创建' }}
          </button>
        </div>
      </div>
    </div>

    <!-- 详情抽屉 -->
    <div v-if="selected" class="drawer-backdrop" @click.self="closeDetail">
      <aside class="drawer" role="dialog" aria-modal="true">
        <header class="drawer-head">
          <div>
            <span class="ikey">{{ keyOf(selected) }}</span>
            <h2>{{ selected.title }}</h2>
          </div>
          <button type="button" class="icon-btn" aria-label="关闭" @click="closeDetail">×</button>
        </header>
        <p v-if="selected.description" class="drawer-desc">{{ selected.description }}</p>
        <p v-else class="drawer-desc muted">暂无描述</p>

        <label class="field">
          状态
          <select
            :value="normalizeIssueStatus(selected.status)"
            @change="changeSelectedStatus(($event.target as HTMLSelectElement).value as IssueStatus)"
          >
            <option v-for="c in ISSUE_COLUMNS" :key="c.id" :value="c.id">{{ c.label }}</option>
          </select>
        </label>

        <section class="block">
          <h4>任务</h4>
          <ul v-if="selected.tasks?.length" class="mini-list">
            <li v-for="t in selected.tasks" :key="t.id">
              <span class="badge">{{ t.status }}</span>
              {{ t.triggerSource || '—' }}
            </li>
          </ul>
          <p v-else class="muted">尚无关联 task</p>
        </section>

        <section class="block">
          <h4>评论</h4>
          <ul v-if="selected.comments?.length" class="comments">
            <li v-for="c in selected.comments" :key="c.id">
              <pre>{{ c.content }}</pre>
              <span class="muted">{{ formatRelative(c.createdAt) }}</span>
            </li>
          </ul>
          <p v-else class="muted">还没有评论</p>
          <textarea v-model="comment" rows="3" placeholder="@智能体名 追加执行…" />
          <button type="button" class="solid" :disabled="detailBusy || !comment.trim()" @click="sendComment">
            发送评论
          </button>
        </section>
      </aside>
    </div>
  </section>
</template>

<style scoped>
.issues-shell {
  display: flex;
  flex-direction: column;
  height: calc(100% + 48px);
  min-height: 0;
  margin: -24px;
  padding: 0;
  background: #f3f4f6;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  padding: 14px 20px 10px;
  background: #fff;
  border-bottom: 1px solid var(--border);
}

.tabs {
  display: inline-flex;
  gap: 2px;
  background: #f3f4f6;
  border-radius: 8px;
  padding: 3px;
}

.tabs button {
  border: none;
  background: transparent;
  color: var(--muted);
  border-radius: 6px;
  padding: 6px 12px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}

.tabs button.on {
  background: #fff;
  color: var(--text);
  box-shadow: 0 1px 2px rgba(16, 24, 40, 0.06);
}

.tools {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.working {
  font-size: 12px;
  color: var(--muted);
  margin-right: 4px;
}

.tool {
  border: 1px solid var(--border);
  background: #fff;
  border-radius: 7px;
  padding: 6px 10px;
  font-size: 12px;
  font-weight: 600;
  color: var(--text);
  cursor: pointer;
}

.tool:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.tool.view.on {
  background: #f8fafc;
}

.error {
  margin: 8px 20px 0;
  color: var(--danger);
  font-size: 13px;
}

.board-wrap {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 14px 16px 20px;
  background: #e8eaed;
}

.board {
  display: flex;
  gap: 14px;
  align-items: stretch;
  min-width: max-content;
  min-height: 100%;
}

.board-empty {
  padding: 40px;
  text-align: center;
  color: var(--muted);
}

.col {
  width: 280px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  max-height: 100%;
  min-height: 420px;
  background: #f7f8fa;
  border: 1px solid #cfd3dc;
  border-radius: 10px;
  padding: 8px 8px 10px;
  box-shadow: 0 1px 0 rgba(16, 24, 40, 0.04);
  transition: background 0.12s ease, border-color 0.12s ease, box-shadow 0.12s ease;
}

.col.drop {
  background: #eef8f4;
  border-color: #0f6e56;
  box-shadow: 0 0 0 1px rgba(15, 110, 86, 0.2);
}

.col-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 4px 2px 10px;
}

.col-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.col-title strong {
  font-size: 13px;
  font-weight: 650;
}

.cnt {
  font-size: 12px;
  color: var(--muted);
  font-weight: 600;
}

.st {
  display: inline-flex;
  color: #98a2b3;
}

.st.amber { color: #f5a524; }
.st.green { color: #12b76a; }
.st.blue { color: #2e90fa; }
.st.muted { color: #98a2b3; }

.col-acts {
  display: inline-flex;
  gap: 2px;
}

.icon-btn {
  border: none;
  background: transparent;
  color: var(--muted);
  width: 26px;
  height: 26px;
  border-radius: 6px;
  font-size: 16px;
  line-height: 1;
  cursor: pointer;
}

.icon-btn:hover {
  background: #e8eaef;
  color: var(--text);
}

.col-body {
  display: flex;
  flex-direction: column;
  gap: 8px;
  overflow: auto;
  padding-bottom: 8px;
  min-height: 120px;
}

.col-empty {
  margin: 8px 4px;
  font-size: 12px;
  color: #98a2b3;
}

.card {
  background: #fff;
  border: 1px solid #e8eaef;
  border-radius: 10px;
  padding: 12px 12px 10px;
  cursor: grab;
  box-shadow: 0 1px 2px rgba(16, 24, 40, 0.04);
  transition: box-shadow 0.12s ease, border-color 0.12s ease, opacity 0.12s ease;
}

.card:hover {
  border-color: #d0d5dd;
  box-shadow: 0 2px 8px rgba(16, 24, 40, 0.06);
}

.card.dragging {
  opacity: 0.55;
  cursor: grabbing;
}

.card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}

.ikey {
  font-size: 11px;
  font-weight: 650;
  color: #667085;
  letter-spacing: 0.02em;
}

.prio {
  display: inline-flex;
  color: #f5a524;
}

.prio.low { color: #98a2b3; }
.prio.medium { color: #f5a524; }
.prio.high { color: #f04438; }
.prio.urgent { color: #d92d20; }

.card-title {
  margin: 0 0 6px;
  font-size: 14px;
  font-weight: 650;
  line-height: 1.35;
  color: var(--text);
}

.card-desc {
  margin: 0 0 10px;
  font-size: 12px;
  line-height: 1.45;
  color: #667085;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.who {
  display: inline-flex;
  align-items: center;
}

.muted-av {
  font-size: 11px;
  color: #98a2b3;
}

.when {
  font-size: 11px;
  color: #98a2b3;
}

.backdrop,
.drawer-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(16, 24, 40, 0.28);
  z-index: 40;
  display: flex;
}

.backdrop {
  align-items: center;
  justify-content: center;
  padding: 24px;
}

.modal {
  width: min(480px, 100%);
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 16px 40px rgba(16, 24, 40, 0.16);
}

.modal h3 {
  margin: 0 0 14px;
  font-size: 17px;
}

.modal label,
.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 12px;
  font-weight: 600;
  color: var(--muted);
  margin-bottom: 12px;
}

.modal input,
.modal textarea,
.modal select,
.field select,
.drawer textarea {
  font: inherit;
  font-weight: 500;
  color: var(--text);
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 9px 10px;
  background: #fff;
}

.row2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.modal-acts {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 8px;
}

.ghost,
.solid {
  border-radius: 8px;
  padding: 8px 14px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}

.ghost {
  border: 1px solid var(--border);
  background: #fff;
  color: var(--text);
}

.solid {
  border: none;
  background: #1c2333;
  color: #fff;
}

.solid:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.drawer-backdrop {
  justify-content: flex-end;
}

.drawer {
  width: min(420px, 100%);
  height: 100%;
  background: #fff;
  border-left: 1px solid var(--border);
  padding: 18px 20px 28px;
  overflow: auto;
  box-shadow: -8px 0 24px rgba(16, 24, 40, 0.08);
}

.drawer-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
  margin-bottom: 12px;
}

.drawer-head h2 {
  margin: 4px 0 0;
  font-size: 18px;
  line-height: 1.3;
}

.drawer-desc {
  margin: 0 0 16px;
  font-size: 13px;
  line-height: 1.55;
  color: #344054;
  white-space: pre-wrap;
}

.drawer-desc.muted,
.muted {
  color: var(--muted);
}

.block {
  margin-top: 18px;
}

.block h4 {
  margin: 0 0 8px;
  font-size: 13px;
}

.mini-list,
.comments {
  list-style: none;
  margin: 0 0 10px;
  padding: 0;
}

.mini-list li,
.comments li {
  padding: 8px 0;
  border-bottom: 1px solid #f0f2f5;
  font-size: 13px;
}

.comments pre {
  margin: 0 0 4px;
  white-space: pre-wrap;
  font: inherit;
}

.badge {
  display: inline-block;
  font-size: 11px;
  font-weight: 650;
  color: #667085;
  background: #f2f4f7;
  border-radius: 999px;
  padding: 2px 8px;
  margin-right: 6px;
}

.drawer textarea {
  width: 100%;
  margin-bottom: 8px;
  resize: vertical;
}
</style>
