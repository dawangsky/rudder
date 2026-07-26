<script setup lang="ts">
/**
 * L3：单个 Runtime（Provider）详情 — 面包屑 / 元信息 / 绑定智能体 / 删除。
 */
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { apiFetch } from '@/lib/api'
import { getHostBridge } from '@/lib/hostBridge'
import {
  clearCustomProviderIcon,
  fileToIconDataUrl,
  getCustomProviderIcon,
  setCustomProviderIcon,
} from '@/lib/providerIcons'
import { getSessionEmail } from '@/lib/session'
import ProviderIcon from '@/components/ProviderIcon.vue'
import {
  formatHeartbeat,
  providerLabel,
  runtimeTitle,
  type Runtime,
} from '@/lib/runtimes'

type Agent = {
  id: string
  name: string
  provider: string
  runtimeId?: string | null
}

const route = useRoute()
const router = useRouter()

const runtime = ref<Runtime | null>(null)
const agents = ref<Agent[]>([])
const localDaemonId = ref('')
const removing = ref(false)
const showDelete = ref(false)
const daemonRunning = ref(false)
const err = ref('')
const iconErr = ref('')
const customIcon = ref('')
const iconBusy = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)
let timer: number | undefined

const runtimeId = computed(() => String(route.params.runtimeId || ''))

function reloadCustomIcon() {
  if (!runtime.value) {
    customIcon.value = ''
    return
  }
  customIcon.value = getCustomProviderIcon(runtime.value.daemonId, runtime.value.provider)
}

watch(runtime, reloadCustomIcon, { immediate: true })

const boundAgents = computed(() =>
  agents.value.filter(
    (a) =>
      (a.runtimeId && a.runtimeId === runtimeId.value) ||
      (!a.runtimeId && runtime.value && a.provider === runtime.value.provider),
  ),
)

const isLocal = computed(
  () => !!(runtime.value?.daemonId && localDaemonId.value && runtime.value.daemonId === localDaemonId.value),
)

const ownerName = computed(() => {
  const e = getSessionEmail()
  if (!e) return '—'
  const at = e.indexOf('@')
  return at > 0 ? e.slice(0, at) : e
})

async function load() {
  const list = await apiFetch<Runtime[]>('/api/runtimes')
  runtime.value = list.find((r) => r.id === runtimeId.value) || null
  try {
    agents.value = await apiFetch('/api/agents')
  } catch {
    agents.value = []
  }
}

async function refreshLocal() {
  try {
    const s = await getHostBridge().getDaemonStatus()
    daemonRunning.value = !!s.running
    const account = await getHostBridge().getDaemonAccount()
    localDaemonId.value = account.daemonId || ''
  } catch {
    /* ignore */
  }
}

function openIconPicker() {
  iconErr.value = ''
  fileInput.value?.click()
}

async function onIconFile(ev: Event) {
  const input = ev.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file || !runtime.value) return
  iconBusy.value = true
  iconErr.value = ''
  try {
    const dataUrl = await fileToIconDataUrl(file)
    setCustomProviderIcon(runtime.value.daemonId, runtime.value.provider, dataUrl)
    customIcon.value = dataUrl
  } catch (e) {
    iconErr.value = e instanceof Error ? e.message : '图标更新失败'
  } finally {
    iconBusy.value = false
  }
}

function resetIcon() {
  if (!runtime.value) return
  clearCustomProviderIcon(runtime.value.daemonId, runtime.value.provider)
  customIcon.value = ''
  iconErr.value = ''
}

async function confirmDelete() {
  if (!runtime.value) return
  removing.value = true
  err.value = ''
  try {
    const r = runtime.value
    const host = getHostBridge()
    // 本机先走 CLI remove；内置 Provider 若仍安装，Daemon 约 10s 内会自动探测并重新注册
    if (isLocal.value) {
      const removed = await host.removeRuntime(r.provider)
      if (!removed.ok) {
        err.value = removed.message || '本机移除失败，删除已中止'
        return
      }
    } else {
      const q = new URLSearchParams()
      if (r.daemonId) q.set('daemonId', r.daemonId)
      try {
        await apiFetch(
          `/api/runtimes/provider/${encodeURIComponent(r.provider)}${q.toString() ? `?${q}` : ''}`,
          { method: 'DELETE' },
        )
      } catch {
        await apiFetch(`/api/runtimes/${r.id}`, { method: 'DELETE' })
      }
    }
    const daemonId = r.daemonId
    await router.replace(
      daemonId
        ? { name: 'runtime-machine', params: { daemonId } }
        : { name: 'runtimes' },
    )
  } catch (e) {
    err.value = e instanceof Error ? e.message : '删除失败'
  } finally {
    removing.value = false
    showDelete.value = false
  }
}

