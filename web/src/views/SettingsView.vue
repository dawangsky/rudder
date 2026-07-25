<script setup lang="ts">
/**
 * 设置页（对齐 Multica）：中栏二级菜单 + 右栏内容。
 * MVP 先落地「一般」与「Daemon」；其余菜单占位。
 */
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getServerBaseUrl, setServerBaseUrl } from '@/lib/config'
import { getHostBridge, type DaemonStatus } from '@/lib/hostBridge'
import { getSessionEmail } from '@/lib/session'

type Section = {
  id: string
  label: string
  soon?: boolean
}

const route = useRoute()
const router = useRouter()

const accountSections: Section[] = [
  { id: 'general', label: '一般' },
  { id: 'daemon', label: 'Daemon' },
  { id: 'profile', label: '个人资料', soon: true },
  { id: 'preferences', label: '偏好设置', soon: true },
  { id: 'notifications', label: '通知', soon: true },
]

const workspaceLabel = computed(() => {
  const e = getSessionEmail()
  if (!e) return '工作区'
  const at = e.indexOf('@')
  return at > 0 ? e.slice(0, at) : e
})

const workspaceSections: Section[] = [
  { id: 'workspace-general', label: '通用', soon: true },
  { id: 'members', label: '成员', soon: true },
]

const section = computed(() => {
  const s = String(route.params.section || 'daemon')
  return s
})

const status = ref<DaemonStatus | null>(null)
const busy = ref(false)
const serverUrl = ref('')
const saved = ref(false)
let timer: number | undefined

function goSection(id: string, soon?: boolean) {
  if (soon) return
  router.push(`/settings/${id}`)
}

async function refresh() {
  status.value = await getHostBridge().getDaemonStatus()
}

async function startDaemon() {
  busy.value = true
  try {
    status.value = await getHostBridge().startDaemon()
  } finally {
    busy.value = false
  }
}

async function stopDaemon() {
  busy.value = true
  try {
    status.value = await getHostBridge().stopDaemon()
  } finally {
    busy.value = false
  }
}

async function togglePref(key: 'autoStartOnLaunch' | 'autoStopOnQuit', value: boolean) {
  await getHostBridge().setDaemonPrefs({ [key]: value })
  await refresh()
}

function saveServer() {
  setServerBaseUrl(serverUrl.value.trim())
  saved.value = true
}

watch(
  () => route.params.section,
  () => {
    if (section.value === 'daemon') refresh()
    if (section.value === 'general') {
      serverUrl.value = getServerBaseUrl()
      saved.value = false
    }
  },
)

onMounted(() => {
  if (!route.params.section) {
    router.replace('/settings/daemon')
    return
  }
  serverUrl.value = getServerBaseUrl()
  refresh()
  timer = window.setInterval(() => {
    if (section.value === 'daemon') refresh()
  }, 5000)
})
onUnmounted(() => {
  if (timer) window.clearInterval(timer)
})
</script>

<template>
  <section class="settings-shell">
    <aside class="settings-nav">
      <div class="group">
        <div class="group-title">我的账号</div>
        <button
          v-for="s in accountSections"
          :key="s.id"
          type="button"
          class="nav-item"
          :class="{ active: section === s.id, soon: s.soon }"
          :disabled="s.soon"
          :title="s.soon ? '即将推出' : undefined"
          @click="goSection(s.id, s.soon)"
        >
          {{ s.label }}
        </button>
      </div>
      <div class="group">
        <div class="group-title">{{ workspaceLabel }}</div>
        <button
          v-for="s in workspaceSections"
          :key="s.id"
          type="button"
          class="nav-item"
          :class="{ soon: s.soon }"
          disabled
          title="即将推出"
        >
          {{ s.label }}
        </button>
      </div>
    </aside>

    <main class="settings-main">
      <!-- Daemon -->
      <div v-if="section === 'daemon'" class="panel-page">
        <header class="page-head">
          <h2>Daemon</h2>
          <p class="desc">配置本机 Agent Daemon 与 Desktop 的协作方式，并查看运行诊断信息。</p>
        </header>

        <div class="card">
          <div class="row-item">
            <div>
              <div class="row-title">启动时自动运行</div>
              <div class="row-desc">应用启动且已登录时自动拉起 Desktop Daemon。</div>
            </div>
            <label class="switch">
              <input
                type="checkbox"
                :checked="!!status?.autoStartOnLaunch"
                @change="togglePref('autoStartOnLaunch', ($event.target as HTMLInputElement).checked)"
              />
              <span class="slider" />
            </label>
          </div>
          <div class="row-item">
            <div>
              <div class="row-title">退出时自动停止</div>
              <div class="row-desc">关闭 Desktop 时停止 Daemon。关闭此项可保持后台继续跑任务。</div>
            </div>
            <label class="switch">
              <input
                type="checkbox"
                :checked="!!status?.autoStopOnQuit"
                @change="togglePref('autoStopOnQuit', ($event.target as HTMLInputElement).checked)"
              />
              <span class="slider" />
            </label>
          </div>
        </div>

        <div class="card soft">
          <div class="cli-line">
            <template v-if="status?.cliInstalled">
              rudder CLI 已安装，可供 Desktop 调用。
            </template>
            <template v-else>
              未找到 rudder CLI（{{ status?.cliPath || '—' }}）。请先编译 daemon 二进制。
            </template>
          </div>
        </div>

        <div class="diag">
          <h3>诊断信息</h3>
          <p class="desc">标识与连接详情，便于排查运行时不上线等问题。</p>
          <dl class="kv">
            <div class="kv-row">
              <dt>状态</dt>
              <dd>
                <span class="dot" :class="{ on: status?.running }" />
                {{ status?.running ? 'Running' : 'Stopped' }}
              </dd>
            </div>
            <div class="kv-row">
              <dt>Uptime</dt>
              <dd>{{ status?.uptime || '—' }}</dd>
            </div>
            <div class="kv-row">
              <dt>PID</dt>
              <dd class="mono">{{ status?.pid ?? '—' }}</dd>
            </div>
            <div class="kv-row">
              <dt>Daemon ID</dt>
              <dd class="mono">{{ status?.daemonId || '—' }}</dd>
            </div>
            <div class="kv-row">
              <dt>Profile</dt>
              <dd class="mono">{{ status?.profile || 'desktop' }}</dd>
            </div>
            <div class="kv-row">
              <dt>Server URL</dt>
              <dd class="mono">{{ status?.server || '—' }}</dd>
            </div>
            <div class="kv-row">
              <dt>设备名</dt>
              <dd class="mono">{{ status?.deviceName || '—' }}</dd>
            </div>
            <div class="kv-row">
              <dt>账号</dt>
              <dd class="mono">{{ status?.email || '—' }}</dd>
            </div>
          </dl>
          <div class="actions">
            <button type="button" class="mini" :disabled="busy || status?.running" @click="startDaemon">
              启动
            </button>
            <button type="button" class="mini" :disabled="busy || !status?.running" @click="stopDaemon">
              停止
            </button>
            <button type="button" class="mini" :disabled="busy" @click="refresh">刷新</button>
          </div>
        </div>
      </div>

      <!-- 一般 / Server -->
      <div v-else-if="section === 'general'" class="panel-page">
        <header class="page-head">
          <h2>一般</h2>
          <p class="desc">Self-Host Server 连接地址。</p>
        </header>
        <label class="field">
          Server 地址
          <input v-model="serverUrl" type="url" placeholder="http://127.0.0.1:8080" />
        </label>
        <button type="button" class="primary" @click="saveServer">保存</button>
        <p v-if="saved" class="ok">已保存到本地。</p>
      </div>

      <div v-else class="panel-page">
        <header class="page-head">
          <h2>{{ section }}</h2>
          <p class="desc">该设置项即将推出。</p>
        </header>
      </div>
    </main>
  </section>
