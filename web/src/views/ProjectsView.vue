<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { apiFetch } from '@/lib/api'

const projectName = ref('')
const localPath = ref('')
const projects = ref<{ id: string; name: string; localPath?: string }[]>([])
const err = ref('')

async function load() {
  projects.value = await apiFetch('/api/projects')
}

async function createProject() {
  err.value = ''
  try {
    await apiFetch('/api/projects', {
      method: 'POST',
      body: JSON.stringify({ name: projectName.value, localPath: localPath.value || null }),
    })
    projectName.value = ''
    localPath.value = ''
    await load()
  } catch (e) {
    err.value = e instanceof Error ? e.message : '创建失败'
  }
}

onMounted(load)
</script>

<template>
  <section class="page">
    <header class="page-header"><h2>项目</h2></header>
    <p class="muted">配置本机路径后，关联该项目的 Chat/Issue 将优先在此目录执行 Agent。</p>
    <label class="field">名称 <input v-model="projectName" /></label>
    <label class="field">本机绝对路径 <input v-model="localPath" placeholder="/Users/you/code/app" /></label>
    <p v-if="err" class="error">{{ err }}</p>
    <button type="button" class="primary" @click="createProject">创建项目</button>
    <ul class="list">
      <li v-for="p in projects" :key="p.id">
        <strong>{{ p.name }}</strong>
        <div class="muted">{{ p.localPath || '（无本地路径，用沙箱）' }}</div>
      </li>
      <li v-if="!projects.length" class="muted">暂无项目</li>
    </ul>
  </section>
</template>

<style scoped>
.list {
  list-style: none;
  margin: 16px 0 0;
  padding: 0;
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: 12px;
}
li {
  padding: 12px 16px;
  border-bottom: 1px solid var(--border);
}
li:last-child { border-bottom: none; }
</style>
