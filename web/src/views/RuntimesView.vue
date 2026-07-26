<script setup lang="ts">
/**
 * L1：运行时 — 按电脑聚合列表；点击进入 L2 机器页。
 */
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { apiFetch } from '@/lib/api'
import { getServerBaseUrl } from '@/lib/config'
import { getHostBridge } from '@/lib/hostBridge'
import { getSessionEmail } from '@/lib/session'
import ProviderIcon from '@/components/ProviderIcon.vue'
import { getCustomProviderIcon, ICONS_CHANGED_EVENT } from '@/lib/providerIcons'
import {
  formatHeartbeat,
  groupMachines,
  looksLikeIp,
  providerLabel,
  type LocalMachineHint,
  type Runtime,
} from '@/lib/runtimes'

const router = useRouter()
const runtimes = ref<Runtime[]>([])
const localDaemonId = ref('')
const localHostName = ref('')
const localProfile = ref('')
const localDaemonRunning = ref(false)
const showAddComputer = ref(false)
const err = ref('')
const okMsg = ref('')
const knownDaemonIds = ref<Set<string>>(new Set())
const computerDetected = ref(false)
const helpOpen = ref(false)
const copiedKey = ref('')
/** 自定义图标变更时递增，驱动列表重绘 */
const iconTick = ref(0)
let timer: number | undefined
let waitTimer: number | undefined

function onIconsChanged() {
  iconTick.value += 1
}

function customIconFor(r: Runtime) {
  void iconTick.value
  return getCustomProviderIcon(r.daemonId, r.provider)
}

const serverUrl = computed(() => getServerBaseUrl().replace(/\/$/, ''))
const sessionEmail = computed(() => getSessionEmail() || '<your-email>')

const installCmd = computed(
  () =>
    `# 目标电脑需 Go 1.23+；也可从已有环境拷贝 rudder 二进制到 PATH\n` +
    `git clone <rudder-repo> && cd rudder/daemon && go build -o rudder ./cmd/rudder && sudo mv rudder /usr/local/bin/`,
)

const setupCmd = computed(
  () =>
    `rudder login --server ${serverUrl.value} --email ${sessionEmail.value} --password '<your-password>'\n` +
    `rudder daemon start\n` +
    `# 可选：添加 Provider 后即可在该电脑下使用，例如：\n` +
    `rudder runtime add --provider stub`,
)

const localHint = computed<LocalMachineHint | null>(() => {
  if (!localDaemonId.value) return null
  return {
    daemonId: localDaemonId.value,
    hostName: localHostName.value || undefined,
    profile: localProfile.value || 'desktop',
    online: localDaemonRunning.value,
  }
})

const machines = computed(() =>
  groupMachines(runtimes.value, localDaemonId.value, localHint.value),
)
const machineCount = computed(() => machines.value.length)

function primaryTitle(hostName: string) {
  return hostName
}

function secondaryTitle(m: { profile: string; daemonId: string; hostName: string }) {
  if (looksLikeIp(m.hostName)) {
    return m.profile ? `${m.profile}` : m.daemonId.slice(0, 8)
  }
  return m.profile || 'daemon'
}

async function load() {
  runtimes.value = await apiFetch('/api/runtimes')
}

async function refreshLocal() {
  try {
    const host = getHostBridge()
    const [account, status] = await Promise.all([host.getDaemonAccount(), host.getDaemonStatus()])
    localDaemonId.value = account.daemonId || status.daemonId || ''
    localHostName.value = status.deviceName || ''
    localProfile.value = account.profile || status.profile || 'desktop'
    localDaemonRunning.value = !!status.running
  } catch {
    localDaemonId.value = ''
    localHostName.value = ''
    localProfile.value = ''
    localDaemonRunning.value = false
  }
}

function goMachine(daemonId: string) {
  router.push({ name: 'runtime-machine', params: { daemonId } })
}

function openAddComputer() {
  knownDaemonIds.value = new Set(machines.value.map((m) => m.daemonId))
  computerDetected.value = false
  helpOpen.value = false
  copiedKey.value = ''
  showAddComputer.value = true
  startWaitLoop()
}

function closeAddComputer() {
  showAddComputer.value = false
  stopWaitLoop()
}

function startWaitLoop() {
  stopWaitLoop()
  waitTimer = window.setInterval(async () => {
    try {
      await load()
      const arrived = machines.value.find((m) => !knownDaemonIds.value.has(m.daemonId))
      if (arrived) {
        computerDetected.value = true
        okMsg.value = `已识别电脑「${arrived.hostName}」`
        stopWaitLoop()
        window.setTimeout(() => {
          showAddComputer.value = false
          goMachine(arrived.daemonId)
        }, 800)
      }
    } catch {
      /* ignore */
    }
  }, 3000)
}

function stopWaitLoop() {
  if (waitTimer) {
    window.clearInterval(waitTimer)
    waitTimer = undefined
  }
}

