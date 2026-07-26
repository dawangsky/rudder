<script setup lang="ts">
/**
 * 聊天：中栏会话列表 + 右侧对话区（对齐 Multica 空态与无智能体提示）。
 */
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { apiFetch } from '@/lib/api'
import { formatRelative } from '@/lib/agents'

type Agent = { id: string; name: string; provider?: string }
type Session = {
  id: string
  title: string
  agentName: string
  agentId: string
  updatedAt?: string
}
type Msg = { id: string; role: string; content: string }

const router = useRouter()
const agents = ref<Agent[]>([])
const sessions = ref<Session[]>([])
const projects = ref<{ id: string; name: string }[]>([])
const currentId = ref('')
const currentAgentName = ref('')
const currentAgentProvider = ref('')
const messages = ref<Msg[]>([])
const draft = ref('')
const projectId = ref('')
const agentId = ref('')
const showNew = ref(false)
const sending = ref(false)
const err = ref('')
const msgsEl = ref<HTMLElement | null>(null)
let ws: WebSocket | null = null

const hasAgents = computed(() => agents.value.length > 0)
const current = computed(() => sessions.value.find((s) => s.id === currentId.value))
const composerDisabled = computed(() => !hasAgents.value || !currentId.value || sending.value)

async function load() {
  agents.value = await apiFetch('/api/agents')
  sessions.value = await apiFetch('/api/chats')
  projects.value = await apiFetch('/api/projects')
  if (!agentId.value && agents.value.length) agentId.value = agents.value[0].id
  if (currentId.value && !sessions.value.some((s) => s.id === currentId.value)) {
    currentId.value = ''
    messages.value = []
  }
}

async function openSession(id: string) {
  err.value = ''
  currentId.value = id
  const data = await apiFetch<{
    agent: { name: string; provider?: string }
    messages: Msg[]
  }>(`/api/chats/${id}`)
  currentAgentName.value = data.agent.name
  currentAgentProvider.value = data.agent.provider || ''
  messages.value = data.messages
  await nextTick()
  if (msgsEl.value) msgsEl.value.scrollTop = msgsEl.value.scrollHeight
}

function openNewModal() {
  err.value = ''
  if (!hasAgents.value) {
    router.push({ name: 'agent-create' })
    return
  }
  if (!agentId.value && agents.value.length) agentId.value = agents.value[0].id
  showNew.value = true
}

async function createChat() {
  err.value = ''
  if (!agentId.value) {
    err.value = '请选择智能体'
    return
  }
  try {
    const body: Record<string, string> = { agentId: agentId.value }
    if (projectId.value) body.projectId = projectId.value
    const s = await apiFetch<Session>('/api/chats', {
      method: 'POST',
      body: JSON.stringify(body),
    })
    showNew.value = false
    await load()
    await openSession(s.id)
  } catch (e) {
    err.value = e instanceof Error ? e.message : '创建失败'
  }
}

async function send() {
  if (!currentId.value || !draft.value.trim() || sending.value) return
  const content = draft.value.trim()
  draft.value = ''
  sending.value = true
  err.value = ''
  try {
    await apiFetch(`/api/chats/${currentId.value}/messages`, {
      method: 'POST',
      body: JSON.stringify({ content }),
    })
    await openSession(currentId.value)
    await load()
  } catch (e) {
    draft.value = content
    err.value = e instanceof Error ? e.message : '发送失败'
  } finally {
    sending.value = false
  }
}

function goCreateAgent() {
  router.push({ name: 'agent-create' })
}

function connectWs() {
  const wsId = sessionStorage.getItem('rudder_workspace_id')
  if (!wsId) return
  const proto = location.protocol === 'https:' ? 'wss' : 'ws'
  // Desktop/本地默认 Netty WS 8081
  const host = location.hostname === 'localhost' || location.hostname === '127.0.0.1'
    ? '127.0.0.1:8081'
    : `${location.hostname}:8081`
  ws = new WebSocket(`${proto}://${host}/ws?workspaceId=${wsId}`)
  ws.onmessage = async (ev) => {
    try {
      const data = JSON.parse(ev.data)
      if (data.type === 'chat.message' && String(data.sessionId) === currentId.value) {
        await openSession(currentId.value)
      }
      if (data.type === 'chat.message') await load()
    } catch {
      /* ignore */
    }
  }
}

onMounted(async () => {
  await load()
  connectWs()
})
onUnmounted(() => ws?.close())
</script>

