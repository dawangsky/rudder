<script setup lang="ts">
/**
 * L2：某台电脑上的运行时列表；点击行进入 L3 Provider 详情。
 * 「内置」= Cursor / Claude Code / Codex；Daemon 每 10s 探测本机安装并自动注册（删除后若仍安装会恢复）。
 */
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { apiFetch } from '@/lib/api'
import { getHostBridge } from '@/lib/hostBridge'
import ProviderIcon from '@/components/ProviderIcon.vue'
import { getCustomProviderIcon, ICONS_CHANGED_EVENT } from '@/lib/providerIcons'
import {
  displayName,
  formatHeartbeat,
  groupMachines,
  iconProvider,
  isCustomRuntime,
  providerLabel,
  type LocalMachineHint,
  type Runtime,
} from '@/lib/runtimes'

const route = useRoute()
const router = useRouter()

const runtimes = ref<Runtime[]>([])
const localDaemonId = ref('')
const localHostName = ref('')
const localProfile = ref('')
const busy = ref(false)
const err = ref('')
const okMsg = ref('')
const showAddProvider = ref(false)
const addStep = ref<1 | 2>(1)
const showRename = ref(false)
const showLogs = ref(false)
const renameInput = ref('')
const machineAlias = ref('')
const provider = ref('cursor')
const customName = ref('')
const customCommand = ref('')
const customDesc = ref('')
const adding = ref(false)
const validating = ref(false)
const daemonRunning = ref(false)
const iconTick = ref(0)
let timer: number | undefined

/** 第 1 步：选择基础协议（内置仍自动探测；此处用于自定义启动命令） */
const PROTOCOL_OPTIONS = [
  { value: 'claude_code', label: 'Claude', enabled: true },
  { value: 'codebuddy', label: 'Codebuddy', enabled: false },
  { value: 'codex', label: 'Codex', enabled: true },
  { value: 'copilot', label: 'Copilot', enabled: false },
  { value: 'opencode', label: 'Opencode', enabled: false },
  { value: 'deveco', label: 'Deveco', enabled: false },
  { value: 'openclaw', label: 'Openclaw', enabled: false },
  { value: 'hermes', label: 'Hermes', enabled: false },
  { value: 'pi', label: 'Pi', enabled: false },
  { value: 'cursor', label: 'Cursor', enabled: true },
  { value: 'kimi', label: 'Kimi', enabled: false },
  { value: 'kiro', label: 'Kiro', enabled: false },
  { value: 'antigravity', label: 'Antigravity', enabled: false },
  { value: 'qoder', label: 'Qoder', enabled: false },
  { value: 'traecli', label: 'Traecli', enabled: false },
  { value: 'grok', label: 'Grok', enabled: false },
  { value: 'qwen', label: 'Qwen', enabled: false },
] as const

const selectedProtocol = computed(
  () => PROTOCOL_OPTIONS.find((p) => p.value === provider.value) || PROTOCOL_OPTIONS[0],
)

function onIconsChanged() {
  iconTick.value += 1
}

function customIconFor(r: Runtime) {
  void iconTick.value
  return getCustomProviderIcon(r.daemonId, r.provider)
}

const daemonId = computed(() => String(route.params.daemonId || ''))

const localHint = computed<LocalMachineHint | null>(() => {
  if (!localDaemonId.value) return null
  return {
    daemonId: localDaemonId.value,
    hostName: localHostName.value || undefined,
    profile: localProfile.value || 'desktop',
    online: daemonRunning.value,
  }
})

const machine = computed(() =>
  groupMachines(runtimes.value, localDaemonId.value, localHint.value).find(
    (m) => m.daemonId === daemonId.value,
  ) || null,
)

const isLocal = computed(
  () => machine.value?.isLocal ?? (!!localDaemonId.value && daemonId.value === localDaemonId.value),
)

const titleName = computed(() => machineAlias.value || machine.value?.hostName || daemonId.value)

function aliasKey(id: string) {
  return `rudder.machineAlias.${id}`
}