async function copyText(text: string, key: string) {
  try {
    await navigator.clipboard.writeText(text)
    copiedKey.value = key
    window.setTimeout(() => {
      if (copiedKey.value === key) copiedKey.value = ''
    }, 1500)
  } catch {
    err.value = '复制失败，请手动选择命令'
  }
}

onMounted(() => {
  load()
  refreshLocal()
  window.addEventListener(ICONS_CHANGED_EVENT, onIconsChanged)
  timer = window.setInterval(() => {
    load()
    refreshLocal()
  }, 5000)
})
onUnmounted(() => {
  if (timer) window.clearInterval(timer)
  stopWaitLoop()
  window.removeEventListener(ICONS_CHANGED_EVENT, onIconsChanged)
})
</script>

<template>
  <section class="rt-page">
    <header class="rt-head">
      <div>
        <h2>运行时 <span class="count">{{ machineCount }}</span></h2>
        <p class="lead">
          为智能体跑 CLI 会话的机器。点击电脑进入详情，再点运行时查看 Provider 与绑定的智能体。
        </p>
      </div>
      <button type="button" class="btn-add" @click="openAddComputer">+ 添加电脑</button>
    </header>

    <p v-if="err" class="error banner">{{ err }}</p>
    <p v-if="okMsg" class="ok banner">{{ okMsg }}</p>

    <div v-if="!machines.length" class="empty-state">
      <p>
        暂无电脑。请在 Desktop 中登录以显示本机，或点击「添加电脑」在远程机安装 CLI 并启动 Daemon。
      </p>
    </div>

    <ul v-else class="machine-list">
      <li v-for="m in machines" :key="m.key" class="machine-card">
        <button type="button" class="machine-main" @click="goMachine(m.daemonId)">
          <span class="pc-icon" :class="{ online: m.online }" aria-hidden="true">
            <svg width="28" height="28" viewBox="0 0 24 24" fill="none">
              <rect x="3.5" y="5" width="17" height="12" rx="2" stroke="currentColor" stroke-width="1.5" />
              <path d="M8 20h8M12 17v3" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" />
            </svg>
            <i class="pulse" />
          </span>
          <span class="machine-info">
            <span class="title-row">
              <strong class="ip">{{ primaryTitle(m.hostName) }}</strong>
              <span v-if="m.isLocal" class="tag-local">本机</span>
            </span>
            <span class="sub">
              <span v-if="looksLikeIp(m.hostName)" class="host-name">{{ secondaryTitle(m) }}</span>
              <span v-else class="host-name">{{ secondaryTitle(m) }}</span>
            </span>
          </span>
          <span class="meta">
            <span class="online-pill" :class="{ on: m.online }">{{ m.online ? '在线' : '离线' }}</span>
            <span class="rt-count">{{ m.runtimes.length }} 个运行时</span>
            <span class="provider-icons">
              <ProviderIcon
                v-for="r in m.runtimes"
                :key="r.id"
                :provider="r.provider"
                :custom-src="customIconFor(r)"
                :title="providerLabel(r)"
                :size="22"
              />
            </span>
            <span class="idle">{{ m.online ? '全部空闲' : '不可用' }}</span>
            <span class="time">{{ formatHeartbeat(m.latestHeartbeat) }}</span>
          </span>
          <span class="chevron" aria-hidden="true">›</span>
        </button>
      </li>
    </ul>

    <div v-if="showAddComputer" class="modal-backdrop" @click.self="closeAddComputer">
      <div class="modal modal-wide" role="dialog" aria-modal="true">
        <button type="button" class="modal-x" aria-label="关闭" @click="closeAddComputer">×</button>
        <h3>添加电脑</h3>
        <p class="modal-body">在要添加的电脑上运行这两条命令。守护进程一上线，这里就会自动识别。</p>
        <div class="step">
          <div class="step-label">1. 安装 Rudder CLI</div>
          <div class="cmd-block">
            <pre>{{ installCmd }}</pre>
            <button type="button" class="copy-btn" @click="copyText(installCmd, 'install')">
              {{ copiedKey === 'install' ? '已复制' : '复制' }}
            </button>
          </div>
        </div>
        <div class="step">
          <div class="step-label">2. 登录并启动守护进程</div>
          <div class="cmd-block">
            <pre>{{ setupCmd }}</pre>
            <button type="button" class="copy-btn" @click="copyText(setupCmd, 'setup')">
              {{ copiedKey === 'setup' ? '已复制' : '复制' }}
            </button>
          </div>
          <p class="step-hint">使用与 Desktop 相同的邮箱登录。本机 Desktop Daemon 会始终出现在列表中；远程机在添加首个 Provider 后出现。</p>
        </div>
        <div class="wait-line" :class="{ ok: computerDetected }">
          <span class="wait-dot" />
          <span v-if="computerDetected">已识别新电脑…</span>
          <span v-else>等待你的电脑上线 — 守护进程一启动就会被识别，通常不到一分钟。</span>
        </div>
        <details class="faq" :open="helpOpen" @toggle="helpOpen = ($event.target as HTMLDetailsElement).open">
          <summary>那台电脑不方便交互式登录？</summary>
          <p>在目标机使用 <code>rudder login --email … --password …</code> 即可，无需浏览器。</p>
        </details>
        <div class="modal-actions">
          <button type="button" class="btn-ghost" @click="closeAddComputer">取消</button>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.rt-page { max-width: 960px; }
