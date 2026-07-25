<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { apiFetch } from '@/lib/api'

const items = ref<{ id: string; name: string; content: string }[]>([])
const name = ref('')
const content = ref('')

async function load() { items.value = await apiFetch('/api/skills') }
async function create() {
  await apiFetch('/api/skills', { method: 'POST', body: JSON.stringify({ name: name.value, content: content.value }) })
  name.value = ''; content.value = ''; await load()
}
onMounted(load)
</script>

<template>
  <section class="page">
    <header class="page-header"><h2>技能</h2></header>
    <div class="panel">
      <label>名称 <input v-model="name" /></label>
      <label>Markdown 内容 <textarea v-model="content" rows="6" /></label>
      <button class="primary" type="button" @click="create">创建 Skill</button>
    </div>
    <ul class="list">
      <li v-for="s in items" :key="s.id"><strong>{{ s.name }}</strong><pre>{{ s.content }}</pre></li>
    </ul>
  </section>
</template>

<style scoped>
.panel, .list { background: var(--panel); border: 1px solid var(--border); border-radius: 12px; padding: 16px; }
label { display:flex; flex-direction:column; gap:6px; margin-bottom:10px; }
input, textarea { border:1px solid var(--border); border-radius:8px; padding:8px; }
.list { list-style:none; margin-top:12px; }
pre { white-space: pre-wrap; color: var(--muted); }
</style>