<template>
  <section class="chat-shell">
    <!-- 中栏：会话列表 -->
    <aside class="list-pane">
      <header class="list-header">
        <h2>聊天</h2>
        <button type="button" class="icon-btn" title="新建对话" aria-label="新建对话" @click="openNewModal">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <path d="M12 5v14M5 12h14" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
          </svg>
        </button>
      </header>

      <div v-if="!sessions.length" class="list-empty">暂无历史对话</div>
      <ul v-else class="session-list">
        <li
          v-for="s in sessions"
          :key="s.id"
          class="session"
          :class="{ active: s.id === currentId }"
          @click="openSession(s.id)"
        >
          <div class="session-title">{{ s.title }}</div>
          <div class="session-meta">
            <span>{{ s.agentName }}</span>
            <span>{{ formatRelative(s.updatedAt) }}</span>
          </div>
        </li>
      </ul>
    </aside>

    <!-- 右侧：对话区 -->
    <main class="chat-pane">
      <!-- 未选中会话 -->
      <div v-if="!currentId" class="empty-main">
        <template v-if="hasAgents">
          <svg class="empty-ico" width="56" height="56" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <path
              d="M5 6.5A2.5 2.5 0 0 1 7.5 4h9A2.5 2.5 0 0 1 19 6.5v6A2.5 2.5 0 0 1 16.5 15H10l-4 3.5V15H7.5A2.5 2.5 0 0 1 5 12.5v-6Z"
              stroke="currentColor"
              stroke-width="1.4"
            />
          </svg>
          <p class="empty-title">选择一个对话，或点 + 新建</p>
        </template>
        <template v-else>
          <p class="empty-title soft">和你的智能体对话</p>
        </template>
      </div>

      <!-- 已选中会话 -->
      <template v-else>
        <header class="chat-top">
          <div>
            <h3>{{ current?.title || '对话' }}</h3>
            <p class="chat-sub">{{ currentAgentName || current?.agentName }}</p>
          </div>
        </header>
        <div ref="msgsEl" class="msgs">
          <div v-if="!messages.length" class="msgs-empty">发送第一条消息，智能体将开始执行</div>
          <div v-for="m in messages" :key="m.id" class="msg" :class="m.role">
            <div class="msg-role">{{ m.role === 'user' ? '我' : currentAgentName || 'Agent' }}</div>
            <pre class="msg-body">{{ m.content }}</pre>
          </div>
        </div>
      </template>

      <p v-if="err" class="error banner">{{ err }}</p>

      <!-- 底部输入 -->
      <footer class="composer-wrap">
        <div v-if="!hasAgents" class="need-agent">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <rect x="6" y="7" width="12" height="10" rx="2" stroke="currentColor" stroke-width="1.6" />
            <path d="M10 11h.01M14 11h.01M9 14.5h6M12 4v3" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
          </svg>
          <span>需要先有一个智能体才能开始对话。</span>
          <button type="button" class="link" @click="goCreateAgent">去创建</button>
        </div>

        <form class="composer" @submit.prevent="send">
          <input
            v-model="draft"
            type="text"
            :placeholder="
              !hasAgents
                ? '创建一个智能体后才能开始对话'
                : !currentId
                  ? '先选择或新建一个对话'
                  : '发送消息即派活…'
            "
            :disabled="composerDisabled || !hasAgents"
          />
          <button
            type="submit"
            class="send"
            :disabled="composerDisabled || !draft.trim() || !hasAgents"
            aria-label="发送"
          >
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path d="M12 19V5M12 5l-5 5M12 5l5 5" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
          </button>
        </form>
      </footer>
    </main>

    <!-- 新建对话 -->
    <div v-if="showNew" class="modal-backdrop" @click.self="showNew = false">
      <div class="modal">
        <h3>新建对话</h3>
        <p class="modal-body">选择智能体后开始聊天。发送消息即派活给该智能体。</p>
        <label class="field">
          智能体
          <select v-model="agentId">
            <option v-for="a in agents" :key="a.id" :value="a.id">{{ a.name }}</option>
          </select>
        </label>
        <label class="field">
          项目（可选）
          <select v-model="projectId">
            <option value="">无项目（沙箱目录）</option>
            <option v-for="p in projects" :key="p.id" :value="p.id">{{ p.name }}</option>
          </select>
        </label>
        <p v-if="err" class="error">{{ err }}</p>
        <div class="modal-actions">
          <button type="button" class="mini" @click="showNew = false">取消</button>
          <button type="button" class="btn-dark" @click="createChat">开始对话</button>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.chat-shell {
  display: flex;
  margin: -24px;
  height: calc(100% + 48px);
  min-height: 520px;
  background: var(--panel);
  flex: 1;
}