.rt-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
}
.rt-head h2 { margin: 0; font-size: 22px; font-weight: 700; }
.count { color: var(--muted); font-weight: 600; font-size: 20px; }
.lead {
  margin: 8px 0 0;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.5;
  max-width: 560px;
}
.btn-add {
  flex-shrink: 0;
  border: 1px solid var(--border);
  background: var(--panel);
  border-radius: 8px;
  padding: 8px 14px;
  cursor: pointer;
  font-size: 13px;
  font-weight: 600;
}
.banner { margin: 0 0 12px; font-size: 13px; }
.ok { color: #065f46; }
.error { color: var(--danger); }
.empty-state {
  border: 1px dashed var(--border);
  border-radius: 12px;
  padding: 48px 24px;
  text-align: center;
  color: var(--muted);
  background: var(--panel);
}
.machine-list { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 10px; }
.machine-card {
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: 12px;
  overflow: hidden;
}
.machine-main {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 16px;
  border: none;
  background: transparent;
  cursor: pointer;
  text-align: left;
  color: inherit;
  font: inherit;
}
.machine-main:hover { background: #fafafa; }
.pc-icon {
  position: relative;
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: #f3f4f6;
  color: #4b5563;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.pc-icon .pulse {
  position: absolute;
  right: 2px;
  bottom: 2px;
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: #d1d5db;
  border: 2px solid #fff;
}
.pc-icon.online .pulse { background: #22c55e; }
.machine-info { min-width: 160px; display: flex; flex-direction: column; gap: 2px; }
.title-row { display: flex; align-items: center; gap: 8px; }
.ip { font-size: 15px; }
.tag-local {
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 4px;
  background: #eef2ff;
  color: #3730a3;
  font-weight: 600;
}
.sub { font-size: 12px; color: var(--muted); }
.meta {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 14px;
  font-size: 13px;
  color: var(--muted);
  flex-wrap: wrap;
  justify-content: flex-end;
}
.online-pill.on { color: #059669; }
.rt-count { color: var(--text); font-weight: 500; }
.provider-icons { display: inline-flex; gap: 4px; align-items: center; }
.chevron { font-size: 22px; color: #9ca3af; width: 16px; text-align: center; }

.modal-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: 24px;
}
.modal {
  width: min(440px, 100%);
  background: #fff;
  border-radius: 12px;
  padding: 22px;
  box-shadow: 0 16px 48px rgba(15, 23, 42, 0.18);
  position: relative;
}
.modal-wide { width: min(520px, 100%); }
.modal-x {
  position: absolute;
  top: 12px;
  right: 12px;
  border: none;
  background: transparent;
  font-size: 22px;
  color: var(--muted);
  cursor: pointer;
}
.modal h3 { margin: 0 0 10px; font-size: 18px; }
.modal-body { margin: 0 0 14px; font-size: 14px; line-height: 1.5; }
.step { margin-bottom: 16px; }
.step-label { font-size: 13px; font-weight: 600; margin-bottom: 8px; }
.step-hint { margin: 8px 0 0; font-size: 12px; color: var(--muted); line-height: 1.45; }
.cmd-block {
  position: relative;
  background: #f4f5f7;
  border-radius: 8px;
  padding: 12px 44px 12px 12px;
  border: 1px solid var(--border);
}
.cmd-block pre {
  margin: 0;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
  font-family: ui-monospace, Menlo, monospace;
  line-height: 1.45;
}
.copy-btn {
  position: absolute;
  top: 8px;
  right: 8px;
  border: 1px solid var(--border);
  background: #fff;
  border-radius: 6px;
  font-size: 11px;
  padding: 4px 8px;
  cursor: pointer;
}
.wait-line {
  display: flex;
  gap: 8px;
  font-size: 13px;
  margin: 8px 0 14px;
  line-height: 1.45;
}
.wait-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #22c55e;
  margin-top: 5px;
  flex-shrink: 0;
  box-shadow: 0 0 0 3px rgba(34, 197, 94, 0.2);
}
.faq { margin-bottom: 16px; font-size: 13px; color: var(--muted); }
.faq summary { cursor: pointer; color: var(--text); font-weight: 500; }
.faq p { margin: 8px 0 0; line-height: 1.5; }
.faq code { font-size: 12px; background: #f3f4f6; padding: 1px 4px; border-radius: 4px; }
.modal-actions { display: flex; justify-content: flex-end; }
.btn-ghost {
  border: 1px solid var(--border);
  background: #fff;
  border-radius: 8px;
  padding: 8px 14px;
  cursor: pointer;
}
@media (max-width: 800px) {
  .idle, .time { display: none; }
}
</style>
