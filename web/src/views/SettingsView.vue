<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getServerBaseUrl, setServerBaseUrl } from '@/lib/config'
import { apiFetch } from '@/lib/api'

const serverUrl = ref('')
const saved = ref(false)
const projectName = ref('')
const localPath = ref('')
const projects = ref<any[]>([])
const err = ref('')

onMounted(async () => {
  serverUrl.value = getServerBaseUrl()
  projects.value = await apiFetch('/api/projects')
})

function save() {
  setServerBaseUrl(serverUrl.value.trim())
  saved.value = true
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
    projects.value = await apiFetch('/api/projects')
  } catch (e) {
    err.value = e instanceof Error ? e.message : '创建失败'
  }
}
</script>

<template>
  <section class="page">
    <header class="page-header"><h2>设置</h2></header>
    <label class="field">
      Server 地址（Self-Host）
      <input v-model="serverUrl" type="url" placeholder="http://127.0.0.1:8080" />
    </label>
    <button type="button" class="primary" @click="save">保存</button>
    <p v-if="saved" class="ok">已保存到本地。</p>

    <h3>项目本地路径</h3>
    <p class="muted">配置后，关联该项目的 Chat/Issue 将优先在此目录执行 Agent。</p>
    <label class="field">名称 <input v-model="projectName" /></label>
    <label class="field">本机绝对路径 <input v-model="localPath" placeholder="/Users/you/code/app" /></label>
    <p v-if="err" class="error">{{ err }}</p>
    <button type="button" class="primary" @click="createProject">创建项目</button>
    <ul>
      <li v-for="p in projects" :key="p.id">{{ p.name }} — {{ p.localPath || '（无本地路径，用沙箱）' }}</li>
    </ul>
  </section>
</template>