.list-pane {
  width: 280px;
  flex-shrink: 0;
  border-right: 1px solid var(--border);
  display: flex;
  flex-direction: column;
  background: var(--panel);
}
.list-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 14px 12px;
}
.list-header h2 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
}
.icon-btn {
  border: none;
  background: transparent;
  color: var(--muted);
  padding: 4px;
  border-radius: 6px;
  cursor: pointer;
  line-height: 0;
}
.icon-btn:hover {
  background: var(--bg);
  color: var(--text);
}
.list-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--muted);
  font-size: 13px;
  padding: 24px;
}
.session-list {
  list-style: none;
  margin: 0;
  padding: 4px 8px 16px;
  overflow: auto;
  flex: 1;
}
.session {
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  margin-bottom: 2px;
}
.session:hover,
.session.active {
  background: var(--bg);
}
.session-title {
  font-size: 13px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.session-meta {
  margin-top: 4px;
  display: flex;
  justify-content: space-between;
  gap: 8px;
  font-size: 12px;
  color: var(--muted);
}

.chat-pane {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: #fff;
}
.empty-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: #d1d5db;
}
.empty-ico { opacity: 0.7; }
.empty-title {
  margin: 0;
  font-size: 14px;
  color: var(--muted);
}
.empty-title.soft {
  font-size: 18px;
  color: #9ca3af;
  font-weight: 500;
}

.chat-top {
  padding: 16px 20px 12px;
  border-bottom: 1px solid var(--border);
}
.chat-top h3 {
  margin: 0;
  font-size: 15px;
}
.chat-sub {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--muted);
}
.msgs {
  flex: 1;
  overflow: auto;
  padding: 16px 20px;
}
.msgs-empty {
  color: var(--muted);
  font-size: 13px;
  text-align: center;
  margin-top: 40px;
}
.msg {
  margin-bottom: 16px;
  max-width: 720px;
}
.msg-role {
  font-size: 12px;
  font-weight: 600;
  color: var(--muted);
  margin-bottom: 4px;
}
.msg.user .msg-role { color: #4338ca; }
.msg-body {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font: inherit;
  font-size: 14px;
  line-height: 1.55;
  background: #f9fafb;
  border-radius: 10px;
  padding: 10px 12px;
}
.msg.user .msg-body {
  background: #eef2ff;
}

.composer-wrap {
  padding: 12px 16px 16px;
  border-top: 1px solid transparent;
}
.need-agent {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  padding: 10px 12px;
  border-radius: 10px;
  background: #f3f4f6;
  color: #374151;
  font-size: 13px;
}
.need-agent .link {
  border: none;
  background: transparent;
  color: #2563eb;
  cursor: pointer;
  font-size: 13px;
  font-weight: 600;
  margin-left: auto;
}
.composer {
  display: flex;
  align-items: center;
  gap: 8px;
  background: #f3f4f6;
  border-radius: 14px;
  padding: 8px 8px 8px 14px;
}
.composer input {
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  font: inherit;
  font-size: 14px;
  min-width: 0;
}
.composer input:disabled {
  color: #9ca3af;
}
.send {
  width: 36px;
  height: 36px;
  border: none;
  border-radius: 50%;
  background: #d1d5db;
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  flex-shrink: 0;
}
.send:not(:disabled) {
  background: #111827;
}
.send:disabled {
  cursor: not-allowed;
  opacity: 0.7;
}

.banner { margin: 0 16px 8px; font-size: 13px; }
.error { color: var(--danger); }

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
.modal h3 { margin: 0 0 8px; }
.modal-body { margin: 0 0 14px; font-size: 13px; color: var(--muted); }
.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13px;
  margin-bottom: 12px;
}
.field select {
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 8px;
  font: inherit;
}
.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
.mini {
  border: 1px solid var(--border);
  background: #fff;
  border-radius: 8px;
  padding: 8px 12px;
  cursor: pointer;
  font-size: 13px;
}
.btn-dark {
  border: none;
  background: #1c2333;
  color: #fff;
  border-radius: 8px;
  padding: 8px 14px;
  cursor: pointer;
  font-size: 13px;
  font-weight: 600;
}
</style>
