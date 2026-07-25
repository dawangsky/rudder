<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { apiFetch } from '@/lib/api'

const items = ref<any[]>([])
const unread = ref(0)
async function load() {
  const data = await apiFetch<{ items: any[]; unread: number }>('/api/inbox')
  items.value = data.items
  unread.value = data.unread
}
async function mark(id: string) {
  await apiFetch(`/api/inbox/${id}/read`, { method: 'POST', body: '{}' })
  await load()
}
onMounted(load)
</script>

<template>
  <section class="page">
    <header class="page-header"><h2>收件箱</h2><span class="muted">未读 {{ unread }}</span></header>
    <ul class="list">
      <li v-for="i in items" :key="i.id" :class="{ unread: !i.read }">
        <strong>{{ i.title }}</strong>
        <div class="muted">{{ i.body }}</div>
        <button v-if="!i.read" type="button" class="mini" @click="mark(i.id)">标为已读</button>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.list { list-style:none; background:var(--panel); border:1px solid var(--border); border-radius:12px; padding:16px; }
li { padding:12px 0; border-bottom:1px solid var(--border); }
.unread { background: var(--accent-soft); margin: 0 -8px; padding: 12px 8px; border-radius: 8px; }
.mini { margin-top:6px; border:1px solid var(--border); background:#fff; border-radius:6px; padding:4px 8px; cursor:pointer; }
</style>
