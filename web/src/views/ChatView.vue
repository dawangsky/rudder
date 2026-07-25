<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { apiFetch } from '@/lib/api'

type Agent = { id: string; name: string }
type Session = { id: string; title: string; agentName: string; agentId: string }
type Msg = { id: string; role: string; content: string }

const agents = ref<Agent[]>([])
const sessions = ref<Session[]>([])
const currentId = ref('')
const currentAgentName = ref('')
const messages = ref<Msg[]>([])
const draft = ref('')
const projects = ref<{ id: string; name: string }[]>([])
const projectId = ref('')
const agentId = ref('')
let ws: WebSocket | null = null

const current = computed(() => sessions.value.find(s => s.id === currentId.value))

async function load() {
  agents.value = await apiFetch('/api/agents')
  sessions.value = await apiFetch('/api/chats')
  projects.value = await apiFetch('/api/projects')
  if (!agentId.value && agents.value.length) agentId.value = agents.value[0].id
}

async function openSession(id: string) {
  currentId.value = id
  const data = await apiFetch<{ agent: { name: string }; messages: Msg[] }>(`/api/chats/${id}`)
  currentAgentName.value = data.agent.name
  messages.value = data.messages
}

async function newChat() {
  if (!agentId.value) {
    alert('请先创建并选择智能体')
    return
  }
  const body: Record<string, string> = { agentId: agentId.value }
  if (projectId.value) body.projectId = projectId.value
  const s = await apiFetch<Session>('/api/chats', { method: 'POST', body: JSON.stringify(body) })
  await load()
  await openSession(s.id)
}

async function send() {
  if (!currentId.value || !draft.value.trim()) return
  const content = draft.value
  draft.value = ''
  await apiFetch(`/api/chats/${currentId.value}/messages`, { method: 'POST', body: JSON.stringify({ content }) })
  await openSession(currentId.value)
}

function connectWs() {
  const wsId = sessionStorage.getItem('rudder_workspace_id')
  if (!wsId) return
  ws = new WebSocket(`ws://127.0.0.1:8081/ws?workspaceId=${wsId}`)
  ws.onmessage = async (ev) => {
    try {
      const data = JSON.parse(ev.data)
      if (data.type === 'chat.message' && String(data.sessionId) === currentId.value) {
        await openSession(currentId.value)
      }
    } catch { /* ignore */ }
  }
}

onMounted(async () => { await load(); connectWs() })
onUnmounted(() => ws?.close())
</script>

<template>
  <section class="page">
    <header class="page-header">
      <h2>对话</h2>
      <div class="row">
        <select v-model="agentId">
          <option disabled value="">选择智能体</option>
          <option v-for="a in agents" :key="a.id" :value="a.id">{{ a.name }}</option>
        </select>
        <select v-model="projectId">
          <option value="">无项目（沙箱目录）</option>
          <option v-for="p in projects" :key="p.id" :value="p.id">{{ p.name }}</option>
        </select>
        <button class="primary" type="button" @click="newChat">新建聊天</button>
      </div>
    </header>
    <div class="chat-layout">
      <aside class="session-list">
        <button v-for="s in sessions" :key="s.id" type="button" class="session" :class="{active: s.id===currentId}" @click="openSession(s.id)">
          <div>{{ s.title }}</div>
          <div class="muted">{{ s.agentName }}</div>
        </button>
        <p v-if="!sessions.length" class="muted">暂无会话。请先创建智能体，再新建聊天。</p>
      </aside>
      <div class="chat-main">
        <div v-if="currentId" class="agent-bar">当前智能体：{{ currentAgentName || current?.agentName }}</div>
        <div class="msgs">
          <div v-for="m in messages" :key="m.id" class="msg" :class="m.role">
            <strong>{{ m.role === 'user' ? '我' : 'Agent' }}</strong>
            <pre>{{ m.content }}</pre>
          </div>
        </div>
        <form class="composer" @submit.prevent="send">
          <input v-model="draft" placeholder="发送消息即派活…" :disabled="!currentId" />
          <button class="primary" type="submit" :disabled="!currentId">发送</button>
        </form>
      </div>
    </div>
  </section>
</template>

<style scoped>
.row { display:flex; gap:8px; align-items:center; flex-wrap: wrap; }
.session { display:block; width:100%; text-align:left; border:none; background:transparent; padding:10px; border-radius:8px; cursor:pointer; }
.session.active { background: var(--accent-soft); }
.agent-bar { font-weight:600; margin-bottom:8px; }
.msgs { min-height: 40vh; max-height: 55vh; overflow:auto; }
.msg { margin-bottom: 12px; }
.msg pre { white-space: pre-wrap; margin: 4px 0 0; }
.composer { display:flex; gap:8px; margin-top:12px; }
.composer input { flex:1; border:1px solid var(--border); border-radius:8px; padding:10px; }
select { border:1px solid var(--border); border-radius:8px; padding:8px; }
</style>
