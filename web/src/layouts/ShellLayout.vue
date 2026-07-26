<script setup lang="ts">
/**
 * 侧栏对齐 Multica：账号头、搜索/新建、主导航、工作区、配置分组。
 */
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { apiFetch } from '@/lib/api'
import { getHostBridge } from '@/lib/hostBridge'
import { clearSession, getSessionEmail } from '@/lib/session'

type NavItem = {
  path?: string
  label: string
  icon: keyof typeof icons
  badge?: 'unread'
  soon?: boolean
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
let timer: number | undefined

const displayName = computed(() => {
  const e = userEmail.value || ''
  if (!e) return '未登录'
  const at = e.indexOf('@')
  return at > 0 ? e.slice(0, at) : e
})

const avatarLetter = computed(() => (displayName.value[0] || 'R').toUpperCase())

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

async function loadUnread() {
  try {
    const data = await apiFetch<{ unread: number }>('/api/inbox')
    unread.value = data.unread || 0
  } catch {
    unread.value = 0
  }
}

async function logout() {
  menuOpen.value = false
  try {
    await getHostBridge().stopDaemon()
  } catch {
    /* ignore */
  }
  // 只清会话，保留本机账号登录记录
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
  // 切换账号同样保留历史记录，登录页可用下拉选择其它账号
  clearSession()
  await router.replace({ name: 'login', query: { switch: '1' } })
}

onMounted(() => {
  userEmail.value = getSessionEmail()
  loadUnread()
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
      <div class="account" @click.stop="menuOpen = !menuOpen">
        <span class="avatar">{{ avatarLetter }}</span>
        <span class="account-name" :title="userEmail">{{ displayName }}</span>
        <svg class="chevron" width="12" height="12" viewBox="0 0 24 24" fill="none" aria-hidden="true">
          <path d="M6 9l6 6 6-6" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
        </svg>
        <div v-if="menuOpen" class="account-menu" @click.stop>
          <div class="menu-email muted">{{ userEmail }}</div>
          <button type="button" @click="switchAccount">切换账号</button>
          <button type="button" @click="logout">退出登录</button>
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
          :disabled="item.soon"
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
  top: calc(100% + 4px);
  left: 0;
  right: 0;
  background: #fff;
  border: 1px solid var(--border);
  border-radius: 10px;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.12);
  padding: 6px;
  z-index: 20;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.menu-email {
  font-size: 11px;
  padding: 6px 8px;
  overflow: hidden;
  text-overflow: ellipsis;
}
.account-menu button {
  border: none;
  background: transparent;
  text-align: left;
  padding: 8px 10px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
}
.account-menu button:hover {
  background: var(--bg);
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
</style>
