<script setup lang="ts">
/**
 * 侧栏：账号/工作区切换、搜索/新建、主导航、配置分组。
 */
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { apiFetch } from '@/lib/api'
import { getHostBridge } from '@/lib/hostBridge'
import { clearSession, getSessionEmail, getWorkspaceId, setWorkspaceId } from '@/lib/session'

type NavItem = {
  path?: string
  label: string
  icon: keyof typeof icons
  badge?: 'unread'
  soon?: boolean
}

type WorkspaceItem = {
  id: string
  name: string
  slug?: string
  role?: string
  issuePrefix?: string
}

const stroke =
  'stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" fill="none"'

const icons = {
  inbox: `<svg width="16" height="16" viewBox="0 0 24 24"><path d="M4 8.5h16v9.5a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V8.5Z" ${stroke}/><path d="M4 8.5 6.5 4h11L20 8.5M4 8.5h16M9 13h6" ${stroke}/></svg>`,
  chat: `<svg width="16" height="16" viewBox="0 0 24 24"><path d="M5 6.5A2.5 2.5 0 0 1 7.5 4h9A2.5 2.5 0 0 1 19 6.5v6A2.5 2.5 0 0 1 16.5 15H10l-4 3.5V15H7.5A2.5 2.5 0 0 1 5 12.5v-6Z" ${stroke}/></svg>`,
  'my-issues': `<svg width="16" height="16" viewBox="0 0 24 24"><circle cx="12" cy="9" r="3.5" ${stroke}/><path d="M5.5 19.5c1.5-3 4-4.5 6.5-4.5s5 1.5 6.5 4.5" ${stroke}/></svg>`,
  issues: `<svg width="16" height="16" viewBox="0 0 24 24"><path d="M8 7h11M8 12h11M8 17h11M5 7h.01M5 12h.01M5 17h.01" ${stroke}/></svg>`,
  projects: `<svg width="16" height="16" viewBox="0 0 24 24"><path d="M4 8.5V18a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9.5a1 1 0 0 0-1-1h-6.5L11 6H5a1 1 0 0 0-1 1v1.5Z" ${stroke}/></svg>`,
  automation: `<svg width="16" height="16" viewBox="0 0 24 24"><path d="M13 3 5.5 13.5H12l-1 7.5L18.5 10H12L13 3Z" ${stroke}/></svg>`,
  agents: `<svg width="16" height="16" viewBox="0 0 24 24"><rect x="6" y="7" width="12" height="10" rx="2" ${stroke}/><path d="M10 11h.01M14 11h.01M9 14.5h6M12 4v3M9 4h6" ${stroke}/></svg>`,
  team: `<svg width="16" height="16" viewBox="0 0 24 24"><circle cx="9" cy="9" r="2.5" ${stroke}/><circle cx="16" cy="10" r="2" ${stroke}/><path d="M4.5 18c1.2-2.2 3-3.3 4.5-3.3S12.3 15.8 13.5 18M13 18c.7-1.4 1.8-2.2 3-2.2s2.2.7 2.8 2" ${stroke}/></svg>`,
  usage: `<svg width="16" height="16" viewBox="0 0 24 24"><path d="M5 19V10M10 19V6M15 19v-7M20 19V8" ${stroke}/></svg>`,
  runtime: `<svg width="16" height="16" viewBox="0 0 24 24"><rect x="3.5" y="5" width="17" height="12" rx="2" ${stroke}/><path d="M8 20h8M12 17v3" ${stroke}/></svg>`,
  skills: `<svg width="16" height="16" viewBox="0 0 24 24"><path d="M6 5.5A2.5 2.5 0 0 1 8.5 3H18v16H8.5A2.5 2.5 0 0 0 6 21.5V5.5Z" ${stroke}/><path d="M6 18h12" ${stroke}/></svg>`,
  settings: `<svg width="16" height="16" viewBox="0 0 24 24"><circle cx="12" cy="12" r="3" ${stroke}/><path d="M12 3.5v2.2M12 18.3v2.2M4.9 7.5l1.9 1.1M17.2 15.4l1.9 1.1M4.9 16.5l1.9-1.1M17.2 8.6l1.9-1.1" ${stroke}/></svg>`,
  compose: `<svg width="16" height="16" viewBox="0 0 24 24"><path d="M5 19h14M8 15.5 16.5 7l2 2L10 17.5H8v-2Z" ${stroke}/></svg>`,
} as const

