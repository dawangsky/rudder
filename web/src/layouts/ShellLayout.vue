<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { getHostBridge } from '@/lib/hostBridge'
import { useRoute, useRouter } from 'vue-router'
import { apiFetch } from '@/lib/api'
import { clearRememberedAuth } from '@/lib/rememberAuth'
import { clearSession, getSessionEmail } from '@/lib/session'

const route = useRoute()
const router = useRouter()
const daemonMsg = ref('')
const unread = ref(0)
const userEmail = ref(getSessionEmail())

const navItems = [
  { path: '/chat', label: '对话' },
  { path: '/issues', label: '议题' },
  { path: '/agents', label: '智能体' },
  { path: '/skills', label: '技能' },
  { path: '/runtimes', label: '运行时' },
  { path: '/inbox', label: '收件箱' },
  { path: '/settings', label: '设置' },
]

const activePath = computed(() => route.path)

async function refreshDaemon() {
  const s = await getHostBridge().getDaemonStatus()
  daemonMsg.value = s.running ? `Daemon 运行中` : (s.message || 'Daemon 未运行')
}

async function startDaemon() {
  daemonMsg.value = (await getHostBridge().startDaemon()).message
  await refreshDaemon()
}

async function stopDaemon() {
  daemonMsg.value = (await getHostBridge().stopDaemon()).message
  await refreshDaemon()
}

async function loadUnread() {
  try {
    const data = await apiFetch<{ unread: number }>('/api/inbox')
    unread.value = data.unread || 0
  } catch {
    unread.value = 0
  }
}

/** 退出：清会话，保留「记住账号密码」，回到登录页。 */
async function logout() {
  clearSession()
  await router.replace({ name: 'login' })
}

/** 切换账号：清会话 + 清记住的账号密码，便于登录另一邮箱。 */
async function switchAccount() {
  clearSession()
  clearRememberedAuth()
  await router.replace({ name: 'login', query: { switch: '1' } })
}

onMounted(() => {
  userEmail.value = getSessionEmail()
  refreshDaemon()
  loadUnread()
  setInterval(loadUnread, 15000)
})

function go(path: string) {
  router.push(path)
}
</script>

<template>
  <div class="shell">
    <aside class="sidebar">
      <div class="brand">Rudder</div>
      <nav>
        <button
          v-for="item in navItems"
          :key="item.path"
          type="button"
          class="nav-item"
          :class="{ active: activePath.startsWith(item.path) }"
          @click="go(item.path)"
        >
          {{ item.label }}
          <span v-if="item.path === '/inbox' && unread" class="badge">{{ unread }}</span>
        </button>
      </nav>
      <div class="account-box">
        <div class="muted small email" :title="userEmail">{{ userEmail || '未登录' }}</div>
        <div class="row">
          <button type="button" class="mini" @click="switchAccount">切换账号</button>
          <button type="button" class="mini" @click="logout">退出</button>
        </div>
      </div>
      <div class="daemon-box">
        <div class="muted small">{{ daemonMsg }}</div>
        <div class="row">
          <button type="button" class="mini" @click="startDaemon">启动</button>
          <button type="button" class="mini" @click="stopDaemon">停止</button>
        </div>
      </div>
    </aside>
    <main class="content">
      <router-view />
    </main>
  </div>
</template>

<style scoped>
.badge {
  margin-left: 6px;
  background: var(--accent);
  color: #fff;
  border-radius: 999px;
  padding: 0 6px;
  font-size: 12px;
}
.account-box {
  margin-top: auto;
  padding: 10px;
  border-top: 1px solid var(--border);
}
.account-box .email {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.daemon-box {
  padding: 10px;
  border-top: 1px solid var(--border);
}
.small { font-size: 12px; }
.row { display: flex; gap: 6px; margin-top: 8px; }
.mini {
  flex: 1;
  border: 1px solid var(--border);
  background: var(--bg);
  border-radius: 6px;
  padding: 6px;
  cursor: pointer;
  font-size: 12px;
}
</style>
