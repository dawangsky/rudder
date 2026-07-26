<script setup lang="ts">
/**
 * 设置页（对齐 Multica）：中栏二级菜单 + 右栏内容。
 * 已落地：一般、Daemon、运行时协议。
 */
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getServerBaseUrl, setServerBaseUrl } from '@/lib/config'
import { getHostBridge, type DaemonStatus } from '@/lib/hostBridge'
import { getSessionEmail } from '@/lib/session'
import AlertDialog from '@/components/AlertDialog.vue'
import ProviderIcon from '@/components/ProviderIcon.vue'
import {
  createProtocol,
  deleteProtocol,
  loadProtocols,
  protocols,
  updateProtocol,
  type ProtocolRecord,
} from '@/lib/protocols'

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
  { id: 'protocols', label: '运行时协议' },
  { id: 'workspace-general', label: '通用', soon: true },
  { id: 'members', label: '成员', soon: true },
]

const section = computed(() => String(route.params.section || 'daemon'))

const status = ref<DaemonStatus | null>(null)
const busy = ref(false)
const serverUrl = ref('')
const saved = ref(false)
const protoErr = ref('')
const protoBusy = ref(false)
const showAdd = ref(false)
const alertOpen = ref(false)
const alertTitle = ref('无法完成操作')
const alertMessage = ref('')
const editing = ref<ProtocolRecord | null>(null)
const form = ref({
  code: '',
  label: '',
  short: '',
  bins: '',
  commandHint: '',
  region: 'intl' as 'intl' | 'cn' | 'test',
})
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

async function refreshProtocols() {
  protoErr.value = ''
  try {
    await loadProtocols(true)
  } catch (e) {
    protoErr.value = e instanceof Error ? e.message : '加载协议失败'
  }
}

function showAlert(msg: string, title = '无法完成操作') {
  alertTitle.value = title
  alertMessage.value = msg
  alertOpen.value = true
}

async function toggleEnabled(p: ProtocolRecord, enabled: boolean) {
  if (protoBusy.value) return
  const prev = p.enabled !== false
  if (prev === enabled) return
  protoErr.value = ''
  protoBusy.value = true
  try {
    await updateProtocol(p.value, { enabled })
  } catch (e) {
    const msg = e instanceof Error ? e.message : '更新失败'
    protoErr.value = msg
    if (!enabled) {
      showAlert(msg, '无法停用运行时协议')
    }
    await refreshProtocols()
  } finally {
    protoBusy.value = false
  }
}

function openAdd() {
  editing.value = null
  form.value = { code: '', label: '', short: '', bins: '', commandHint: '', region: 'intl' }
  showAdd.value = true
  protoErr.value = ''
}

function openEdit(p: ProtocolRecord) {
  editing.value = p
  form.value = {
    code: p.value,
    label: p.label,
    short: p.short,
    bins: (p.bins || []).join(', '),
    commandHint: p.commandHint || '',
    region: (p.region as 'intl' | 'cn' | 'test') || 'intl',
  }
  showAdd.value = true
  protoErr.value = ''
}

function closeForm() {
  showAdd.value = false
  editing.value = null
}

async function saveForm() {
  protoErr.value = ''
  const label = form.value.label.trim()
  if (!label) {
    protoErr.value = '请填写名称'
    return
  }
  protoBusy.value = true
  try {
    if (editing.value) {
      await updateProtocol(editing.value.value, {
        label,
        short: form.value.short.trim() || label,
        bins: form.value.bins,
        commandHint: form.value.commandHint.trim(),
        region: form.value.region,
      })
    } else {
      const code = form.value.code.trim().toLowerCase()
      if (!code) {
        protoErr.value = '请填写协议标识'
        return
      }
      await createProtocol({
        code,
        label,
        short: form.value.short.trim() || label,
        bins: form.value.bins,
        commandHint: form.value.commandHint.trim(),
        region: form.value.region,
        enabled: true,
      })
    }
    closeForm()
  } catch (e) {
    protoErr.value = e instanceof Error ? e.message : '保存失败'
  } finally {
    protoBusy.value = false
  }
}

async function removeProtocol(p: ProtocolRecord) {
  if (p.builtin) return
  if (!confirm(`删除自定义协议「${p.label}」？`)) return
  protoBusy.value = true
  protoErr.value = ''
  try {
    await deleteProtocol(p.value)
  } catch (e) {
    const msg = e instanceof Error ? e.message : '删除失败'
    protoErr.value = msg
    showAlert(msg, '无法删除协议')
  } finally {
    protoBusy.value = false
  }
}