const route = useRoute()
const router = useRouter()
const unread = ref(0)
const userEmail = ref(getSessionEmail())
const menuOpen = ref(false)
const searchQ = ref('')
const workspaces = ref<WorkspaceItem[]>([])
const currentWorkspaceId = ref(getWorkspaceId())
const workspaceBusy = ref(false)
const showCreateWs = ref(false)
const newWsName = ref('')
const createWsErr = ref('')
const createWsBusy = ref(false)
let timer: number | undefined

const displayName = computed(() => {
  const e = userEmail.value || ''
  if (!e) return '未登录'
  const at = e.indexOf('@')
  return at > 0 ? e.slice(0, at) : e
})

const avatarLetter = computed(() => (displayName.value[0] || 'R').toUpperCase())

const currentWorkspace = computed(
  () =>
    workspaces.value.find((w) => String(w.id) === String(currentWorkspaceId.value)) ||
    workspaces.value[0] ||
    null,
)

const workspaceTitle = computed(
  () => currentWorkspace.value?.name || currentWorkspace.value?.slug || '工作区',
)

const workspaceLetter = computed(() => {
  const n = workspaceTitle.value.trim()
  return (n[0] || 'W').toUpperCase()
})

const primaryNav: NavItem[] = [
  { path: '/inbox', label: '收件箱', icon: 'inbox', badge: 'unread' },
  { path: '/chat', label: '聊天', icon: 'chat' },
  { path: '/issues', label: '我的 issue', icon: 'my-issues' },
]

const workspaceNav: NavItem[] = [
  { path: '/issues', label: 'Issues', icon: 'issues' },
  { path: '/projects', label: '项目', icon: 'projects' },
  { label: '自动化', icon: 'automation', soon: true },
  { path: '/agents', label: '智能体', icon: 'agents' },
  { label: '小队', icon: 'team', soon: true },
  { label: '用量', icon: 'usage', soon: true },
]

const configNav: NavItem[] = [
  { path: '/runtimes', label: '运行时', icon: 'runtime' },
  { path: '/skills', label: 'Skills', icon: 'skills' },
  { path: '/settings', label: '设置', icon: 'settings' },
]

const activePath = computed(() => route.path)

function isActive(item: NavItem) {
  if (!item.path) return false
  return activePath.value.startsWith(item.path)
}

function go(item: NavItem) {
  if (item.soon || !item.path) return
  menuOpen.value = false
  router.push(item.path)
}

function newIssue() {
  router.push({ path: '/issues', query: { new: '1' } })
}

function onSearchKey(e: KeyboardEvent) {
  if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
    e.preventDefault()
    document.getElementById('sidebar-search')?.focus()
  }
}

function letterOf(name?: string) {
  const n = (name || 'W').trim()
  return (n[0] || 'W').toUpperCase()
}

async function loadUnread() {
  try {
    const data = await apiFetch<{ unread: number }>('/api/inbox')
    unread.value = data.unread || 0
  } catch {
    unread.value = 0
  }
}

async function loadWorkspaces() {
  try {
    const list = await apiFetch<WorkspaceItem[]>('/api/auth/workspaces')
    workspaces.value = list
    const id = getWorkspaceId()
    if (id) currentWorkspaceId.value = id
    else if (list[0]?.id) {
      currentWorkspaceId.value = list[0].id
      setWorkspaceId(list[0].id)
    }
  } catch {
    workspaces.value = []
  }
}

async function toggleMenu() {
  menuOpen.value = !menuOpen.value
  if (menuOpen.value) await loadWorkspaces()
}

async function switchWorkspace(ws: WorkspaceItem) {
  if (workspaceBusy.value) return
  if (String(ws.id) === String(currentWorkspaceId.value)) {
    menuOpen.value = false
    return
  }
  workspaceBusy.value = true
  try {
    await apiFetch(`/api/auth/workspaces/${ws.id}/switch`, { method: 'POST' })
    setWorkspaceId(ws.id)
    currentWorkspaceId.value = ws.id
    menuOpen.value = false
    window.location.reload()
  } catch {
    /* keep menu open */
  } finally {
    workspaceBusy.value = false
  }
}