onMounted(() => {
  load()
  refreshLocal()
  timer = window.setInterval(load, 5000)
})
onUnmounted(() => {
  if (timer) window.clearInterval(timer)
})
</script>

<template>
  <section class="page" v-if="runtime">
    <nav class="crumb">
      <router-link :to="{ name: 'runtimes' }">运行时</router-link>
      <span class="sep">/</span>
      <router-link
        v-if="runtime.daemonId"
        :to="{ name: 'runtime-machine', params: { daemonId: runtime.daemonId } }"
      >{{ runtime.hostName || '电脑' }}</router-link>
      <span v-else>电脑</span>
      <span class="sep">/</span>
      <span>{{ runtimeTitle(runtime) }}</span>
    </nav>

    <header class="hero">
      <div class="hero-icon">
        <button
          type="button"
          class="icon-btn"
          :disabled="iconBusy"
          title="更改图标"
          @click="openIconPicker"
        >
          <ProviderIcon
            :provider="runtime.provider"
            :custom-src="customIcon"
            :title="providerLabel(runtime)"
            :size="48"
          />
          <span class="icon-edit">更改</span>
        </button>
        <button
          v-if="customIcon"
          type="button"
          class="icon-reset"
          :disabled="iconBusy"
          @click="resetIcon"
        >
          恢复默认
        </button>
        <input
          ref="fileInput"
          type="file"
          accept="image/png,image/jpeg,image/webp,image/svg+xml"
          class="file-hidden"
          @change="onIconFile"
        />
        <p v-if="iconErr" class="error icon-err">{{ iconErr }}</p>
      </div>
      <div>
        <h1>{{ runtimeTitle(runtime) }}</h1>
        <p class="status">
          <span class="dot" :class="{ on: runtime.status === 'online' }" />
          {{ runtime.status === 'online' ? '在线' : '离线' }}
          · 最后活跃 {{ formatHeartbeat(runtime.lastHeartbeatAt) }}
        </p>
      </div>
    </header>

    <div class="layout">
      <div class="main">
        <div class="cards">
          <div class="card">
            <div class="card-label">所有者</div>
            <div class="card-value">{{ ownerName }}</div>
          </div>
          <div class="card">
            <div class="card-label">设备</div>
            <div class="card-value">{{ runtime.hostName || '—' }}</div>
          </div>
          <div class="card">
            <div class="card-label">运行时</div>
            <div class="card-value">{{ providerLabel(runtime) }}</div>
          </div>
        </div>

        <details class="tech" open>
          <summary>技术详情</summary>
          <dl>
            <div><dt>守护进程 ID</dt><dd class="mono">{{ runtime.daemonId || '—' }}</dd></div>
            <div><dt>Profile</dt><dd class="mono">{{ runtime.profile || '—' }}</dd></div>
            <div><dt>Runtime ID</dt><dd class="mono">{{ runtime.id }}</dd></div>
          </dl>
        </details>

        <div class="usage">
          <div class="usage-empty">还没有使用数据</div>
        </div>
      </div>

      <aside class="side">
        <div class="side-card">
          <div class="side-title">服务中</div>
          <div class="side-count">{{ boundAgents.length }} 个智能体</div>
          <ul v-if="boundAgents.length" class="agent-list">
            <li v-for="a in boundAgents" :key="a.id">
              <router-link :to="{ name: 'agents' }">{{ a.name }}</router-link>
            </li>
          </ul>
          <p v-else class="side-empty">还没有智能体绑定到这个运行时。</p>
        </div>

        <div class="side-card">
          <div class="side-title">诊断</div>
          <div class="vis-row">
            <span class="vis active">私有</span>
            <span class="vis disabled" title="二期">公开</span>
          </div>
          <button type="button" class="btn-del" @click="showDelete = true">删除运行时</button>
        </div>
      </aside>
    </div>

    <div v-if="showDelete" class="modal-backdrop" @click.self="showDelete = false">
      <div class="modal">
        <h3>删除运行时？</h3>
        <p>确定要删除「{{ runtimeTitle(runtime) }}」吗？</p>
        <div v-if="isLocal" class="callout">
          内置运行时（Cursor / Claude Code / Codex）若本机仍安装对应 CLI，Daemon 轮询探测后会自动再次出现。彻底消失请卸载 CLI 或移出 PATH。
        </div>
        <p v-if="err" class="error">{{ err }}</p>
        <div class="modal-actions">
          <button type="button" class="mini" :disabled="removing" @click="showDelete = false">取消</button>
          <button type="button" class="btn-del" :disabled="removing" @click="confirmDelete">
            {{ removing ? '删除中…' : '删除运行时' }}
          </button>
        </div>
      </div>
    </div>
  </section>

  <section v-else class="page">
    <nav class="crumb">
      <router-link :to="{ name: 'runtimes' }">运行时</router-link>
      <span class="sep">/</span>
      <span>未找到</span>
    </nav>
    <p class="muted">运行时不存在或已删除。</p>
  </section>
