<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { apiFetch } from '@/lib/api'
import { getHostBridge } from '@/lib/hostBridge'

type Runtime = {
  id: string
  provider: string
  status: string
  hostName?: string
  lastHeartbeatAt?: string
  profile?: string
  daemonId?: string
}

const PROVIDERS = [
  { value: 'cursor', label: 'Cursor', short: 'Cursor' },
  { value: 'claude_code', label: 'Claude Code', short: 'Claude' },
  { value: 'codex', label: 'Codex', short: 'Codex' },
  { value: 'stub', label: 'Stub（本机冒烟）', short: 'Stub' },
]

const runtimes = ref<Runtime[]>([])
const provider = ref('cursor')
const err = ref('')
const okMsg = ref('')
const adding = ref(false)
const removing = ref(false)
const daemonRunning = ref(false)
/** 待删除确认的行 */
const pendingDelete = ref<Runtime | null>(null)
let timer: number | undefined

function providerMeta(code: string) {
  return PROVIDERS.find((p) => p.value === code) || { value: code, label: code, short: code }
}

function displayName(r: Runtime) {
  return providerMeta(r.provider).short
}

function providerLabel(r: Runtime) {
  return providerMeta(r.provider).label
}

function statusLabel(status: string) {
  if (status === 'online') return '在线'
  if (status === 'offline') return '离线'
  return status || '—'
}

/** 相对时间，贴近 Multica「几秒前 / 几分钟前」 */
function formatHeartbeat(iso?: string) {
  if (!iso) return '—'
  const t = Date.parse(iso)
  if (Number.isNaN(t)) return iso
  const sec = Math.max(0, Math.floor((Date.now() - t) / 1000))
  if (sec < 10) return '几秒前'
  if (sec < 60) return `${sec} 秒前`
  const min = Math.floor(sec / 60)
  if (min < 60) return `${min} 分钟前`
  const hr = Math.floor(min / 60)
  if (hr < 24) return `${hr} 小时前`
  return new Date(t).toLocaleString()
}

function deleteTargetLabel(r: Runtime) {
  const host = r.hostName || '本机'
  return `${displayName(r)} (${host})`
}

const showDaemonWarn = computed(() => daemonRunning.value && !!pendingDelete.value)

async function load() {
  runtimes.value = await apiFetch('/api/runtimes')
}

async function refreshDaemonFlag() {
  try {
    const s = await getHostBridge().getDaemonStatus()
    daemonRunning.value = !!s.running
  } catch {
    daemonRunning.value = false
  }
}

async function addRuntime() {
  err.value = ''
  okMsg.value = ''
  adding.value = true
  try {
    const host = getHostBridge()
    const detected = await host.detectRuntime(provider.value)
    if (!detected.ok) {
      err.value = detected.message || '本机未安装，无法注册'
      return
    }
    const enabled = await host.enableRuntime(provider.value)
    if (!enabled.ok) {
      err.value = enabled.message || '写入本机启用列表失败'
      return
    }
    const account = await host.getDaemonAccount()
    await apiFetch('/api/runtimes', {
      method: 'POST',
      body: JSON.stringify({
        provider: provider.value,
        daemonId: account.daemonId || '',
      }),
    })
    okMsg.value = '已添加。Desktop Daemon 将在约 10 秒内接管心跳。'
    await load()
  } catch (e) {
    err.value = e instanceof Error ? e.message : '添加失败'
  } finally {
    adding.value = false
  }
}

function askDelete(r: Runtime) {
  err.value = ''
  okMsg.value = ''
  pendingDelete.value = r
  refreshDaemonFlag()
}

function cancelDelete() {
  pendingDelete.value = null
}

async function confirmDelete() {
  const r = pendingDelete.value
  if (!r) return
  removing.value = true
  err.value = ''
  okMsg.value = ''
  try {
    const host = getHostBridge()
    const account = await host.getDaemonAccount()
    const res = await host.removeRuntime(r.provider)
    const q = new URLSearchParams()
    if (r.daemonId) q.set('daemonId', r.daemonId)
    else if (account.daemonId) q.set('daemonId', account.daemonId)
    const qs = q.toString()
    try {
      await apiFetch(
        `/api/runtimes/provider/${encodeURIComponent(r.provider)}${qs ? `?${qs}` : ''}`,
        { method: 'DELETE' },
      )
    } catch {
      await apiFetch(`/api/runtimes/${r.id}`, { method: 'DELETE' })
    }
    if (!res.ok) {
      err.value = res.message || '服务端已删，但本机启用列表可能未清除'
    } else {
      okMsg.value = '已删除运行时'
    }
    pendingDelete.value = null
    await load()
  } catch (e) {
    err.value = e instanceof Error ? e.message : '删除失败'
  } finally {
    removing.value = false
  }
}

onMounted(() => {
  load()
  refreshDaemonFlag()
  timer = window.setInterval(() => {
    load()
    refreshDaemonFlag()
  }, 5000)
})
onUnmounted(() => {
  if (timer) window.clearInterval(timer)
})
</script>

