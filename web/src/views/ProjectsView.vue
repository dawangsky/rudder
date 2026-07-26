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

type ChipMenu = 'status' | 'priority' | 'assignee' | 'repo' | 'more' | null

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
const startDate = ref('')
const dueDate = ref('')
const openChip = ref<ChipMenu>(null)

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
              <div v-if="openChip === 'status'" class="pop" @click.stop>
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
                <span class="prio">—</span>
                {{ priorityLabel(priority) }}
              </button>
              <div v-if="openChip === 'priority'" class="pop" @click.stop>
                <button
                  v-for="pr in PROJECT_PRIORITIES"
                  :key="pr.value"
                  type="button"
                  :class="{ on: priority === pr.value }"
                  @click="priority = pr.value; openChip = null"
                >
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
              <div v-if="openChip === 'assignee'" class="pop" @click.stop>
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
                {{ repoUrl || localPath ? '已关联仓库' : '代码仓库' }}
              </button>
              <div v-if="openChip === 'repo'" class="pop pop-form" @click.stop>
                <label>
                  GitHub / Git URL
                  <input v-model="repoUrl" type="url" placeholder="https://github.com/org/repo" />
                </label>
                <label>
                  本机路径（可选）
                  <input v-model="localPath" type="text" placeholder="/Users/you/code/app" />
                </label>
                <button type="button" class="btn-mini" @click="openChip = null">完成</button>
              </div>
            </div>

            <div class="chip-wrap">
              <button type="button" class="chip chip-more" @click.stop="toggleChip('more')">⋯</button>
              <div v-if="openChip === 'more'" class="pop pop-dates" @click.stop>
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
  max-height: min(92vh, 760px);
  overflow: auto;
  display: flex;
  flex-direction: column;
}
.modal-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 16px 0;
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
.composer { padding: 12px 18px 8px; }
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
  margin-bottom: 14px;
}
.desc-input::placeholder { color: #9ca3af; }

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  position: relative;
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
.prio { color: #9ca3af; font-weight: 700; }
.user-ico, .gh {
  display: inline-flex;
  align-items: center;
  color: #6b7280;
}

.pop {
  position: absolute;
  top: calc(100% + 6px);
  left: 0;
  z-index: 5;
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
.pop-form,
.pop-dates {
  min-width: 260px;
  padding: 10px;
  gap: 8px;
}
.pop-form label,
.pop-dates label {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
  color: var(--muted);
  font-weight: 600;
}
.pop-form input,
.pop-dates input {
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 7px 8px;
  font: inherit;
  font-size: 13px;
  font-weight: 400;
  color: var(--text);
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
  margin-top: 8px;
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