function loadAlias() {
  const id = daemonId.value
  if (!id) {
    machineAlias.value = ''
    return
  }
  machineAlias.value = localStorage.getItem(aliasKey(id)) || ''
}

async function load() {
  runtimes.value = await apiFetch('/api/runtimes')
}

async function refreshLocal() {
  try {
    const host = getHostBridge()
    const [s, account] = await Promise.all([host.getDaemonStatus(), host.getDaemonAccount()])
    daemonRunning.value = !!s.running
    localDaemonId.value = account.daemonId || s.daemonId || ''
    localHostName.value = s.deviceName || ''
    localProfile.value = account.profile || s.profile || 'desktop'
  } catch {
    localDaemonId.value = ''
    localHostName.value = ''
    localProfile.value = ''
    daemonRunning.value = false
  }
}

function goRuntime(id: string) {
  router.push({ name: 'runtime-detail', params: { runtimeId: id } })
}

function openRename() {
  renameInput.value = titleName.value
  showRename.value = true
}

function saveRename() {
  const id = daemonId.value
  const name = renameInput.value.trim()
  if (!id || !name) return
  localStorage.setItem(aliasKey(id), name)
  machineAlias.value = name
  showRename.value = false
  okMsg.value = '机器名称已更新'
}

async function startLocal() {
  if (!isLocal.value) return
  busy.value = true
  err.value = ''
  try {
    await getHostBridge().startDaemon()
    okMsg.value = '已请求启动本机 Daemon'
    await refreshLocal()
  } catch (e) {
    err.value = e instanceof Error ? e.message : '启动失败'
  } finally {
    busy.value = false
  }
}

async function restartLocal() {
  if (!isLocal.value) return
  busy.value = true
  err.value = ''
  try {
    await getHostBridge().restartDaemon()
    okMsg.value = '已请求重启本机 Daemon'
    await refreshLocal()
  } catch (e) {
    err.value = e instanceof Error ? e.message : '重启失败'
  } finally {
    busy.value = false
  }
}

async function stopLocal() {
  if (!isLocal.value) return
  busy.value = true
  err.value = ''
  try {
    await getHostBridge().stopDaemon()
    okMsg.value = '已停止本机 Daemon'
    await refreshLocal()
  } catch (e) {
    err.value = e instanceof Error ? e.message : '停止失败'
  } finally {
    busy.value = false
  }
}

function openAddCustom() {
  err.value = ''
  addStep.value = 1
  provider.value = 'cursor'
  customName.value = ''
  customCommand.value = ''
  customDesc.value = ''
  showAddProvider.value = true
}

function closeAddCustom() {
  showAddProvider.value = false
  addStep.value = 1
  err.value = ''
}

function selectProtocol(value: string, enabled: boolean) {
  err.value = ''
  if (!enabled) {
    err.value = '该协议类型即将支持，请选择 Claude / Codex / Cursor'
    return
  }
  provider.value = value
  customName.value = ''
  customCommand.value = ''
  customDesc.value = ''
  addStep.value = 2
}

const commandPlaceholder = computed(() => {
  if (provider.value === 'cursor') return '例如：agent --model composer-2.5…'
  if (provider.value === 'claude_code') return '例如：claude --model sonnet…'
  if (provider.value === 'codex') return '例如：codex exec…'
  return '例如：my-team-cli --flag…'
})

function backToStep1() {
  err.value = ''
  addStep.value = 1
}

function openConfigGuide() {
  window.open('https://github.com/dawangsky/rudder', '_blank', 'noopener,noreferrer')
}