function openCreateWorkspace() {
  menuOpen.value = false
  newWsName.value = ''
  createWsErr.value = ''
  showCreateWs.value = true
}

async function createWorkspace() {
  const name = newWsName.value.trim()
  if (!name || createWsBusy.value) return
  createWsBusy.value = true
  createWsErr.value = ''
  try {
    const ws = await apiFetch<WorkspaceItem>('/api/auth/workspaces', {
      method: 'POST',
      body: JSON.stringify({ name }),
    })
    setWorkspaceId(ws.id)
    currentWorkspaceId.value = ws.id
    showCreateWs.value = false
    window.location.reload()
  } catch (e) {
    createWsErr.value = e instanceof Error ? e.message : '创建失败'
  } finally {
    createWsBusy.value = false
  }
}

async function logout() {
  menuOpen.value = false
  try {
    await getHostBridge().stopDaemon()
  } catch {
    /* ignore */
  }
  clearSession()
  await router.replace({ name: 'login' })
}

async function switchAccount() {
  menuOpen.value = false
  try {
    await getHostBridge().stopDaemon()
  } catch {
    /* ignore */
  }
  clearSession()
  await router.replace({ name: 'login', query: { switch: '1' } })
}

onMounted(() => {
  userEmail.value = getSessionEmail()
  currentWorkspaceId.value = getWorkspaceId()
  loadUnread()
  loadWorkspaces()
  timer = window.setInterval(loadUnread, 15000)
  window.addEventListener('keydown', onSearchKey)
})
onUnmounted(() => {
  if (timer) window.clearInterval(timer)
  window.removeEventListener('keydown', onSearchKey)
})
</script>

