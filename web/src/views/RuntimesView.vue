<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { apiFetch } from '@/lib/api'

const runtimes = ref<any[]>([])
async function load() { runtimes.value = await apiFetch('/api/runtimes') }
onMounted(() => { load(); setInterval(load, 5000) })
</script>

<template>
  <section class="page">
    <header class="page-header"><h2>运行时</h2></header>
    <p class="muted">本机 `rudder daemon start` 后，这里会显示在线 Runtime 与心跳。</p>
    <ul class="list">
      <li v-for="r in runtimes" :key="r.id">
        <strong>{{ r.provider }}</strong> · {{ r.status }} · {{ r.hostName }}
        <div class="muted">心跳 {{ r.lastHeartbeatAt || '-' }}</div>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.list { list-style:none; background:var(--panel); border:1px solid var(--border); border-radius:12px; padding:16px; }
li { padding:10px 0; border-bottom:1px solid var(--border); }
</style>
