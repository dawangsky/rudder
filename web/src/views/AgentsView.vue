<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { apiFetch } from '@/lib/api'

type Agent = { id: string; name: string; provider: string; instructions?: string; skillIds?: string[] }
const agents = ref<Agent[]>([])
const skills = ref<{ id: string; name: string }[]>([])
const form = ref({ name: '', provider: 'cursor', instructions: '', skillIds: [] as string[] })
const error = ref('')

async function load() {
  agents.value = await apiFetch('/api/agents')
  skills.value = await apiFetch('/api/skills')
}

async function create() {
  error.value = ''
  try {
    await apiFetch('/api/agents', { method: 'POST', body: JSON.stringify(form.value) })
    form.value = { name: '', provider: 'cursor', instructions: '', skillIds: [] }
    await load()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '创建失败'
  }
}

onMounted(load)
</script>

<template>
  <section class="page">
    <header class="page-header"><h2>智能体</h2></header>
    <div class="panel">
      <h3>新建 Agent</h3>
      <label>名称 <input v-model="form.name" /></label>
      <label>Provider
        <select v-model="form.provider">
          <option value="cursor">Cursor</option>
          <option value="claude_code">Claude Code</option>
          <option value="codex">Codex</option>
        </select>
      </label>
      <label>指示词（可限定领域）
        <textarea v-model="form.instructions" rows="4" />
      </label>
      <label>挂载 Skills
        <select v-model="form.skillIds" multiple>
          <option v-for="s in skills" :key="s.id" :value="s.id">{{ s.name }}</option>
        </select>
      </label>
      <p v-if="error" class="error">{{ error }}</p>
      <button class="primary" type="button" @click="create">创建</button>
    </div>
    <ul class="list">
      <li v-for="a in agents" :key="a.id">
        <strong>{{ a.name }}</strong> · {{ a.provider }}
        <div class="muted">{{ a.instructions || '无指示词' }}</div>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.panel, .list { background: var(--panel); border: 1px solid var(--border); border-radius: 12px; padding: 16px; margin-bottom: 12px; }
label { display: flex; flex-direction: column; gap: 6px; margin-bottom: 10px; }
input, select, textarea { border: 1px solid var(--border); border-radius: 8px; padding: 8px; }
.list { list-style: none; margin: 0; }
.list li { padding: 10px 0; border-bottom: 1px solid var(--border); }
</style>
