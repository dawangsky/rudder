<script setup lang="ts">
/**
 * 项目列表：空态 + 新建弹框（状态/优先级/负责人/仓库/日期）。
 */
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { apiFetch } from '@/lib/api'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import MoreMenu from '@/components/MoreMenu.vue'
import ActionIcon from '@/components/ActionIcon.vue'
import {
  PROJECT_PRIORITIES,
  PROJECT_STATUSES,
  memberLabel,
  priorityLabel,
  statusLabel,
  type Project,
  type ProjectPriority,
  type ProjectStatus,
  type WorkspaceMember,
} from '@/lib/projects'
import { getWorkspaceId } from '@/lib/session'
import { getHostBridge, isDesktopHost } from '@/lib/hostBridge'
import type { Runtime } from '@/lib/runtimes'

type ChipMenu = 'status' | 'priority' | 'assignee' | 'repo' | 'more' | null
type RepoTab = 'github' | 'local'

const items = ref<Project[]>([])
const members = ref<WorkspaceMember[]>([])
const loading = ref(false)
const busy = ref(false)
const err = ref('')
const showCreate = ref(false)
const menuOpenId = ref('')
const pendingDelete = ref<Project | null>(null)

const name = ref('')
const description = ref('')
const status = ref<ProjectStatus>('planned')
const priority = ref<ProjectPriority>('none')
const assigneeUserId = ref('')
const repoUrl = ref('')
const localPath = ref('')
const repoDraft = ref('')
const localDraft = ref('')
const repoTab = ref<RepoTab>('github')
const startDate = ref('')
const dueDate = ref('')
const openChip = ref<ChipMenu>(null)
const isDesktop = isDesktopHost()
const boundHost = ref('本机')

const workspaceName = ref('工作区')

const canCreate = computed(() => name.value.trim().length > 0)

const assignee = computed(() =>
  members.value.find((m) => m.id === assigneeUserId.value) || null,
)

const memberById = computed(() => {
  const m = new Map<string, WorkspaceMember>()
  for (const x of members.value) m.set(x.id, x)
  return m
})

const localSelectLabel = computed(() => localPath.value || '选择目录...')
const boundHostLabel = computed(() => `绑定到 ${boundHost.value}`)

const repoChipLabel = computed(() => {
  if (localPath.value) {
    const parts = localPath.value.replace(/\\/g, '/').split('/')
    return parts.filter(Boolean).pop() || '本地目录'
  }
  if (repoUrl.value) {
    try {
      const u = new URL(repoUrl.value)
      const segs = u.pathname.split('/').filter(Boolean)
      return segs.slice(-2).join('/') || 'GitHub 仓库'
    } catch {
      return 'GitHub 仓库'
    }
  }
  return '代码仓库'
})

function priorityBars(level: ProjectPriority | string) {
  const n =
    level === 'urgent' ? 4 : level === 'high' ? 3 : level === 'medium' ? 2 : level === 'low' ? 1 : 0
  return n
}

function priorityTone(level: ProjectPriority | string) {
  switch (level) {
    case 'urgent':
      return 'urgent'
    case 'high':
      return 'high'
    case 'medium':
      return 'medium'
    case 'low':
      return 'low'
    default:
      return 'none'
  }
}

async function resolveBoundHost() {
  try {
    if (isDesktop) {
      const s = await getHostBridge().getDaemonStatus()
      if (s.deviceName) {
        boundHost.value = s.deviceName
        return
      }
    }
  } catch {
    // ignore
  }
  try {
    const rts = await apiFetch<Runtime[]>('/api/runtimes')
    const online = rts.find((r) => (r.status || '').toLowerCase() === 'online' && r.hostName)
    const any = rts.find((r) => r.hostName)
    boundHost.value = online?.hostName || any?.hostName || '本机'
  } catch {
    boundHost.value = '本机'
  }
}