<template>
  <section class="page">
    <header class="page-header"><h2>运行时</h2></header>
    <p class="muted">
      须<strong>手动添加</strong>后才会探测并注册；未安装对应 CLI 时添加会失败。
      Desktop 使用独立 Daemon profile，与终端 CLI 可并存。
    </p>

    <div class="panel add-panel">
      <label>
        添加运行时
        <select v-model="provider">
          <option v-for="p in PROVIDERS" :key="p.value" :value="p.value">{{ p.label }}</option>
        </select>
      </label>
      <button type="button" class="primary" :disabled="adding" @click="addRuntime">
        {{ adding ? '探测并注册…' : '添加' }}
      </button>
      <p v-if="err" class="error">{{ err }}</p>
      <p v-if="okMsg" class="ok">{{ okMsg }}</p>
    </div>

    <div class="table-wrap">
      <table class="rt-table">
        <thead>
          <tr>
            <th>名称</th>
            <th>状态</th>
            <th>提供商</th>
            <th>机器</th>
            <th>最近心跳</th>
            <th class="col-actions">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="!runtimes.length">
            <td colspan="6" class="empty muted">暂无已添加的运行时</td>
          </tr>
          <tr v-for="r in runtimes" :key="r.id">
            <td class="name">{{ displayName(r) }}</td>
            <td>
              <span class="badge" :class="r.status">{{ statusLabel(r.status) }}</span>
            </td>
            <td>{{ providerLabel(r) }}</td>
            <td class="mono">{{ r.hostName || '—' }}</td>
            <td class="muted">{{ formatHeartbeat(r.lastHeartbeatAt) }}</td>
            <td class="col-actions">
              <button
                type="button"
                class="icon-btn"
                title="删除运行时"
                aria-label="删除运行时"
                @click="askDelete(r)"
              >
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                  <path
                    d="M4 7h16M10 11v6M14 11v6M6 7l1 12a2 2 0 0 0 2 2h6a2 2 0 0 0 2-2l1-12M9 7V5a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2v2"
                    stroke="currentColor"
                    stroke-width="1.75"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  />
                </svg>
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 删除二次确认 -->
    <div
      v-if="pendingDelete"
      class="modal-backdrop"
      role="presentation"
      @click.self="cancelDelete"
    >
      <div class="modal" role="dialog" aria-modal="true" aria-labelledby="del-rt-title">
        <h3 id="del-rt-title">删除运行时？</h3>
        <p class="modal-body">
          确定要删除「{{ deleteTargetLabel(pendingDelete) }}」吗？此操作无法撤销。
        </p>
        <div v-if="showDaemonWarn" class="callout">
          <span class="callout-icon" aria-hidden="true">i</span>
          <p>
            该运行时由正在运行的本地守护进程托管。若仅删服务端记录、本机启用列表未清除，守护进程会在数秒内重新注册——彻底移除时请确认本机已解除启用（本操作会一并清除），或先停止守护进程。
          </p>
        </div>
        <div class="modal-actions">
          <button type="button" class="btn-ghost" :disabled="removing" @click="cancelDelete">
            取消
          </button>
          <button type="button" class="btn-danger" :disabled="removing" @click="confirmDelete">
            {{ removing ? '删除中…' : '删除运行时' }}
          </button>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.add-panel {
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 16px;
  margin-bottom: 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.table-wrap {
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: 12px;
  overflow: auto;
}
.rt-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
}
.rt-table th,
.rt-table td {
  padding: 12px 16px;
  text-align: left;
  border-bottom: 1px solid var(--border);
  white-space: nowrap;
}
.rt-table th {
  font-weight: 600;
  color: var(--muted);
  font-size: 12px;
  letter-spacing: 0.02em;
}
.rt-table tbody tr:last-child td {
  border-bottom: none;
}
.name {
  font-weight: 600;
}
.mono {
  font-variant-numeric: tabular-nums;
}
.empty {
  text-align: center;
  padding: 28px 16px !important;
}
.badge {
  display: inline-block;
  font-size: 12px;
  padding: 2px 10px;
  border-radius: 999px;
  background: #eee;
}
.badge.online {
  background: #d1fae5;
  color: #065f46;
}
.badge.offline {
  background: #fee2e2;
  color: #991b1b;
}
.col-actions {
  width: 64px;
  text-align: right;
}
.icon-btn {
  border: none;
  background: transparent;
  color: var(--muted);
  padding: 6px;
  border-radius: 6px;
  cursor: pointer;
  line-height: 0;
}
.icon-btn:hover {
  color: var(--danger);
  background: #fef3f2;
}
.ok { color: #065f46; font-size: 14px; }
select {
  width: 100%;
  margin-top: 6px;
  padding: 8px;
  border-radius: 8px;
  border: 1px solid var(--border);
}

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
  padding: 22px 22px 18px;
  box-shadow: 0 16px 48px rgba(15, 23, 42, 0.18);
}
.modal h3 {
  margin: 0 0 10px;
  font-size: 18px;
}
.modal-body {
  margin: 0 0 14px;
  color: var(--text);
  line-height: 1.5;
  font-size: 14px;
}
.callout {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  background: #fffbeb;
  border: 1px solid #f6d98a;
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 18px;
}
.callout p {
  margin: 0;
  font-size: 13px;
  line-height: 1.45;
  color: #78350f;
}
.callout-icon {
  flex-shrink: 0;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: #f59e0b;
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-top: 1px;
}
.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
.btn-ghost {
  border: 1px solid var(--border);
  background: #fff;
  border-radius: 8px;
  padding: 8px 14px;
  cursor: pointer;
}
.btn-danger {
  border: none;
  background: #fce8e6;
  color: #b42318;
  border-radius: 8px;
  padding: 8px 14px;
  cursor: pointer;
  font-weight: 600;
}
.btn-danger:hover:not(:disabled) {
  background: #f9d5d2;
}
.btn-ghost:disabled,
.btn-danger:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>