</template>

<style scoped>
.settings-shell {
  display: flex;
  margin: -24px;
  height: calc(100% + 48px);
  min-height: 520px;
  background: var(--panel);
  flex: 1;
}

.settings-nav {
  width: 220px;
  flex-shrink: 0;
  border-right: 1px solid var(--border);
  padding: 16px 10px;
  background: #fafafa;
  overflow: auto;
}
.group { margin-bottom: 16px; }
.group-title {
  font-size: 11px;
  color: var(--muted);
  padding: 4px 10px 8px;
  font-weight: 500;
}
.nav-item {
  display: block;
  width: 100%;
  text-align: left;
  border: none;
  background: transparent;
  padding: 8px 10px;
  border-radius: 8px;
  cursor: pointer;
  font-size: 13px;
  color: var(--text);
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
  opacity: 0.4;
  cursor: not-allowed;
}

.settings-main {
  flex: 1;
  min-width: 0;
  overflow: auto;
  background: var(--bg);
}

.panel-page {
  padding: 28px 36px 40px;
  max-width: 760px;
}
.page-head h2 {
  margin: 0 0 6px;
  font-size: 22px;
}
.desc {
  margin: 0;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.5;
}

.card {
  margin-top: 20px;
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: 12px;
  overflow: hidden;
}
.card.soft {
  padding: 14px 16px;
}
.row-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--border);
}
.row-item:last-child { border-bottom: none; }
.row-title { font-size: 14px; font-weight: 600; }
.row-desc { font-size: 12px; color: var(--muted); margin-top: 4px; }
.cli-line { font-size: 13px; color: var(--text); }

.diag {
  margin-top: 28px;
}
.diag h3 {
  margin: 0 0 4px;
  font-size: 15px;
}
.kv {
  margin: 14px 0 0;
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 4px 0;
}
.kv-row {
  display: grid;
  grid-template-columns: 140px 1fr;
  gap: 12px;
  padding: 10px 16px;
  border-bottom: 1px solid var(--border);
  font-size: 13px;
}
.kv-row:last-child { border-bottom: none; }
.kv-row dt { color: var(--muted); margin: 0; }
.kv-row dd { margin: 0; }
.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
  word-break: break-all;
}
.dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #d1d5db;
  margin-right: 8px;
  vertical-align: middle;
}
.dot.on { background: #22c55e; }

.actions {
  display: flex;
  gap: 8px;
  margin-top: 14px;
}
.mini {
  border: 1px solid var(--border);
  background: var(--panel);
  border-radius: 8px;
  padding: 8px 12px;
  cursor: pointer;
  font-size: 13px;
}
.mini:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin: 20px 0 12px;
  max-width: 480px;
}
.field input {
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 10px 12px;
}

/* toggle */
.switch {
  position: relative;
  width: 42px;
  height: 24px;
  flex-shrink: 0;
}
.switch input {
  opacity: 0;
  width: 0;
  height: 0;
}
.slider {
  position: absolute;
  inset: 0;
  background: #d1d5db;
  border-radius: 999px;
  cursor: pointer;
  transition: 0.15s;
}
.slider::before {
  content: '';
  position: absolute;
  width: 18px;
  height: 18px;
  left: 3px;
  top: 3px;
  background: #fff;
  border-radius: 50%;
  transition: 0.15s;
}
.switch input:checked + .slider {
  background: #22c55e;
}
.switch input:checked + .slider::before {
  transform: translateX(18px);
}

@media (max-width: 900px) {
  .settings-nav { width: 180px; }
  .panel-page { padding: 20px; }
  .kv-row { grid-template-columns: 110px 1fr; }
}
</style>