async function addProvider() {
  err.value = ''
  const name = customName.value.trim()
  const command = customCommand.value.trim()
  if (!name) {
    err.value = '请填写显示名称'
    return
  }
  if (!command) {
    err.value = '请填写命令'
    return
  }
  adding.value = true
  validating.value = true
  try {
    const host = getHostBridge()
    if (!isLocal.value) {
      err.value = 'MVP 仅支持在本机 Desktop Daemon 上添加自定义运行时'
      return
    }
    const valid = await host.validateCommand(command)
    if (!valid.ok) {
      err.value = valid.message || '命令无效：本机找不到可执行文件'
      return
    }
    const added = await host.addCustomRuntime({
      base: provider.value,
      name,
      command,
      description: customDesc.value.trim(),
    })
    if (!added.ok) {
      err.value = added.message || '创建失败'
      return
    }
    closeAddCustom()
    okMsg.value = `已添加自定义运行时「${name}」`
    await load()
  } catch (e) {
    err.value = e instanceof Error ? e.message : '创建失败'
  } finally {
    adding.value = false
    validating.value = false
  }
}

watch(daemonId, loadAlias, { immediate: true })

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
  window.removeEventListener(ICONS_CHANGED_EVENT, onIconsChanged)
})
</script>

<template>
  <section class="page" v-if="machine">
    <nav class="crumb">
      <router-link :to="{ name: 'runtimes' }">运行时</router-link>
      <span class="sep">/</span>
      <span>{{ titleName }}</span>
    </nav>

    <header class="machine-head">
      <div class="head-left">
        <div class="machine-icon" aria-hidden="true">
          <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6">
            <rect x="3" y="4" width="18" height="12" rx="2" />
            <path d="M8 20h8M12 16v4" />
          </svg>
        </div>
        <div>
          <div class="title-row">
            <h1>{{ titleName }}</h1>
            <span class="online" :class="{ on: machine.online }">
              {{ machine.online ? '在线' : '离线' }}
            </span>
            <span v-if="machine.isLocal" class="tag">本机</span>
          </div>
          <p class="daemon-id">daemon {{ machine.daemonId }}</p>
          <p class="sub">
            {{ machine.runtimes.length }} 个运行时
            · {{ machine.online ? '全部空闲' : '不可用' }}
            <template v-if="machine.profile"> · profile {{ machine.profile }}</template>
            <template v-if="machine.isLocal"> · 由桌面端管理</template>
            · {{ formatHeartbeat(machine.runtimes[0]?.lastHeartbeatAt) }}
          </p>
        </div>
      </div>
      <div class="actions">
        <button type="button" class="act" @click="openRename">
          <svg class="ico-svg" width="14" height="14" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <path
              d="M4 20h4l10.5-10.5a1.5 1.5 0 0 0 0-2.12L16.62 5.5a1.5 1.5 0 0 0-2.12 0L4 16v4z"
              stroke="currentColor"
              stroke-width="1.8"
              stroke-linejoin="round"
            />
            <path d="M13.5 6.5 17.5 10.5" stroke="currentColor" stroke-width="1.8" />
          </svg>
          重命名机器
        </button>
        <button type="button" class="act" @click="showLogs = true">
          <span class="ico" aria-hidden="true">☰</span>
          View logs
        </button>
        <template v-if="machine.isLocal">
          <button type="button" class="act" :disabled="busy" @click="restartLocal">
            <span class="ico" aria-hidden="true">↻</span>
            Restart
          </button>
          <button
            v-if="daemonRunning"
            type="button"
            class="act danger"
            :disabled="busy"
            @click="stopLocal"
          >
            <span class="ico" aria-hidden="true">■</span>
            Stop
          </button>
          <button
            v-else
            type="button"
            class="act"
            :disabled="busy"
            @click="startLocal"
          >
            <span class="ico" aria-hidden="true">▶</span>
            Start
          </button>
        </template>
      </div>
    </header>

    <p v-if="err" class="error">{{ err }}</p>
    <p v-if="okMsg" class="ok">{{ okMsg }}</p>

    <div class="section-head">
      <div>
        <h2>运行时</h2>
        <p class="muted">
          内置运行时（Claude / Codex / Cursor）会按本机安装自动出现。
          若需团队包装命令、预设模型参数等，请添加自定义运行时。
        </p>
      </div>
      <button type="button" class="btn-dark" @click="openAddCustom">+ 添加自定义运行时</button>
    </div>

    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>运行时</th>
            <th>健康度</th>
            <th>智能体</th>
            <th>最近心跳</th>
            <th>CLI</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="!machine.runtimes.length">
            <td colspan="5" class="empty">暂无运行时，可点击「添加自定义运行时」</td>
          </tr>
          <tr
            v-for="r in machine.runtimes"
            :key="r.id"
            class="clickable"
            @click="goRuntime(r.id)"
          >
            <td>
              <span class="rt-cell">
                <ProviderIcon
                  :provider="iconProvider(r)"
                  :custom-src="customIconFor(r)"
                  :title="providerLabel(r)"
                  :size="24"
                />
                <span class="rt-name">{{ displayName(r) }}</span>
                <span class="built-in" :class="{ custom: isCustomRuntime(r) }">
                  {{ isCustomRuntime(r) ? '自定义' : '内置' }}
                </span>
              </span>
            </td>
            <td>
              <span class="health" :class="r.status">{{ r.status === 'online' ? '在线' : '离线' }}</span>
            </td>
            <td class="muted">—</td>
            <td class="muted">{{ formatHeartbeat(r.lastHeartbeatAt) }}</td>
            <td class="muted">{{ providerLabel(r) }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="showAddProvider" class="modal-backdrop" @click.self="closeAddCustom">
      <div class="modal modal-custom" role="dialog" aria-modal="true" aria-labelledby="add-rt-title">
        <header class="custom-head">
          <div class="custom-title-row">
            <span class="custom-title-ico" aria-hidden="true">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
                <path d="M4 8h16M4 16h16" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
                <rect x="7" y="5" width="10" height="4" rx="1" stroke="currentColor" stroke-width="1.5" />
                <rect x="7" y="15" width="10" height="4" rx="1" stroke="currentColor" stroke-width="1.5" />
              </svg>
            </span>
            <h3 id="add-rt-title">新建自定义运行时</h3>
          </div>
          <button type="button" class="modal-x" aria-label="关闭" @click="closeAddCustom">×</button>
        </header>
        <p class="custom-desc">
          从 {{ titleName }} 配置一个自定义命令。定义会同步到整个工作区，但只会在能找到该命令的机器上注册。
        </p>
        <button type="button" class="guide-link" @click="openConfigGuide">
          查看配置指南
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <path d="M14 5h5v5M19 5 10 14" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
            <path d="M19 13v5a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1h5" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
          </svg>
        </button>

        <template v-if="addStep === 1">
          <p class="step-label">第 1 / 2 步</p>
          <h4 class="step-title">选择基础协议类型</h4>
          <p class="step-hint">此运行时使用的底层 CLI 协议。</p>
          <div class="protocol-grid">
            <button
              v-for="p in PROTOCOL_OPTIONS"
              :key="p.value"
              type="button"
              class="protocol-card"
              :class="{ disabled: !p.enabled }"
              :title="p.enabled ? p.label : `${p.label}（即将支持）`"
              @click="selectProtocol(p.value, p.enabled)"
            >
              <ProviderIcon :provider="p.value" :title="p.label" :size="28" />
              <span>{{ p.label }}</span>
            </button>
          </div>
          <p v-if="err" class="error">{{ err }}</p>
          <div class="modal-actions">
            <button type="button" class="mini" @click="closeAddCustom">取消</button>
          </div>
        </template>

        <template v-else>
          <p class="step-label">第 2 / 2 步</p>
          <h4 class="step-title">配置运行时</h4>

          <div class="field">
            <span class="label">基础协议类型</span>
            <div class="base-proto">
              <ProviderIcon :provider="provider" :size="22" />
              <strong>{{ selectedProtocol.label }}</strong>
            </div>
            <p class="field-hint">创建后无法更改基础协议类型。</p>
          </div>

          <label class="field">
            显示名称
            <input
              v-model="customName"
              type="text"
              placeholder="例如：我的自定义 Claude…"
              required
            />
          </label>
          <label class="field">
            命令
            <input
              v-model="customCommand"
              type="text"
              :placeholder="commandPlaceholder"
              required
            />
          </label>
          <label class="field">
            描述
            <textarea
              v-model="customDesc"
              rows="3"
              placeholder="描述此运行时的用途（可选）…"
            />
          </label>

          <p v-if="err" class="error">{{ err }}</p>
          <div class="modal-actions step2-actions">
            <button type="button" class="mini back-btn" :disabled="adding" @click="backToStep1">
              ‹ 返回
            </button>
            <div class="right-actions">
              <button type="button" class="mini" :disabled="adding" @click="closeAddCustom">取消</button>
              <button type="button" class="btn-dark" :disabled="adding" @click="addProvider">
                {{ validating ? '校验命令…' : adding ? '创建中…' : '创建运行时' }}
              </button>
            </div>
          </div>
        </template>
      </div>
    </div>

    <div v-if="showRename" class="modal-backdrop" @click.self="showRename = false">
      <div class="modal">
        <h3>重命名机器</h3>
        <p class="modal-body">仅本机显示名（存于浏览器），不影响 Daemon ID / hostname。</p>
        <label class="field">
          名称
          <input v-model="renameInput" type="text" @keydown.enter="saveRename" />
        </label>
        <div class="modal-actions">
          <button type="button" class="mini" @click="showRename = false">取消</button>
          <button type="button" class="btn-dark" @click="saveRename">保存</button>
        </div>
      </div>
    </div>

    <div v-if="showLogs" class="modal-backdrop" @click.self="showLogs = false">
      <div class="modal modal-wide">
        <h3>View logs</h3>
        <p class="modal-body">
          MVP 尚未内嵌日志流。本机 Desktop Daemon（profile <code>desktop</code>）可在终端查看：
        </p>
        <pre class="code">rudder --profile desktop daemon status</pre>
        <p class="modal-body tip">进程标准输出由桌面端拉起时写入系统日志；更完整的日志查看能力后续接入。</p>
        <div class="modal-actions">
          <button type="button" class="btn-dark" @click="showLogs = false">Close</button>
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
    <p class="muted">该电脑不存在。本机请从运行时列表进入；远程机需先添加至少一个 Provider。</p>
    <router-link :to="{ name: 'runtimes' }">返回列表</router-link>
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
.machine-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 24px;
  align-items: flex-start;
}
.head-left {
  display: flex;
  gap: 14px;
  align-items: flex-start;
  min-width: 0;
}
.machine-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  background: #f3f4f6;
  color: #374151;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.title-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