<template>
  <div class="shell">
    <aside class="sidebar">
      <div class="account" @click.stop="toggleMenu">
        <span class="avatar">{{ workspaceLetter }}</span>
        <span class="account-name" :title="workspaceTitle">{{ workspaceTitle }}</span>
        <svg class="chevron" width="12" height="12" viewBox="0 0 24 24" fill="none" aria-hidden="true">
          <path d="M6 9l6 6 6-6" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
        </svg>
        <div v-if="menuOpen" class="account-menu" @click.stop>
          <div class="menu-user">
            <span class="user-av">{{ avatarLetter }}</span>
            <div class="user-meta">
              <strong>{{ displayName }}</strong>
              <small>{{ userEmail }}</small>
            </div>
          </div>

          <div class="menu-divider" />

          <div class="menu-label">工作区</div>
          <button
            v-for="ws in workspaces"
            :key="ws.id"
            type="button"
            class="ws-item"
            :disabled="workspaceBusy"
            @click="switchWorkspace(ws)"
          >
            <span class="ws-av">{{ letterOf(ws.name || ws.slug) }}</span>
            <span class="ws-name">{{ ws.name || ws.slug }}</span>
            <svg
              v-if="String(ws.id) === String(currentWorkspaceId)"
              class="check"
              width="16"
              height="16"
              viewBox="0 0 24 24"
              fill="none"
              aria-hidden="true"
            >
              <path
                d="M5 12.5l4.5 4.5L19 7"
                stroke="currentColor"
                stroke-width="2.2"
                stroke-linecap="round"
                stroke-linejoin="round"
              />
            </svg>
          </button>
          <button type="button" class="ws-item create" @click="openCreateWorkspace">
            <span class="plus">+</span>
            <span class="ws-name">创建工作区</span>
          </button>

          <div class="menu-divider" />

          <button type="button" class="action" @click="switchAccount">切换账号</button>
          <button type="button" class="action danger" @click="logout">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path
                d="M10 4H7a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h3"
                stroke="currentColor"
                stroke-width="1.8"
                stroke-linecap="round"
              />
              <path
                d="M14 12H4m10 0 3.5-3.5M14 12l3.5 3.5"
                stroke="currentColor"
                stroke-width="1.8"
                stroke-linecap="round"
                stroke-linejoin="round"
              />
            </svg>
            退出登录
          </button>
        </div>
      </div>

      <div class="quick">
        <label class="search">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <circle cx="11" cy="11" r="7" stroke="currentColor" stroke-width="1.75" />
            <path d="M20 20l-3.5-3.5" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" />
          </svg>
          <input id="sidebar-search" v-model="searchQ" type="search" placeholder="搜索..." />
          <kbd>⌘K</kbd>
        </label>
        <button type="button" class="quick-row" @click="newIssue">
          <span class="ico" v-html="icons.compose" />
          <span>新建 issue</span>
          <kbd class="solo">C</kbd>
        </button>
      </div>

      <nav class="nav-block">
        <button
          v-for="item in primaryNav"
          :key="'p-' + item.label"
          type="button"
          class="nav-item"
          :class="{ active: isActive(item) }"
          @click="go(item)"
        >
          <span class="ico" v-html="icons[item.icon]" />
          <span class="label">{{ item.label }}</span>
          <span v-if="item.badge === 'unread' && unread" class="badge">{{ unread }}</span>
        </button>
      </nav>

      <div class="nav-block">
        <div class="section-title">工作区</div>
        <button
          v-for="item in workspaceNav"
          :key="'w-' + item.label"
          type="button"
          class="nav-item"
          :class="{ active: isActive(item), soon: item.soon }"
          :title="item.soon ? '即将推出' : undefined"
          @click="go(item)"
        >
          <span class="ico" v-html="icons[item.icon]" />
          <span class="label">{{ item.label }}</span>
        </button>
      </div>

      <div class="nav-block">
        <div class="section-title">配置</div>
        <button
          v-for="item in configNav"
          :key="'c-' + item.label"
          type="button"
          class="nav-item"
          :class="{ active: isActive(item) }"
          @click="go(item)"
        >
          <span class="ico" v-html="icons[item.icon]" />
          <span class="label">{{ item.label }}</span>
        </button>
      </div>

      <div class="sidebar-foot">
        <button type="button" class="help-btn" title="帮助" aria-label="帮助">?</button>
      </div>
    </aside>

    <main class="content" @click="menuOpen = false">
      <router-view />
    </main>

    <div v-if="showCreateWs" class="ws-backdrop" @click.self="showCreateWs = false">
      <div class="ws-modal" role="dialog" aria-modal="true" aria-labelledby="create-ws-title">
        <h3 id="create-ws-title">创建工作区</h3>
        <p class="ws-lead">新建后将切换到该工作区。</p>
        <label>
          名称
          <input
            v-model="newWsName"
            type="text"
            placeholder="例如：dev / 产品研发"
            autofocus
            @keydown.enter.prevent="createWorkspace"
          />
        </label>
        <p v-if="createWsErr" class="ws-err">{{ createWsErr }}</p>
        <div class="ws-actions">
          <button type="button" class="btn-ghost" :disabled="createWsBusy" @click="showCreateWs = false">
            取消
          </button>
          <button
            type="button"
            class="btn-primary"
            :disabled="createWsBusy || !newWsName.trim()"
            @click="createWorkspace"
          >
            {{ createWsBusy ? '创建中…' : '创建' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.sidebar {
  width: var(--sidebar-w);
  background: #f3f4f6;
  border-right: 1px solid var(--border);
  padding: 12px 10px 10px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  user-select: none;
}

.account {
  position: relative;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  border-radius: 8px;
  cursor: pointer;
}
.account:hover {
  background: rgba(0, 0, 0, 0.04);
}
.avatar {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: #d1d5db;
  color: #374151;
  font-size: 11px;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.account-name {
  flex: 1;
  font-size: 13px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.chevron {
  color: var(--muted);
}
.account-menu {
  position: absolute;
  top: calc(100% + 6px);
  left: 0;
  width: min(280px, calc(100vw - 24px));
  background: #fff;
  border: 1px solid var(--border);
  border-radius: 12px;
  box-shadow: 0 10px 28px rgba(15, 23, 42, 0.14);
  padding: 8px;
  z-index: 40;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.menu-user {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 8px 10px;
}
.user-av,
.ws-av {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: #e5e7eb;
  color: #374151;
  font-size: 13px;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.ws-av {
  width: 26px;
  height: 26px;
  font-size: 11px;
}
.user-meta {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.user-meta strong {
  font-size: 14px;
  font-weight: 650;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.user-meta small {
  font-size: 12px;
  color: var(--muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.menu-divider {
  height: 1px;
  background: #f3f4f6;
  margin: 4px 0;
}
.menu-label {
  font-size: 11px;
  color: var(--muted);
  padding: 4px 8px 6px;
  font-weight: 500;
}
.ws-item,
.action {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  border: none;
  background: transparent;
  text-align: left;
  padding: 8px;
  border-radius: 8px;
  cursor: pointer;
  font-size: 13px;
  color: var(--text);
}
.ws-item:hover:not(:disabled),
.action:hover {
  background: #f3f4f6;
}
.ws-item:disabled {
  opacity: 0.6;
  cursor: wait;
}
.ws-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.check {
  color: var(--text);
  flex-shrink: 0;
}
.plus {
  width: 26px;
  height: 26px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  font-weight: 500;
  color: var(--text);
  flex-shrink: 0;
}
.action.danger {
  color: #dc2626;
  gap: 8px;
}
.action.danger:hover {
  background: #fef2f2;
}

.quick {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin-bottom: 4px;
}
.search {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 10px;
  border-radius: 8px;
  color: var(--muted);
}
.search:focus-within {
  background: rgba(0, 0, 0, 0.04);
}
.search input {
  flex: 1;
  border: none;
  background: transparent;
  outline: none;
  font-size: 13px;
  min-width: 0;
  color: var(--text);
}
.quick-row {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  border: none;
  background: transparent;
  padding: 7px 10px;
  border-radius: 8px;
  cursor: pointer;
  font-size: 13px;
  color: var(--text);
  text-align: left;
}
.quick-row:hover {
  background: rgba(0, 0, 0, 0.04);
}
kbd {
  margin-left: auto;
  font-family: inherit;
  font-size: 11px;
  color: var(--muted);
  background: rgba(0, 0, 0, 0.05);
  border-radius: 4px;
  padding: 1px 5px;
}
kbd.solo {
  background: transparent;
}

.nav-block {
  display: flex;
  flex-direction: column;
  gap: 1px;
  margin-top: 6px;
}
.section-title {
  font-size: 11px;
  color: var(--muted);
  padding: 8px 10px 4px;
  font-weight: 500;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  border: none;
  background: transparent;
  color: var(--text);
  padding: 7px 10px;
  border-radius: 8px;
  cursor: pointer;
  font-size: 13px;
  text-align: left;
}
.nav-item:hover:not(:disabled) {
  background: rgba(0, 0, 0, 0.04);
}
.nav-item.active {
  background: rgba(0, 0, 0, 0.07);
  font-weight: 600;
}
.nav-item.soon,
.nav-item:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.ico {
  width: 16px;
  height: 16px;
  display: inline-flex;
  color: #4b5563;
  flex-shrink: 0;
}
.nav-item.active .ico {
  color: var(--text);
}
.label {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.badge {
  background: #e5e7eb;
  color: #374151;
  border-radius: 999px;
  padding: 0 6px;
  font-size: 11px;
  font-weight: 600;
  min-width: 18px;
  text-align: center;
}

.sidebar-foot {
  margin-top: auto;
  display: flex;
  justify-content: flex-start;
  padding: 8px 4px 2px;
}
.help-btn {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  border: 1px solid var(--border);
  background: #fff;
  color: var(--muted);
  font-size: 12px;
  cursor: pointer;
  line-height: 1;
}
.help-btn:hover {
  color: var(--text);
}

.ws-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 90;
  padding: 16px;
}
.ws-modal {
  width: min(400px, 100%);
  background: #fff;
  border-radius: 12px;
  padding: 18px;
  box-shadow: 0 16px 40px rgba(0, 0, 0, 0.16);
}
.ws-modal h3 {
  margin: 0;
  font-size: 16px;
}
.ws-lead {
  margin: 6px 0 14px;
  font-size: 13px;
  color: var(--muted);
}
.ws-modal label {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
}
.ws-modal input {
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 8px 10px;
  font: inherit;
  font-size: 13px;
  font-weight: 400;
}
.ws-err {
  margin: 8px 0 0;
  color: var(--danger);
  font-size: 13px;
}
.ws-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 14px;
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
  background: #1c2333;
  color: #fff;
  border-radius: 8px;
  padding: 8px 12px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}
.btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
