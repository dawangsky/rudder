<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { apiFetch } from '@/lib/api'

type Agent = { id: string; name: string }
type Issue = { id: string; title: string; status: string; priority: string; assigneeType?: string; assigneeId?: string }

const issues = ref<Issue[]>([])
const agents = ref<Agent[]>([])
const title = ref('')
const description = ref('')
const assigneeId = ref('')
const selected = ref<any>(null)
const comment = ref('')
const view = ref<'list' | 'board'>('list')

async function load() {
  issues.value = await apiFetch('/api/issues')
  agents.value = await apiFetch('/api/agents')
}

async function create() {
  const body: Record<string, string> = { title: title.value, description: description.value }
  if (assigneeId.value) {
    body.assigneeType = 'agent'
    body.assigneeId = assigneeId.value
  }
  await apiFetch('/api/issues', { method: 'POST', body: JSON.stringify(body) })
  title.value = ''; description.value = ''; assigneeId.value = ''
  await load()
}

async function openIssue(id: string) {
  selected.value = await apiFetch(`/api/issues/${id}`)
}

async function sendComment() {
  await apiFetch(`/api/issues/${selected.value.id}/comments`, {
    method: 'POST',
    body: JSON.stringify({ content: comment.value }),
  })
  comment.value = ''
  await openIssue(selected.value.id)
  await load()
}

const columns = ['todo', 'doing', 'done']

onMounted(load)
</script>

<template>
  <section class="page">
    <header class="page-header">
      <h2>议题</h2>
      <div>
        <button type="button" class="mini" @click="view='list'">列表</button>
        <button type="button" class="mini" @click="view='board'">看板</button>
      </div>
    </header>
    <div class="panel">
      <input v-model="title" placeholder="标题" />
      <textarea v-model="description" placeholder="描述" rows="3" />
      <select v-model="assigneeId">
        <option value="">不指派</option>
        <option v-for="a in agents" :key="a.id" :value="a.id">指派 {{ a.name }}</option>
      </select>
      <button class="primary" type="button" @click="create">创建 Issue</button>
    </div>

    <div v-if="view==='list'" class="panel">
      <div v-for="i in issues" :key="i.id" class="row" @click="openIssue(i.id)">
        <strong>{{ i.title }}</strong>
        <span class="muted">{{ i.status }} / {{ i.priority }}</span>
      </div>
    </div>

    <div v-else class="board">
      <div v-for="col in columns" :key="col" class="col">
        <h4>{{ col }}</h4>
        <div v-for="i in issues.filter(x => x.status===col)" :key="i.id" class="card" @click="openIssue(i.id)">{{ i.title }}</div>
      </div>
    </div>

    <div v-if="selected" class="panel">
      <h3>{{ selected.title }}</h3>
      <p>{{ selected.description }}</p>
      <h4>任务</h4>
      <div v-for="t in selected.tasks || []" :key="t.id" class="muted">{{ t.status }} — {{ t.triggerSource }}</div>
      <h4>评论</h4>
      <div v-for="c in selected.comments || []" :key="c.id"><pre>{{ c.content }}</pre></div>
      <textarea v-model="comment" placeholder="@AgentName 追加执行…" rows="3" />
      <button class="primary" type="button" @click="sendComment">发送评论</button>
    </div>
  </section>
</template>

<style scoped>
.panel { background:var(--panel); border:1px solid var(--border); border-radius:12px; padding:16px; margin-bottom:12px; }
input, textarea, select { width:100%; margin-bottom:8px; border:1px solid var(--border); border-radius:8px; padding:8px; }
.row { display:flex; justify-content:space-between; padding:8px 0; border-bottom:1px solid var(--border); cursor:pointer; }
.board { display:grid; grid-template-columns: repeat(3, 1fr); gap:12px; }
.col { background:var(--panel); border:1px solid var(--border); border-radius:12px; padding:12px; min-height:200px; }
.card { background:var(--bg); border-radius:8px; padding:10px; margin-bottom:8px; cursor:pointer; }
.mini { margin-left:6px; border:1px solid var(--border); background:#fff; border-radius:6px; padding:6px 10px; cursor:pointer; }
pre { white-space: pre-wrap; }
</style>