h1 { margin: 0; font-size: 26px; }
.daemon-id {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--muted);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}
.tag {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 4px;
  background: #111827;
  color: #fff;
  font-weight: 600;
}
.online { font-size: 13px; color: var(--muted); }
.online.on { color: #059669; }
.sub { margin: 8px 0 0; font-size: 13px; color: var(--muted); }
.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
}
.act {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 1px solid var(--border);
  background: #fff;
  border-radius: 8px;
  padding: 8px 12px;
  cursor: pointer;
  font-size: 13px;
  color: var(--text);
}
.act:hover:not(:disabled) { background: #f9fafb; }
.act:disabled { opacity: 0.5; cursor: not-allowed; }
.act.danger { color: #b42318; border-color: #f3c6c2; }
.act .ico { font-size: 12px; line-height: 1; opacity: 0.75; }
.act .ico-svg { flex-shrink: 0; opacity: 0.8; }
.mini {
  border: 1px solid var(--border);
  background: #fff;
  border-radius: 8px;
  padding: 8px 12px;
  cursor: pointer;
  font-size: 13px;
}
.section-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 12px;
}
.section-head h2 { margin: 0 0 4px; font-size: 16px; }
.muted { color: var(--muted); font-size: 13px; margin: 0; line-height: 1.5; }
.muted strong { color: var(--text); font-weight: 600; }
.btn-dark {
  border: none;
  background: #1c2333;
  color: #fff;
  border-radius: 8px;
  padding: 8px 14px;
  cursor: pointer;
  font-size: 13px;
  font-weight: 600;
  white-space: nowrap;
}
.table-wrap {
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: 12px;
  overflow: hidden;
}
table { width: 100%; border-collapse: collapse; font-size: 13px; }
th, td { padding: 12px 16px; text-align: left; border-bottom: 1px solid var(--border); }
th { font-size: 12px; color: var(--muted); font-weight: 600; }
tr:last-child td { border-bottom: none; }
tr.clickable { cursor: pointer; }
tr.clickable:hover { background: #fafafa; }
.rt-cell {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}
.rt-name { font-weight: 600; }
.built-in {
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 4px;
  background: #f3f4f6;
  color: var(--muted);
}
.built-in.custom {
  background: #eef2ff;
  color: #4338ca;
}
.base-proto {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  border: 1px solid var(--border);
  border-radius: 10px;
  padding: 10px 12px;
  background: #fafafa;
}
.field-hint {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--muted);
}
.field .label { font-size: 13px; margin-bottom: 2px; }
.field textarea {
  padding: 8px;
  border-radius: 8px;
  border: 1px solid var(--border);
  font: inherit;
  resize: vertical;
}
.step2-actions {
  justify-content: space-between;
  align-items: center;
}
.right-actions { display: flex; gap: 8px; }
.back-btn { border: none; background: transparent; color: var(--muted); }
.health.online { color: #059669; }
.health.offline { color: #b42318; }
.empty { text-align: center; color: var(--muted); padding: 28px !important; }
.error { color: var(--danger); }
.ok { color: #065f46; }
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
  width: min(400px, 100%);
  background: #fff;
  border-radius: 12px;
  padding: 20px;
}
.modal-wide { width: min(520px, 100%); }
.modal-custom {
  width: min(640px, 100%);
  max-height: min(90vh, 820px);
  overflow: auto;
  padding: 22px 24px 18px;
}
.modal h3 { margin: 0; font-size: 16px; }
.custom-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}
.custom-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.custom-title-ico {
  color: #4b5563;
  display: inline-flex;
}
.modal-x {
  border: none;
  background: transparent;
  font-size: 22px;
  line-height: 1;
  color: #9ca3af;
  cursor: pointer;
  padding: 0 2px;
}
.modal-x:hover { color: var(--text); }
.custom-desc {
  margin: 0 0 8px;
  font-size: 13px;
  line-height: 1.55;
  color: var(--muted);
}
.guide-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: none;
  background: transparent;
  color: #2563eb;
  font-size: 13px;
  cursor: pointer;
  padding: 0;
  margin-bottom: 18px;
}
.guide-link:hover { text-decoration: underline; }
.step-label {
  margin: 0 0 6px;
  font-size: 12px;
  color: var(--muted);
}
.step-title {
  margin: 0 0 4px;
  font-size: 15px;
  font-weight: 650;
}
.step-hint {
  margin: 0 0 14px;
  font-size: 13px;
  color: var(--muted);
}
.step-hint.tip { margin-top: -4px; font-size: 12px; }
.protocol-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 14px;
}
.protocol-card {
  display: flex;
  align-items: center;
  gap: 10px;
  border: 1px solid var(--border);
  background: #fff;
  border-radius: 10px;
  padding: 10px 12px;
  cursor: pointer;
  font-size: 13px;
  font-weight: 500;
  color: var(--text);
  text-align: left;
}
.protocol-card:hover:not(.disabled) {
  border-color: #c7ccd6;
  background: #fafafa;
}
.protocol-card.disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.modal-body { font-size: 14px; margin: 0 0 12px; color: var(--text); }
.modal-body.tip { font-size: 13px; color: var(--muted); }
.field { display: flex; flex-direction: column; gap: 6px; font-size: 13px; margin-bottom: 12px; }
.field select,
.field input {
  padding: 8px;
  border-radius: 8px;
  border: 1px solid var(--border);
  font: inherit;
}
.code {
  margin: 0 0 12px;
  padding: 12px;
  border-radius: 8px;
  background: #f3f4f6;
  font-size: 12px;
  overflow-x: auto;
}
.modal-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 4px; }

@media (max-width: 640px) {
  .protocol-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
</style>