watch(
  () => route.params.section,
  () => {
    if (section.value === 'daemon') refresh()
    if (section.value === 'general') {
      serverUrl.value = getServerBaseUrl()
      saved.value = false
    }
    if (section.value === 'protocols') void refreshProtocols()
  },
)

onMounted(() => {
  if (!route.params.section) {
    router.replace('/settings/daemon')
    return
  }
  serverUrl.value = getServerBaseUrl()
  refresh()
  if (section.value === 'protocols') void refreshProtocols()
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
          :class="{ active: section === s.id, soon: s.soon }"
          :disabled="s.soon"
          :title="s.soon ? '即将推出' : undefined"
          @click="goSection(s.id, s.soon)"
        >
          {{ s.label }}
        </button>
      </div>
    </aside>

    <main class="settings-main">
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
              rudder CLI v{{ status?.cliVersion || '?' }} 已就绪
              <span v-if="status?.cliEnsureOk === false" class="warn-inline">（{{ status?.cliEnsureMessage || '版本校验失败' }}）</span>
            </template>
            <template v-else>
              未找到 rudder CLI（{{ status?.cliPath || '—' }}）。请先编译 daemon 二进制，或重启 Desktop 触发自动编译。
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
              <dt>CLI 版本</dt>
              <dd class="mono">{{ status?.cliVersion || '—' }}</dd>
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

      <div v-else-if="section === 'protocols'" class="panel-page">
        <header class="page-head row-head">
          <div>
            <h2>运行时协议</h2>
            <p class="desc">管理本工作区支持的 Agent CLI 协议。启用后可供 Daemon 自动探测与自定义运行时选用。</p>
          </div>
          <button type="button" class="primary" :disabled="protoBusy" @click="openAdd">添加协议</button>
        </header>

        <p v-if="protoErr" class="error">{{ protoErr }}</p>

        <div class="card">
          <div v-for="p in protocols" :key="p.value" class="proto-row">
            <ProviderIcon :provider="p.value" :size="28" />
            <div class="proto-meta">
              <div class="proto-title">
                <strong>{{ p.label }}</strong>
                <span class="tag" :class="{ builtin: p.builtin }">{{ p.builtin ? '内置' : '自定义' }}</span>
                <span v-if="p.region === 'cn'" class="tag region">国产</span>
              </div>
              <div class="proto-sub mono">{{ p.value }} · bins: {{ (p.bins || []).join(', ') || '—' }}</div>
            </div>
            <div class="proto-actions">
              <button type="button" class="linkish" :disabled="protoBusy" @click="openEdit(p)">编辑</button>
              <button
                v-if="!p.builtin"
                type="button"
                class="linkish danger"
                :disabled="protoBusy"
                @click="removeProtocol(p)"
              >
                删除
              </button>
              <label class="switch">
                <input
                  type="checkbox"
                  :checked="p.enabled !== false"
                  :disabled="protoBusy"
                  @click.prevent="toggleEnabled(p, !(p.enabled !== false))"
                />
                <span class="slider" />
              </label>
            </div>
          </div>
          <p v-if="!protocols.length" class="empty-proto">暂无协议，点击「添加协议」或等待种子同步。</p>
        </div>

        <div v-if="showAdd" class="modal-mask" @click.self="closeForm">
          <div class="modal">
            <h3>{{ editing ? '编辑协议' : '添加协议' }}</h3>
            <label class="field">
              协议标识
              <input
                v-model="form.code"
                type="text"
                :disabled="!!editing"
                placeholder="小写，如 my_agent"
              />
            </label>
            <label class="field">
              显示名称
              <input v-model="form.label" type="text" placeholder="例如：My Agent" />
            </label>
            <label class="field">
              短名称
              <input v-model="form.short" type="text" placeholder="可选" />
            </label>
            <label class="field">
              探测命令（bins）
              <input v-model="form.bins" type="text" placeholder="逗号分隔，如 my-cli, mycli" />
            </label>
            <label class="field">
              命令示例
              <input v-model="form.commandHint" type="text" placeholder='例如：my-cli -p "{prompt}"' />
            </label>
            <label class="field">
              地区
              <select v-model="form.region">
                <option value="intl">国际</option>
                <option value="cn">国产</option>
                <option value="test">测试</option>
              </select>
            </label>
            <p v-if="protoErr" class="error">{{ protoErr }}</p>
            <div class="modal-actions">
              <button type="button" class="mini" @click="closeForm">取消</button>
              <button type="button" class="primary" :disabled="protoBusy" @click="saveForm">
                {{ protoBusy ? '保存中…' : '保存' }}
              </button>
            </div>
          </div>
        </div>
      </div>

      <div v-else class="panel-page">
        <header class="page-head">
          <h2>{{ section }}</h2>
          <p class="desc">该设置项即将推出。</p>
        </header>
      </div>
    </main>

    <AlertDialog
      :open="alertOpen"
      :title="alertTitle"
      :message="alertMessage"
      ok-label="知道了"
      @close="alertOpen = false"
    />
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
.nav-item:hover:not(:disabled) { background: rgba(0, 0, 0, 0.04); }
.nav-item.active { background: rgba(0, 0, 0, 0.07); font-weight: 600; }
.nav-item.soon,
.nav-item:disabled { opacity: 0.4; cursor: not-allowed; }
.settings-main { flex: 1; min-width: 0; overflow: auto; background: var(--bg); }
.panel-page { padding: 28px 36px 40px; max-width: 820px; }
.page-head h2 { margin: 0 0 6px; font-size: 22px; }
.row-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.desc { margin: 0; color: var(--muted); font-size: 13px; line-height: 1.5; }
.card {
  margin-top: 20px;
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: 12px;
  overflow: hidden;
}
.card.soft { padding: 14px 16px; }
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
.warn-inline { color: var(--danger, #c44); margin-left: 6px; }
.proto-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--border);
}
.proto-row:last-child { border-bottom: none; }
.proto-meta { flex: 1; min-width: 0; }
.proto-title { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; font-size: 14px; }
.proto-sub { margin-top: 4px; font-size: 12px; color: var(--muted); }
.tag {
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 999px;
  background: #f3f4f6;
  color: var(--muted);
  font-weight: 500;
}
.tag.builtin { background: #ecfdf5; color: #047857; }
.tag.region { background: #fff7ed; color: #c2410c; }
.proto-actions { display: flex; align-items: center; gap: 10px; flex-shrink: 0; }
.linkish {
  border: none;
  background: transparent;
  color: var(--muted);
  font-size: 12px;
  cursor: pointer;
  padding: 0;
}
.linkish:hover { color: var(--text); }
.linkish.danger:hover { color: #b42318; }
.empty-proto { margin: 0; padding: 24px 16px; text-align: center; color: var(--muted); font-size: 13px; }
.modal-mask {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.35);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 40;
  padding: 20px;
}
.modal {
  width: min(440px, 100%);
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  border: 1px solid var(--border);
}
.modal h3 { margin: 0 0 12px; font-size: 16px; }
.modal-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 8px; }
.error { color: var(--danger); font-size: 13px; margin: 8px 0; }
.ok { color: #047857; font-size: 13px; }
.primary {
  border: none;
  background: #111827;
  color: #fff;
  border-radius: 8px;
  padding: 8px 14px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}
.primary:disabled { opacity: 0.5; cursor: not-allowed; }
.diag { margin-top: 28px; }
.diag h3 { margin: 0 0 4px; font-size: 15px; }
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
.actions { display: flex; gap: 8px; margin-top: 14px; }
.mini {
  border: 1px solid var(--border);
  background: var(--panel);
  border-radius: 8px;
  padding: 8px 12px;
  cursor: pointer;
  font-size: 13px;
}
.mini:disabled { opacity: 0.5; cursor: not-allowed; }
.field {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin: 12px 0;
  max-width: 480px;
}
.field input,
.field select {
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 10px 12px;
  font: inherit;
}
.switch {
  position: relative;
  width: 42px;
  height: 24px;
  flex-shrink: 0;
}
.switch input { opacity: 0; width: 0; height: 0; }
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
.switch input:checked + .slider { background: #22c55e; }
.switch input:checked + .slider::before { transform: translateX(18px); }
@media (max-width: 900px) {
  .settings-nav { width: 180px; }
  .panel-page { padding: 20px; }
  .kv-row { grid-template-columns: 110px 1fr; }
  .row-head { flex-direction: column; }
  .proto-row { flex-wrap: wrap; }
}
</style>