async function load() {
  loading.value = true
  err.value = ''
  try {
    const wsId = getWorkspaceId()
    const [projects, ms, workspaces] = await Promise.all([
      apiFetch<Project[]>('/api/projects'),
      apiFetch<WorkspaceMember[]>('/api/auth/workspace-members').catch(() => [] as WorkspaceMember[]),
      apiFetch<{ id: string; name?: string; slug?: string }[]>('/api/auth/workspaces').catch(() => []),
    ])
    items.value = projects
    members.value = ms
    const current = workspaces.find((w) => String(w.id) === String(wsId))
    workspaceName.value = current?.name || current?.slug || '工作区'
    void resolveBoundHost()
  } catch (e) {
    err.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

function openCreate() {
  name.value = ''
  description.value = ''
  status.value = 'planned'
  priority.value = 'none'
  assigneeUserId.value = ''
  repoUrl.value = ''
  localPath.value = ''
  repoDraft.value = ''
  localDraft.value = ''
  repoTab.value = 'github'
  startDate.value = ''
  dueDate.value = ''
  openChip.value = null
  err.value = ''
  showCreate.value = true
}

function closeCreate() {
  if (busy.value) return
  showCreate.value = false
  openChip.value = null
}

function toggleChip(chip: ChipMenu) {
  openChip.value = openChip.value === chip ? null : chip
  if (chip === 'repo' && openChip.value === 'repo') {
    repoDraft.value = repoUrl.value
    localDraft.value = localPath.value
    repoTab.value = localPath.value && !repoUrl.value ? 'local' : 'github'
  }
}

function applyRepoUrl() {
  const v = repoDraft.value.trim()
  if (!v) return
  repoUrl.value = v
  openChip.value = null
}

async function pickLocalDirectory() {
  if (isDesktop) {
    const res = await getHostBridge().selectDirectory()
    if (res.ok && res.path) {
      localDraft.value = res.path
      localPath.value = res.path
    }
    return
  }
  // 浏览器：退化为路径输入确认
  const v = window.prompt('请输入本机绝对路径', localDraft.value || localPath.value || '')
  if (v == null) return
  const trimmed = v.trim()
  if (!trimmed) return
  localDraft.value = trimmed
  localPath.value = trimmed
}

function clearRepo() {
  repoUrl.value = ''
  repoDraft.value = ''
}

function clearLocal() {
  localPath.value = ''
  localDraft.value = ''
}

function onDocClick(e: MouseEvent) {
  const t = e.target as HTMLElement | null
  if (!t?.closest?.('.chip-wrap')) openChip.value = null
}

async function createProject() {
  if (!canCreate.value) return
  busy.value = true
  err.value = ''
  try {
    await apiFetch('/api/projects', {
      method: 'POST',
      body: JSON.stringify({
        name: name.value.trim(),
        description: description.value.trim() || null,
        status: status.value,
        priority: priority.value,
        assigneeUserId: assigneeUserId.value || null,
        repoUrl: repoUrl.value.trim() || null,
        localPath: localPath.value.trim() || null,
        startDate: startDate.value || null,
        dueDate: dueDate.value || null,
      }),
    })
    showCreate.value = false
    await load()
  } catch (e) {
    err.value = e instanceof Error ? e.message : '创建失败'
  } finally {
    busy.value = false
  }
}

function setMenuOpen(id: string, open: boolean) {
  menuOpenId.value = open ? id : ''
}

function askDelete(p: Project) {
  pendingDelete.value = p
}

async function confirmDelete() {
  if (!pendingDelete.value) return
  busy.value = true
  err.value = ''
  try {
    await apiFetch(`/api/projects/${pendingDelete.value.id}`, { method: 'DELETE' })
    pendingDelete.value = null
    await load()
  } catch (e) {
    err.value = e instanceof Error ? e.message : '删除失败'
  } finally {
    busy.value = false
  }
}

function assigneeName(p: Project) {
  if (!p.assigneeUserId) return '—'
  return memberLabel(memberById.value.get(p.assigneeUserId) || null)
}

onMounted(() => {
  load()
  document.addEventListener('click', onDocClick)
})
onUnmounted(() => document.removeEventListener('click', onDocClick))
</script>

<template>
  <section class="page">
    <header class="head">
      <div>
        <h2>
          <span class="title-icon" aria-hidden="true">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none">
              <path
                d="M4 8.5V18a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9.5a1 1 0 0 0-1-1h-6.5L11 6H5a1 1 0 0 0-1 1v1.5Z"
                stroke="currentColor"
                stroke-width="1.6"
              />
            </svg>
          </span>
          项目
          <span v-if="items.length" class="count">{{ items.length }}</span>
        </h2>
      </div>
      <button type="button" class="btn-add" @click="openCreate">+ 新建项目</button>
    </header>

    <p v-if="err && !showCreate" class="error">{{ err }}</p>

    <div v-if="loading" class="empty-state">
      <p class="muted">加载中…</p>
    </div>

    <div v-else-if="!items.length" class="empty-state">
      <div class="empty-icon" aria-hidden="true">
        <svg width="28" height="28" viewBox="0 0 24 24" fill="none">
          <path
            d="M4 8.5V18a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9.5a1 1 0 0 0-1-1h-6.5L11 6H5a1 1 0 0 0-1 1v1.5Z"
            stroke="currentColor"
            stroke-width="1.5"
          />
        </svg>
      </div>
      <h3>还没有项目</h3>
      <button type="button" class="btn-ghost-strong" @click="openCreate">创建第一个项目</button>
    </div>

    <div v-else class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>项目</th>
            <th>状态</th>
            <th>优先级</th>
            <th>负责人</th>
            <th>日期</th>
            <th class="col-actions"></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="p in items" :key="p.id">
            <td>
              <div class="proj-cell">
                <strong>{{ p.name }}</strong>
                <span class="desc">{{ p.description || p.repoUrl || p.localPath || '暂无描述' }}</span>
              </div>
            </td>
            <td>
              <span class="status-pill">
                <i class="dot" :class="p.status" />
                {{ statusLabel(p.status) }}
              </span>
            </td>
            <td class="muted">{{ priorityLabel(p.priority) }}</td>
            <td class="muted">{{ assigneeName(p) }}</td>
            <td class="muted">
              <template v-if="p.startDate || p.dueDate">
                {{ p.startDate || '…' }} → {{ p.dueDate || '…' }}
              </template>
              <template v-else>—</template>
            </td>
            <td class="col-actions" @click.stop>
              <MoreMenu
                :open="menuOpenId === p.id"
                @update:open="(v) => setMenuOpen(p.id, v)"
              >
                <template #default="{ close }">
                  <button
                    type="button"
                    class="danger"
                    :disabled="busy"
                    @click="close(); askDelete(p)"
                  >
                    <ActionIcon name="delete" />
                    删除
                  </button>
                </template>
              </MoreMenu>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="showCreate" class="modal-backdrop" @click.self="closeCreate">
      <div class="modal" role="dialog" aria-modal="true" aria-labelledby="proj-create-title">
        <div class="modal-top">
          <div class="crumb">
            <span>{{ workspaceName }}</span>
            <span class="sep">›</span>
            <span>新建项目</span>
          </div>
          <button type="button" class="modal-x" aria-label="关闭" @click="closeCreate">×</button>
        </div>

        <div class="composer">
          <div class="folder-icon" aria-hidden="true">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none">
              <path
                d="M4 8.5V18a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9.5a1 1 0 0 0-1-1h-6.5L11 6H5a1 1 0 0 0-1 1v1.5Z"
                stroke="currentColor"
                stroke-width="1.5"
              />
            </svg>
          </div>
          <input
            v-model="name"
            id="proj-create-title"
            class="title-input"
            type="text"
            placeholder="项目标题"
            autofocus
          />
          <textarea
            v-model="description"
            class="desc-input"
            rows="3"
            placeholder="添加描述..."
          />

          <div class="chips">
            <div class="chip-wrap">
              <button type="button" class="chip" @click.stop="toggleChip('status')">
                <i class="dot" :class="status" />
                {{ statusLabel(status) }}
              </button>
              <div v-if="openChip === 'status'" class="pop pop-up" @click.stop>
                <button
                  v-for="s in PROJECT_STATUSES"
                  :key="s.value"
                  type="button"
                  :class="{ on: status === s.value }"
                  @click="status = s.value; openChip = null"
                >
                  <i class="dot" :class="s.value" />
                  {{ s.label }}
                </button>
              </div>
            </div>

            <div class="chip-wrap">
              <button type="button" class="chip" @click.stop="toggleChip('priority')">
                <span class="bars" :class="priorityTone(priority)" aria-hidden="true">
                  <i v-for="i in 4" :key="i" :class="{ on: i <= priorityBars(priority) }" />
                </span>
                {{ priorityLabel(priority) }}
              </button>
              <div v-if="openChip === 'priority'" class="pop pop-up" @click.stop>
                <button
                  v-for="pr in PROJECT_PRIORITIES"
                  :key="pr.value"
                  type="button"
                  :class="{ on: priority === pr.value }"
                  @click="priority = pr.value; openChip = null"
                >
                  <span class="bars" :class="priorityTone(pr.value)" aria-hidden="true">
                    <i v-for="i in 4" :key="i" :class="{ on: i <= priorityBars(pr.value) }" />
                  </span>
                  {{ pr.label }}
                </button>
              </div>
            </div>

            <div class="chip-wrap">
              <button type="button" class="chip" @click.stop="toggleChip('assignee')">
                <span class="user-ico" aria-hidden="true">
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="none">
                    <circle cx="12" cy="8" r="3.5" stroke="currentColor" stroke-width="1.6" />
                    <path d="M5 19c1.5-3.5 4-5 7-5s5.5 1.5 7 5" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
                  </svg>
                </span>
                {{ assignee ? memberLabel(assignee) : '负责人' }}
              </button>
              <div v-if="openChip === 'assignee'" class="pop pop-up" @click.stop>
                <button
                  type="button"
                  :class="{ on: !assigneeUserId }"
                  @click="assigneeUserId = ''; openChip = null"
                >
                  未指定
                </button>
                <button
                  v-for="m in members"
                  :key="m.id"
                  type="button"
                  :class="{ on: assigneeUserId === m.id }"
                  @click="assigneeUserId = m.id; openChip = null"
                >
                  {{ memberLabel(m) }}
                </button>
              </div>
            </div>

            <div class="chip-wrap">
              <button type="button" class="chip" @click.stop="toggleChip('repo')">
                <span class="gh" aria-hidden="true">
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="currentColor">
                    <path d="M12 2C6.48 2 2 6.58 2 12.26c0 4.52 2.87 8.35 6.84 9.7.5.1.68-.22.68-.48 0-.24-.01-.87-.01-1.7-2.78.62-3.37-1.37-3.37-1.37-.45-1.18-1.11-1.5-1.11-1.5-.91-.64.07-.63.07-.63 1 .07 1.53 1.06 1.53 1.06.89 1.56 2.34 1.11 2.91.85.09-.66.35-1.11.63-1.37-2.22-.26-4.55-1.14-4.55-5.07 0-1.12.39-2.03 1.03-2.75-.1-.26-.45-1.31.1-2.73 0 0 .84-.27 2.75 1.05A9.3 9.3 0 0 1 12 6.84c.85 0 1.71.12 2.51.35 1.91-1.32 2.75-1.05 2.75-1.05.55 1.42.2 2.47.1 2.73.64.72 1.03 1.63 1.03 2.75 0 3.94-2.34 4.8-4.57 5.06.36.32.68.94.68 1.9 0 1.37-.01 2.47-.01 2.81 0 .26.18.58.69.48A10.03 10.03 0 0 0 22 12.26C22 6.58 17.52 2 12 2Z" />
                  </svg>
                </span>
                {{ repoChipLabel }}
              </button>
              <div v-if="openChip === 'repo'" class="pop pop-up pop-repo" @click.stop>
                <div class="repo-tabs" role="tablist">
                  <button
                    type="button"
                    role="tab"
                    :class="{ on: repoTab === 'github' }"
                    @click="repoTab = 'github'"
                  >GitHub 仓库</button>
                  <button
                    type="button"
                    role="tab"
                    :class="{ on: repoTab === 'local' }"
                    @click="repoTab = 'local'"
                  >本地目录</button>
                </div>

                <div v-if="repoTab === 'github'" class="repo-pane">
                  <strong>为此项目关联 GitHub 仓库</strong>
                  <p>还没有工作区级别的仓库。可以在下方粘贴 URL 临时关联一个。</p>
                  <div v-if="repoUrl" class="linked">
                    <span>{{ repoUrl }}</span>
                    <button type="button" class="link-clear" @click="clearRepo">移除</button>
                  </div>
                  <div class="repo-row">
                    <input
                      v-model="repoDraft"
                      type="url"
                      placeholder="https://github.com/owner/repo 或 git..."
                      @keydown.enter.prevent="applyRepoUrl"
                    />
                    <button type="button" class="link-add" :disabled="!repoDraft.trim()" @click="applyRepoUrl">
                      添加
                    </button>
                  </div>
                </div>

                <div v-else class="repo-pane local-pane">
                  <strong>使用本机的本地工作目录</strong>
                  <p class="bound">{{ boundHostLabel }}</p>
                  <button type="button" class="pick-dir" @click="pickLocalDirectory">
                    <span class="pick-ico" aria-hidden="true">
                      <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
                        <path
                          d="M4 8.5V18a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9.5a1 1 0 0 0-1-1h-6.5L11 6H5a1 1 0 0 0-1 1v1.5Z"
                          stroke="currentColor"
                          stroke-width="1.5"
                        />
                      </svg>
                    </span>
                    <span class="pick-text" :title="localPath || undefined">{{ localSelectLabel }}</span>
                  </button>
                  <button
                    v-if="localPath"
                    type="button"
                    class="link-clear local-clear"
                    @click="clearLocal"
                  >清除已选目录</button>
                  <p class="warn">
                    其他机器上的 agent 看不到这个路径，会启动失败。需要协作请用仓库模式。
                  </p>
                </div>
              </div>
            </div>

            <div class="chip-wrap">
              <button type="button" class="chip chip-more" @click.stop="toggleChip('more')">⋯</button>
              <div v-if="openChip === 'more'" class="pop pop-up pop-dates" @click.stop>
                <label>
                  设置截止日期…
                  <input v-model="dueDate" type="date" />
                </label>
                <label>
                  设置开始日期…
                  <input v-model="startDate" type="date" />
                </label>
                <button type="button" class="btn-mini" @click="openChip = null">完成</button>
              </div>
            </div>
          </div>

          <p v-if="err" class="error">{{ err }}</p>
        </div>

        <div class="modal-foot">
          <button
            type="button"
            class="btn-create"
            :disabled="!canCreate || busy"
            @click="createProject"
          >
            {{ busy ? '创建中…' : '创建项目' }}
          </button>
        </div>
      </div>
    </div>

    <ConfirmDialog
      v-if="pendingDelete"
      :open="true"
      :title="`删除项目「${pendingDelete.name}」？`"
      description="将从工作区移除该项目。已关联的 Issue/Chat 不会自动删除。"
      confirm-label="删除"
      tone="danger"
      :busy="busy"
      @cancel="pendingDelete = null"
      @confirm="confirmDelete"
    />
  </section>
</template>

<style scoped>
.page { max-width: 1000px; }
.head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 18px;
}
h2 {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  display: flex;
  align-items: center;
  gap: 8px;
}
.title-icon { display: inline-flex; color: var(--text); }
.count { color: var(--muted); font-weight: 600; }
.btn-add {
  border: none;
  background: #1c2333;
  color: #fff;
  border-radius: 8px;
  padding: 9px 14px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}
.btn-ghost-strong {
  border: 1px solid var(--border);
  background: #fff;
  color: var(--text);
  border-radius: 8px;
  padding: 8px 14px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}
.empty-state {
  margin-top: 64px;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  gap: 12px;
  padding: 40px 20px;
}
.empty-icon {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: #f3f4f6;
  color: #9ca3af;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.empty-state h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #374151;
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
th { font-size: 12px; color: var(--muted); font-weight: 600; }
tr:last-child td { border-bottom: none; }
.proj-cell { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.proj-cell strong { font-weight: 650; }
.desc {
  color: var(--muted);
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 360px;
}
.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #9ca3af;
  display: inline-block;
}
.dot.planned { background: #9ca3af; }
.dot.in_progress { background: #3b82f6; }
.dot.completed { background: #10b981; }
.dot.canceled { background: #ef4444; }
.col-actions { width: 48px; text-align: right; }
.muted { color: var(--muted); }
.error { color: var(--danger); margin: 8px 0 0; font-size: 13px; }

.modal-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 80;
  padding: 16px;
}
.modal {
  width: min(640px, 100%);
  background: #fff;
  border-radius: 14px;
  box-shadow: 0 20px 50px rgba(0, 0, 0, 0.18);
  overflow: visible;
  display: flex;
  flex-direction: column;
}
.modal-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 16px 0;
  flex-shrink: 0;
}
.crumb {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--muted);
}
.crumb .sep { color: #d1d5db; }
.modal-x {
  border: none;
  background: transparent;
  font-size: 22px;
  line-height: 1;
  color: var(--muted);
  cursor: pointer;
}
.composer {
  padding: 12px 18px 10px;
  display: flex;
  flex-direction: column;
  min-height: 280px;
}
.folder-icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: #f3f4f6;
  color: #6b7280;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 10px;
  flex-shrink: 0;
}
.title-input {
  display: block;
  width: 100%;
  border: none;
  outline: none;
  font-size: 22px;
  font-weight: 650;
  padding: 0;
  margin-bottom: 8px;
  background: transparent;
  color: var(--text);
  flex-shrink: 0;
}
.title-input::placeholder { color: #9ca3af; font-weight: 500; }
.desc-input {
  display: block;
  width: 100%;
  border: none;
  outline: none;
  resize: none;
  font: inherit;
  font-size: 14px;
  line-height: 1.5;
  color: var(--text);
  background: transparent;
  margin-bottom: 0;
  min-height: 64px;
  flex-shrink: 0;
}
.desc-input::placeholder { color: #9ca3af; }

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  position: relative;
  margin-top: auto;
  padding-top: 28px;
  padding-bottom: 4px;
}
.chip-wrap { position: relative; }
.chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 1px solid var(--border);
  background: #fff;
  border-radius: 999px;
  padding: 6px 10px;
  font-size: 12px;
  color: #374151;
  cursor: pointer;
}
.chip:hover { background: #f9fafb; }
.chip-more { padding: 6px 10px; font-weight: 700; letter-spacing: 1px; }
.user-ico, .gh {
  display: inline-flex;
  align-items: center;
  color: #6b7280;
}

.bars {
  display: inline-flex;
  align-items: flex-end;
  gap: 1.5px;
  height: 12px;
  width: 14px;
}
.bars i {
  display: block;
  width: 2.5px;
  border-radius: 1px;
  background: #d1d5db;
  align-self: flex-end;
}
.bars i:nth-child(1) { height: 4px; }
.bars i:nth-child(2) { height: 6px; }
.bars i:nth-child(3) { height: 8px; }
.bars i:nth-child(4) { height: 10px; }
.bars.urgent i.on { background: #ef4444; }
.bars.high i.on { background: #f59e0b; }
.bars.medium i.on { background: #fb923c; }
.bars.low i.on { background: #3b82f6; }
.bars.none i { background: #d1d5db; }

.pop {
  position: absolute;
  z-index: 20;
  min-width: 160px;
  background: #fff;
  border: 1px solid var(--border);
  border-radius: 10px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.12);
  padding: 6px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.pop-up {
  bottom: calc(100% + 6px);
  left: 0;
  top: auto;
}
.pop button {
  border: none;
  background: transparent;
  text-align: left;
  padding: 8px 10px;
  border-radius: 8px;
  font-size: 13px;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text);
}
.pop button:hover,
.pop button.on { background: #f3f4f6; }
.pop-dates {
  min-width: 240px;
  padding: 10px;
  gap: 8px;
}
.pop-dates label {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
  color: var(--muted);
  font-weight: 600;
}
.pop-dates input {
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 7px 8px;
  font: inherit;
  font-size: 13px;
  font-weight: 400;
  color: var(--text);
}
.pop-repo {
  width: min(360px, 78vw);
  min-width: 300px;
  padding: 10px;
  gap: 10px;
  right: 0;
  left: auto;
}
.repo-tabs {
  display: flex;
  gap: 2px;
  padding: 3px;
  border-radius: 999px;
  background: #f3f4f6;
}
.repo-tabs button {
  flex: 1;
  justify-content: center;
  border-radius: 999px;
  padding: 6px 10px;
  font-size: 12px;
  font-weight: 600;
  color: #6b7280;
}
.repo-tabs button.on {
  background: #fff;
  color: var(--text);
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.06);
}
.repo-pane {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 4px 2px 0;
}
.repo-pane strong {
  font-size: 13px;
  font-weight: 650;
}
.repo-pane > p {
  margin: 0;
  font-size: 12px;
  color: var(--muted);
  line-height: 1.45;
}
.local-pane {
  gap: 10px;
  padding-top: 6px;
}
.local-pane .bound {
  margin-top: -4px;
  color: #9ca3af;
}
.pick-dir {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100%;
  border: none;
  background: #f3f4f6;
  color: #111827;
  border-radius: 10px;
  padding: 12px 14px;
  font-size: 13px;
  font-weight: 550;
  cursor: pointer;
}
.pick-dir:hover { background: #e5e7eb; }
.pick-ico {
  display: inline-flex;
  color: #6b7280;
  flex-shrink: 0;
}
.pick-text {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.local-clear {
  align-self: center;
  font-size: 12px;
}
.local-pane .warn {
  margin: 2px 0 0;
  font-size: 11px;
  color: #9ca3af;
  line-height: 1.45;
  text-align: left;
}
.linked {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #374151;
  background: #f9fafb;
  border-radius: 8px;
  padding: 6px 8px;
}
.linked span {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.link-clear {
  border: none;
  background: transparent;
  color: #2563eb;
  font-size: 12px;
  cursor: pointer;
  padding: 0;
}
.repo-row {
  display: flex;
  align-items: center;
  gap: 8px;
  border-top: 1px solid #f3f4f6;
  padding-top: 10px;
  margin-top: 2px;
}
.repo-row input {
  flex: 1;
  min-width: 0;
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 7px 8px;
  font: inherit;
  font-size: 13px;
  color: var(--text);
}
.link-add {
  border: none;
  background: transparent;
  color: #2563eb;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  padding: 4px 2px;
  white-space: nowrap;
}
.link-add:disabled {
  color: #9ca3af;
  cursor: not-allowed;
}
.btn-mini {
  align-self: flex-end;
  border: none;
  background: #1c2333;
  color: #fff;
  border-radius: 6px;
  padding: 6px 10px;
  font-size: 12px;
  cursor: pointer;
}

.modal-foot {
  display: flex;
  justify-content: flex-end;
  padding: 12px 16px 16px;
  border-top: 1px solid #f3f4f6;
  margin-top: 0;
  flex-shrink: 0;
  border-radius: 0 0 14px 14px;
}
.btn-create {
  border: none;
  background: #e5e7eb;
  color: #9ca3af;
  border-radius: 8px;
  padding: 9px 14px;
  font-size: 13px;
  font-weight: 600;
  cursor: not-allowed;
}
.btn-create:not(:disabled) {
  background: #1c2333;
  color: #fff;
  cursor: pointer;
}
</style>