</template>

<style scoped>
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
  gap: 14px;
  align-items: flex-start;
  margin-bottom: 24px;
}
.hero-icon {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}
.icon-btn {
  position: relative;
  border: 1px solid var(--border);
  background: #fff;
  border-radius: 14px;
  padding: 6px;
  cursor: pointer;
  display: inline-flex;
}
.icon-btn:hover:not(:disabled) { background: #f9fafb; }
.icon-btn:disabled { opacity: 0.6; cursor: not-allowed; }
.icon-edit {
  position: absolute;
  right: 4px;
  bottom: 4px;
  font-size: 10px;
  font-weight: 600;
  background: rgba(17, 24, 39, 0.85);
  color: #fff;
  border-radius: 4px;
  padding: 1px 5px;
  line-height: 1.4;
}
.icon-reset {
  border: none;
  background: transparent;
  color: var(--muted);
  font-size: 12px;
  cursor: pointer;
  padding: 0;
  text-decoration: underline;
}
.icon-reset:hover { color: var(--text); }
.file-hidden { display: none; }
.icon-err { margin: 0; font-size: 12px; max-width: 140px; text-align: center; }
h1 { margin: 0; font-size: 24px; }
.status {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--muted);
  display: flex;
  align-items: center;
  gap: 6px;
}
.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #d1d5db;
}
.dot.on { background: #22c55e; }

.layout {
  display: grid;
  grid-template-columns: 1fr 280px;
  gap: 20px;
  align-items: start;
}
.cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-bottom: 16px;
}
.card {
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 14px 16px;
}
.card-label { font-size: 12px; color: var(--muted); margin-bottom: 6px; }
.card-value { font-size: 14px; font-weight: 600; }

.tech {
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 12px 16px;
  margin-bottom: 16px;
}
.tech summary {
  cursor: pointer;
  font-weight: 600;
  font-size: 14px;
}
.tech dl {
  margin: 12px 0 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.tech dl > div {
  display: grid;
  grid-template-columns: 120px 1fr;
  gap: 8px;
  font-size: 13px;
}
.tech dt { color: var(--muted); margin: 0; }
.tech dd { margin: 0; }
.mono {
  font-family: ui-monospace, Menlo, monospace;
  font-size: 12px;
  word-break: break-all;
}

.usage {
  min-height: 160px;
  border: 1px dashed var(--border);
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--panel);
}
.usage-empty { color: var(--muted); font-size: 13px; }

.side-card {
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 16px;
  margin-bottom: 12px;
}
.side-title { font-size: 13px; font-weight: 600; margin-bottom: 8px; }
.side-count { font-size: 14px; margin-bottom: 8px; }
.side-empty { font-size: 13px; color: var(--muted); margin: 0; line-height: 1.45; }
.agent-list { list-style: none; margin: 0; padding: 0; }
.agent-list li { padding: 6px 0; font-size: 13px; }
.agent-list a { color: var(--accent); text-decoration: none; }

.vis-row { display: flex; gap: 8px; margin-bottom: 14px; }
.vis {
  flex: 1;
  text-align: center;
  padding: 8px;
  border-radius: 8px;
  border: 1px solid var(--border);
  font-size: 12px;
}
.vis.active { background: #f3f4f6; font-weight: 600; }
.vis.disabled { opacity: 0.4; }

.btn-del {
  width: 100%;
  border: none;
  background: #fce8e6;
  color: #b42318;
  border-radius: 8px;
  padding: 10px;
  cursor: pointer;
  font-weight: 600;
  font-size: 13px;
}
.mini {
  border: 1px solid var(--border);
  background: #fff;
  border-radius: 8px;
  padding: 8px 12px;
  cursor: pointer;
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
  width: min(420px, 100%);
  background: #fff;
  border-radius: 12px;
  padding: 20px;
}
.modal h3 { margin: 0 0 8px; }
.modal p { font-size: 14px; line-height: 1.5; }
.callout {
  background: #fffbeb;
  border: 1px solid #f6d98a;
  border-radius: 8px;
  padding: 10px 12px;
  font-size: 13px;
  color: #78350f;
  margin: 12px 0;
}
.modal-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 12px; }
.error { color: var(--danger); }

@media (max-width: 900px) {
  .layout { grid-template-columns: 1fr; }
  .cards { grid-template-columns: 1fr; }
}
</style>
